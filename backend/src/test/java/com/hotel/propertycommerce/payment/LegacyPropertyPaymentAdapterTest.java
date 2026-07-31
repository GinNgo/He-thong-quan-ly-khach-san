package com.hotel.propertycommerce.payment;

import com.hotel.entities.Hotel;
import com.hotel.entities.Payment;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.domain.FinancialStates.PaymentState;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.repositories.PaymentRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyPropertyPaymentAdapterTest {

    private static final Instant NOW = Instant.parse("2026-07-31T13:00:00Z");

    @Mock private ReservationRepository reservationRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PropertyPaymentAttemptRepository attemptRepository;
    @Mock private PropertyFinancialTransactionRepository transactionRepository;
    @Mock private PropertyAccessService propertyAccessService;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ResultSet resultSet;

    private LegacyPropertyPaymentAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LegacyPropertyPaymentAdapter(
                reservationRepository,
                paymentRepository,
                attemptRepository,
                transactionRepository,
                propertyAccessService,
                jdbcTemplate,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void mapsLegacySessionsAndPaymentsWithoutMutatingThem() throws Exception {
        Reservation reservation = reservation();
        Payment payment = payment(reservation, 81L, "txn-MixedCase", "SUCCEEDED", "360000");
        authorizeOwner(reservation);
        when(attemptRepository.findByReservationIdOrderByCreatedAtAsc(42L)).thenReturn(List.of());
        when(transactionRepository.findByReservationIdOrderByOccurredAtAsc(42L)).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(1L);
        sessionRow("legacy-Session-A", "PENDING", "360000.00",
                LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(42L))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<LegacyPropertyPaymentAdapter.LegacyAttempt> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(resultSet, 0));
        });
        when(paymentRepository.findByReservationId(42L)).thenReturn(List.of(payment));

        LegacyPropertyPaymentAdapter.CompatibilityRead result = adapter.readReservation(42L);

        assertTrue(result.sessionTablePresent());
        assertFalse(result.authoritativeAttemptsPresent());
        assertFalse(result.authoritativeLedgerPresent());
        assertEquals("legacy-Session-A", result.attempts().get(0).publicId());
        assertEquals(PaymentState.EXPIRED, result.attempts().get(0).mappedStatus());
        assertTrue(result.attempts().get(0).fallbackEligible());
        assertEquals("txn-MixedCase", result.transactions().get(0).transactionReference());
        assertEquals(PaymentState.SUCCESS, result.transactions().get(0).mappedStatus());
        assertTrue(result.transactions().get(0).fallbackEligible());
        assertEquals(List.of("COMPATIBILITY_READ_ONLY"), result.warnings());
        verify(paymentRepository, never()).save(any());
        verify(attemptRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void authoritativeRecordsDisableLegacyFallbackToPreventDoubleCounting() throws Exception {
        Reservation reservation = reservation();
        authorizeOwner(reservation);
        when(attemptRepository.findByReservationIdOrderByCreatedAtAsc(42L))
                .thenReturn(List.of(org.mockito.Mockito.mock(PropertyPaymentAttempt.class)));
        when(transactionRepository.findByReservationIdOrderByOccurredAtAsc(42L))
                .thenReturn(List.of(org.mockito.Mockito.mock(PropertyFinancialTransaction.class)));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(1L);
        sessionRow("legacy-session", "PENDING", "360000",
                LocalDateTime.ofInstant(NOW.plusSeconds(900), ZoneOffset.UTC));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(42L))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<LegacyPropertyPaymentAdapter.LegacyAttempt> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(resultSet, 0));
        });
        when(paymentRepository.findByReservationId(42L))
                .thenReturn(List.of(payment(reservation, 81L, "txn-001", "SUCCESS", "360000")));

        LegacyPropertyPaymentAdapter.CompatibilityRead result = adapter.readReservation(42L);

        assertFalse(result.attempts().get(0).fallbackEligible());
        assertFalse(result.transactions().get(0).fallbackEligible());
        assertTrue(result.warnings().contains("AUTHORITATIVE_ATTEMPTS_PRESENT"));
        assertTrue(result.warnings().contains("AUTHORITATIVE_LEDGER_PRESENT"));
    }

    @Test
    void ambiguousLegacyValuesStayVisibleButRequireReconciliation() throws Exception {
        Reservation reservation = reservation();
        authorizeOwner(reservation);
        when(attemptRepository.findByReservationIdOrderByCreatedAtAsc(42L)).thenReturn(List.of());
        when(transactionRepository.findByReservationIdOrderByOccurredAtAsc(42L)).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(1L);
        sessionRow("legacy-invalid", "UNKNOWN", "360000.50",
                LocalDateTime.ofInstant(NOW.plusSeconds(900), ZoneOffset.UTC));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(42L))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<LegacyPropertyPaymentAdapter.LegacyAttempt> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(resultSet, 0));
        });
        when(paymentRepository.findByReservationId(42L))
                .thenReturn(List.of(payment(reservation, 81L, "refund-ambiguous", "REFUNDED", "360000")));

        LegacyPropertyPaymentAdapter.CompatibilityRead result = adapter.readReservation(42L);

        assertFalse(result.attempts().get(0).compatible());
        assertFalse(result.attempts().get(0).fallbackEligible());
        assertFalse(result.transactions().get(0).compatible());
        assertFalse(result.transactions().get(0).fallbackEligible());
        assertTrue(result.warnings().contains("RECONCILIATION_REQUIRED"));
    }

    @Test
    void missingSessionTableStillReturnsLegacyPayments() {
        Reservation reservation = reservation();
        authorizeOwner(reservation);
        when(attemptRepository.findByReservationIdOrderByCreatedAtAsc(42L)).thenReturn(List.of());
        when(transactionRepository.findByReservationIdOrderByOccurredAtAsc(42L)).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        when(paymentRepository.findByReservationId(42L))
                .thenReturn(List.of(payment(reservation, 81L, "txn-001", "FAILED", "360000")));

        LegacyPropertyPaymentAdapter.CompatibilityRead result = adapter.readReservation(42L);

        assertFalse(result.sessionTablePresent());
        assertTrue(result.attempts().isEmpty());
        assertEquals(1, result.transactions().size());
        assertEquals(PaymentState.FAILED, result.transactions().get(0).mappedStatus());
        assertTrue(result.transactions().get(0).compatible());
        assertFalse(result.transactions().get(0).fallbackEligible());
        assertTrue(result.warnings().contains("PAYMENT_SESSIONS_TABLE_NOT_PRESENT"));
        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), eq(42L));
    }

    @Test
    void unauthorizedReservationIsHiddenBeforeAnyLegacyRead() {
        Reservation reservation = reservation();
        User attacker = new User();
        attacker.setId(999L);
        when(reservationRepository.findById(42L)).thenReturn(Optional.of(reservation));
        when(propertyAccessService.currentUser()).thenReturn(attacker);

        FinancialException exception = assertThrows(
                FinancialException.class,
                () -> adapter.readReservation(42L));

        assertEquals(FinancialErrorCode.RESOURCE_NOT_FOUND, exception.code());
        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Long.class));
        verify(paymentRepository, never()).findByReservationId(any());
        verify(attemptRepository, never()).findByReservationIdOrderByCreatedAtAsc(any());
    }

    private void authorizeOwner(Reservation reservation) {
        when(reservationRepository.findById(42L)).thenReturn(Optional.of(reservation));
        when(propertyAccessService.currentUser()).thenReturn(reservation.getUser());
    }

    private void sessionRow(
            String publicId,
            String status,
            String amount,
            LocalDateTime expiresAt) throws Exception {
        when(resultSet.getLong("id")).thenReturn(71L);
        when(resultSet.getString("public_id")).thenReturn(publicId);
        when(resultSet.getLong("reservation_id")).thenReturn(42L);
        when(resultSet.getLong("hotel_id")).thenReturn(3L);
        when(resultSet.getLong("owner_user_id")).thenReturn(7L);
        when(resultSet.getObject("reservation_id")).thenReturn(42L);
        when(resultSet.getObject("hotel_id")).thenReturn(3L);
        when(resultSet.getObject("owner_user_id")).thenReturn(7L);
        when(resultSet.getString("provider")).thenReturn("MOMO");
        when(resultSet.getString("method")).thenReturn("MOMO");
        when(resultSet.getBigDecimal("expected_amount")).thenReturn(new BigDecimal(amount));
        when(resultSet.getString("currency")).thenReturn("VND");
        when(resultSet.getString("status")).thenReturn(status);
        when(resultSet.getString("provider_reference")).thenReturn("provider-order-001");
        when(resultSet.getString("provider_transaction_id")).thenReturn("provider-txn-001");
        when(resultSet.getTimestamp("expires_at")).thenReturn(Timestamp.valueOf(expiresAt));
        when(resultSet.getTimestamp("completed_at")).thenReturn(null);
        when(resultSet.getBoolean("reconciliation_required")).thenReturn(false);
        when(resultSet.getString("failure_code")).thenReturn(null);
    }

    private Reservation reservation() {
        Hotel hotel = new Hotel();
        hotel.setId(3L);
        User owner = new User();
        owner.setId(7L);
        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setHotel(hotel);
        reservation.setUser(owner);
        return reservation;
    }

    private Payment payment(
            Reservation reservation,
            Long id,
            String transactionId,
            String status,
            String amount) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setReservation(reservation);
        payment.setAmount(new BigDecimal(amount));
        payment.setPaymentMethod("MOMO");
        payment.setStatus(status);
        payment.setTransactionId(transactionId);
        payment.setPaymentDate(LocalDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC));
        return payment;
    }
}
