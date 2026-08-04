package com.hotel.propertycommerce.booking;

import com.hotel.exceptions.CorrelationIdSupport;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management/reservations/{reservationId}")
public class ManagementReservationAmendmentController {

    private final ReservationAmendmentService service;

    public ManagementReservationAmendmentController(ReservationAmendmentService service) {
        this.service = service;
    }

    @GetMapping("/amendment-context")
    @Permission(function = FunctionCode.RESERVATION_AMEND, action = ActionCode.VIEW)
    public ReservationAmendmentService.ContextResponse context(@PathVariable Long reservationId) {
        return service.context(reservationId, ReservationAmendmentService.AccessMode.STAFF);
    }

    @PostMapping("/amendment-quotes")
    @Permission(function = FunctionCode.RESERVATION_AMEND, action = ActionCode.UPDATE)
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
                ReservationAmendmentService.AccessMode.STAFF));
    }

    @GetMapping("/amendment-quotes/{quotePublicId}")
    @Permission(function = FunctionCode.RESERVATION_AMEND, action = ActionCode.VIEW)
    public ReservationAmendmentService.QuoteResponse get(
            @PathVariable Long reservationId,
            @PathVariable String quotePublicId) {
        return service.get(reservationId, quotePublicId, ReservationAmendmentService.AccessMode.STAFF);
    }

    @PostMapping("/amendment-quotes/{quotePublicId}/payment-attempts")
    @Permission(function = FunctionCode.RESERVATION_AMEND, action = ActionCode.UPDATE)
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
                ReservationAmendmentService.AccessMode.STAFF));
    }

    @PostMapping("/amendment-quotes/{quotePublicId}/apply")
    @Permission(function = FunctionCode.RESERVATION_AMEND, action = ActionCode.UPDATE)
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
                ReservationAmendmentService.AccessMode.STAFF);
    }

    public record PaymentMethodRequest(String method) {
    }
}
