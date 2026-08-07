package com.hotel.propertycommerce.refund;

import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
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
public class PropertyRefundController {

    private final PropertyRefundService refundService;
    private final RefundProviderOrchestrator providerOrchestrator;
    private final RefundProviderCredentialsResolver credentialsResolver;

    public PropertyRefundController(
            PropertyRefundService refundService,
            RefundProviderOrchestrator providerOrchestrator,
            RefundProviderCredentialsResolver credentialsResolver) {
        this.refundService = refundService;
        this.providerOrchestrator = providerOrchestrator;
        this.credentialsResolver = credentialsResolver;
    }

    @PostMapping("/api/property-payments/{transactionId}/refunds")
    public ResponseEntity<PropertyRefundService.RefundResult> request(
            @PathVariable String transactionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody RefundRequest request) {
        return ResponseEntity.ok(refundService.request(new PropertyRefundService.RequestCommand(
                transactionId, request.amount(), request.reason(), idempotencyKey, correlationId)));
    }

    @PostMapping("/api/property-refunds/{refundId}/approve")
    @Permission(function = FunctionCode.PROPERTY_REFUND, action = ActionCode.APPROVE)
    public ResponseEntity<PropertyRefundService.RefundResult> approve(
            @PathVariable String refundId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        return ResponseEntity.ok(refundService.approve(refundId, correlationId));
    }

    @GetMapping("/api/property-refunds")
    @Permission(function = FunctionCode.PROPERTY_REFUND, action = ActionCode.VIEW)
    public ResponseEntity<java.util.List<PropertyRefundService.RefundResult>> list(
            @org.springframework.web.bind.annotation.RequestParam Long propertyId) {
        return ResponseEntity.ok(refundService.listForProperty(propertyId));
    }

    @PostMapping("/api/property-refunds/{refundId}/attempts")
    @Permission(function = FunctionCode.PROPERTY_REFUND, action = ActionCode.APPROVE)
    public ResponseEntity<RefundProviderOrchestrator.AttemptResult> createAttempt(
            @PathVariable String refundId,
            @RequestBody ProviderAttemptRequest request) {
        RefundProviderCredentialsResolver.Context context = credentialsResolver.property(
                request.provider(), request.environment());
        return ResponseEntity.ok(providerOrchestrator.createPropertyAttempt(
                new RefundProviderOrchestrator.AttemptCommand(
                        refundId, context.provider(), context.environment(), context.merchantId(),
                        context.credentials(), context.endpoint())));
    }

    @GetMapping("/api/property-refunds/{refundId}")
    public ResponseEntity<PropertyRefundService.RefundResult> status(@PathVariable String refundId) {
        return ResponseEntity.ok(refundService.get(refundId));
    }

    @PostMapping("/api/payment-providers/property/{provider}/refund-callback")
    public ResponseEntity<RefundProviderOrchestrator.CallbackResult> callback(
            @PathVariable String provider,
            @RequestHeader(value = "X-Payment-Signature", required = false) String signature,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody Map<String, Object> payload) {
        RefundProviderCredentialsResolver.Context context = credentialsResolver.propertyCallback(provider);
        RefundProviderOrchestrator.CallbackResult result = providerOrchestrator.processPropertyCallback(
                new RefundProviderOrchestrator.CallbackCommand(
                        context.provider(), context.merchantId(), signature, payload,
                        context.credentials(), null, correlationId));
        FinancialErrorCode code = result.errorCode() == null ? FinancialErrorCode.PROVIDER_UNAVAILABLE : result.errorCode();
        return result.accepted() ? ResponseEntity.ok(result) : ResponseEntity.status(code.status()).body(result);
    }

    public record RefundRequest(BigDecimal amount, String reason) {
    }

    public record ProviderAttemptRequest(String provider, PaymentEnvironment environment) {
    }
}
