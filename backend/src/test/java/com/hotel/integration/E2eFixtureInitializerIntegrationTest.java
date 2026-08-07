package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.entities.Payment;
import com.hotel.entities.User;
import com.hotel.repositories.AccountSubscriptionRepository;
import com.hotel.repositories.CustomerMembershipRepository;
import com.hotel.repositories.MembershipTierRepository;
import com.hotel.repositories.NotificationRepository;
import com.hotel.repositories.PaymentRepository;
import com.hotel.repositories.PaymentSessionRepository;
import com.hotel.repositories.PromotionCampaignRepository;
import com.hotel.repositories.RefundRequestRepository;
import com.hotel.repositories.ReservationDetailRepository;
import com.hotel.repositories.SponsoredPlacementRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.services.impl.E2eFixtureInitializer;
import com.hotel.services.SubscriptionFeatureService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = BackendApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:e2efixtures;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "app.e2e-fixtures.enabled=true",
        "payment.property.encryption-key=e2e-fixture-encryption-key-for-tests-only",
        "LUXESTAY_E2E_CUSTOMER_USERNAME=e2e-test-customer",
        "LUXESTAY_E2E_CUSTOMER_PASSWORD=customer-test-password",
        "LUXESTAY_E2E_ADMIN_USERNAME=e2e-test-admin",
        "LUXESTAY_E2E_ADMIN_PASSWORD=admin-test-password",
        "LUXESTAY_E2E_OWNER_USERNAME=e2e-test-owner",
        "LUXESTAY_E2E_OWNER_PASSWORD=owner-test-password"
})
@ActiveProfiles("test")
@Transactional
class E2eFixtureInitializerIntegrationTest {

    @Autowired
    private E2eFixtureInitializer fixtureInitializer;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserPropertyRepository userPropertyRepository;

    @Autowired
    private AccountSubscriptionRepository accountSubscriptionRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentSessionRepository paymentSessionRepository;

    @Autowired
    private RefundRequestRepository refundRequestRepository;

    @Autowired
    private ReservationDetailRepository reservationDetailRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PromotionCampaignRepository promotionCampaignRepository;

    @Autowired
    private MembershipTierRepository membershipTierRepository;

    @Autowired
    private CustomerMembershipRepository customerMembershipRepository;

    @Autowired
    private SponsoredPlacementRepository sponsoredPlacementRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SubscriptionFeatureService subscriptionFeatureService;

    @Test
    void fixtureProvisioningIsCompleteAndIdempotent() {
        fixtureInitializer.provision();

        User customer = userRepository.findByUsername("e2e-test-customer").orElseThrow();
        User admin = userRepository.findByUsername("e2e-test-admin").orElseThrow();
        User owner = userRepository.findByUsername("e2e-test-owner").orElseThrow();
        User expiredOwner = userRepository.findByUsername("e2e-test-owner-expired").orElseThrow();
        User lifetimeOwner = userRepository.findByUsername("e2e-test-owner-lifetime").orElseThrow();
        User multiPlanOwner = userRepository.findByUsername("e2e-test-owner-multi").orElseThrow();

        assertTrue(passwordEncoder.matches("customer-test-password", customer.getPasswordHash()));
        assertTrue(admin.getRoles().stream().anyMatch(role -> "SUPER_ADMIN".equals(role.getCode())));
        assertEquals(2, userPropertyRepository.countByUserIdAndStatus(owner.getId(), "ACTIVE"));
        assertEquals(1, userPropertyRepository.countByUserIdAndStatus(expiredOwner.getId(), "ACTIVE"));
        assertTrue(accountSubscriptionRepository.findEffectiveSubscriptionsByUserId(owner.getId()).size() >= 1);
        assertTrue(accountSubscriptionRepository.findEffectiveSubscriptionsByUserId(expiredOwner.getId()).isEmpty());
        assertEquals(-1, subscriptionFeatureService.getActiveFeaturesForUser(lifetimeOwner.getId()).get("MAX_ROOMS"));
        assertEquals(1000, subscriptionFeatureService.getActiveFeaturesForUser(multiPlanOwner.getId()).get("MAX_ROOMS"));

        Payment payment = paymentRepository.findByTransactionId("E2E-FIXTURE-PAYMENT").orElseThrow();
        assertEquals(customer.getId(), payment.getReservation().getUser().getId());
        assertEquals(1, reservationDetailRepository.findByReservationId(payment.getReservation().getId()).size());
        assertEquals(4, paymentSessionRepository.findAll().stream()
                .filter(item -> item.getPublicId().startsWith("E2E-PAYMENT-SESSION-"))
                .count());
        assertEquals(4, refundRequestRepository.findAll().stream()
                .filter(item -> item.getPublicId().startsWith("E2E-REFUND-"))
                .count());
        assertTrue(notificationRepository.findByUserIdOrUserIdIsNullOrderByCreatedAtDesc(admin.getId()).size() >= 2);
        assertTrue(promotionCampaignRepository.findByCodeAndHotelIsNull("E2E-AUTO-10").isPresent());
        assertTrue(promotionCampaignRepository.findByCodeAndHotelIsNull("E2EGOLD").isPresent());
        assertTrue(membershipTierRepository.findByCodeAndHotelIsNull("GOLD").isPresent());
        assertEquals(1, customerMembershipRepository.findByCustomerIdOrderByStartsAtDesc(customer.getId()).stream()
                .filter(item -> item.getHotel() == null)
                .filter(item -> item.getTier() != null && "GOLD".equals(item.getTier().getCode()))
                .count());
        assertEquals(1, sponsoredPlacementRepository.findAll().stream()
                .filter(item -> "SEARCH_RESULTS".equals(item.getPlacementSurface()))
                .filter(item -> item.getTargetHotel() != null
                        && payment.getReservation().getHotel().getId().equals(item.getTargetHotel().getId()))
                .filter(item -> "E2E sponsored search placement".equals(item.getTitleEn()))
                .count());

        fixtureInitializer.provision();

        assertEquals(1, paymentRepository.findAll().stream()
                .filter(item -> "E2E-FIXTURE-PAYMENT".equals(item.getTransactionId()))
                .count());
        assertEquals(1, reservationDetailRepository.findByReservationId(payment.getReservation().getId()).size());
        assertEquals(1, userRepository.findAll().stream()
                .filter(item -> "e2e-test-owner-expired".equals(item.getUsername()))
                .count());
        assertEquals(4, paymentSessionRepository.findAll().stream()
                .filter(item -> item.getPublicId().startsWith("E2E-PAYMENT-SESSION-"))
                .count());
        assertEquals(4, refundRequestRepository.findAll().stream()
                .filter(item -> item.getPublicId().startsWith("E2E-REFUND-"))
                .count());
        assertEquals(2, accountSubscriptionRepository.findEffectiveSubscriptionsByUserId(multiPlanOwner.getId()).size());
        assertEquals(1, promotionCampaignRepository.findAll().stream()
                .filter(item -> "E2E-AUTO-10".equals(item.getCode()))
                .count());
        assertEquals(1, promotionCampaignRepository.findAll().stream()
                .filter(item -> "E2EGOLD".equals(item.getCode()))
                .count());
        assertEquals(1, customerMembershipRepository.findByCustomerIdOrderByStartsAtDesc(customer.getId()).stream()
                .filter(item -> item.getHotel() == null)
                .filter(item -> item.getTier() != null && "GOLD".equals(item.getTier().getCode()))
                .count());
        assertEquals(1, sponsoredPlacementRepository.findAll().stream()
                .filter(item -> "E2E sponsored search placement".equals(item.getTitleEn()))
                .count());
    }
}
