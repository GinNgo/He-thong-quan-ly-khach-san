package com.hotel.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.dtos.PromotionCampaignDTO;
import com.hotel.dtos.PromotionCampaignRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.PromotionCampaign;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.PromotionCampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PromotionCampaignManagementService {

    static final String SUBSCRIPTION_FEATURE = "PROMOTION_CAMPAIGNS";
    private static final Set<String> COUNTED_STATUSES = Set.of("DRAFT", "SCHEDULED", "ACTIVE", "PAUSED");

    private final PromotionCampaignRepository campaignRepository;
    private final PropertyAccessService propertyAccessService;
    private final SubscriptionFeatureService subscriptionFeatureService;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();

    @Transactional(readOnly = true)
    public List<PromotionCampaignDTO> list(Long hotelId) {
        if (propertyAccessService.isSystemAdministrator()) {
            List<PromotionCampaign> campaigns = hotelId == null
                    ? campaignRepository.findAll()
                    : campaignsForHotel(hotelId);
            return campaigns.stream().sorted(campaignOrder()).map(this::toDto).toList();
        }
        Hotel hotel = propertyAccessService.requireAssignedHotel(hotelId);
        return campaignsForHotel(hotel.getId()).stream().sorted(campaignOrder()).map(this::toDto).toList();
    }

    @Transactional
    public PromotionCampaignDTO create(PromotionCampaignRequest request) {
        validateRequest(request);
        PromotionCampaign campaign = new PromotionCampaign();
        applyOwnership(campaign, request, true);
        if (campaign.getHotel() != null) {
            long current = campaignRepository.countByHotelIdAndStatusIn(campaign.getHotel().getId(), COUNTED_STATUSES);
            subscriptionFeatureService.checkFeatureLimitForProperty(
                    campaign.getHotel().getId(), SUBSCRIPTION_FEATURE, current, 1);
        }
        applyMutableFields(campaign, request);
        campaign.setStatus(PromotionCampaignDTO.CampaignStatus.DRAFT.name());
        return toDto(campaignRepository.save(campaign));
    }

    @Transactional
    public PromotionCampaignDTO update(Long id, PromotionCampaignRequest request) {
        validateRequest(request);
        PromotionCampaign campaign = requireCampaign(id);
        requireCanManage(campaign);
        requireSameOwnership(campaign, request);
        if (campaign.getHotel() != null) {
            subscriptionFeatureService.requireFeatureForProperty(campaign.getHotel().getId(), SUBSCRIPTION_FEATURE);
        }
        if (PromotionCampaignDTO.CampaignStatus.ACTIVE.name().equals(campaign.getStatus())) {
            throw new IllegalStateException("Pause the campaign before editing it.");
        }
        applyMutableFields(campaign, request);
        if (PromotionCampaignDTO.CampaignStatus.REJECTED.name().equals(campaign.getStatus())) {
            campaign.setStatus(PromotionCampaignDTO.CampaignStatus.DRAFT.name());
        }
        return toDto(campaignRepository.save(campaign));
    }

    @Transactional
    public PromotionCampaignDTO activate(Long id) {
        PromotionCampaign campaign = requireCampaignForUpdate(id);
        requireCanManage(campaign);
        if (campaign.getHotel() != null) {
            subscriptionFeatureService.requireFeatureForProperty(campaign.getHotel().getId(), SUBSCRIPTION_FEATURE);
        }
        Instant now = clock.instant();
        if (!campaign.getEndsAt().isAfter(now)) {
            throw new IllegalStateException("Expired campaigns cannot be activated.");
        }
        if (campaign.getNameEn() == null || campaign.getNameEn().isBlank()) {
            throw new IllegalStateException("English campaign name is required before publication.");
        }
        campaign.setStatus(campaign.getStartsAt().isAfter(now)
                ? PromotionCampaignDTO.CampaignStatus.SCHEDULED.name()
                : PromotionCampaignDTO.CampaignStatus.ACTIVE.name());
        return toDto(campaignRepository.save(campaign));
    }

    @Transactional
    public PromotionCampaignDTO pause(Long id) {
        PromotionCampaign campaign = requireCampaignForUpdate(id);
        requireCanManage(campaign);
        if (!Set.of("ACTIVE", "SCHEDULED").contains(campaign.getStatus())) {
            throw new IllegalStateException("Only active or scheduled campaigns can be paused.");
        }
        campaign.setStatus(PromotionCampaignDTO.CampaignStatus.PAUSED.name());
        return toDto(campaignRepository.save(campaign));
    }

    private List<PromotionCampaign> campaignsForHotel(Long hotelId) {
        List<PromotionCampaign> campaigns = new ArrayList<>(campaignRepository.findByHotelIsNullAndStatusOrderByPriorityDescIdDesc("ACTIVE"));
        campaigns.addAll(campaignRepository.findByHotelIdAndStatusOrderByPriorityDescIdDesc(hotelId, "ACTIVE"));
        for (String status : List.of("DRAFT", "SCHEDULED", "PAUSED", "EXPIRED", "REJECTED")) {
            campaigns.addAll(campaignRepository.findByHotelIdAndStatusOrderByPriorityDescIdDesc(hotelId, status));
        }
        return campaigns;
    }

    private PromotionCampaign requireCampaign(Long id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion campaign not found."));
    }

    private PromotionCampaign requireCampaignForUpdate(Long id) {
        return campaignRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion campaign not found."));
    }

    private void requireCanManage(PromotionCampaign campaign) {
        if (campaign.getHotel() == null) {
            requireSystemAdministrator();
            return;
        }
        propertyAccessService.requireAssignedHotel(campaign.getHotel().getId());
    }

    private void applyOwnership(PromotionCampaign campaign, PromotionCampaignRequest request, boolean creating) {
        if (request.ownerType() == PromotionCampaignRequest.OwnerType.SYSTEM) {
            requireSystemAdministrator();
            if (request.hotelId() != null) {
                throw new IllegalArgumentException("System campaigns cannot carry hotelId.");
            }
            campaign.setOwnerType(request.ownerType().name());
            campaign.setHotel(null);
            return;
        }
        if (request.hotelId() == null) {
            throw new IllegalArgumentException("Tenant campaigns require hotelId.");
        }
        Hotel hotel = propertyAccessService.requireManagedHotel(request.hotelId());
        campaign.setOwnerType(request.ownerType().name());
        campaign.setHotel(hotel);
        if (!creating && propertyAccessService.isSystemAdministrator()) {
            propertyAccessService.requireAssignedHotel(hotel.getId());
        }
    }

    private void requireSameOwnership(PromotionCampaign campaign, PromotionCampaignRequest request) {
        Long currentHotelId = campaign.getHotel() == null ? null : campaign.getHotel().getId();
        if (!campaign.getOwnerType().equals(request.ownerType().name())
                || !java.util.Objects.equals(currentHotelId, request.hotelId())) {
            throw new IllegalArgumentException("Campaign ownership cannot be changed.");
        }
    }

    private void applyMutableFields(PromotionCampaign campaign, PromotionCampaignRequest request) {
        campaign.setCode(normalizeCode(request.code()));
        campaign.setApplicationType(request.applicationType().name());
        campaign.setNameVi(request.nameVi().trim());
        campaign.setNameEn(trimToNull(request.nameEn()));
        campaign.setDiscountType(request.discountType().name());
        campaign.setDiscountValue(request.discountValue());
        campaign.setMaxDiscount(request.maxDiscount());
        campaign.setStartsAt(request.startsAt());
        campaign.setEndsAt(request.endsAt());
        campaign.setTimezone(request.timezone().trim());
        campaign.setEligibilityJson(writeJson(request.eligibility()));
        campaign.setBudget(request.budget());
        campaign.setRedemptionLimit(request.redemptionLimit());
        campaign.setPerCustomerLimit(request.perCustomerLimit());
        campaign.setStackingPolicy(request.stackingPolicy().name());
        campaign.setPriority(request.priority() == null ? 0 : request.priority());
    }

    private void validateRequest(PromotionCampaignRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new IllegalArgumentException("Campaign endsAt must be after startsAt.");
        }
        try {
            ZoneId.of(request.timezone());
        } catch (ZoneRulesException exception) {
            throw new IllegalArgumentException("Campaign timezone is invalid.");
        }
        if (request.discountType() == PromotionCampaignRequest.DiscountType.PERCENT
                && request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Percentage discount cannot exceed 100.");
        }
        String code = normalizeCode(request.code());
        if (!code.matches("[A-Z0-9][A-Z0-9_-]{2,79}")) {
            throw new IllegalArgumentException("Campaign code must contain 3-80 uppercase letters, numbers, dashes or underscores.");
        }
    }

    private PromotionCampaignDTO toDto(PromotionCampaign campaign) {
        return new PromotionCampaignDTO(
                campaign.getId(), campaign.getCode(), PromotionCampaignRequest.OwnerType.valueOf(campaign.getOwnerType()),
                campaign.getHotel() == null ? null : campaign.getHotel().getId(),
                PromotionCampaignRequest.ApplicationType.valueOf(campaign.getApplicationType()),
                campaign.getNameVi(), campaign.getNameEn(),
                PromotionCampaignRequest.DiscountType.valueOf(campaign.getDiscountType()),
                campaign.getDiscountValue(), campaign.getMaxDiscount(), campaign.getStartsAt(), campaign.getEndsAt(),
                campaign.getTimezone(), readJson(campaign.getEligibilityJson()), campaign.getBudget(),
                campaign.getRedemptionLimit(), campaign.getPerCustomerLimit(),
                PromotionCampaignRequest.StackingPolicy.valueOf(campaign.getStackingPolicy()), campaign.getPriority(),
                PromotionCampaignDTO.CampaignStatus.valueOf(campaign.getStatus()),
                campaign.getCreatedAt(), campaign.getUpdatedAt());
    }

    private Comparator<PromotionCampaign> campaignOrder() {
        return Comparator.comparing(PromotionCampaign::getPriority, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PromotionCampaign::getId, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private void requireSystemAdministrator() {
        if (!propertyAccessService.isSystemAdministrator()) {
            throw new SecurityException("Only a platform administrator can manage system campaigns.");
        }
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Campaign eligibility is not valid JSON data.", exception);
        }
    }

    private Map<String, Object> readJson(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored campaign eligibility is invalid.", exception);
        }
    }
}

