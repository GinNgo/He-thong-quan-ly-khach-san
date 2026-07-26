package com.hotel.controllers;

import com.hotel.services.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Profile("!prod")
public class MockPaymentController {

    private final PaymentService paymentService;

    // Callback for Simulator (MoMo, Stripe)
    @GetMapping("/callback")
    public ResponseEntity<Map<String, String>> mockCallback(
            @RequestParam Long reservationId,
            @RequestParam String status,
            @RequestParam String method,
            @RequestParam String transactionId) {

        if ("SUCCESS".equalsIgnoreCase(status)) {
            paymentService.handleSuccessfulPayment(reservationId, method, transactionId);
        }

        return ResponseEntity.ok(Map.of("message", "Payment callback processed successfully"));
    }
}