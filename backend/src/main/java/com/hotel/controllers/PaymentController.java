package com.hotel.controllers;

import com.hotel.config.VnpayConfig;
import com.hotel.dtos.CreatePaymentSessionRequest;
import com.hotel.dtos.PaymentDTO;
import com.hotel.dtos.PaymentSessionResponse;
import com.hotel.dtos.PaymentSessionStatusResponse;
import com.hotel.dtos.MomoCallbackRequest;
import com.hotel.dtos.ZaloPayCallbackRequest;
import com.hotel.services.PaymentSessionService;
import com.hotel.services.payment.MomoCallbackVerification;
import com.hotel.services.payment.MomoPaymentGateway;
import com.hotel.services.payment.ProviderCallbackOutcome;
import com.hotel.services.payment.VnpayCallbackVerification;
import com.hotel.services.payment.VnpayIpnResponse;
import com.hotel.services.payment.VnpayPaymentGateway;
import com.hotel.services.payment.ZaloPayCallbackVerification;
import com.hotel.services.payment.ZaloPayPaymentGateway;
import com.hotel.services.PaymentService;
import com.hotel.services.ReservationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import com.hotel.security.Permission;
import com.hotel.security.FunctionCode;
import com.hotel.security.ActionCode;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;
    private final ReservationService reservationService;
    private final PaymentSessionService paymentSessionService;
    private final VnpayPaymentGateway vnpayPaymentGateway;
    private final MomoPaymentGateway momoPaymentGateway;
    private final ZaloPayPaymentGateway zaloPayPaymentGateway;

    @GetMapping("/reservation/{reservationId}")
    @Permission(function = FunctionCode.FINANCE, action = ActionCode.VIEW)
    public ResponseEntity<List<PaymentDTO>> getPaymentsByReservation(@PathVariable Long reservationId) {
        reservationService.getReservationById(reservationId);
        return ResponseEntity.ok(paymentService.getPaymentsByReservation(reservationId));
    }

    @PostMapping
    @Permission(function = FunctionCode.FINANCE, action = ActionCode.CREATE)
    public ResponseEntity<Void> processPayment(@RequestBody(required = false) PaymentDTO dto) {
        // Legacy callers cannot prove a server-owned amount or settlement event.
        // Keep the route discoverable long enough to return a clear successor.
        return ResponseEntity.status(HttpStatus.GONE)
                .header("Deprecation", "true")
                .header("Link", "</api/reservations/{reservationId}/payment-attempts>; rel=successor-version")
                .build();
    }

    @PostMapping("/sessions")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<PaymentSessionResponse> createPaymentSession(
            @Valid @RequestBody CreatePaymentSessionRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(paymentSessionService.createSession(
                request.getReservationId(),
                request.getProvider(),
                idempotencyKey,
                VnpayConfig.getIpAddress(httpRequest)));
    }

    @GetMapping("/sessions/{sessionId}")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<PaymentSessionStatusResponse> getPaymentSessionStatus(@PathVariable String sessionId) {
        return ResponseEntity.ok(paymentSessionService.getOwnedSessionStatus(sessionId));
    }

    // Transitional compatibility endpoint. New clients should POST /sessions.
    @GetMapping("/create-url")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<java.util.Map<String, String>> createPaymentUrl(
            @RequestParam Long reservationId,
            @RequestParam String method,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request) {
        PaymentSessionResponse session = paymentSessionService.createSession(
                reservationId,
                method,
                idempotencyKey,
                VnpayConfig.getIpAddress(request));
        return ResponseEntity.ok(Map.of("url", session.getUrl(), "sessionId", session.getSessionId()));
    }

    // Browser return is display-only; the server-to-server IPN is authoritative.
    @GetMapping("/vnpay-callback")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, String>> vnpayCallback(HttpServletRequest request) {
        VnpayCallbackVerification verification = vnpayPaymentGateway.verifyCallback(requestFields(request));
        if (!verification.valid()) {
            return ResponseEntity.ok(Map.of("status", "INVALID_SIGNATURE", "message", "Invalid checksum"));
        }
        if (!verification.data().successful()) {
            return ResponseEntity.ok(Map.of("status", "FAILED", "message", "Payment failed at gateway"));
        }
        return ResponseEntity.ok(Map.of(
                "status", "PENDING_VERIFICATION",
                "message", "Payment return received; waiting for authoritative IPN confirmation"));
    }

    @GetMapping("/vnpay-ipn")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, String>> vnpayIpn(HttpServletRequest request) {
        VnpayCallbackVerification verification = vnpayPaymentGateway.verifyCallback(requestFields(request));
        if (!verification.valid()) {
            return ResponseEntity.ok(Map.of(
                    "RspCode", verification.responseCode(),
                    "Message", verification.message()));
        }
        try {
            VnpayIpnResponse response = paymentSessionService.processVnpayCallback(verification.data());
            return ResponseEntity.ok(Map.of(
                    "RspCode", response.responseCode(),
                    "Message", response.message()));
        } catch (RuntimeException exception) {
            return ResponseEntity.ok(Map.of("RspCode", "99", "Message", "Unknown error"));
        }
    }

    @PostMapping("/momo-ipn")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Void> momoIpn(@RequestBody MomoCallbackRequest request) {
        MomoCallbackVerification verification = momoPaymentGateway.verifyCallback(request);
        if (!verification.valid()) {
            return ResponseEntity.badRequest().build();
        }
        ProviderCallbackOutcome outcome = paymentSessionService.processProviderCallback(verification.data());
        return switch (outcome) {
            case CONFIRMED, DUPLICATE, FAILED_RECORDED -> ResponseEntity.noContent().build();
            case NOT_FOUND, INVALID_AMOUNT -> ResponseEntity.badRequest().build();
        };
    }

    @PostMapping("/zalopay-callback")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, Object>> zaloPayCallback(@RequestBody ZaloPayCallbackRequest request) {
        ZaloPayCallbackVerification verification = zaloPayPaymentGateway.verifyCallback(request);
        if (!verification.valid()) {
            return ResponseEntity.ok(Map.of("return_code", 2, "return_message", "Invalid"));
        }
        ProviderCallbackOutcome outcome = paymentSessionService.processProviderCallback(verification.data());
        if (outcome == ProviderCallbackOutcome.NOT_FOUND || outcome == ProviderCallbackOutcome.INVALID_AMOUNT) {
            return ResponseEntity.ok(Map.of("return_code", 2, "return_message", "Invalid"));
        }
        return ResponseEntity.ok(Map.of("return_code", 1, "return_message", "Success"));
    }

    private Map<String, String> requestFields(HttpServletRequest request) {
        Map<String, String> fields = new HashMap<>();
        for (Enumeration<String> params = request.getParameterNames(); params.hasMoreElements();) {
            String fieldName = params.nextElement();
            String fieldValue = request.getParameter(fieldName);
            if (fieldValue != null && !fieldValue.isBlank()) {
                fields.put(fieldName, fieldValue);
            }
        }
        return fields;
    }

}
