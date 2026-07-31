package com.hotel.propertycommerce.payment;

import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.FinancialStates.BookingFinancialState;
import com.hotel.paymentprovider.domain.FinancialStates.PaymentState;
import com.hotel.propertycommerce.booking.BookingFinancialSummaryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

@RestController
public class PropertyPaymentController {

    private final PropertyPaymentAttemptService attemptService;
    private final ManualTransferConfirmationService manualConfirmationService;
    private final PropertyPaymentCallbackService callbackService;
    private final PropertyPaymentCallbackCredentialsResolver callbackCredentialsResolver;

    public PropertyPaymentController(
            PropertyPaymentAttemptService attemptService,
            ManualTransferConfirmationService manualConfirmationService,
            PropertyPaymentCallbackService callbackService,
            PropertyPaymentCallbackCredentialsResolver callbackCredentialsResolver) {
        this.attemptService = attemptService;
        this.manualConfirmationService = manualConfirmationService;
        this.callbackService = callbackService;
        this.callbackCredentialsResolver = callbackCredentialsResolver;
    }

    @GetMapping("/api/reservations/{reservationId}/financial-summary")
    public FinancialSummaryResponse financialSummary(@PathVariable Long reservationId) {
        return FinancialSummaryResponse.from(attemptService.financialSummary(reservationId));
    }

    @PostMapping("/api/reservations/{reservationId}/payment-attempts")
    public ResponseEntity<PaymentAttemptResponse> createAttempt(
            @PathVariable Long reservationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody CreateAttemptRequest request) {
        PropertyPaymentAttemptService.AttemptResponse result = attemptService.create(
                new PropertyPaymentAttemptService.CreateCommand(
                        reservationId,
                        purpose(request == null ? null : request.purpose()),
                        request == null ? null : request.method(),
                        idempotencyKey,
                        correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentAttemptResponse.from(result));
    }

    @GetMapping("/api/payment-attempts/{attemptId}")
    public PaymentAttemptResponse getAttempt(@PathVariable String attemptId) {
        return PaymentAttemptResponse.from(attemptService.getOwned(attemptId));
    }

    @PostMapping("/api/payment-attempts/{attemptId}/cancel")
    public PaymentAttemptResponse cancelAttempt(
            @PathVariable String attemptId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        return PaymentAttemptResponse.from(attemptService.cancelOwned(
                new PropertyPaymentAttemptService.CancelCommand(attemptId, idempotencyKey, correlationId)));
    }

    @PostMapping("/api/management/payment-attempts/{attemptId}/confirm-manual")
    public ManualConfirmationResponse confirmManual(
            @PathVariable String attemptId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody ManualConfirmationRequest request) {
        ManualTransferConfirmationService.ConfirmationResult result = manualConfirmationService.confirm(
                new ManualTransferConfirmationService.ConfirmCommand(
                        attemptId,
                        request == null ? null : request.reason(),
                        request == null ? null : request.evidenceReference(),
                        idempotencyKey,
                        correlationId));
        return ManualConfirmationResponse.from(result);
    }

    @PostMapping("/api/payment-providers/property/{provider}/callback")
    public ResponseEntity<ProviderCallbackResponse> providerCallback(
            @PathVariable String provider,
            @RequestHeader(value = "X-Payment-Signature", required = false) String signature,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody Map<String, Object> payload) {
        PropertyPaymentCallbackCredentialsResolver.CallbackContext context =
                callbackCredentialsResolver.resolve(provider, payload);
        PropertyPaymentCallbackService.CallbackResult result = callbackService.process(
                new PropertyPaymentCallbackService.CallbackCommand(
                        provider,
                        context.environment(),
                        context.merchantId(),
                        signature,
                        payload,
                        context.credentials(),
                        null,
                        correlationId));
        ProviderCallbackResponse body = ProviderCallbackResponse.from(result);
        return result.accepted()
                ? ResponseEntity.ok(body)
                : ResponseEntity.status(result.errorCode().status()).body(body);
    }

    private PropertyPaymentAttempt.Purpose purpose(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("purpose is required.");
        }
        try {
            return PropertyPaymentAttempt.Purpose.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported payment purpose: " + value + ".");
        }
    }

    public record CreateAttemptRequest(String purpose, String method) {
    }

    public record ManualConfirmationRequest(String reason, String evidenceReference) {
    }

    public record FinancialSummaryResponse(
            Long reservationId,
            BigDecimal grossCharges,
            BigDecimal depositRequired,
            BigDecimal successfulPayments,
            BigDecimal successfulRefunds,
            BigDecimal remainingBalance,
            String currency,
            BookingFinancialState financialState,
            long sourceVersion,
            LocalDateTime calculatedAt) {

        static FinancialSummaryResponse from(BookingFinancialSummaryService.Summary summary) {
            return new FinancialSummaryResponse(
                    summary.reservationId(),
                    summary.grossCharges().amount(),
                    summary.depositRequired().amount(),
                    summary.successfulPayments().amount(),
                    summary.successfulRefunds().amount(),
                    summary.remainingBalance(),
                    "VND",
                    summary.financialState(),
                    summary.sourceVersion(),
                    summary.calculatedAt());
        }
    }

    public record PaymentAttemptResponse(
            String attemptId,
            Long reservationId,
            PropertyPaymentAttempt.Purpose purpose,
            PaymentState status,
            PaymentEnvironment environment,
            BigDecimal expectedAmount,
            String currency,
            LocalDateTime expiresAt,
            String method,
            String provider,
            PropertyPaymentAttemptService.ReceiverSnapshot receiver,
            String uniqueTransferContent,
            String qrData,
            String redirectUrl,
            boolean replayed) {

        static PaymentAttemptResponse from(PropertyPaymentAttemptService.AttemptResponse result) {
            return new PaymentAttemptResponse(
                    result.publicId(),
                    result.reservationId(),
                    result.purpose(),
                    result.status(),
                    result.environment(),
                    result.expectedAmount(),
                    result.currency(),
                    result.expiresAt(),
                    result.method(),
                    result.provider(),
                    result.receiver(),
                    result.uniqueTransferContent(),
                    null,
                    null,
                    result.replayed());
        }
    }

    public record ManualConfirmationResponse(
            String attemptId,
            String transactionId,
            PaymentState status,
            BigDecimal amount,
            LocalDateTime confirmedAt,
            boolean replayed) {

        static ManualConfirmationResponse from(ManualTransferConfirmationService.ConfirmationResult result) {
            return new ManualConfirmationResponse(
                    result.attemptPublicId(),
                    result.transactionPublicId(),
                    result.status(),
                    result.amount(),
                    result.confirmedAt(),
                    result.replayed());
        }
    }

    public record ProviderCallbackResponse(
            boolean accepted,
            boolean replayed,
            String errorCode,
            String attemptId,
            PaymentState status,
            String transactionId) {

        static ProviderCallbackResponse from(PropertyPaymentCallbackService.CallbackResult result) {
            return new ProviderCallbackResponse(
                    result.accepted(),
                    result.replayed(),
                    result.errorCode() == null ? null : result.errorCode().name(),
                    result.attemptPublicId(),
                    result.status(),
                    result.transactionPublicId());
        }
    }
}
