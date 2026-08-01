package com.hotel.platformbilling.payment;

import com.hotel.paymentprovider.error.FinancialErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PlatformPaymentCallbackController {

    private final PlatformPaymentCallbackService callbackService;

    public PlatformPaymentCallbackController(PlatformPaymentCallbackService callbackService) {
        this.callbackService = callbackService;
    }

    @PostMapping("/api/payment-providers/platform/{provider}/callback")
    public ResponseEntity<ProviderCallbackResponse> providerCallback(
            @PathVariable String provider,
            @RequestHeader(value = "X-Payment-Signature", required = false) String signature,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody Map<String, Object> payload) {
        PlatformPaymentCallbackService.CallbackResult result = callbackService.process(
                new PlatformPaymentCallbackService.CallbackCommand(
                        provider, signature, payload, null, correlationId));
        ProviderCallbackResponse body = ProviderCallbackResponse.from(result);
        if (result.accepted()) return ResponseEntity.ok(body);
        FinancialErrorCode errorCode = result.errorCode() == null
                ? FinancialErrorCode.PROVIDER_UNAVAILABLE : result.errorCode();
        return ResponseEntity.status(errorCode.status()).body(body);
    }

    public record ProviderCallbackResponse(
            boolean accepted,
            boolean replayed,
            String errorCode,
            String attemptId,
            String attemptStatus,
            String orderStatus,
            String transactionPublicId,
            String contractPublicId) {

        static ProviderCallbackResponse from(PlatformPaymentCallbackService.CallbackResult result) {
            return new ProviderCallbackResponse(
                    result.accepted(),
                    result.replayed(),
                    result.errorCode() == null ? null : result.errorCode().name(),
                    result.attemptPublicId(),
                    result.attemptStatus() == null ? null : result.attemptStatus().name(),
                    result.orderStatus() == null ? null : result.orderStatus().name(),
                    result.transactionPublicId(),
                    result.contractPublicId());
        }
    }
}
