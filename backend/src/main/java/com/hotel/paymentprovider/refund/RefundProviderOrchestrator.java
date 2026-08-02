package com.hotel.paymentprovider.refund;

import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.FinancialStates.RefundState;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.paymentprovider.spi.PaymentProviderAdapter;
import com.hotel.platformbilling.refund.PlatformRefundAttempt;
import com.hotel.platformbilling.refund.PlatformRefundAttemptRepository;
import com.hotel.platformbilling.refund.PlatformRefundRequest;
import com.hotel.platformbilling.refund.PlatformRefundRequestRepository;
import com.hotel.platformbilling.refund.PlatformRefundService;
import com.hotel.propertycommerce.refund.PropertyRefundAttempt;
import com.hotel.propertycommerce.refund.PropertyRefundAttemptRepository;
import com.hotel.propertycommerce.refund.PropertyRefundRequest;
import com.hotel.propertycommerce.refund.PropertyRefundRequestRepository;
import com.hotel.propertycommerce.refund.PropertyRefundService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
public class RefundProviderOrchestrator {

    private final RefundProviderGateway gateway;
    private final PropertyRefundRequestRepository propertyRequestRepository;
    private final PropertyRefundAttemptRepository propertyAttemptRepository;
    private final PropertyRefundService propertyRefundService;
    private final PlatformRefundRequestRepository platformRequestRepository;
    private final PlatformRefundAttemptRepository platformAttemptRepository;
    private final PlatformRefundService platformRefundService;
    private final Clock clock;

    @Autowired
    public RefundProviderOrchestrator(
            RefundProviderGateway gateway,
            PropertyRefundRequestRepository propertyRequestRepository,
            PropertyRefundAttemptRepository propertyAttemptRepository,
            PropertyRefundService propertyRefundService,
            PlatformRefundRequestRepository platformRequestRepository,
            PlatformRefundAttemptRepository platformAttemptRepository,
            PlatformRefundService platformRefundService) {
        this(gateway, propertyRequestRepository, propertyAttemptRepository, propertyRefundService,
                platformRequestRepository, platformAttemptRepository, platformRefundService, Clock.systemUTC());
    }

    RefundProviderOrchestrator(
            RefundProviderGateway gateway,
            PropertyRefundRequestRepository propertyRequestRepository,
            PropertyRefundAttemptRepository propertyAttemptRepository,
            PropertyRefundService propertyRefundService,
            PlatformRefundRequestRepository platformRequestRepository,
            PlatformRefundAttemptRepository platformAttemptRepository,
            PlatformRefundService platformRefundService,
            Clock clock) {
        this.gateway = gateway;
        this.propertyRequestRepository = propertyRequestRepository;
        this.propertyAttemptRepository = propertyAttemptRepository;
        this.propertyRefundService = propertyRefundService;
        this.platformRequestRepository = platformRequestRepository;
        this.platformAttemptRepository = platformAttemptRepository;
        this.platformRefundService = platformRefundService;
        this.clock = clock;
    }

    @Transactional
    public AttemptResult createPropertyAttempt(AttemptCommand command) {
        PropertyRefundRequest refund = propertyRequestRepository.findByPublicIdForUpdate(command.refundPublicId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        requireProviderReady(refund.getStatus());
        List<PropertyRefundAttempt> existing = propertyAttemptRepository
                .findByRefundRequestIdOrderByAttemptNumberAsc(refund.getId());
        PropertyRefundAttempt active = existing.stream()
                .filter(attempt -> attempt.getStatus() == RefundState.PENDING_PROVIDER)
                .findFirst().orElse(null);
        if (active != null) return result(active, true);
        int attemptNumber = existing.stream().map(PropertyRefundAttempt::getAttemptNumber).max(Integer::compareTo).orElse(0) + 1;
        RefundProviderGateway.PreparedAttempt prepared = gateway.prepare(new RefundProviderGateway.PrepareCommand(
                refund.getPublicId(), attemptNumber, command.provider(), command.environment(),
                refund.getRequestedAmount(), refund.getCurrency(), command.merchantId(), command.credentials(), command.endpoint()));
        PropertyRefundAttempt attempt = PropertyRefundAttempt.create(
                refund, attemptNumber, prepared.provider(), prepared.environment(), now());
        attempt.markPending(prepared.providerReference());
        return result(propertyAttemptRepository.saveAndFlush(attempt), false);
    }

    @Transactional
    public AttemptResult createPlatformAttempt(AttemptCommand command) {
        PlatformRefundRequest refund = platformRequestRepository.findByPublicIdForUpdate(command.refundPublicId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        requireProviderReady(refund.getStatus());
        List<PlatformRefundAttempt> existing = platformAttemptRepository
                .findByRefundRequestIdOrderByAttemptNumberAsc(refund.getId());
        PlatformRefundAttempt active = existing.stream()
                .filter(attempt -> attempt.getStatus() == RefundState.PENDING_PROVIDER)
                .findFirst().orElse(null);
        if (active != null) return result(active, true);
        int attemptNumber = existing.stream().map(PlatformRefundAttempt::getAttemptNumber).max(Integer::compareTo).orElse(0) + 1;
        RefundProviderGateway.PreparedAttempt prepared = gateway.prepare(new RefundProviderGateway.PrepareCommand(
                refund.getPublicId(), attemptNumber, command.provider(), command.environment(),
                refund.getRequestedAmount(), refund.getCurrency(), command.merchantId(), command.credentials(), command.endpoint()));
        PlatformRefundAttempt attempt = PlatformRefundAttempt.create(
                refund, attemptNumber, prepared.provider(), prepared.environment(), now());
        attempt.markPending(prepared.providerReference());
        return result(platformAttemptRepository.saveAndFlush(attempt), false);
    }

    @Transactional
    public CallbackResult processPropertyCallback(CallbackCommand command) {
        Instant receivedAt = command.receivedAt() == null ? clock.instant() : command.receivedAt();
        PaymentProviderAdapter.NormalizedCallback callback = gateway.normalizeCallback(
                command.provider(), command.signature(), command.payload(), receivedAt);
        PropertyRefundAttempt attempt = propertyAttemptRepository.findByProviderAndReferenceForUpdate(
                        command.provider(), requireReference(callback))
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        RefundProviderGateway.VerifiedCallback verified = verify(command, attempt.getRefundRequest().getRequestedAmount(),
                attempt.getProviderReference(), receivedAt, callback);
        if (!verified.accepted()) return CallbackResult.rejected(verified.errorCode(), verified.retryable());
        return applyPropertyCallback(attempt, verified.callback(), command.correlationId());
    }

    @Transactional
    public CallbackResult processPlatformCallback(CallbackCommand command) {
        Instant receivedAt = command.receivedAt() == null ? clock.instant() : command.receivedAt();
        PaymentProviderAdapter.NormalizedCallback callback = gateway.normalizeCallback(
                command.provider(), command.signature(), command.payload(), receivedAt);
        PlatformRefundAttempt attempt = platformAttemptRepository.findByProviderAndReferenceForUpdate(
                        command.provider(), requireReference(callback))
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        RefundProviderGateway.VerifiedCallback verified = verify(command, attempt.getRefundRequest().getRequestedAmount(),
                attempt.getProviderReference(), receivedAt, callback);
        if (!verified.accepted()) return CallbackResult.rejected(verified.errorCode(), verified.retryable());
        return applyPlatformCallback(attempt, verified.callback(), command.correlationId());
    }

    private RefundProviderGateway.VerifiedCallback verify(
            CallbackCommand command,
            BigDecimal amount,
            String reference,
            Instant receivedAt,
            PaymentProviderAdapter.NormalizedCallback callback) {
        return gateway.verify(new RefundProviderGateway.VerifyCommand(
                command.provider(), command.expectedMerchantId(), amount, "VND", reference,
                command.signature(), command.payload(), command.credentials(), null, receivedAt, callback));
    }

    private CallbackResult applyPropertyCallback(
            PropertyRefundAttempt attempt,
            PaymentProviderAdapter.NormalizedCallback callback,
            String correlationId) {
        boolean successful = Boolean.TRUE.equals(callback.metadata().get("successful"));
        if (attempt.getStatus() == RefundState.SUCCEEDED) {
            if (!attempt.getProviderEventId().equals(callback.eventId())) {
                return CallbackResult.rejected(FinancialErrorCode.INVALID_STATE_TRANSITION, false);
            }
            PropertyRefundService.RefundResult result = propertyRefundService.completeSucceeded(
                    attempt.getRefundRequest().getPublicId(), callback.transactionId(), correlationId);
            return CallbackResult.accepted(result.publicId(), result.status(), true);
        }
        if (!successful) {
            attempt.markFailed(failureCode(callback), false, eventTime(callback));
            propertyAttemptRepository.saveAndFlush(attempt);
            PropertyRefundService.RefundResult result = propertyRefundService.fail(
                    attempt.getRefundRequest().getPublicId(), correlationId);
            return CallbackResult.accepted(result.publicId(), result.status(), false);
        }
        attempt.markSucceeded(requireEvent(callback), eventTime(callback));
        propertyAttemptRepository.saveAndFlush(attempt);
        PropertyRefundService.RefundResult result = propertyRefundService.completeSucceeded(
                attempt.getRefundRequest().getPublicId(), callback.transactionId(), correlationId);
        return CallbackResult.accepted(result.publicId(), result.status(), result.replayed());
    }

    private CallbackResult applyPlatformCallback(
            PlatformRefundAttempt attempt,
            PaymentProviderAdapter.NormalizedCallback callback,
            String correlationId) {
        boolean successful = Boolean.TRUE.equals(callback.metadata().get("successful"));
        if (attempt.getStatus() == RefundState.SUCCEEDED) {
            if (!attempt.getProviderEventId().equals(callback.eventId())) {
                return CallbackResult.rejected(FinancialErrorCode.INVALID_STATE_TRANSITION, false);
            }
            PlatformRefundService.RefundResult result = platformRefundService.completeSucceeded(
                    attempt.getRefundRequest().getPublicId(), callback.transactionId(), correlationId);
            return CallbackResult.accepted(result.publicId(), result.status(), true);
        }
        if (!successful) {
            attempt.markFailed(failureCode(callback), false, eventTime(callback));
            platformAttemptRepository.saveAndFlush(attempt);
            PlatformRefundService.RefundResult result = platformRefundService.fail(
                    attempt.getRefundRequest().getPublicId(), correlationId);
            return CallbackResult.accepted(result.publicId(), result.status(), false);
        }
        attempt.markSucceeded(requireEvent(callback), eventTime(callback));
        platformAttemptRepository.saveAndFlush(attempt);
        PlatformRefundService.RefundResult result = platformRefundService.completeSucceeded(
                attempt.getRefundRequest().getPublicId(), callback.transactionId(), correlationId);
        return CallbackResult.accepted(result.publicId(), result.status(), result.replayed());
    }

    private void requireProviderReady(RefundState status) {
        if (status != RefundState.PENDING_PROVIDER) {
            throw new FinancialException(FinancialErrorCode.INVALID_STATE_TRANSITION);
        }
    }

    private String requireEvent(PaymentProviderAdapter.NormalizedCallback callback) {
        if (callback.eventId() == null || callback.eventId().isBlank()) {
            throw new FinancialException(FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH);
        }
        return callback.eventId().trim();
    }

    private String requireReference(PaymentProviderAdapter.NormalizedCallback callback) {
        if (callback.reference() == null || callback.reference().isBlank()) {
            throw new FinancialException(FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH);
        }
        return callback.reference().trim();
    }

    private String failureCode(PaymentProviderAdapter.NormalizedCallback callback) {
        Object value = callback.metadata().get("status");
        return value == null ? "PROVIDER_FAILED" : String.valueOf(value);
    }

    private LocalDateTime eventTime(PaymentProviderAdapter.NormalizedCallback callback) {
        Instant event = callback.occurredAt() == null ? clock.instant() : callback.occurredAt();
        return LocalDateTime.ofInstant(event, ZoneOffset.UTC);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private AttemptResult result(PropertyRefundAttempt attempt, boolean replayed) {
        return new AttemptResult(attempt.getRefundRequest().getPublicId(), attempt.getAttemptNumber(),
                attempt.getProvider(), attempt.getEnvironment(), attempt.getProviderReference(), attempt.getStatus(), replayed);
    }

    private AttemptResult result(PlatformRefundAttempt attempt, boolean replayed) {
        return new AttemptResult(attempt.getRefundRequest().getPublicId(), attempt.getAttemptNumber(),
                attempt.getProvider(), attempt.getEnvironment(), attempt.getProviderReference(), attempt.getStatus(), replayed);
    }

    public record AttemptCommand(
            String refundPublicId,
            String provider,
            PaymentEnvironment environment,
            String merchantId,
            Map<String, ?> credentials,
            URI endpoint) {
    }

    public record AttemptResult(
            String refundPublicId,
            int attemptNumber,
            String provider,
            PaymentEnvironment environment,
            String providerReference,
            RefundState status,
            boolean replayed) {
    }

    public record CallbackCommand(
            String provider,
            String expectedMerchantId,
            String signature,
            Map<String, ?> payload,
            Map<String, ?> credentials,
            Instant receivedAt,
            String correlationId) {
    }

    public record CallbackResult(
            boolean accepted,
            boolean replayed,
            FinancialErrorCode errorCode,
            boolean retryable,
            String refundPublicId,
            RefundState status) {

        static CallbackResult accepted(String refundPublicId, RefundState status, boolean replayed) {
            return new CallbackResult(true, replayed, null, false, refundPublicId, status);
        }

        static CallbackResult rejected(FinancialErrorCode errorCode, boolean retryable) {
            return new CallbackResult(false, false, errorCode, retryable, null, null);
        }
    }
}
