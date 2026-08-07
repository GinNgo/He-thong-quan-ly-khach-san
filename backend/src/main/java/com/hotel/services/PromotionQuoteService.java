package com.hotel.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.dtos.PromotionQuoteDTO;
import com.hotel.dtos.PromotionQuoteRequest;
import com.hotel.entities.CustomerMembership;
import com.hotel.entities.Hotel;
import com.hotel.entities.PromotionCampaign;
import com.hotel.entities.PromotionRedemption;
import com.hotel.entities.Reservation;
import com.hotel.entities.RoomType;
import com.hotel.entities.User;
import com.hotel.repositories.CustomerMembershipRepository;
import com.hotel.repositories.PromotionCampaignRepository;
import com.hotel.repositories.PromotionRedemptionRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.util.VietnameseTextNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The only service allowed to turn a room price and promotion policy into a
 * payable quote. Search, detail and booking use this same calculation.
 */
@Service
public class PromotionQuoteService {

    public static final String CURRENCY = "VND";
    public static final BigDecimal TAX_RATE = new BigDecimal("0.15");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(0);
    private static final Duration QUOTE_TTL = Duration.ofMinutes(10);

    private final PromotionCampaignRepository campaignRepository;
    private final PromotionRedemptionRepository redemptionRepository;
    private final CustomerMembershipRepository membershipRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomAvailabilityService roomAvailabilityService;
    private final PublicInventoryEligibilityPolicy publicInventoryEligibilityPolicy;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PromotionQuoteService(
            PromotionCampaignRepository campaignRepository,
            PromotionRedemptionRepository redemptionRepository,
            CustomerMembershipRepository membershipRepository,
            RoomTypeRepository roomTypeRepository,
            RoomAvailabilityService roomAvailabilityService,
            PublicInventoryEligibilityPolicy publicInventoryEligibilityPolicy,
            ObjectMapper objectMapper,
            Clock clock) {
        this.campaignRepository = campaignRepository;
        this.redemptionRepository = redemptionRepository;
        this.membershipRepository = membershipRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.roomAvailabilityService = roomAvailabilityService;
        this.publicInventoryEligibilityPolicy = publicInventoryEligibilityPolicy;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PromotionQuoteDTO quote(PromotionQuoteRequest request, Long customerId) {
        validateRequest(request);
        RoomType roomType = roomTypeRepository.findById(request.roomTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy loại phòng."));
        if (roomType.getHotel() == null || !Objects.equals(roomType.getHotel().getId(), request.propertyId())) {
            throw new IllegalArgumentException("Loại phòng không thuộc cơ sở đã chọn.");
        }
        publicInventoryEligibilityPolicy.requirePublicProperty(request.propertyId());
        if (!publicInventoryEligibilityPolicy.isPubliclySellable(roomType)) {
            throw new IllegalArgumentException("Loại phòng hiện không được mở bán.");
        }
        roomAvailabilityService.validateCapacity(
                roomType, request.quantity(), request.adultCount(), request.childCount());
        long available = roomAvailabilityService.countAvailableRooms(
                roomType.getId(), request.checkInDate(), request.checkOutDate());
        if (available < request.quantity()) {
            throw new IllegalStateException("Loại phòng không đủ số lượng trong khoảng ngày đã chọn.");
        }
        return quoteForRoom(
                roomType,
                request.checkInDate(),
                request.checkOutDate(),
                request.quantity(),
                request.adultCount(),
                request.childCount(),
                request.couponCode(),
                customerId);
    }

    @Transactional(readOnly = true)
    public PromotionQuoteDTO quoteForRoom(
            RoomType roomType,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            int quantity,
            int adultCount,
            int childCount,
            String couponCode,
            Long customerId) {
        if (roomType == null || roomType.getHotel() == null || roomType.getBasePrice() == null) {
            throw new IllegalArgumentException("Thông tin loại phòng chưa đủ để báo giá.");
        }
        if (quantity < 1 || adultCount < 1 || childCount < 0) {
            throw new IllegalArgumentException("Số phòng hoặc số khách không hợp lệ.");
        }
        if (checkInDate == null || checkOutDate == null || !checkOutDate.isAfter(checkInDate)) {
            throw new IllegalArgumentException("Ngày trả phòng phải sau ngày nhận phòng.");
        }
        roomAvailabilityService.validateCapacity(roomType, quantity, adultCount, childCount);

        long nights = ChronoUnit.DAYS.between(checkInDate, checkOutDate);
        BigDecimal nightlyPrice = money(roomType.getBasePrice());
        BigDecimal baseSubtotal = money(nightlyPrice.multiply(BigDecimal.valueOf(nights * (long) quantity)));
        BigDecimal taxAmount = money(baseSubtotal.multiply(TAX_RATE));
        BigDecimal feeAmount = ZERO;
        Instant now = clock.instant();
        List<Long> hotelScope = roomType.getHotel().getId() == null
                ? List.of()
                : List.of(roomType.getHotel().getId());
        MemberContext member = resolveMember(customerId, roomType.getHotel().getId(), now);

        List<PromotionCampaign> campaigns = campaignRepository.findEligibleCampaigns(hotelScope, null, now);
        List<PromotionCandidate> automatic = campaigns.stream()
                .filter(campaign -> activeAt(campaign, now))
                .filter(campaign -> "AUTOMATIC".equals(campaign.getApplicationType()))
                .filter(campaign -> eligible(campaign, roomType, checkInDate, checkOutDate, quantity,
                        adultCount, childCount, baseSubtotal, member))
                .map(campaign -> candidate(campaign, baseSubtotal, baseSubtotal, customerId))
                .filter(Objects::nonNull)
                .sorted(candidateOrder())
                .toList();

        List<PromotionCandidate> applied = new ArrayList<>();
        BigDecimal remainingSubtotal = baseSubtotal;
        PromotionCandidate automaticWinner = automatic.isEmpty() ? null : automatic.get(0);
        if (automaticWinner != null) {
            applied.add(automaticWinner);
            remainingSubtotal = money(remainingSubtotal.subtract(automaticWinner.discountAmount()));
        }

        String normalizedCoupon = normalizeCode(couponCode);
        if (!normalizedCoupon.isBlank()
                && (automaticWinner == null
                || ("ALLOW_ONE_COUPON".equals(automaticWinner.campaign().getStackingPolicy())))) {
            BigDecimal couponBase = remainingSubtotal;
            List<PromotionCandidate> coupons = campaigns.stream()
                    .filter(campaign -> activeAt(campaign, now))
                    .filter(campaign -> "COUPON".equals(campaign.getApplicationType()))
                    .filter(campaign -> normalizedCoupon.equals(normalizeCode(campaign.getCode())))
                    .filter(campaign -> eligible(campaign, roomType, checkInDate, checkOutDate, quantity,
                            adultCount, childCount, baseSubtotal, member))
                    .filter(campaign -> automaticWinner == null
                            || "ALLOW_ONE_COUPON".equals(campaign.getStackingPolicy()))
                    .map(campaign -> candidate(campaign, baseSubtotal, couponBase, customerId))
                    .filter(Objects::nonNull)
                    .sorted(candidateOrder())
                    .toList();
            if (!coupons.isEmpty()) {
                PromotionCandidate coupon = coupons.get(0);
                applied.add(coupon);
                remainingSubtotal = money(remainingSubtotal.subtract(coupon.discountAmount()));
            }
        }

        BigDecimal discount = money(baseSubtotal.subtract(remainingSubtotal));
        BigDecimal finalTotal = money(remainingSubtotal.add(taxAmount).add(feeAmount));
        return new PromotionQuoteDTO(
                UUID.randomUUID().toString(),
                now.plus(QUOTE_TTL),
                roomType.getHotel().getId(),
                roomType.getId(),
                nightlyPrice,
                (int) nights,
                quantity,
                baseSubtotal,
                taxAmount,
                feeAmount,
                money(taxAmount.add(feeAmount)),
                applied.stream().map(this::toAppliedPromotion).toList(),
                member.toDto(),
                discount,
                finalTotal,
                CURRENCY);
    }

    /** Copy the accepted quote into immutable booking columns before persistence. */
    public void captureSnapshot(Reservation reservation, PromotionQuoteDTO quote) {
        if (reservation == null || quote == null) {
            throw new IllegalArgumentException("Reservation and quote are required.");
        }
        reservation.setPricingQuoteId(quote.quoteId());
        reservation.setPricingQuoteExpiresAt(quote.expiresAt());
        reservation.setPricingRoomTypeId(quote.roomTypeId());
        reservation.setPricingNightlyPrice(quote.nightlyPrice());
        reservation.setPricingNights(quote.numberOfNights());
        reservation.setPricingRoomQuantity(quote.roomQuantity());
        reservation.setPricingBaseSubtotal(quote.baseSubtotal());
        reservation.setPricingTaxAmount(quote.taxAmount());
        reservation.setPricingFeeAmount(quote.feeAmount());
        reservation.setPricingDiscountAmount(quote.totalDiscount());
        reservation.setPricingCurrency(quote.currency());
        reservation.setPricingPromotionsJson(writeJson(quote.appliedPromotions()));
        reservation.setPricingMemberBenefitJson(writeJson(quote.memberBenefit()));
    }

    public PromotionQuoteDTO restoreSnapshot(Reservation reservation) {
        if (reservation == null || reservation.getPricingQuoteId() == null) return null;
        List<PromotionQuoteDTO.AppliedPromotion> promotions = readList(
                reservation.getPricingPromotionsJson(), new TypeReference<>() { });
        PromotionQuoteDTO.MemberBenefit member = readValue(
                reservation.getPricingMemberBenefitJson(), PromotionQuoteDTO.MemberBenefit.class,
                new PromotionQuoteDTO.MemberBenefit(false, null, null, null, ""));
        BigDecimal tax = money(defaultZero(reservation.getPricingTaxAmount()));
        BigDecimal fee = money(defaultZero(reservation.getPricingFeeAmount()));
        return new PromotionQuoteDTO(
                reservation.getPricingQuoteId(),
                reservation.getPricingQuoteExpiresAt(),
                reservation.getHotel() == null ? null : reservation.getHotel().getId(),
                reservation.getPricingRoomTypeId(),
                money(defaultZero(reservation.getPricingNightlyPrice())),
                reservation.getPricingNights() == null ? 0 : reservation.getPricingNights(),
                reservation.getPricingRoomQuantity() == null ? 0 : reservation.getPricingRoomQuantity(),
                money(defaultZero(reservation.getPricingBaseSubtotal())),
                tax,
                fee,
                money(tax.add(fee)),
                promotions,
                member,
                money(defaultZero(reservation.getPricingDiscountAmount())),
                money(defaultZero(reservation.getTotalAmount())),
                reservation.getPricingCurrency() == null ? CURRENCY : reservation.getPricingCurrency());
    }

    /**
     * Reserve campaign budget/quota after the reservation has an id. Replaying
     * the same reservation is idempotent and never creates a second redemption.
     */
    @Transactional
    public void redeem(Reservation reservation, User customer, PromotionQuoteDTO quote) {
        if (reservation == null || customer == null || quote == null || quote.appliedPromotions().isEmpty()) return;
        if (quote.expiresAt() != null && !quote.expiresAt().isAfter(clock.instant())) {
            throw new IllegalStateException("Báo giá khuyến mãi đã hết hạn; vui lòng lấy báo giá mới.");
        }
        List<PromotionQuoteDTO.AppliedPromotion> promotions = quote.appliedPromotions().stream()
                .sorted(Comparator.comparing(PromotionQuoteDTO.AppliedPromotion::campaignId))
                .toList();
        for (PromotionQuoteDTO.AppliedPromotion applied : promotions) {
            PromotionCampaign campaign = campaignRepository.findByIdForUpdate(applied.campaignId())
                    .orElseThrow(() -> new IllegalStateException("Chiến dịch khuyến mãi không còn tồn tại."));
            validateStillAvailable(campaign, reservation.getHotel(), customer.getId(), applied.discountAmount());

            String idempotencyKey = "RESERVATION-" + reservation.getId() + "-CAMPAIGN-" + campaign.getId();
            PromotionRedemption existing = redemptionRepository.findByIdempotencyKeyForUpdate(idempotencyKey)
                    .orElse(null);
            if (existing != null) {
                if (!Objects.equals(existing.getReservation().getId(), reservation.getId())
                        || existing.getDiscountAmount().compareTo(applied.discountAmount()) != 0) {
                    throw new IllegalStateException("Khuyến mãi đã được ghi nhận cho một giao dịch khác.");
                }
                continue;
            }

            PromotionRedemption redemption = new PromotionRedemption();
            redemption.setCampaign(campaign);
            redemption.setCustomer(customer);
            redemption.setReservation(reservation);
            redemption.setHotel(reservation.getHotel());
            redemption.setQuoteKey(quote.quoteId());
            redemption.setDiscountAmount(money(applied.discountAmount()));
            redemption.setStatus("RESERVED");
            redemption.setIdempotencyKey(idempotencyKey);
            redemption.setRedeemedAt(clock.instant());
            redemptionRepository.save(redemption);
        }
    }

    private void validateStillAvailable(PromotionCampaign campaign, Hotel hotel, Long customerId,
                                        BigDecimal discountAmount) {
        Instant now = clock.instant();
        if (!"ACTIVE".equals(campaign.getStatus())
                || campaign.getStartsAt() == null || campaign.getEndsAt() == null
                || campaign.getStartsAt().isAfter(now) || !campaign.getEndsAt().isAfter(now)) {
            throw new IllegalStateException("Khuyến mãi vừa hết hạn hoặc đã tạm dừng; vui lòng lấy báo giá mới.");
        }
        Long campaignHotelId = campaign.getHotel() == null ? null : campaign.getHotel().getId();
        if (campaignHotelId != null && (hotel == null || !campaignHotelId.equals(hotel.getId()))) {
            throw new IllegalStateException("Khuyến mãi không thuộc cơ sở đã đặt.");
        }
        long redemptions = redemptionRepository.countByCampaignIdAndStatusIn(
                campaign.getId(), List.of("RESERVED", "APPLIED"));
        if (campaign.getRedemptionLimit() != null && redemptions >= campaign.getRedemptionLimit()) {
            throw new IllegalStateException("Khuyến mãi đã hết lượt sử dụng.");
        }
        if (customerId != null && campaign.getPerCustomerLimit() != null) {
            long customerRedemptions = redemptionRepository.countByCampaignIdAndCustomerIdAndStatusIn(
                    campaign.getId(), customerId, List.of("RESERVED", "APPLIED"));
            if (customerRedemptions >= campaign.getPerCustomerLimit()) {
                throw new IllegalStateException("Tài khoản đã đạt giới hạn sử dụng khuyến mãi.");
            }
        }
        if (campaign.getBudget() != null) {
            BigDecimal committed = defaultZero(redemptionRepository.sumCommittedDiscount(campaign.getId()));
            if (committed.add(discountAmount).compareTo(campaign.getBudget()) > 0) {
                throw new IllegalStateException("Ngân sách khuyến mãi vừa được sử dụng hết.");
            }
        }
    }

    private PromotionCandidate candidate(PromotionCampaign campaign, BigDecimal eligibilityBase,
                                         BigDecimal discountBase, Long customerId) {
        BigDecimal discount = calculateDiscount(campaign, discountBase);
        if (discount.signum() <= 0) return null;
        long used = redemptionRepository.countByCampaignIdAndStatusIn(
                campaign.getId(), List.of("RESERVED", "APPLIED"));
        if (campaign.getRedemptionLimit() != null && used >= campaign.getRedemptionLimit()) return null;
        if (customerId != null && campaign.getPerCustomerLimit() != null) {
            long customerUsed = redemptionRepository.countByCampaignIdAndCustomerIdAndStatusIn(
                    campaign.getId(), customerId, List.of("RESERVED", "APPLIED"));
            if (customerUsed >= campaign.getPerCustomerLimit()) return null;
        }
        if (campaign.getBudget() != null) {
            BigDecimal remainingBudget = money(campaign.getBudget()
                    .subtract(defaultZero(redemptionRepository.sumCommittedDiscount(campaign.getId()))));
            if (remainingBudget.signum() <= 0) return null;
            discount = discount.min(remainingBudget);
        }
        return discount.signum() <= 0 ? null : new PromotionCandidate(campaign, money(discount));
    }

    private boolean activeAt(PromotionCampaign campaign, Instant at) {
        return campaign != null
                && "ACTIVE".equals(campaign.getStatus())
                && campaign.getStartsAt() != null
                && campaign.getEndsAt() != null
                && !campaign.getStartsAt().isAfter(at)
                && campaign.getEndsAt().isAfter(at);
    }

    private BigDecimal calculateDiscount(PromotionCampaign campaign, BigDecimal discountBase) {
        BigDecimal discount = "PERCENT".equals(campaign.getDiscountType())
                ? discountBase.multiply(campaign.getDiscountValue()).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
                : campaign.getDiscountValue();
        if (campaign.getMaxDiscount() != null) discount = discount.min(campaign.getMaxDiscount());
        return money(discount.min(discountBase).max(ZERO));
    }

    private boolean eligible(PromotionCampaign campaign, RoomType roomType, LocalDate checkIn,
                             LocalDate checkOut, int quantity, int adults, int children,
                             BigDecimal subtotal, MemberContext member) {
        JsonNode rules = readTree(campaign.getEligibilityJson());
        if (rules == null || rules.isNull() || rules.isMissingNode()) return true;
        if (!matchesLongSet(rules.path("propertyIds"), roomType.getHotel().getId())) return false;
        if (!matchesLongSet(rules.path("hotelIds"), roomType.getHotel().getId())) return false;
        if (!matchesLongSet(rules.path("roomTypeIds"), roomType.getId())) return false;
        if (!matchesStringSet(rules.path("roomTypeCodes"), roomType.getCode())) return false;
        if (!within(rules.path("minNights"), ChronoUnit.DAYS.between(checkIn, checkOut))) return false;
        if (!withinMax(rules.path("maxNights"), ChronoUnit.DAYS.between(checkIn, checkOut))) return false;
        if (!within(rules.path("minQuantity"), quantity)) return false;
        if (!withinMax(rules.path("maxQuantity"), quantity)) return false;
        if (!within(rules.path("minGuests"), adults + children)) return false;
        if (!withinMax(rules.path("maxGuests"), adults + children)) return false;
        if (!within(rules.has("minSubtotal") ? rules.path("minSubtotal") : rules.path("minSpend"), subtotal)) return false;
        if (!withinMax(rules.path("maxSubtotal"), subtotal)) return false;
        if (!withinDate(rules.path("checkInDateFrom"), checkIn, false)) return false;
        if (!withinDate(rules.path("checkInDateTo"), checkIn, true)) return false;
        if (!matchesWeekday(rules.path("allowedWeekdays"), checkIn)) return false;
        JsonNode requiredTiers = rules.has("memberTierCodes") ? rules.path("memberTierCodes") : rules.path("memberTierCode");
        if (!requiredTiers.isMissingNode() && !requiredTiers.isNull()
                && (member == null || member.tier() == null
                || !matchesStringSet(requiredTiers, member.tier().code()))) return false;
        if (rules.path("memberOnly").asBoolean(false) && (member == null || member.tier() == null)) return false;
        return true;
    }

    private MemberContext resolveMember(Long customerId, Long hotelId, Instant at) {
        if (customerId == null) return MemberContext.none();
        List<CustomerMembership> memberships = membershipRepository.findActiveMemberships(customerId, hotelId, at);
        return memberships.isEmpty() ? MemberContext.none() : new MemberContext(memberships.get(0));
    }

    private PromotionQuoteDTO.AppliedPromotion toAppliedPromotion(PromotionCandidate candidate) {
        PromotionCampaign campaign = candidate.campaign();
        return new PromotionQuoteDTO.AppliedPromotion(
                campaign.getId(), campaign.getCode(), campaign.getApplicationType(),
                campaign.getNameVi(), campaign.getNameEn(), candidate.discountAmount());
    }

    private Comparator<PromotionCandidate> candidateOrder() {
        return Comparator.comparing(PromotionCandidate::discountAmount, Comparator.reverseOrder())
                .thenComparing(candidate -> candidate.campaign().getPriority(), Comparator.reverseOrder())
                .thenComparing(candidate -> candidate.campaign().getId());
    }

    private boolean matchesLongSet(JsonNode node, Long value) {
        if (node == null || node.isMissingNode() || node.isNull()) return true;
        if (node.isArray()) {
            for (JsonNode item : node) if (item.canConvertToLong() && Objects.equals(item.longValue(), value)) return true;
            return false;
        }
        return node.canConvertToLong() && Objects.equals(node.longValue(), value);
    }

    private boolean matchesStringSet(JsonNode node, String value) {
        if (node == null || node.isMissingNode() || node.isNull()) return true;
        String normalized = VietnameseTextNormalizer.normalize(value == null ? "" : value);
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (normalized.equals(VietnameseTextNormalizer.normalize(item.asText()))) return true;
            }
            return false;
        }
        return normalized.equals(VietnameseTextNormalizer.normalize(node.asText()));
    }

    private boolean within(JsonNode node, long value) {
        return node == null || node.isMissingNode() || node.isNull() || !node.isNumber() || value >= node.longValue();
    }

    private boolean within(JsonNode node, BigDecimal value) {
        return node == null || node.isMissingNode() || node.isNull() || !node.isNumber()
                || value.compareTo(node.decimalValue()) >= 0;
    }

    private boolean withinMax(JsonNode node, long value) {
        return node == null || node.isMissingNode() || node.isNull() || !node.isNumber() || value <= node.longValue();
    }

    private boolean withinMax(JsonNode node, BigDecimal value) {
        return node == null || node.isMissingNode() || node.isNull() || !node.isNumber()
                || value.compareTo(node.decimalValue()) <= 0;
    }

    private boolean withinDate(JsonNode node, LocalDate value, boolean upperBound) {
        if (node == null || node.isMissingNode() || node.isNull() || node.asText().isBlank()) return true;
        try {
            LocalDate ruleDate = LocalDate.parse(node.asText());
            return upperBound ? !value.isAfter(ruleDate) : !value.isBefore(ruleDate);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean matchesWeekday(JsonNode node, LocalDate date) {
        if (node == null || node.isMissingNode() || node.isNull()) return true;
        String day = date.getDayOfWeek().name();
        if (node.isArray()) {
            for (JsonNode item : node) if (day.equalsIgnoreCase(item.asText()) || Integer.toString(date.getDayOfWeek().getValue()).equals(item.asText())) return true;
            return false;
        }
        return day.equalsIgnoreCase(node.asText()) || Integer.toString(date.getDayOfWeek().getValue()).equals(node.asText());
    }

    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) return objectMapper.createObjectNode();
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            return objectMapper.createObjectNode().put("invalid", true);
        }
    }

    private <T> T readValue(String json, Class<T> type, T fallback) {
        if (json == null || json.isBlank()) return fallback;
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            return fallback;
        }
    }

    private <T> T readList(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) return (T) List.of();
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            return (T) List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không thể lưu snapshot báo giá.", exception);
        }
    }

    private void validateRequest(PromotionQuoteRequest request) {
        if (request == null || request.propertyId() == null || request.roomTypeId() == null
                || request.checkInDate() == null || request.checkOutDate() == null) {
            throw new IllegalArgumentException("Thiếu thông tin báo giá.");
        }
        if (!request.checkOutDate().isAfter(request.checkInDate())) {
            throw new IllegalArgumentException("Ngày trả phòng phải sau ngày nhận phòng.");
        }
        if (request.quantity() < 1 || request.adultCount() < 1 || request.childCount() < 0) {
            throw new IllegalArgumentException("Số phòng hoặc số khách không hợp lệ.");
        }
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? ZERO : value).setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private record PromotionCandidate(PromotionCampaign campaign, BigDecimal discountAmount) { }

    private record MemberContext(CustomerMembership membership) {
        static MemberContext none() { return new MemberContext(null); }
        MembershipTierView tier() {
            if (membership == null || membership.getTier() == null) return null;
            return new MembershipTierView(membership.getTier().getCode(), membership.getTier().getNameVi(), membership.getTier().getNameEn());
        }
        PromotionQuoteDTO.MemberBenefit toDto() {
            MembershipTierView tier = tier();
            return tier == null
                    ? new PromotionQuoteDTO.MemberBenefit(false, null, null, null, "")
                    : new PromotionQuoteDTO.MemberBenefit(true, tier.code(), tier.nameVi(), tier.nameEn(), "Membership tier assigned by the managed policy.");
        }
    }

    private record MembershipTierView(String code, String nameVi, String nameEn) { }
}
