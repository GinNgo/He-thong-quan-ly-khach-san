package com.hotel.propertycommerce.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.paymentprovider.audit.FinancialAuditEvent;
import com.hotel.paymentprovider.audit.FinancialAuditEventRepository;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.FinancialStates.PaymentState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.paymentprovider.idempotency.FinancialIdempotencyRepository;
import com.hotel.paymentprovider.idempotency.FinancialIdempotencyService;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ContextConfiguration(classes = ManualTransferConfirmationIntegrationTest.TestApplication.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:manual-confirmation-integration;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class ManualTransferConfirmationIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.hotel")
    @EnableJpaRepositories(basePackages = "com.hotel")
    @Import({
            ManualTransferConfirmationService.class,
            PropertyAccessService.class,
            FinancialIdempotencyService.class,
            FinancialAuditService.class
    })
    static class TestApplication {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private ManualTransferConfirmationService confirmationService;
    @org.springframework.beans.factory.annotation.Autowired
    private PropertyPaymentAttemptRepository attemptRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private PropertyFinancialTransactionRepository transactionRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private FinancialAuditEventRepository auditRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private FinancialIdempotencyRepository idempotencyRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private UserRepository userRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private HotelRepository hotelRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private ReservationRepository reservationRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private UserPropertyRepository userPropertyRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authorizedStaffConfirmationCreatesOneLedgerAndAuditEffect() {
        Fixture fixture = fixture("authorized");
        authenticate(fixture.staff(), Map.of(FunctionCode.PROPERTY_PAYMENT_CONFIRM_MANUAL, ActionCode.APPROVE));
        long transactionCountBefore = transactionRepository.count();
        long auditCountBefore = auditRepository.count();
        long idempotencyCountBefore = idempotencyRepository.count();

        ManualTransferConfirmationService.ConfirmationResult result = confirmationService.confirm(command(
                fixture.attempt().getPublicId(), "Bank statement reviewed by receptionist", "BANK-TRACE-A", "idem-a"));

        assertEquals(PaymentState.SUCCESS, result.status());
        assertEquals(transactionCountBefore + 1, transactionRepository.count());
        List<FinancialAuditEvent> events = auditRepository.findAll().stream()
                .filter(event -> fixture.attempt().getPublicId().equals(event.getAggregateId()))
                .toList();
        assertEquals(1, events.size());
        assertEquals("MANUAL_CONFIRMATION", events.get(0).getSource());
        assertEquals(fixture.attempt().getHotel().getId(), events.get(0).getHotelId());
        assertEquals(auditCountBefore + 1, auditRepository.count());
        assertEquals(idempotencyCountBefore + 1, idempotencyRepository.count());
    }

    @Test
    void missingApprovalPermissionFailsBeforeAnyFinancialMutation() {
        Fixture fixture = fixture("missing-approval");
        authenticate(fixture.staff(), Map.of(FunctionCode.PROPERTY_PAYMENT_CONFIRM_MANUAL, ActionCode.VIEW));
        long transactionCountBefore = transactionRepository.count();
        long auditCountBefore = auditRepository.count();
        long idempotencyCountBefore = idempotencyRepository.count();

        FinancialException exception = assertThrows(FinancialException.class,
                () -> confirmationService.confirm(command(
                        fixture.attempt().getPublicId(), "Review", "BANK-TRACE-B", "idem-b")));

        assertEquals(FinancialErrorCode.TENANT_ACCESS_DENIED, exception.code());
        assertEquals(transactionCountBefore, transactionRepository.count());
        assertEquals(auditCountBefore, auditRepository.count());
        assertEquals(idempotencyCountBefore, idempotencyRepository.count());
    }

    @Test
    void reservationOwnerCannotSelfConfirmEvenWhenAssignedApprovalPermission() {
        Fixture fixture = fixture("self-confirm");
        authenticate(fixture.customer(), Map.of(FunctionCode.PROPERTY_PAYMENT_CONFIRM_MANUAL, ActionCode.APPROVE));
        long transactionCountBefore = transactionRepository.count();
        long auditCountBefore = auditRepository.count();
        long idempotencyCountBefore = idempotencyRepository.count();

        FinancialException exception = assertThrows(FinancialException.class,
                () -> confirmationService.confirm(command(
                        fixture.attempt().getPublicId(), "I paid this transfer", "BANK-TRACE-C", "idem-c")));

        assertEquals(FinancialErrorCode.TENANT_ACCESS_DENIED, exception.code());
        assertEquals(transactionCountBefore, transactionRepository.count());
        assertEquals(auditCountBefore, auditRepository.count());
        assertEquals(idempotencyCountBefore, idempotencyRepository.count());
    }

    @Test
    void staffAssignedToAnotherPropertyGetsResourceNotFoundWithoutMutation() {
        Fixture fixture = fixture("cross-property");
        User otherStaff = userRepository.saveAndFlush(user("other-staff-" + UUID.randomUUID()));
        userPropertyRepository.saveAndFlush(assignment(otherStaff, fixture.otherHotel()));
        authenticate(otherStaff, Map.of(FunctionCode.PROPERTY_PAYMENT_CONFIRM_MANUAL, ActionCode.APPROVE));
        long transactionCountBefore = transactionRepository.count();
        long auditCountBefore = auditRepository.count();
        long idempotencyCountBefore = idempotencyRepository.count();

        FinancialException exception = assertThrows(FinancialException.class,
                () -> confirmationService.confirm(command(
                        fixture.attempt().getPublicId(), "Wrong property", "BANK-TRACE-D", "idem-d")));

        assertEquals(FinancialErrorCode.RESOURCE_NOT_FOUND, exception.code());
        assertEquals(transactionCountBefore, transactionRepository.count());
        assertEquals(auditCountBefore, auditRepository.count());
        assertEquals(idempotencyCountBefore, idempotencyRepository.count());
    }

    @Test
    void equivalentReplayReturnsOriginalEffectWithoutSecondLedgerOrAudit() {
        Fixture fixture = fixture("replay");
        authenticate(fixture.staff(), Map.of(FunctionCode.PROPERTY_PAYMENT_CONFIRM_MANUAL, ActionCode.APPROVE));
        long transactionCountBefore = transactionRepository.count();
        long auditCountBefore = auditRepository.count();
        long idempotencyCountBefore = idempotencyRepository.count();
        ManualTransferConfirmationService.ConfirmCommand command = command(
                fixture.attempt().getPublicId(), "Statement checked", "BANK-TRACE-E", "idem-e");

        ManualTransferConfirmationService.ConfirmationResult first = confirmationService.confirm(command);
        ManualTransferConfirmationService.ConfirmationResult replay = confirmationService.confirm(command);

        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(first.transactionPublicId(), replay.transactionPublicId());
        assertEquals(transactionCountBefore + 1, transactionRepository.count());
        assertEquals(auditCountBefore + 1, auditRepository.count());
        assertEquals(idempotencyCountBefore + 1, idempotencyRepository.count());
    }

    private ManualTransferConfirmationService.ConfirmCommand command(
            String attemptPublicId, String reason, String evidence, String idempotencyKey) {
        return new ManualTransferConfirmationService.ConfirmCommand(
                attemptPublicId, reason, evidence, idempotencyKey, "manual-confirmation-test");
    }

    private Fixture fixture(String suffix) {
        String unique = suffix + "-" + UUID.randomUUID();
        User customer = user("customer-" + unique);
        User staff = user("staff-" + unique);
        Hotel hotel = hotel("primary-" + unique);
        Hotel otherHotel = hotel("other-" + unique);
        customer = userRepository.saveAndFlush(customer);
        staff = userRepository.saveAndFlush(staff);
        hotel = hotelRepository.saveAndFlush(hotel);
        otherHotel = hotelRepository.saveAndFlush(otherHotel);
        userPropertyRepository.saveAndFlush(assignment(staff, hotel));

        Reservation reservation = new Reservation();
        reservation.setUser(customer);
        reservation.setHotel(hotel);
        reservation.setCheckInDate(LocalDate.of(2026, 8, 2));
        reservation.setCheckOutDate(LocalDate.of(2026, 8, 3));
        reservation.setGuests(2);
        reservation.setTotalAmount(BigDecimal.valueOf(1_000_000));
        reservation.setStatus("PENDING_PAYMENT");
        reservation.setPaymentMethod("MANUAL_TRANSFER");
        reservation = reservationRepository.saveAndFlush(reservation);

        PropertyPaymentAttempt attempt = PropertyPaymentAttempt.create(
                "attempt-" + unique,
                hotel,
                reservation,
                null,
                customer,
                PropertyPaymentAttempt.Purpose.DEPOSIT,
                "MANUAL_TRANSFER",
                "BANK",
                PaymentEnvironment.SIMULATOR,
                VndMoney.of(350_000),
                "BOOKING-" + unique,
                "{\"account\":\"****6789\"}",
                "attempt-idem-" + unique,
                "request-hash-" + unique,
                LocalDateTime.now().plusHours(1));
        attempt.transitionTo(PaymentState.PENDING_VERIFICATION,
                LocalDateTime.now().minusMinutes(1), null, null);
        attempt = attemptRepository.saveAndFlush(attempt);
        return new Fixture(customer, staff, hotel, otherHotel, attempt);
    }

    private User user(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setPasswordHash("test");
        user.setStatus("ACTIVE");
        return user;
    }

    private Hotel hotel(String code) {
        Hotel hotel = new Hotel();
        hotel.setCode(code);
        hotel.setSlug(code.toLowerCase());
        hotel.setName(code);
        hotel.setAddressLine("Payment test street");
        hotel.setCity("Ho Chi Minh City");
        hotel.setCountry("Viet Nam");
        hotel.setStatus("ACTIVE");
        hotel.setOperationStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        return hotel;
    }

    private UserProperty assignment(User user, Hotel hotel) {
        UserProperty assignment = new UserProperty();
        assignment.setUser(user);
        assignment.setHotel(hotel);
        assignment.setRelationshipType("STAFF");
        assignment.setStatus("ACTIVE");
        return assignment;
    }

    private void authenticate(User user, Map<FunctionCode, Integer> permissions) {
        CustomUserDetails principal = new CustomUserDetails(
                user.getUsername(),
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_STAFF")),
                permissions,
                user.getId(),
                null,
                Map.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private record Fixture(User customer, User staff, Hotel hotel, Hotel otherHotel, PropertyPaymentAttempt attempt) {
    }
}
