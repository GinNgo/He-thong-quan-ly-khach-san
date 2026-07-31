package com.hotel.propertycommerce.payment;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.FinancialStates.PaymentState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.paymentprovider.idempotency.FinancialIdempotencyRecord;
import com.hotel.paymentprovider.idempotency.FinancialIdempotencyService;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualTransferConfirmationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T12:00:00Z");

    @Mock
    private PropertyPaymentAttemptRepository attemptRepository;
    @Mock
    private PropertyFinancialTransactionRepository transactionRepository;
    @Mock
    private PropertyAccessService propertyAccessService;
    @Mock
    private FinancialIdempotencyService idempotencyService;
    @Mock
    private FinancialAuditService auditService;

    private ManualTransferConfirmationService service;

    @BeforeEach
    void setUp() {
        service = new ManualTransferConfirmationService(
                attemptRepository,
                transactionRepository,
                propertyAccessService,
                idempotencyService,
                auditService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authorizedPropertyActorConfirmsManualTransferExactlyOnce() {
        User actor = user(9L);
        PropertyPaymentAttempt attempt = attempt("attempt-manual", user(7L));
        authenticate(9L, Map.of(FunctionCode.PROPERTY_PAYMENT_CONFIRM_MANUAL, ActionCode.APPROVE));
        acquired();
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.currentUser()).thenReturn(actor);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(3L));
        when(attemptRepository.findByPublicIdForUpdate("attempt-manual")).thenReturn(Optional.of(attempt));
        when(transactionRepository.findByIdempotencyIdentity(any())).thenReturn(Optional.empty());
        when(transactionRepository.findByAttemptIdOrderByOccurredAtAsc(71L)).thenReturn(List.of());
        when(attemptRepository.saveAndFlush(attempt)).thenReturn(attempt);
        when(transactionRepository.saveAndFlush(any(PropertyFinancialTransaction.class))).thenAnswer(invocation -> {
            PropertyFinancialTransaction transaction = invocation.getArgument(0);
            ReflectionTestUtils.setField(transaction, "id", 81L);
            return transaction;
        });

        ManualTransferConfirmationService.ConfirmationResult result = service.confirm(command());

        assertEquals(PaymentState.SUCCESS, result.status());
        assertEquals(0, java.math.BigDecimal.valueOf(350_000).compareTo(result.amount()));
        assertEquals(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), result.confirmedAt());
        assertSame(actor, attempt.getVerifiedBy());
        assertEquals("BANK-TRACE-001", attempt.getProviderTransactionReference());
        assertTrue(attempt.getProviderEventId().startsWith("MANUAL:"));
        verify(idempotencyService).complete(91L, 200, result.transactionPublicId());
        verify(auditService).append(any());
    }

    @Test
    void reservationOwnerCannotSelfConfirmEvenWithPermission() {
        User customer = user(7L);
        PropertyPaymentAttempt attempt = attempt("attempt-self", customer);
        authenticate(7L, Map.of(FunctionCode.PROPERTY_PAYMENT_CONFIRM_MANUAL, ActionCode.APPROVE));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.currentUser()).thenReturn(customer);
        when(attemptRepository.findByPublicIdForUpdate("attempt-manual")).thenReturn(Optional.of(attempt));

        FinancialException exception = assertThrows(FinancialException.class, () -> service.confirm(command()));

        assertEquals(FinancialErrorCode.TENANT_ACCESS_DENIED, exception.code());
        verify(idempotencyService, never()).begin(any());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void missingApprovalPermissionIsRejectedBeforeResourceLookup() {
        authenticate(9L, Map.of(FunctionCode.PROPERTY_PAYMENT_CONFIRM_MANUAL, ActionCode.VIEW));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);

        FinancialException exception = assertThrows(FinancialException.class, () -> service.confirm(command()));

        assertEquals(FinancialErrorCode.TENANT_ACCESS_DENIED, exception.code());
        verify(attemptRepository, never()).findByPublicIdForUpdate(any());
        verify(idempotencyService, never()).begin(any());
    }

    @Test
    void assignedRoleCannotConfirmAnotherPropertyAttempt() {
        User actor = user(9L);
        PropertyPaymentAttempt attempt = attempt("attempt-cross-property", user(7L));
        authenticate(9L, Map.of(FunctionCode.PROPERTY_PAYMENT_CONFIRM_MANUAL, ActionCode.APPROVE));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.currentUser()).thenReturn(actor);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(99L));
        when(attemptRepository.findByPublicIdForUpdate("attempt-manual")).thenReturn(Optional.of(attempt));

        FinancialException exception = assertThrows(FinancialException.class, () -> service.confirm(command()));

        assertEquals(FinancialErrorCode.RESOURCE_NOT_FOUND, exception.code());
        verify(idempotencyService, never()).begin(any());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void expiredManualAttemptCannotBeConfirmed() {
        User actor = user(9L);
        PropertyPaymentAttempt attempt = attempt("attempt-expired", user(7L));
        ReflectionTestUtils.setField(attempt, "expiresAt",
                LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        authenticate(9L, Map.of(FunctionCode.PROPERTY_PAYMENT_CONFIRM_MANUAL, ActionCode.APPROVE));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.currentUser()).thenReturn(actor);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(3L));
        when(attemptRepository.findByPublicIdForUpdate("attempt-manual")).thenReturn(Optional.of(attempt));

        FinancialException exception = assertThrows(FinancialException.class, () -> service.confirm(command()));

        assertEquals(FinancialErrorCode.ATTEMPT_EXPIRED, exception.code());
        verify(idempotencyService, never()).begin(any());
        verify(attemptRepository, never()).saveAndFlush(any());
    }

    @Test
    void completedIdempotencyReplayReturnsOriginalLedgerWithoutMutation() {
        User actor = user(9L);
        PropertyPaymentAttempt attempt = attempt("attempt-manual", user(7L));
        attempt.transitionTo(PaymentState.SUCCESS, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), actor, null);
        PropertyFinancialTransaction transaction = transaction(attempt, "transaction-original");
        authenticate(9L, Map.of(FunctionCode.PROPERTY_PAYMENT_CONFIRM_MANUAL, ActionCode.APPROVE));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.currentUser()).thenReturn(actor);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(3L));
        when(attemptRepository.findByPublicIdForUpdate("attempt-manual")).thenReturn(Optional.of(attempt));
        when(idempotencyService.begin(any())).thenReturn(
                new FinancialIdempotencyService.Replay(91L, 200, "transaction-original"));
        when(transactionRepository.findByPublicId("transaction-original")).thenReturn(Optional.of(transaction));

        ManualTransferConfirmationService.ConfirmationResult result = service.confirm(command());

        assertTrue(result.replayed());
        assertEquals("transaction-original", result.transactionPublicId());
        verify(attemptRepository, never()).saveAndFlush(any());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    private ManualTransferConfirmationService.ConfirmCommand command() {
        return new ManualTransferConfirmationService.ConfirmCommand(
                "attempt-manual",
                "Bank statement reviewed by receptionist",
                "BANK-TRACE-001",
                "idem-manual-001",
                "correlation-manual");
    }

    private void acquired() {
        FinancialIdempotencyRecord record = org.mockito.Mockito.mock(FinancialIdempotencyRecord.class);
        when(record.getId()).thenReturn(91L);
        when(idempotencyService.begin(any())).thenReturn(new FinancialIdempotencyService.Acquired(record));
    }

    private PropertyPaymentAttempt attempt(String publicId, User customer) {
        Hotel hotel = new Hotel();
        hotel.setId(3L);
        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setHotel(hotel);
        reservation.setUser(customer);
        PropertyPaymentAttempt attempt = PropertyPaymentAttempt.create(
                publicId,
                hotel,
                reservation,
                null,
                customer,
                PropertyPaymentAttempt.Purpose.DEPOSIT,
                "MANUAL_TRANSFER",
                "BANK",
                PaymentEnvironment.SIMULATOR,
                VndMoney.of(350_000),
                "BOOKING-LS42",
                null,
                "idem-attempt",
                "request-hash",
                LocalDateTime.ofInstant(NOW.plusSeconds(900), ZoneOffset.UTC));
        attempt.transitionTo(PaymentState.PENDING_VERIFICATION,
                LocalDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC), null, null);
        ReflectionTestUtils.setField(attempt, "id", 71L);
        return attempt;
    }

    private PropertyFinancialTransaction transaction(PropertyPaymentAttempt attempt, String publicId) {
        return PropertyFinancialTransaction.record(
                publicId,
                attempt.getHotel(),
                attempt.getReservation(),
                null,
                attempt,
                null,
                PropertyFinancialTransaction.TransactionType.BOOKING_DEPOSIT,
                PropertyFinancialTransaction.Direction.DEBIT,
                VndMoney.of(350_000),
                attempt.getMethod(),
                attempt.getProvider(),
                attempt.getEnvironment(),
                "BANK-TRACE-001",
                "PROP-MANUAL-existing",
                "USER",
                9L,
                "Confirmed",
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private void authenticate(Long userId, Map<FunctionCode, Integer> permissions) {
        CustomUserDetails principal = new CustomUserDetails(
                "staff@example.com",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_STAFF")),
                permissions,
                userId,
                3L,
                Map.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
