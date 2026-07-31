package com.hotel.propertycommerce.payment;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.paymentprovider.adapters.PaymentProviderAdapterRegistry;
import com.hotel.paymentprovider.adapters.SimulatorPaymentProviderAdapter;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.FinancialStates.PaymentState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyPaymentCallbackServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T11:15:00Z");
    private static final String SECRET = "simulator-signing-secret-with-32-chars";

    @Mock
    private PropertyPaymentAttemptRepository attemptRepository;
    @Mock
    private PropertyFinancialTransactionRepository transactionRepository;
    @Mock
    private FinancialAuditService auditService;

    private PropertyPaymentCallbackService service;

    @BeforeEach
    void setUp() {
        service = new PropertyPaymentCallbackService(
                attemptRepository,
                transactionRepository,
                new PaymentProviderAdapterRegistry(List.of(new SimulatorPaymentProviderAdapter())),
                auditService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void successfulCallbackCreatesOneLedgerEffectAndEquivalentReplayReturnsIt() {
        PropertyPaymentAttempt attempt = attempt("attempt-success");
        Map<String, Object> payload = signedPayload(
                attempt.getPublicId(), "SIM-EVENT-1", "SIM-TXN-1", 350_000, "SUCCEEDED");
        AtomicReference<PropertyFinancialTransaction> savedTransaction = new AtomicReference<>();
        when(attemptRepository.findByProviderAndReferenceForUpdate("SIMULATOR", attempt.getPublicId()))
                .thenReturn(java.util.Optional.of(attempt));
        when(attemptRepository.findByProviderEventForUpdate("SIMULATOR", PaymentEnvironment.SIMULATOR, "SIM-EVENT-1"))
                .thenAnswer(invocation -> attempt.getProviderEventId() == null
                        ? java.util.Optional.empty()
                        : java.util.Optional.of(attempt));
        when(transactionRepository.findByAttemptIdOrderByOccurredAtAsc(71L))
                .thenAnswer(invocation -> savedTransaction.get() == null
                        ? List.of()
                        : List.of(savedTransaction.get()));
        when(transactionRepository.findByIdempotencyIdentity(any())).thenReturn(java.util.Optional.empty());
        when(attemptRepository.saveAndFlush(attempt)).thenReturn(attempt);
        when(transactionRepository.saveAndFlush(any(PropertyFinancialTransaction.class))).thenAnswer(invocation -> {
            PropertyFinancialTransaction transaction = invocation.getArgument(0);
            ReflectionTestUtils.setField(transaction, "id", 81L);
            savedTransaction.set(transaction);
            return transaction;
        });

        PropertyPaymentCallbackService.CallbackResult first = service.process(command(payload));
        PropertyPaymentCallbackService.CallbackResult replay = service.process(command(payload));

        assertTrue(first.accepted());
        assertFalse(first.replayed());
        assertEquals(PaymentState.SUCCESS, first.status());
        assertEquals("SIM-TXN-1", attempt.getProviderTransactionReference());
        assertEquals("SIM-EVENT-1", attempt.getProviderEventId());
        assertTrue(replay.accepted());
        assertTrue(replay.replayed());
        assertEquals(first.transactionPublicId(), replay.transactionPublicId());
        verify(transactionRepository, times(1)).saveAndFlush(any(PropertyFinancialTransaction.class));
        verify(auditService, times(2)).append(any());
    }

    @Test
    void invalidSignatureAndWrongAmountChangeNoFinancialState() {
        PropertyPaymentAttempt attempt = attempt("attempt-invalid");
        Map<String, Object> invalidSignature = signedPayload(
                attempt.getPublicId(), "SIM-EVENT-2", "SIM-TXN-2", 350_000, "SUCCEEDED");
        invalidSignature.put("signature", "tampered");
        Map<String, Object> wrongAmount = signedPayload(
                attempt.getPublicId(), "SIM-EVENT-3", "SIM-TXN-3", 350_001, "SUCCEEDED");
        when(attemptRepository.findByProviderAndReferenceForUpdate("SIMULATOR", attempt.getPublicId()))
                .thenReturn(java.util.Optional.of(attempt));

        PropertyPaymentCallbackService.CallbackResult invalid = service.process(command(invalidSignature));
        PropertyPaymentCallbackService.CallbackResult mismatched = service.process(command(wrongAmount));

        assertEquals(FinancialErrorCode.CALLBACK_SIGNATURE_INVALID, invalid.errorCode());
        assertEquals(FinancialErrorCode.CALLBACK_AMOUNT_MISMATCH, mismatched.errorCode());
        assertEquals(PaymentState.PENDING, attempt.getStatus());
        assertNull(attempt.getProviderEventId());
        verify(attemptRepository, never()).saveAndFlush(any());
        verify(transactionRepository, never()).saveAndFlush(any());
        verify(auditService, times(2)).append(any());
    }

    @Test
    void verifiedFailureTransitionsAttemptWithoutCreatingLedgerMoney() {
        PropertyPaymentAttempt attempt = attempt("attempt-failed");
        Map<String, Object> payload = signedPayload(
                attempt.getPublicId(), "SIM-EVENT-4", "SIM-TXN-4", 350_000, "FAILED");
        when(attemptRepository.findByProviderAndReferenceForUpdate("SIMULATOR", attempt.getPublicId()))
                .thenReturn(java.util.Optional.of(attempt));
        when(attemptRepository.findByProviderEventForUpdate("SIMULATOR", PaymentEnvironment.SIMULATOR, "SIM-EVENT-4"))
                .thenReturn(java.util.Optional.empty());
        when(transactionRepository.findByAttemptIdOrderByOccurredAtAsc(71L)).thenReturn(List.of());
        when(attemptRepository.saveAndFlush(attempt)).thenReturn(attempt);

        PropertyPaymentCallbackService.CallbackResult result = service.process(command(payload));

        assertTrue(result.accepted());
        assertFalse(result.replayed());
        assertEquals(PaymentState.FAILED, attempt.getStatus());
        assertEquals("SIMULATOR_FAILED", attempt.getFailureCode());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void existingLedgerEffectRepairsPendingAttemptWithoutCreatingDuplicateMoney() {
        PropertyPaymentAttempt attempt = attempt("attempt-recovery");
        Map<String, Object> payload = signedPayload(
                attempt.getPublicId(), "SIM-EVENT-RECOVERY", "SIM-TXN-RECOVERY", 350_000, "SUCCEEDED");
        PropertyFinancialTransaction existing = PropertyFinancialTransaction.record(
                "transaction-recovery",
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
                "SIM-TXN-RECOVERY",
                "existing-effect",
                "PROVIDER",
                null,
                "Existing callback effect",
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        when(attemptRepository.findByProviderAndReferenceForUpdate("SIMULATOR", attempt.getPublicId()))
                .thenReturn(java.util.Optional.of(attempt));
        when(attemptRepository.findByProviderEventForUpdate(
                "SIMULATOR", PaymentEnvironment.SIMULATOR, "SIM-EVENT-RECOVERY"))
                .thenReturn(java.util.Optional.empty());
        when(transactionRepository.findByAttemptIdOrderByOccurredAtAsc(71L)).thenReturn(List.of(existing));
        when(attemptRepository.saveAndFlush(attempt)).thenReturn(attempt);

        PropertyPaymentCallbackService.CallbackResult result = service.process(command(payload));

        assertTrue(result.accepted());
        assertTrue(result.replayed());
        assertEquals(PaymentState.SUCCESS, attempt.getStatus());
        assertEquals("transaction-recovery", result.transactionPublicId());
        verify(transactionRepository, never()).saveAndFlush(any());
        verify(attemptRepository).saveAndFlush(attempt);
    }

    @Test
    void providerEventOwnedByAnotherAttemptIsRejectedBeforeMutation() {
        PropertyPaymentAttempt attempt = attempt("attempt-first");
        PropertyPaymentAttempt other = attempt("attempt-other");
        ReflectionTestUtils.setField(other, "id", 72L);
        Map<String, Object> payload = signedPayload(
                attempt.getPublicId(), "SIM-EVENT-5", "SIM-TXN-5", 350_000, "SUCCEEDED");
        when(attemptRepository.findByProviderAndReferenceForUpdate("SIMULATOR", attempt.getPublicId()))
                .thenReturn(java.util.Optional.of(attempt));
        when(attemptRepository.findByProviderEventForUpdate("SIMULATOR", PaymentEnvironment.SIMULATOR, "SIM-EVENT-5"))
                .thenReturn(java.util.Optional.of(other));

        PropertyPaymentCallbackService.CallbackResult result = service.process(command(payload));

        assertEquals(FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH, result.errorCode());
        assertEquals(PaymentState.PENDING, attempt.getStatus());
        verify(attemptRepository, never()).saveAndFlush(any());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    private PropertyPaymentCallbackService.CallbackCommand command(Map<String, Object> payload) {
        return new PropertyPaymentCallbackService.CallbackCommand(
                "SIMULATOR",
                PaymentEnvironment.SIMULATOR,
                "SIM-HOTEL-3",
                payload.get("signature").toString(),
                payload,
                Map.of("signingSecret", SECRET),
                NOW,
                "correlation-1");
    }

    private PropertyPaymentAttempt attempt(String publicId) {
        Hotel hotel = new Hotel();
        hotel.setId(3L);
        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setHotel(hotel);
        PropertyPaymentAttempt attempt = PropertyPaymentAttempt.create(
                publicId,
                hotel,
                reservation,
                null,
                null,
                PropertyPaymentAttempt.Purpose.DEPOSIT,
                "MOMO",
                "SIMULATOR",
                PaymentEnvironment.SIMULATOR,
                VndMoney.of(350_000),
                null,
                null,
                "idem-" + publicId,
                "request-hash",
                LocalDateTime.ofInstant(NOW.plusSeconds(900), ZoneOffset.UTC));
        attempt.bindProviderOrderReference(publicId);
        attempt.transitionTo(PaymentState.PENDING, LocalDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC), null, null);
        ReflectionTestUtils.setField(attempt, "id", 71L);
        return attempt;
    }

    private Map<String, Object> signedPayload(
            String reference,
            String eventId,
            String transactionId,
            long amount,
            String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("merchantId", "SIM-HOTEL-3");
        payload.put("eventId", eventId);
        payload.put("transactionId", transactionId);
        payload.put("reference", reference);
        payload.put("amount", amount);
        payload.put("currency", "VND");
        payload.put("occurredAt", NOW.toString());
        payload.put("status", status);
        payload.put("signature", hmac(canonical(payload)));
        return payload;
    }

    private String canonical(Map<String, Object> payload) {
        return payload.entrySet().stream()
                .filter(entry -> !"signature".equals(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
