package com.hotel.propertycommerce.payment;

import com.hotel.entities.Payment;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.domain.FinancialStates.PaymentState;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.repositories.PaymentRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.services.PropertyAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class LegacyPropertyPaymentAdapter {

    private static final String SESSION_TABLE_EXISTS_SQL = """
            SELECT COUNT(*)
            FROM INFORMATION_SCHEMA.TABLES
            WHERE UPPER(TABLE_NAME) = 'PAYMENT_SESSIONS'
            """;
    private static final String SESSIONS_BY_RESERVATION_SQL = """
            SELECT id, public_id, reservation_id, hotel_id, owner_user_id,
                   provider, method, expected_amount, currency, provider_reference,
                   provider_transaction_id, status, expires_at, completed_at,
                   reconciliation_required, failure_code
            FROM payment_sessions
            WHERE reservation_id = ?
            ORDER BY id DESC
            """;

    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final PropertyPaymentAttemptRepository attemptRepository;
    private final PropertyFinancialTransactionRepository transactionRepository;
    private final PropertyAccessService propertyAccessService;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    public LegacyPropertyPaymentAdapter(
            ReservationRepository reservationRepository,
            PaymentRepository paymentRepository,
            PropertyPaymentAttemptRepository attemptRepository,
            PropertyFinancialTransactionRepository transactionRepository,
            PropertyAccessService propertyAccessService,
            JdbcTemplate jdbcTemplate) {
        this(
                reservationRepository,
                paymentRepository,
                attemptRepository,
                transactionRepository,
                propertyAccessService,
                jdbcTemplate,
                Clock.systemUTC());
    }

    LegacyPropertyPaymentAdapter(
            ReservationRepository reservationRepository,
            PaymentRepository paymentRepository,
            PropertyPaymentAttemptRepository attemptRepository,
            PropertyFinancialTransactionRepository transactionRepository,
            PropertyAccessService propertyAccessService,
            JdbcTemplate jdbcTemplate,
            Clock clock) {
        this.reservationRepository = reservationRepository;
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
        this.transactionRepository = transactionRepository;
        this.propertyAccessService = propertyAccessService;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CompatibilityRead readReservation(Long reservationId) {
        if (reservationId == null) {
            throw new IllegalArgumentException("reservationId is required.");
        }
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        authorize(reservation, propertyAccessService.currentUser());

        boolean authoritativeAttemptsPresent =
                !attemptRepository.findByReservationIdOrderByCreatedAtAsc(reservationId).isEmpty();
        boolean authoritativeLedgerPresent =
                !transactionRepository.findByReservationIdOrderByOccurredAtAsc(reservationId).isEmpty();
        boolean sessionTablePresent = sessionTablePresent();
        List<LegacyAttempt> attempts = sessionTablePresent
                ? jdbcTemplate.query(SESSIONS_BY_RESERVATION_SQL, this::mapSession, reservationId).stream()
                        .map(attempt -> attempt.withFallbackEligible(
                                !authoritativeAttemptsPresent && attempt.compatible()))
                        .toList()
                : List.of();
        List<LegacyTransaction> transactions = paymentRepository.findByReservationId(reservationId).stream()
                .map(payment -> mapPayment(reservation, payment, authoritativeLedgerPresent))
                .toList();

        List<String> warnings = new ArrayList<>();
        warnings.add("COMPATIBILITY_READ_ONLY");
        if (!sessionTablePresent) {
            warnings.add("PAYMENT_SESSIONS_TABLE_NOT_PRESENT");
        }
        if (authoritativeAttemptsPresent) {
            warnings.add("AUTHORITATIVE_ATTEMPTS_PRESENT");
        }
        if (authoritativeLedgerPresent) {
            warnings.add("AUTHORITATIVE_LEDGER_PRESENT");
        }
        if (attempts.stream().anyMatch(attempt -> !attempt.compatible())
                || transactions.stream().anyMatch(transaction -> !transaction.compatible())) {
            warnings.add("RECONCILIATION_REQUIRED");
        }
        return new CompatibilityRead(
                reservation.getId(),
                reservation.getHotel().getId(),
                sessionTablePresent,
                authoritativeAttemptsPresent,
                authoritativeLedgerPresent,
                attempts,
                transactions,
                List.copyOf(warnings));
    }

    private boolean sessionTablePresent() {
        Long count = jdbcTemplate.queryForObject(SESSION_TABLE_EXISTS_SQL, Long.class);
        return count != null && count > 0;
    }

    private LegacyAttempt mapSession(ResultSet resultSet, int rowNumber) throws SQLException {
        BigDecimal rawAmount = resultSet.getBigDecimal("expected_amount");
        BigDecimal amount = integerAmount(rawAmount);
        String currency = code(resultSet.getString("currency"));
        String legacyStatus = code(resultSet.getString("status"));
        LocalDateTime expiresAt = timestamp(resultSet.getTimestamp("expires_at"));
        PaymentState mappedStatus = sessionStatus(legacyStatus, expiresAt);
        boolean reconciliationRequired = resultSet.getBoolean("reconciliation_required");
        boolean ownershipComplete = resultSet.getObject("hotel_id") != null
                && resultSet.getObject("reservation_id") != null
                && resultSet.getObject("owner_user_id") != null;
        boolean compatible = ownershipComplete
                && amount != null
                && amount.signum() > 0
                && "VND".equals(currency)
                && mappedStatus != null
                && !reconciliationRequired;
        return new LegacyAttempt(
                resultSet.getLong("id"),
                text(resultSet.getString("public_id")),
                resultSet.getLong("reservation_id"),
                resultSet.getLong("hotel_id"),
                resultSet.getLong("owner_user_id"),
                code(resultSet.getString("provider")),
                code(resultSet.getString("method")),
                rawAmount,
                amount,
                currency,
                legacyStatus,
                mappedStatus,
                text(resultSet.getString("provider_reference")),
                text(resultSet.getString("provider_transaction_id")),
                expiresAt,
                timestamp(resultSet.getTimestamp("completed_at")),
                reconciliationRequired,
                text(resultSet.getString("failure_code")),
                compatible,
                false);
    }

    private LegacyTransaction mapPayment(
            Reservation expectedReservation,
            Payment payment,
            boolean authoritativeLedgerPresent) {
        boolean sameReservation = payment.getReservation() != null
                && payment.getReservation().getId() != null
                && payment.getReservation().getId().equals(expectedReservation.getId());
        BigDecimal rawAmount = payment.getAmount();
        BigDecimal amount = integerAmount(rawAmount == null ? null : rawAmount.abs());
        String legacyStatus = code(payment.getStatus());
        PaymentState mappedStatus = paymentStatus(legacyStatus);
        Direction direction = rawAmount != null && rawAmount.signum() < 0
                ? Direction.CREDIT
                : Direction.DEBIT;
        boolean directionCompatible = mappedStatus != PaymentState.REFUNDED
                || direction == Direction.CREDIT;
        boolean compatible = sameReservation
                && amount != null
                && amount.signum() > 0
                && mappedStatus != null
                && directionCompatible;
        boolean settledEvidence = mappedStatus == PaymentState.SUCCESS
                || mappedStatus == PaymentState.REFUNDED && direction == Direction.CREDIT;
        return new LegacyTransaction(
                payment.getId(),
                expectedReservation.getId(),
                expectedReservation.getHotel().getId(),
                rawAmount,
                amount,
                "VND",
                direction,
                code(payment.getPaymentMethod()),
                legacyStatus,
                mappedStatus,
                text(payment.getTransactionId()),
                payment.getPaymentDate(),
                compatible,
                !authoritativeLedgerPresent && compatible && settledEvidence);
    }

    private PaymentState sessionStatus(String value, LocalDateTime expiresAt) {
        PaymentState mapped = paymentStatus(value);
        if ((mapped == PaymentState.CREATED || mapped == PaymentState.PENDING)
                && expiresAt != null
                && !expiresAt.toInstant(ZoneOffset.UTC).isAfter(clock.instant())) {
            return PaymentState.EXPIRED;
        }
        return mapped;
    }

    private PaymentState paymentStatus(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "CREATED" -> PaymentState.CREATED;
            case "PENDING", "PENDING_PAYMENT", "PROCESSING" -> PaymentState.PENDING;
            case "SUCCESS", "SUCCEEDED", "PAID", "COMPLETED" -> PaymentState.SUCCESS;
            case "FAILED" -> PaymentState.FAILED;
            case "CANCELLED", "CANCELED" -> PaymentState.CANCELLED;
            case "EXPIRED" -> PaymentState.EXPIRED;
            case "REFUNDED" -> PaymentState.REFUNDED;
            default -> null;
        };
    }

    private BigDecimal integerAmount(BigDecimal value) {
        if (value == null) {
            return null;
        }
        try {
            return value.setScale(0, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private LocalDateTime timestamp(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String code(String value) {
        String normalized = text(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private void authorize(Reservation reservation, User actor) {
        boolean reservationOwner = reservation.getUser() != null
                && reservation.getUser().getId() != null
                && reservation.getUser().getId().equals(actor.getId());
        if (reservationOwner || propertyAccessService.isSystemAdministrator()) {
            return;
        }
        if (!propertyAccessService.accessibleHotelIds().contains(reservation.getHotel().getId())) {
            throw new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    public enum Direction {
        DEBIT,
        CREDIT
    }

    public record CompatibilityRead(
            Long reservationId,
            Long propertyId,
            boolean sessionTablePresent,
            boolean authoritativeAttemptsPresent,
            boolean authoritativeLedgerPresent,
            List<LegacyAttempt> attempts,
            List<LegacyTransaction> transactions,
            List<String> warnings) {
    }

    public record LegacyAttempt(
            Long legacyId,
            String publicId,
            Long reservationId,
            Long propertyId,
            Long ownerUserId,
            String provider,
            String method,
            BigDecimal rawAmount,
            BigDecimal amount,
            String currency,
            String legacyStatus,
            PaymentState mappedStatus,
            String providerReference,
            String providerTransactionId,
            LocalDateTime expiresAt,
            LocalDateTime completedAt,
            boolean legacyReconciliationRequired,
            String failureCode,
            boolean compatible,
            boolean fallbackEligible) {

        LegacyAttempt withFallbackEligible(boolean eligible) {
            return new LegacyAttempt(
                    legacyId, publicId, reservationId, propertyId, ownerUserId, provider, method,
                    rawAmount, amount, currency, legacyStatus, mappedStatus, providerReference,
                    providerTransactionId, expiresAt, completedAt, legacyReconciliationRequired,
                    failureCode, compatible, eligible);
        }
    }

    public record LegacyTransaction(
            Long legacyId,
            Long reservationId,
            Long propertyId,
            BigDecimal rawAmount,
            BigDecimal amount,
            String currency,
            Direction direction,
            String method,
            String legacyStatus,
            PaymentState mappedStatus,
            String transactionReference,
            LocalDateTime occurredAt,
            boolean compatible,
            boolean fallbackEligible) {
    }
}
