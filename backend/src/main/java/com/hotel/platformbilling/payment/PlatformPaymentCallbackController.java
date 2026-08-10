package com.hotel.platformbilling.payment;

import com.hotel.paymentprovider.error.FinancialErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.HashMap;
import java.util.Enumeration;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;

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

    @GetMapping("/api/payment-providers/platform/{provider}/callback")
    public ResponseEntity<?> providerBrowserReturn(
            @PathVariable String provider,
            @RequestParam(value = "redirect", required = false) String redirect,
            @RequestParam(value = "orderId", required = false) String orderId,
            HttpServletRequest request) {
        Map<String, Object> payload = new HashMap<>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (name.startsWith("vnp_")) payload.put(name, request.getParameter(name));
        }
        PlatformPaymentCallbackService.CallbackResult result = callbackService.process(
                new PlatformPaymentCallbackService.CallbackCommand(
                provider, null, payload, null, request.getHeader("X-Correlation-ID")));
        if ("1".equals(redirect)) {
            String status = result.accepted() ? "processed" : "failed";
            String location = "/management/billing?paymentOrder=" + safe(orderId) + "&paymentStatus=" + status;
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
        }
        return ResponseEntity.ok(Map.of(
                "RspCode", result.accepted() ? "00" : "99",
                "Message", result.accepted() ? "Confirm Success" : "Invalid request"));
    }

    private String safe(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9-]", "");
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
