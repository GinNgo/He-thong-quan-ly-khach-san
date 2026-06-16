package com.hotel.controllers;

import com.hotel.dtos.PaymentDTO;
import com.hotel.services.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.hotel.security.Permission;
import com.hotel.security.FunctionCode;
import com.hotel.security.ActionCode;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @GetMapping("/reservation/{reservationId}")
    @Permission(function = FunctionCode.FINANCE, action = ActionCode.VIEW)
    public ResponseEntity<List<PaymentDTO>> getPaymentsByReservation(@PathVariable Long reservationId) {
        return ResponseEntity.ok(paymentService.getPaymentsByReservation(reservationId));
    }

    @PostMapping
    @Permission(function = FunctionCode.FINANCE, action = ActionCode.CREATE)
    public ResponseEntity<PaymentDTO> processPayment(@RequestBody PaymentDTO dto) {
        return ResponseEntity.ok(paymentService.processPayment(dto));
    }
}
