package com.hotel.controllers;

import com.hotel.domain.payment.PaymentCompletionResult;
import com.hotel.dtos.DemoPaymentConfirmationRequest;
import com.hotel.services.PaymentSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Profile("!prod")
public class MockPaymentController {

    private final PaymentSessionService paymentSessionService;

    @PostMapping("/simulator/confirm")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<Map<String, String>> confirmPayment(
            @Valid @RequestBody DemoPaymentConfirmationRequest request) {
        PaymentCompletionResult result = paymentSessionService.confirmDemoPayment(request.getToken());
        return ResponseEntity.ok(Map.of(
                "status", result.name(),
                "message", "Payment confirmation processed successfully"));
    }
}
