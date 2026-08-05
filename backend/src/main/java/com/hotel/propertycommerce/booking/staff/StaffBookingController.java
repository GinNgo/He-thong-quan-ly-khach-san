package com.hotel.propertycommerce.booking.staff;

import com.hotel.dtos.ReservationDTO;
import com.hotel.exceptions.CorrelationIdSupport;
import com.hotel.paymentprovider.idempotency.FinancialIdempotencyService;
import com.hotel.paymentprovider.idempotency.MutationIdempotencyService;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/management/staff-bookings")
@RequiredArgsConstructor
public class StaffBookingController {
    private final StaffBookingService service;
    private final MutationIdempotencyService idempotencyService;

    @GetMapping("/context")
    @Permission(function = FunctionCode.RESERVATION, action = ActionCode.CREATE)
    public StaffBookingService.ContextResponse context(
            @RequestParam Long hotelId,
            @RequestParam(required = false) String customerQuery) {
        return service.context(hotelId, customerQuery);
    }

    @PostMapping("/quotes")
    @Permission(function = FunctionCode.RESERVATION, action = ActionCode.CREATE)
    public ResponseEntity<StaffBookingService.QuoteResponse> quote(
            Authentication authentication,
            @RequestBody StaffBookingService.QuoteRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest httpRequest) {
        FinancialIdempotencyService.BeginCommand command = command(
                "STAFF_BOOKING_QUOTE", authentication.getName() + ":" + request.hotelId(), key, request, httpRequest);
        StaffBookingService.QuoteResponse response = idempotencyService.execute(
                command, HttpStatus.CREATED.value(), StaffBookingService.QuoteResponse.class,
                () -> service.quote(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping
    @Permission(function = FunctionCode.RESERVATION, action = ActionCode.CREATE)
    public ResponseEntity<ReservationDTO> create(
            Authentication authentication,
            @RequestBody StaffBookingService.CreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest httpRequest) {
        FinancialIdempotencyService.BeginCommand command = command(
                "STAFF_BOOKING_CREATE", authentication.getName() + ":" + request.quoteId(), key, request, httpRequest);
        ReservationDTO response = idempotencyService.execute(
                command, HttpStatus.CREATED.value(), ReservationDTO.class,
                () -> service.create(request.quoteId(), command.scopeKey(), command.idempotencyKey()),
                () -> service.replay(request.quoteId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private FinancialIdempotencyService.BeginCommand command(
            String operation, String scope, String key, Object payload, HttpServletRequest request) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Idempotency-Key is required.");
        return new FinancialIdempotencyService.BeginCommand(
                "PROPERTY_COMMERCE", operation, scope, key.trim(), payload, null, null,
                CorrelationIdSupport.resolve(request));
    }
}
