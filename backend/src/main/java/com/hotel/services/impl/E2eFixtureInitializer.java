package com.hotel.services.impl;

import com.hotel.domain.lifecycle.PaymentStatus;
import com.hotel.domain.lifecycle.RefundStatus;
import com.hotel.entities.AccountSubscription;
import com.hotel.entities.CustomerMembership;
import com.hotel.entities.Hotel;
import com.hotel.entities.MembershipTier;
import com.hotel.entities.Notification;
import com.hotel.entities.Payment;
import com.hotel.entities.PaymentSession;
import com.hotel.entities.PromotionCampaign;
import com.hotel.entities.RefundRequest;
import com.hotel.entities.Reservation;
import com.hotel.entities.ReservationDetail;
import com.hotel.entities.Role;
import com.hotel.entities.Room;
import com.hotel.entities.SponsoredPlacement;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.repositories.AccountSubscriptionRepository;
import com.hotel.repositories.CustomerMembershipRepository;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.MembershipTierRepository;
import com.hotel.repositories.NotificationRepository;
import com.hotel.repositories.PaymentRepository;
import com.hotel.repositories.PaymentSessionRepository;
import com.hotel.repositories.PromotionCampaignRepository;
import com.hotel.repositories.RefundRequestRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.ReservationDetailRepository;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.SponsoredPlacementRepository;
import com.hotel.repositories.SubscriptionPlanRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@Profile({"development", "e2e", "test"})
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.e2e-fixtures.enabled", havingValue = "true")
public class E2eFixtureInitializer {

    private static final String SECONDARY_PROPERTY_CODE = "E2E-MULTI-02";
    private static final String PAYMENT_TRANSACTION_ID = "E2E-FIXTURE-PAYMENT";
    private static final String AUTOMATIC_CAMPAIGN_CODE = "E2E-AUTO-10";
    private static final String GOLD_CAMPAIGN_CODE = "E2EGOLD";
    private static final String GOLD_TIER_CODE = "GOLD";
    private static final String SEARCH_PLACEMENT_TITLE = "E2E sponsored search placement";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final UserPropertyRepository userPropertyRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final AccountSubscriptionRepository accountSubscriptionRepository;
    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentSessionRepository paymentSessionRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final ReservationDetailRepository reservationDetailRepository;
    private final NotificationRepository notificationRepository;
    private final PromotionCampaignRepository promotionCampaignRepository;
    private final MembershipTierRepository membershipTierRepository;
    private final CustomerMembershipRepository customerMembershipRepository;
    private final SponsoredPlacementRepository sponsoredPlacementRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${LUXESTAY_E2E_CUSTOMER_USERNAME:e2e-customer}")
    private String customerUsername;

    @Value("${LUXESTAY_E2E_CUSTOMER_PASSWORD:}")
    private String customerPassword;

    @Value("${LUXESTAY_E2E_ADMIN_USERNAME:e2e-admin}")
    private String adminUsername;

    @Value("${LUXESTAY_E2E_ADMIN_PASSWORD:}")
    private String adminPassword;

    @Value("${LUXESTAY_E2E_OWNER_USERNAME:e2e-owner}")
    private String ownerUsername;

    @Value("${LUXESTAY_E2E_OWNER_PASSWORD:}")
    private String ownerPassword;

    @EventListener(ApplicationReadyEvent.class)
    @Order(400)
    @Transactional
    public void provision() {
        requireCredential(customerUsername, customerPassword, "CUSTOMER");
        requireCredential(adminUsername, adminPassword, "ADMIN");
        requireCredential(ownerUsername, ownerPassword, "OWNER");

        Role customerRole = requireRole("CUSTOMER");
        Role adminRole = requireRole("SUPER_ADMIN");
        Role ownerRole = requireRole("PROPERTY_OWNER");
        List<Hotel> properties = ensurePropertyFixtures();
        Hotel primaryProperty = properties.getFirst();

        User customer = upsertUser(customerUsername, customerPassword, "E2E Customer", customerRole, null);
        User admin = upsertUser(adminUsername, adminPassword, "E2E Admin and AI Chat Support", adminRole, null);
        User owner = upsertUser(ownerUsername, ownerPassword, "E2E Multi-property Owner", ownerRole, primaryProperty);
        User expiredOwner = upsertUser(
                ownerUsername + "-expired",
                ownerPassword,
                "E2E Expired Owner",
                ownerRole,
                primaryProperty);
        User lifetimeOwner = upsertUser(
                ownerUsername + "-lifetime",
                ownerPassword,
                "E2E Lifetime Owner",
                ownerRole,
                primaryProperty);
        User multiPlanOwner = upsertUser(
                ownerUsername + "-multi",
                ownerPassword,
                "E2E Multiple Subscription Owner",
                ownerRole,
                primaryProperty);

        assignOwner(owner, properties.get(0), true);
        assignOwner(owner, properties.get(1), false);
        assignOwner(expiredOwner, primaryProperty, true);
        assignOwner(lifetimeOwner, primaryProperty, true);
        assignOwner(multiPlanOwner, primaryProperty, true);
        ensureSubscription(owner, "STANDARD", false);
        ensureSubscription(expiredOwner, "STANDARD", true);
        ensureSubscription(lifetimeOwner, "LIFETIME", false);
        ensureSubscription(multiPlanOwner, "STANDARD", false);
        ensureSubscription(multiPlanOwner, "PREMIUM", false);
        ensureBookingAndPayment(customer, primaryProperty);
        ensurePaymentLifecycleFixtures(customer, primaryProperty);
        ensureRefundLifecycleFixtures(customer, primaryProperty);
        ensureNotifications(admin);
        ensureMarketplaceFixtures(customer, admin, primaryProperty);

        log.info("E2E_FIXTURES ready actors={} properties={}", 6, properties.size());
    }

    private void requireCredential(String username, String password, String actor) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Missing LUXESTAY_E2E_" + actor + "_USERNAME/PASSWORD while app.e2e-fixtures.enabled=true");
        }
    }

    private Role requireRole(String code) {
        return roleRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Required E2E role is missing: " + code));
    }

    private List<Hotel> ensurePropertyFixtures() {
        Hotel primary = hotelRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("DataInitializer did not create a primary property"));
        primary.setStatus("ACTIVE");
        primary.setOperationStatus("ACTIVE");
        primary.setApprovalStatus("APPROVED");
        primary.setIsDemo(true);
        primary.setDataSource("E2E_FIXTURE");
        primary = hotelRepository.saveAndFlush(primary);

        Hotel secondary = hotelRepository.findByCode(SECONDARY_PROPERTY_CODE).orElseGet(Hotel::new);
        secondary.setCode(SECONDARY_PROPERTY_CODE);
        secondary.setSlug("e2e-multi-property-02");
        secondary.setName("E2E Riverside Annex");
        secondary.setNameVi("Cơ sở E2E Riverside Annex");
        secondary.setNameEn("E2E Riverside Annex");
        secondary.setDescription("Local-only property used for multi-property browser regression.");
        secondary.setAddressLine("02 E2E Fixture Street");
        secondary.setCity(primary.getCity() == null ? "Hà Nội" : primary.getCity());
        secondary.setCountry(primary.getCountry() == null ? "Việt Nam" : primary.getCountry());
        secondary.setProvinceId(primary.getProvinceId());
        secondary.setWardId(primary.getWardId());
        secondary.setStatus("ACTIVE");
        secondary.setOperationStatus("ACTIVE");
        secondary.setApprovalStatus("APPROVED");
        secondary.setPropertyType("HOTEL");
        secondary.setStarRating(4);
        secondary.setIsDemo(true);
        secondary.setDataSource("E2E_FIXTURE");
        secondary.setSeedKey(SECONDARY_PROPERTY_CODE);
        secondary = hotelRepository.saveAndFlush(secondary);
        return List.of(primary, secondary);
    }

    private User upsertUser(String username, String password, String fullName, Role role, Hotel hotel) {
        User user = userRepository.findByUsername(username).orElseGet(User::new);
        user.setUsername(username);
        user.setEmail(fixtureEmail(username));
        user.setFullName(fullName);
        user.setStatus("ACTIVE");
        user.setHotel(hotel);
        user.setRoles(new HashSet<>(Set.of(role)));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(password));
        }
        return userRepository.save(user);
    }

    private String fixtureEmail(String username) {
        return username.toLowerCase().replaceAll("[^a-z0-9._-]", "-") + "@e2e.local";
    }

    private void assignOwner(User owner, Hotel hotel, boolean primary) {
        UserProperty assignment = userPropertyRepository
                .findByUserIdAndHotelIdAndRelationshipType(owner.getId(), hotel.getId(), "OWNER")
                .orElseGet(UserProperty::new);
        assignment.setUser(owner);
        assignment.setHotel(hotel);
        assignment.setRelationshipType("OWNER");
        assignment.setIsPrimaryOwner(primary);
        assignment.setStatus("ACTIVE");
        assignment.setStartDate(LocalDateTime.now().minusDays(30));
        assignment.setEndDate(null);
        userPropertyRepository.save(assignment);
    }

    private void ensureSubscription(User owner, String planCode, boolean expired) {
        SubscriptionPlan plan = subscriptionPlanRepository.findByCode(planCode)
                .orElseThrow(() -> new IllegalStateException("Required E2E subscription plan is missing: " + planCode));
        AccountSubscription subscription = accountSubscriptionRepository
                .findFirstByUserIdAndPlanCodeOrderByStartAtDesc(owner.getId(), planCode)
                .orElseGet(AccountSubscription::new);
        subscription.setUser(owner);
        subscription.setPlan(plan);
        boolean lifetime = Boolean.TRUE.equals(plan.getIsLifetime());
        subscription.setIsLifetime(lifetime);
        subscription.setStartAt(LocalDateTime.now().minusDays(60));
        subscription.setEndAt(lifetime ? null
                : expired ? LocalDateTime.now().minusDays(1) : LocalDateTime.now().plusDays(30));
        subscription.setStatus(expired ? "EXPIRED" : "ACTIVE");
        accountSubscriptionRepository.save(subscription);
    }

    private void ensureBookingAndPayment(User customer, Hotel hotel) {
        if (paymentRepository.findByTransactionId(PAYMENT_TRANSACTION_ID).isPresent()) {
            return;
        }
        Room room = roomRepository.findByHotelId(hotel.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Primary E2E property has no room inventory"));
        Reservation reservation = new Reservation();
        reservation.setUser(customer);
        reservation.setHotel(hotel);
        reservation.setRoom(room);
        reservation.setCheckInDate(LocalDate.now().plusDays(14));
        reservation.setCheckOutDate(LocalDate.now().plusDays(16));
        reservation.setGuests(2);
        reservation.setTotalAmount(new BigDecimal("2500000"));
        reservation.setStatus("CONFIRMED");
        reservation.setPaymentMethod("MOMO");
        reservation.setSpecialRequests("E2E fixture booking");
        reservation = reservationRepository.save(reservation);

        ReservationDetail detail = new ReservationDetail();
        detail.setReservation(reservation);
        detail.setRoom(room);
        detail.setRoomType(room.getRoomType());
        detail.setQuantity(1);
        detail.setAdults(2);
        detail.setChildren(0);
        detail.setPrice(room.getRoomType().getBasePrice());
        detail.setUnitPrice(room.getRoomType().getBasePrice());
        detail.setSubtotal(reservation.getTotalAmount());
        reservationDetailRepository.save(detail);

        Payment payment = new Payment();
        payment.setReservation(reservation);
        payment.setAmount(reservation.getTotalAmount());
        payment.setPaymentMethod("MOMO");
        payment.setStatus(PaymentStatus.SUCCEEDED.name());
        payment.setTransactionId(PAYMENT_TRANSACTION_ID);
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);
    }

    private void ensurePaymentLifecycleFixtures(User customer, Hotel hotel) {
        ensurePaymentSessionFixture(
                customer,
                hotel,
                "PENDING",
                "PENDING_PAYMENT",
                PaymentStatus.PENDING,
                false,
                false,
                null);
        ensurePaymentSessionFixture(
                customer,
                hotel,
                "FAILED",
                "PENDING_PAYMENT",
                PaymentStatus.FAILED,
                false,
                false,
                "PROVIDER_DECLINED");
        ensurePaymentSessionFixture(
                customer,
                hotel,
                "EXPIRED",
                "EXPIRED",
                PaymentStatus.CREATED,
                false,
                true,
                null);
        ensurePaymentSessionFixture(
                customer,
                hotel,
                "RECONCILIATION",
                "CANCELLED",
                PaymentStatus.SUCCEEDED,
                true,
                false,
                null);
    }

    private void ensurePaymentSessionFixture(
            User customer,
            Hotel hotel,
            String code,
            String reservationStatus,
            PaymentStatus paymentStatus,
            boolean reconciliationRequired,
            boolean expired,
            String failureCode) {
        String publicId = "E2E-PAYMENT-SESSION-" + code;
        if (paymentSessionRepository.findByPublicId(publicId).isPresent()) {
            return;
        }

        Reservation reservation = createLifecycleReservation(
                customer,
                hotel,
                reservationStatus,
                "E2E payment lifecycle " + code,
                new BigDecimal("1250000"));
        LocalDateTime now = LocalDateTime.now();

        PaymentSession session = new PaymentSession();
        session.setPublicId(publicId);
        session.setReservation(reservation);
        session.setHotel(hotel);
        session.setOwner(customer);
        session.setProvider("MOMO");
        session.setMethod("MOMO");
        session.setExpectedAmount(reservation.getTotalAmount());
        session.setCurrency("VND");
        session.setProviderReference("E2E-PROVIDER-" + code);
        session.setIdempotencyKey("E2E-PAYMENT-IDEMPOTENCY-" + code);
        session.setStatus(paymentStatus.name());
        session.setExpiresAt(expired ? now.minusMinutes(5) : now.plusMinutes(30));
        session.setCompletedAt(
                paymentStatus == PaymentStatus.SUCCEEDED || paymentStatus == PaymentStatus.FAILED ? now : null);
        session.setReconciliationRequired(reconciliationRequired);
        session.setFailureCode(failureCode);
        paymentSessionRepository.save(session);
    }

    private void ensureRefundLifecycleFixtures(User customer, Hotel hotel) {
        ensureRefundFixture(customer, hotel, RefundStatus.REQUESTED, null);
        ensureRefundFixture(customer, hotel, RefundStatus.PENDING_PROVIDER, null);
        ensureRefundFixture(customer, hotel, RefundStatus.SUCCEEDED, null);
        ensureRefundFixture(customer, hotel, RefundStatus.FAILED, "PROVIDER_REFUND_REJECTED");
    }

    private void ensureRefundFixture(
            User customer,
            Hotel hotel,
            RefundStatus refundStatus,
            String failureCode) {
        String code = refundStatus.name();
        String transactionId = "E2E-REFUND-ORIGINAL-" + code;
        Payment originalPayment = paymentRepository.findByTransactionId(transactionId).orElse(null);

        if (originalPayment == null) {
            Reservation reservation = createLifecycleReservation(
                    customer,
                    hotel,
                    "CANCELLED",
                    "E2E refund lifecycle " + code,
                    new BigDecimal("950000"));
            originalPayment = new Payment();
            originalPayment.setReservation(reservation);
            originalPayment.setAmount(reservation.getTotalAmount());
            originalPayment.setPaymentMethod("MOMO");
            originalPayment.setStatus(PaymentStatus.SUCCEEDED.name());
            originalPayment.setTransactionId(transactionId);
            originalPayment.setPaymentDate(LocalDateTime.now().minusDays(1));
            originalPayment = paymentRepository.save(originalPayment);
        }

        if (refundRequestRepository.findByOriginalPaymentId(originalPayment.getId()).isPresent()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        RefundRequest request = new RefundRequest();
        request.setPublicId("E2E-REFUND-" + code);
        request.setReservation(originalPayment.getReservation());
        request.setOriginalPayment(originalPayment);
        request.setHotel(hotel);
        request.setRequestedAmount(originalPayment.getAmount());
        request.setCurrency("VND");
        request.setProvider("MOMO");
        request.setStatus(refundStatus.name());
        request.setIdempotencyKey("E2E-REFUND-IDEMPOTENCY-" + code);
        request.setReason("E2E refund lifecycle fixture");
        request.setRequestedAt(now.minusHours(2));
        request.setCompletedAt(
                refundStatus == RefundStatus.SUCCEEDED || refundStatus == RefundStatus.FAILED ? now : null);
        request.setProviderRefundReference(
                refundStatus == RefundStatus.SUCCEEDED ? "E2E-REFUND-PROVIDER-" + code : null);
        request.setFailureCode(failureCode);
        refundRequestRepository.save(request);
    }

    private Reservation createLifecycleReservation(
            User customer,
            Hotel hotel,
            String status,
            String fixtureLabel,
            BigDecimal amount) {
        Room room = roomRepository.findByHotelId(hotel.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Primary E2E property has no room inventory"));
        Reservation reservation = new Reservation();
        reservation.setUser(customer);
        reservation.setHotel(hotel);
        reservation.setRoom(room);
        reservation.setCheckInDate(LocalDate.now().plusDays(21));
        reservation.setCheckOutDate(LocalDate.now().plusDays(23));
        reservation.setGuests(2);
        reservation.setTotalAmount(amount);
        reservation.setStatus(status);
        reservation.setPaymentMethod("MOMO");
        reservation.setSpecialRequests(fixtureLabel);
        return reservationRepository.save(reservation);
    }

    private void ensureNotifications(User admin) {
        ensureNotification(null, "E2E fixture system notification");
        ensureNotification(admin.getId(), "E2E fixture personal notification");
    }

    private void ensureNotification(Long userId, String title) {
        boolean exists = notificationRepository.findAll().stream()
                .anyMatch(notification -> title.equals(notification.getTitle())
                        && java.util.Objects.equals(userId, notification.getUserId()));
        if (exists) {
            return;
        }
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType("SYSTEM");
        notification.setTitle(title);
        notification.setMessage("Local-only data for notification browser regression.");
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    private void ensureMarketplaceFixtures(User customer, User admin, Hotel primaryProperty) {
        Instant now = Instant.now();
        ensureAutomaticCampaign(now);
        MembershipTier goldTier = ensureGoldTier();
        ensureGoldCampaign(now);
        ensureCustomerMembership(customer, admin, goldTier, now);
        ensureSponsoredSearchPlacement(admin, primaryProperty, now);
    }

    private void ensureAutomaticCampaign(Instant now) {
        PromotionCampaign campaign = promotionCampaignRepository
                .findByCodeAndHotelIsNull(AUTOMATIC_CAMPAIGN_CODE)
                .orElseGet(PromotionCampaign::new);
        campaign.setCode(AUTOMATIC_CAMPAIGN_CODE);
        campaign.setOwnerType("SYSTEM");
        campaign.setHotel(null);
        campaign.setApplicationType("AUTOMATIC");
        campaign.setNameVi("\u01afu \u0111\u00e3i E2E 10%");
        campaign.setNameEn("E2E automatic 10% offer");
        campaign.setDiscountType("PERCENT");
        campaign.setDiscountValue(new BigDecimal("10"));
        campaign.setMaxDiscount(new BigDecimal("150000"));
        campaign.setStartsAt(now.minusSeconds(86400));
        campaign.setEndsAt(now.plusSeconds(86400L * 30));
        campaign.setTimezone("Asia/Ho_Chi_Minh");
        campaign.setEligibilityJson("{\"minNights\":1}");
        campaign.setBudget(new BigDecimal("50000000"));
        campaign.setRedemptionLimit(1000L);
        campaign.setPerCustomerLimit(10L);
        campaign.setStackingPolicy("ALLOW_ONE_COUPON");
        campaign.setPriority(100);
        campaign.setStatus("ACTIVE");
        promotionCampaignRepository.save(campaign);
    }

    private MembershipTier ensureGoldTier() {
        MembershipTier tier = membershipTierRepository.findByCodeAndHotelIsNull(GOLD_TIER_CODE)
                .orElseGet(MembershipTier::new);
        tier.setOwnerType("SYSTEM");
        tier.setHotel(null);
        tier.setCode(GOLD_TIER_CODE);
        tier.setNameVi("H\u1ea1ng V\u00e0ng");
        tier.setNameEn("Gold");
        tier.setTierRank(20);
        tier.setEligibilityJson("{\"assignment\":\"MANAGED_POLICY\"}");
        tier.setBenefitsJson("{\"promotionAccess\":true}");
        tier.setStatus("ACTIVE");
        return membershipTierRepository.save(tier);
    }

    private void ensureGoldCampaign(Instant now) {
        PromotionCampaign campaign = promotionCampaignRepository
                .findByCodeAndHotelIsNull(GOLD_CAMPAIGN_CODE)
                .orElseGet(PromotionCampaign::new);
        campaign.setCode(GOLD_CAMPAIGN_CODE);
        campaign.setOwnerType("SYSTEM");
        campaign.setHotel(null);
        campaign.setApplicationType("COUPON");
        campaign.setNameVi("\u01afu \u0111\u00e3i th\u00e0nh vi\u00ean V\u00e0ng");
        campaign.setNameEn("Gold member offer");
        campaign.setDiscountType("FIXED");
        campaign.setDiscountValue(new BigDecimal("50000"));
        campaign.setMaxDiscount(new BigDecimal("50000"));
        campaign.setStartsAt(now.minusSeconds(86400));
        campaign.setEndsAt(now.plusSeconds(86400L * 30));
        campaign.setTimezone("Asia/Ho_Chi_Minh");
        campaign.setEligibilityJson("{\"memberOnly\":true,\"memberTierCodes\":[\"GOLD\"],\"minNights\":1}");
        campaign.setBudget(new BigDecimal("10000000"));
        campaign.setRedemptionLimit(500L);
        campaign.setPerCustomerLimit(5L);
        campaign.setStackingPolicy("ALLOW_ONE_COUPON");
        campaign.setPriority(90);
        campaign.setStatus("ACTIVE");
        promotionCampaignRepository.save(campaign);
    }

    private void ensureCustomerMembership(User customer, User admin, MembershipTier tier, Instant now) {
        CustomerMembership membership = customerMembershipRepository.findByCustomerIdOrderByStartsAtDesc(customer.getId())
                .stream()
                .filter(item -> item.getHotel() == null)
                .filter(item -> item.getTier() != null && GOLD_TIER_CODE.equals(item.getTier().getCode()))
                .findFirst()
                .orElseGet(CustomerMembership::new);
        membership.setCustomer(customer);
        membership.setTier(tier);
        membership.setHotel(null);
        membership.setStartsAt(now.minusSeconds(86400));
        membership.setEndsAt(now.plusSeconds(86400L * 30));
        membership.setStatus("ACTIVE");
        membership.setAssignmentReason("Deterministic local-only E2E membership fixture");
        membership.setAssignedBy(admin);
        customerMembershipRepository.save(membership);
    }

    private void ensureSponsoredSearchPlacement(User admin, Hotel primaryProperty, Instant now) {
        SponsoredPlacement placement = sponsoredPlacementRepository
                .findByHotelIdOrderByStartsAtDescIdDesc(primaryProperty.getId())
                .stream()
                .filter(item -> "SEARCH_RESULTS".equals(item.getPlacementSurface()))
                .filter(item -> item.getTargetHotel() != null
                        && primaryProperty.getId().equals(item.getTargetHotel().getId()))
                .filter(item -> SEARCH_PLACEMENT_TITLE.equals(item.getTitleEn()))
                .findFirst()
                .orElseGet(SponsoredPlacement::new);
        placement.setHotel(primaryProperty);
        placement.setPlacementSurface("SEARCH_RESULTS");
        placement.setPlacementKind("SPONSORED");
        placement.setStatus("ACTIVE");
        placement.setTitleVi("V\u1ecb tr\u00ed t\u00e0i tr\u1ee3 t\u00ecm ki\u1ebfm E2E");
        placement.setTitleEn(SEARCH_PLACEMENT_TITLE);
        placement.setDescriptionVi("D\u1eef li\u1ec7u c\u1ee5c b\u1ed9 d\u00f9ng \u0111\u1ec3 ki\u1ec3m th\u1eed nh\u00e3n t\u00e0i tr\u1ee3 minh b\u1ea1ch.");
        placement.setDescriptionEn("Local-only data used to verify transparent sponsored disclosure.");
        placement.setImageUrl("/assets/demo/hotel-demo-1.png");
        placement.setImageAltVi("Kh\u00e1ch s\u1ea1n E2E \u0111\u01b0\u1ee3c t\u00e0i tr\u1ee3");
        placement.setImageAltEn("Sponsored E2E hotel");
        placement.setTargetType("PROPERTY");
        placement.setTargetHotel(primaryProperty);
        placement.setTargetQueryJson(null);
        placement.setTargetProvinceId(primaryProperty.getProvinceId());
        placement.setStartsAt(now.minusSeconds(86400));
        placement.setEndsAt(now.plusSeconds(86400L * 30));
        placement.setSortPriority(100);
        placement.setBudget(new BigDecimal("10000000"));
        placement.setSpentAmount(BigDecimal.ZERO);
        placement.setImpressionLimit(10000L);
        placement.setImpressionCount(0L);
        placement.setClickLimit(1000L);
        placement.setClickCount(0L);
        placement.setApprovedBy(admin);
        placement.setApprovedAt(now.minusSeconds(3600));
        placement.setRejectedReason(null);
        sponsoredPlacementRepository.save(placement);
    }
}
