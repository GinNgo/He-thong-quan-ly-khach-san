package com.hotel.platformbilling.refund;

import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.refund.RefundProviderCredentialsResolver;
import com.hotel.paymentprovider.refund.RefundProviderOrchestrator;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
public class PlatformRefundController {

    private final PlatformRefundService refundService;
    private final RefundProviderOrchestrator providerOrchestrator;
    private final RefundProviderCredentialsResolver credentialsResolver;

    public PlatformRefundController(
            PlatformRefundService refundService,
            RefundProviderOrchestrator providerOrchestrator,
            RefundProviderCredentialsResolver credentialsResolver) {
        this.refundService = refundService;
        this.providerOrchestrator = providerOrchestrator;
        this.credentialsResolver = credentialsResolver;
    }

    @PostMapping("/api/platform-payments/{transactionId}/refunds")
    @Permission(function = FunctionCode.PLATFORM_REFUND, action = ActionCode.CREATE)
    public ResponseEntity<PlatformRefundService.RefundResult> request(
            @PathVariable String transactionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody RefundRequest request) {
        return ResponseEntity.ok(refundService.request(new PlatformRefundService.RequestCommand(
                transactionId, request.amount(), request.reason(), idempotencyKey, correlationId)));
    }

    @PostMapping("/api/platform-refunds/{refundId}/approve")
    @Permission(function = FunctionCode.PLATFORM_REFUND, action = ActionCode.APPROVE)
    public ResponseEntity<PlatformRefundService.RefundResult> approve(
            @PathVariable String refundId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        return ResponseEntity.ok(refundService.approve(refundId, correlationId));
    }

    @PostMapping("/api/platform-refunds/{refundId}/attempts")
    @Permission(function = FunctionCode.PLATFORM_REFUND, action = ActionCode.APPROVE)
    public ResponseEntity<RefundProviderOrchestrator.AttemptResult> createAttempt(
            @PathVariable String refundId,
            @RequestBody ProviderAttemptRequest request) {
        RefundProviderCredentialsResolver.Context context = credentialsResolver.platform(request.provider());
        return ResponseEntity.ok(providerOrchestrator.createPlatformAttempt(
                new RefundProviderOrchestrator.AttemptCommand(
                        refundId, context.provider(), context.environment(), context.merchantId(),
                        context.credentials(), context.endpoint())));
    }

    @GetMapping("/api/platform-refunds/{refundId}")
    @Permission(function = FunctionCode.PLATFORM_REFUND, action = ActionCode.VIEW)
    public ResponseEntity<PlatformRefundService.RefundResult> status(@PathVariable String refundId) {
        return ResponseEntity.ok(refundService.get(refundId));
    }

    @PostMapping("/api/payment-providers/platform/{provider}/refund-callback")
    public ResponseEntity<RefundProviderOrchestrator.CallbackResult> callback(
            @PathVariable String provider,
            @RequestHeader(value = "X-Payment-Signature", required = false) String signature,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody Map<String, Object> payload) {
        RefundProviderCredentialsResolver.Context context = credentialsResolver.platform(provider);
        RefundProviderOrchestrator.CallbackResult result = providerOrchestrator.processPlatformCallback(
                new RefundProviderOrchestrator.CallbackCommand(
                        context.provider(), context.merchantId(), signature, payload,
                        context.credentials(), null, correlationId));
        FinancialErrorCode code = result.errorCode() == null ? FinancialErrorCode.PROVIDER_UNAVAILABLE : result.errorCode();
        return result.accepted() ? ResponseEntity.ok(result) : ResponseEntity.status(code.status()).body(result);
    }

    public record RefundRequest(BigDecimal amount, String reason) {
    }

    public record ProviderAttemptRequest(String provider) {
    }
}
