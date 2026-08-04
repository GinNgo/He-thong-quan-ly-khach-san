package com.hotel.propertycommerce.booking;

import com.hotel.exceptions.CorrelationIdSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations/{reservationId}")
@PreAuthorize("hasAuthority('CUSTOMER')")
public class CustomerReservationAmendmentController {

    private final ReservationAmendmentService service;

    public CustomerReservationAmendmentController(ReservationAmendmentService service) {
        this.service = service;
    }

    @GetMapping("/amendment-context")
    public ReservationAmendmentService.ContextResponse context(@PathVariable Long reservationId) {
        return service.context(reservationId, ReservationAmendmentService.AccessMode.CUSTOMER);
    }

    @PostMapping("/amendment-quotes")
    public ResponseEntity<ReservationAmendmentService.QuoteResponse> quote(
            @PathVariable Long reservationId,
            @RequestBody ReservationAmendmentService.QuoteRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.quote(
                reservationId,
                request,
                idempotencyKey,
                CorrelationIdSupport.resolve(httpRequest),
                ReservationAmendmentService.AccessMode.CUSTOMER));
    }

    @GetMapping("/amendment-quotes/{quotePublicId}")
    public ReservationAmendmentService.QuoteResponse get(
            @PathVariable Long reservationId,
            @PathVariable String quotePublicId) {
        return service.get(
                reservationId, quotePublicId, ReservationAmendmentService.AccessMode.CUSTOMER);
    }

    @PostMapping("/amendment-quotes/{quotePublicId}/payment-attempts")
    public ResponseEntity<ReservationAmendmentService.QuoteResponse> createPaymentAttempt(
            @PathVariable Long reservationId,
            @PathVariable String quotePublicId,
            @RequestBody PaymentMethodRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createPaymentAttempt(
                reservationId,
                quotePublicId,
                request.method(),
                idempotencyKey,
                CorrelationIdSupport.resolve(httpRequest),
                ReservationAmendmentService.AccessMode.CUSTOMER));
    }

    @PostMapping("/amendment-quotes/{quotePublicId}/apply")
    public ReservationAmendmentService.QuoteResponse apply(
            @PathVariable Long reservationId,
            @PathVariable String quotePublicId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        return service.apply(
                reservationId,
                quotePublicId,
                idempotencyKey,
                CorrelationIdSupport.resolve(httpRequest),
                ReservationAmendmentService.AccessMode.CUSTOMER);
    }

    public record PaymentMethodRequest(String method) {
    }
}
