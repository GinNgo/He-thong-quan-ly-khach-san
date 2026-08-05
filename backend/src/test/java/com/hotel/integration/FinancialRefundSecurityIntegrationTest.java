package com.hotel.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.entities.User;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.paymentprovider.refund.RefundProviderCredentialsResolver;
import com.hotel.paymentprovider.refund.RefundProviderOrchestrator;
import com.hotel.platformbilling.order.PlatformSubscriptionOrderRepository;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.platformbilling.payment.PlatformFinancialTransaction;
import com.hotel.platformbilling.payment.PlatformFinancialTransactionRepository;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
import com.hotel.propertycommerce.payment.PropertyFinancialTransactionRepository;
import com.hotel.propertycommerce.refund.PropertyRefundController;
import com.hotel.propertycommerce.refund.PropertyRefundRequestRepository;
import com.hotel.propertycommerce.refund.PropertyRefundService;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.SubscriptionPlanRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.security.PermissionInterceptor;
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.method.HandlerMethod;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ContextConfiguration(classes = FinancialRefundSecurityIntegrationTest.TestApplication.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:financial-refund-security;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FinancialRefundSecurityIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.hotel")
    @EnableJpaRepositories(basePackages = "com.hotel")
    @Import(FinancialAuditService.class)
    static class TestApplication {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        PropertyRefundService propertyRefundService(
                PropertyFinancialTransactionRepository transactionRepository,
                com.hotel.propertycommerce.refund.PropertyRefundRequestRepository requestRepository,
                PropertyAccessService propertyAccessService,
                FinancialAuditService auditService) {
            return new PropertyRefundService(transactionRepository, requestRepository, propertyAccessService, auditService);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private HotelRepository hotelRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private UserRepository userRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private ReservationRepository reservationRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private SubscriptionPlanRepository planRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private PlatformSubscriptionOrderRepository orderRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private PlatformFinancialTransactionRepository platformTransactionRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private PropertyRefundRequestRepository propertyRefundRequestRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private PropertyRefundService propertyRefundService;

    @MockBean
    private PropertyAccessService propertyAccessService;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void crossPropertyRefundIsHiddenAndDoesNotCreateMutation() {
        Fixture fixture = createPropertyFixture("cross-property");
        when(propertyAccessService.currentUser()).thenReturn(fixture.actor());
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(fixture.authorizedHotel().getId()));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);

        FinancialException exception = assertThrows(FinancialException.class, () -> propertyRefundService.request(
                command(fixture.propertyTransaction().getPublicId(), 200_000, "cross-property-key")));

        assertEquals(FinancialErrorCode.RESOURCE_NOT_FOUND, exception.code());
        assertTrue(propertyRefundRequestRepository.findByOriginalTransactionIdOrderByRequestedAtAsc(
                fixture.propertyTransaction().getId()).isEmpty());
    }

    @Test
    void platformTransactionCannotBeRefundedThroughPropertyContext() {
        Fixture fixture = createPropertyFixture("wrong-context");
        PlatformFinancialTransaction platformTransaction = createPlatformTransaction(fixture);
        when(propertyAccessService.currentUser()).thenReturn(fixture.actor());
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(fixture.authorizedHotel().getId()));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);

        assertNotNull(platformTransactionRepository.findByPublicId(platformTransaction.getPublicId()).orElse(null));
        FinancialException exception = assertThrows(FinancialException.class, () -> propertyRefundService.request(
                command(platformTransaction.getPublicId(), 100_000, "wrong-context-key")));

        assertEquals(FinancialErrorCode.RESOURCE_NOT_FOUND, exception.code());
        assertTrue(propertyRefundRequestRepository.findAll().isEmpty());
    }

    @Test
    void manualRefundApprovalRequiresDedicatedApprovePermission() throws Exception {
        PropertyRefundController controller = new PropertyRefundController(
                mock(PropertyRefundService.class),
                mock(RefundProviderOrchestrator.class),
                mock(RefundProviderCredentialsResolver.class));
        Method approve = PropertyRefundController.class.getMethod("approve", String.class, String.class);
        Permission permission = approve.getAnnotation(Permission.class);
        assertNotNull(permission);
        assertEquals(FunctionCode.PROPERTY_REFUND, permission.function());
        assertEquals(ActionCode.APPROVE, permission.action());

        PermissionInterceptor interceptor = new PermissionInterceptor(new ObjectMapper());
        HandlerMethod handler = new HandlerMethod(controller, approve);
        authenticate(Map.of(FunctionCode.PROPERTY_REFUND, ActionCode.VIEW));
        MockHttpServletResponse denied = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(
                new MockHttpServletRequest("POST", "/api/property-refunds/refund-1/approve"), denied, handler));
        assertEquals(403, denied.getStatus());
        assertTrue(denied.getContentAsString().contains("FORBIDDEN_PERMISSION"));

        authenticate(Map.of(FunctionCode.PROPERTY_REFUND, ActionCode.APPROVE));
        assertTrue(interceptor.preHandle(
                new MockHttpServletRequest("POST", "/api/property-refunds/refund-1/approve"),
                new MockHttpServletResponse(), handler));
    }

    private PropertyRefundService.RequestCommand command(String transactionId, int amount, String key) {
        return new PropertyRefundService.RequestCommand(
                transactionId, BigDecimal.valueOf(amount), "Security integration refund", key, key);
    }

    private void authenticate(Map<FunctionCode, Integer> permissions) {
        CustomUserDetails user = new CustomUserDetails(
                "security-test", "password", List.of(new SimpleGrantedAuthority("ROLE_OWNER")),
                permissions, 1L, 1L, Map.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private Fixture createPropertyFixture(String suffix) {
        String unique = suffix + "-" + UUID.randomUUID();
        User actor = user("actor-" + unique);
        User customer = user("customer-" + unique);
        Hotel authorizedHotel = hotel("authorized-" + unique);
        Hotel propertyHotel = hotel("property-" + unique);

        Reservation reservation = new Reservation();
        reservation.setUser(customer);
        reservation.setHotel(propertyHotel);
        reservation.setCheckInDate(LocalDate.of(2028, 6, 1));
        reservation.setCheckOutDate(LocalDate.of(2028, 6, 2));
        reservation.setGuests(2);
        reservation.setTotalAmount(BigDecimal.valueOf(500_000));
        reservation.setStatus("CONFIRMED");
        reservation = reservationRepository.saveAndFlush(reservation);

        PropertyFinancialTransaction transaction = PropertyFinancialTransaction.record(
                "security-tx-" + UUID.randomUUID(), propertyHotel, reservation, null, null, null,
                PropertyFinancialTransaction.TransactionType.BOOKING_DEPOSIT,
                PropertyFinancialTransaction.Direction.DEBIT, VndMoney.of(500_000), "MOMO", "SIMULATOR",
                PaymentEnvironment.SIMULATOR, "security-provider-charge-" + UUID.randomUUID(),
                "security-effect-" + UUID.randomUUID(), "PROVIDER", null, "Security fixture",
                LocalDateTime.now(ZoneOffset.UTC));
        transaction = propertyTransactionRepository().saveAndFlush(transaction);
        return new Fixture(actor, customer, authorizedHotel, propertyHotel, transaction);
    }

    private PlatformFinancialTransaction createPlatformTransaction(Fixture fixture) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setCode("SEC-" + UUID.randomUUID());
        plan.setFamilyCode(plan.getCode());
        plan.setNameVi("Security plan");
        plan.setNameEn("Security plan");
        plan.setBillingType("YEARLY");
        plan.setPrice(BigDecimal.valueOf(2_000_000));
        plan.setIsLifetime(false);
        plan.setStatus("ACTIVE");
        plan = planRepository.saveAndFlush(plan);

        SubscriptionOrder order = SubscriptionOrder.create(
                "platform-order-" + UUID.randomUUID(), "SEC-ORDER-" + UUID.randomUUID(), fixture.actor(),
                fixture.propertyHotel(), SubscriptionOrder.Operation.PURCHASE, plan, "PLAN-V1", plan.getCode(),
                plan.getNameEn(), VndMoney.of(2_000_000), "YEARLY", 1, SubscriptionOrder.DurationUnit.YEAR,
                "{}", "platform-key-" + UUID.randomUUID(), "platform-hash", LocalDateTime.now().plusHours(1));
        order = orderRepository.saveAndFlush(order);
        PlatformFinancialTransaction transaction = PlatformFinancialTransaction.record(
                "platform-tx-" + UUID.randomUUID(), order, null, null,
                PlatformFinancialTransaction.TransactionType.SUBSCRIPTION_PURCHASE,
                PlatformFinancialTransaction.Direction.DEBIT, VndMoney.of(2_000_000), "SIMULATOR", "SIMULATOR",
                PaymentEnvironment.SIMULATOR, "platform-provider-charge-" + UUID.randomUUID(),
                "platform-effect-" + UUID.randomUUID(), "PROVIDER", null, "Platform fixture",
                LocalDateTime.now(ZoneOffset.UTC));
        return platformTransactionRepository.saveAndFlush(transaction);
    }

    private User user(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setPasswordHash("test");
        user.setFullName(username);
        user.setStatus("ACTIVE");
        return userRepository.saveAndFlush(user);
    }

    private Hotel hotel(String code) {
        Hotel hotel = new Hotel();
        hotel.setCode(code);
        hotel.setSlug(code.toLowerCase());
        hotel.setName("Security " + code);
        hotel.setAddressLine("Security Street");
        hotel.setCity("Ho Chi Minh City");
        hotel.setCountry("Viet Nam");
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        return hotelRepository.saveAndFlush(hotel);
    }

    private PropertyFinancialTransactionRepository propertyTransactionRepository() {
        return propertyFinancialTransactionRepository;
    }

    @org.springframework.beans.factory.annotation.Autowired
    private PropertyFinancialTransactionRepository propertyFinancialTransactionRepository;

    private record Fixture(
            User actor,
            User customer,
            Hotel authorizedHotel,
            Hotel propertyHotel,
            PropertyFinancialTransaction propertyTransaction) {
    }
}
