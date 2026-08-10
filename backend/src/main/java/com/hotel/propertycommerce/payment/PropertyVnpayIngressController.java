package com.hotel.propertycommerce.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** VNPay's official GET ingress; all authority remains in the canonical callback service. */
@RestController
public class PropertyVnpayIngressController {
    private final PropertyPaymentCallbackCredentialsResolver credentials;
    private final PropertyPaymentCallbackService callbacks;

    public PropertyVnpayIngressController(PropertyPaymentCallbackCredentialsResolver credentials,
                                           PropertyPaymentCallbackService callbacks) {
        this.credentials = credentials; this.callbacks = callbacks;
    }

    @GetMapping({"/api/payment-providers/vnpay/ipn", "/api/payment-providers/vnpay/return"})
    public ResponseEntity<Map<String, Object>> receive(@RequestParam Map<String, String> params) {
        Map<String, Object> payload = new LinkedHashMap<>(params);
        var context = credentials.resolve("VNPAY", payload);
        var result = callbacks.process(new PropertyPaymentCallbackService.CallbackCommand(
                "VNPAY", context.environment(), context.merchantId(), null, payload,
                context.credentials(), null, null));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("RspCode", result.accepted() ? "00" : "99");
        body.put("Message", result.accepted() ? "Confirm Success" : result.errorCode().defaultMessage());
        body.put("attemptId", result.attemptPublicId());
        if (result.accepted()) return ResponseEntity.ok(body);
        return ResponseEntity.status(result.errorCode().status()).body(body);
    }
}
