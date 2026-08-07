package com.hotel.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.domain.payment.PaymentProvider;
import com.hotel.entities.PaymentSession;
import com.hotel.entities.RefundProviderAttempt;
import com.hotel.entities.RefundRequest;
import com.hotel.repositories.PaymentSessionRepository;
import com.hotel.repositories.RefundProviderAttemptRepository;
import com.hotel.services.payment.MomoPaymentGateway;
import com.hotel.services.payment.ProviderCallbackData;
import com.hotel.services.payment.ProviderOperationStatus;
import com.hotel.services.payment.ProviderRefundCommand;
import com.hotel.services.payment.ProviderRefundQuery;
import com.hotel.services.payment.ProviderRefundResult;
import com.hotel.services.payment.ProviderTransactionQueryResult;
import com.hotel.services.payment.ZaloPayPaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recovers missed provider callbacks and advances asynchronous refund attempts.
 * Provider calls happen outside a database transaction; lifecycle mutations are
 * delegated to their own locked, idempotent services.
 */
@Service
public class PaymentProviderRecoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentProviderRecoveryService.class);
    private static final List<String> PROVIDERS = List.of("MOMO", "ZALOPAY");
    private static final List<String> PAYMENT_STATUSES = List.of("CREATED", "PENDING");
    private static final List<String> REFUND_STATUSES = List.of("REQUESTED", "PENDING_PROVIDER");

    private final PaymentSessionRepository sessionRepository;
    private final RefundProviderAttemptRepository attemptRepository;
    private final PaymentSessionService paymentSessionService;
    private final RefundService refundService;
    private final MomoPaymentGateway momoGateway;
    private final ZaloPayPaymentGateway zaloPayGateway;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final long minimumAgeMillis;
    private final Clock clock;

    public PaymentProviderRecoveryService(
            PaymentSessionRepository sessionRepository,
            RefundProviderAttemptRepository attemptRepository,
            PaymentSessionService paymentSessionService,
            RefundService refundService,
            MomoPaymentGateway momoGateway,
            ZaloPayPaymentGateway zaloPayGateway,
            ObjectMapper objectMapper,
            @Value("${payment.provider-recovery.enabled:true}") boolean enabled,
            @Value("${payment.provider-recovery.minimum-age-ms:60000}") long minimumAgeMillis,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.attemptRepository = attemptRepository;
        this.paymentSessionService = paymentSessionService;
        this.refundService = refundService;
        this.momoGateway = momoGateway;
        this.zaloPayGateway = zaloPayGateway;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.minimumAgeMillis = Math.max(0, minimumAgeMillis);
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${payment.provider-recovery.scan-ms:60000}")
    public void recoverProviderState() {
        if (!enabled) {
            return;
        }
        recoverPaymentSessions();
        recoverRefundAttempts();
    }

    private void recoverPaymentSessions() {
        LocalDateTime before = LocalDateTime.now(clock).minus(Duration.ofMillis(minimumAgeMillis));
        for (PaymentSession session : sessionRepository
                .findTop50ByStatusInAndProviderInAndCreatedAtBeforeOrderByCreatedAtAsc(
                        PAYMENT_STATUSES,
                        PROVIDERS,
                        before)) {
            try {
                ProviderTransactionQueryResult result = queryPayment(session);
                if (result == null || result.status() == ProviderOperationStatus.PENDING
                        || result.status() == ProviderOperationStatus.NOT_FOUND) {
                    continue;
                }
                BigDecimal amount = result.status() == ProviderOperationStatus.SUCCEEDED
                        && result.amount() != null
                        ? result.amount()
                        : session.getExpectedAmount();
                String transactionId = result.providerTransactionId() == null
                        ? "QUERY-" + session.getProviderReference()
                        : result.providerTransactionId();
                paymentSessionService.processProviderCallback(new ProviderCallbackData(
                        result.provider(),
                        session.getProviderReference(),
                        transactionId,
                        amount,
                        result.status() == ProviderOperationStatus.SUCCEEDED,
                        "QUERY_" + result.responseCode()));
            } catch (RuntimeException exception) {
                LOGGER.warn("Payment provider recovery deferred for session {}: {}",
                        session.getPublicId(), exception.getMessage());
            }
        }
    }

    private ProviderTransactionQueryResult queryPayment(PaymentSession session) {
        PaymentProvider provider = PaymentProvider.fromRequest(session.getProvider());
        if (provider == PaymentProvider.MOMO) {
            if (!momoGateway.isConfigured()) {
                return null;
            }
            return momoGateway.queryTransaction(session.getProviderReference(), session.getPublicId() + "-query");
        }
        if (provider == PaymentProvider.ZALOPAY) {
            return zaloPayGateway.isConfigured()
                    ? zaloPayGateway.queryTransaction(session.getProviderReference())
                    : null;
        }
        return null;
    }

    private void recoverRefundAttempts() {
        for (RefundProviderAttempt attempt : attemptRepository.findTop50ByStatusInOrderByRequestedAtAsc(REFUND_STATUSES)) {
            try {
                processRefundAttempt(attempt);
            } catch (RuntimeException exception) {
                LOGGER.warn("Refund provider recovery deferred for attempt {}: {}",
                        attempt.getId(), exception.getMessage());
                markNetworkRetry(attempt);
            }
        }
    }

    private void processRefundAttempt(RefundProviderAttempt attempt) {
        RefundRequest request = attempt.getRefundRequest();
        PaymentProvider provider = PaymentProvider.fromRequest(request.getProvider());
        if (provider != PaymentProvider.MOMO && provider != PaymentProvider.ZALOPAY) {
            return;
        }
        if (!isConfigured(provider)) {
            return;
        }

        String providerReference = attempt.getProviderReference();
        if (providerReference == null || providerReference.isBlank()) {
            providerReference = createRefundReference(provider, request);
        }
        if (request.getOriginalPayment() == null
                || request.getOriginalPayment().getTransactionId() == null
                || request.getOriginalPayment().getTransactionId().isBlank()) {
            refundService.markProviderFailed(
                    request.getId(),
                    attempt.getAttemptNumber(),
                    providerReference,
                    "ORIGINAL_PROVIDER_TRANSACTION_MISSING",
                    "LOCAL_BINDING");
            return;
        }

        if ("REQUESTED".equalsIgnoreCase(attempt.getStatus())) {
            refundService.markProviderPending(
                    request.getId(),
                    attempt.getAttemptNumber(),
                    providerReference,
                    null,
                    null);
            ProviderRefundResult result = requestRefund(provider, request, attempt, providerReference);
            applyRefundResult(request, attempt, result, providerReference);
            return;
        }

        ProviderRefundResult result = queryRefund(provider, request, attempt, providerReference);
        applyRefundResult(request, attempt, result, providerReference);
    }

    private ProviderRefundResult requestRefund(
            PaymentProvider provider,
            RefundRequest request,
            RefundProviderAttempt attempt,
            String providerReference) {
        ProviderRefundCommand command = new ProviderRefundCommand(
                providerReference,
                attempt.getIdempotencyKey(),
                request.getOriginalPayment().getTransactionId(),
                request.getRequestedAmount(),
                request.getReason());
        if (provider == PaymentProvider.MOMO) {
            return momoGateway.requestRefund(command);
        }
        return zaloPayGateway.requestRefund(command);
    }

    private ProviderRefundResult queryRefund(
            PaymentProvider provider,
            RefundRequest request,
            RefundProviderAttempt attempt,
            String providerReference) {
        ProviderRefundQuery query = new ProviderRefundQuery(
                providerReference,
                "Q-" + request.getPublicId(),
                request.getRequestedAmount());
        if (provider == PaymentProvider.MOMO) {
            return momoGateway.queryRefund(query);
        }
        return zaloPayGateway.queryRefund(query);
    }

    private void applyRefundResult(
            RefundRequest request,
            RefundProviderAttempt attempt,
            ProviderRefundResult result,
            String providerReference) {
        if (result == null || result.status() == ProviderOperationStatus.PENDING) {
            refundService.markProviderPending(
                    request.getId(),
                    attempt.getAttemptNumber(),
                    providerReference,
                    result == null ? "UNKNOWN" : result.responseCode(),
                    result == null
                            ? "Provider did not return a terminal outcome."
                            : providerDetails(result));
            return;
        }
        if (result.status() == ProviderOperationStatus.SUCCEEDED) {
            refundService.markProviderSucceeded(
                    request.getId(),
                    attempt.getAttemptNumber(),
                    providerReference,
                    result.responseCode(),
                    providerDetails(result));
            return;
        }
        refundService.markProviderFailed(
                request.getId(),
                attempt.getAttemptNumber(),
                providerReference,
                result.status() == ProviderOperationStatus.NOT_FOUND
                        ? "PROVIDER_REFERENCE_NOT_FOUND"
                        : "PROVIDER_REJECTED",
                result.responseCode(),
                providerDetails(result));
    }

    private void markNetworkRetry(RefundProviderAttempt attempt) {
        RefundRequest request = attempt.getRefundRequest();
        if (request == null || attempt.getAttemptNumber() == null) {
            return;
        }
        String providerReference = attempt.getProviderReference();
        if (providerReference == null || providerReference.isBlank()) {
            PaymentProvider provider = PaymentProvider.fromRequest(request.getProvider());
            providerReference = createRefundReference(provider, request);
        }
        refundService.markProviderPending(
                request.getId(),
                attempt.getAttemptNumber(),
                providerReference,
                "NETWORK_RETRY",
                "Provider request outcome is unknown; query will retry.");
    }

    private boolean isConfigured(PaymentProvider provider) {
        return provider == PaymentProvider.MOMO ? momoGateway.isConfigured() : zaloPayGateway.isConfigured();
    }

    private String createRefundReference(PaymentProvider provider, RefundRequest request) {
        if (provider == PaymentProvider.MOMO) {
            return momoGateway.refundReference(request.getPublicId());
        }
        return zaloPayGateway.refundReference(request.getPublicId(), request.getRequestedAt());
    }

    private String providerDetails(ProviderRefundResult result) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", result.status().name());
        if (result.providerTransactionId() != null && !result.providerTransactionId().isBlank()) {
            details.put("providerTransactionId", result.providerTransactionId());
        }
        if (result.message() != null && !result.message().isBlank()) {
            details.put("message", result.message());
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }
}
