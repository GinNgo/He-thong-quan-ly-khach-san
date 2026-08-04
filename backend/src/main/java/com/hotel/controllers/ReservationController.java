package com.hotel.controllers;

import com.hotel.dtos.*;
import com.hotel.exceptions.CorrelationIdSupport;
import com.hotel.paymentprovider.idempotency.FinancialIdempotencyService;
import com.hotel.paymentprovider.idempotency.MutationIdempotencyService;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/reservations")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
public class ReservationController {

    private static final Set<String> DEDICATED_LIFECYCLE_STATUSES = Set.of(
            "CANCELLED", "NO_SHOW", "CHECKED_IN", "CHECKED_OUT");

    private final ReservationService reservationService;
    private final MutationIdempotencyService mutationIdempotencyService;

    @PostMapping
    @Permission(function = FunctionCode.RESERVATION, action = ActionCode.CREATE)
    public ResponseEntity<ReservationDTO> createReservation(Authentication authentication,
                                                             @Valid @RequestBody ReservationRequest request,
                                                             @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                             HttpServletRequest httpRequest) {
        return createIdempotentReservation(authentication.getName(), request, idempotencyKey, httpRequest);
    }

    @GetMapping
    @Permission(function = FunctionCode.RESERVATION, action = ActionCode.VIEW)
    public ResponseEntity<List<ReservationDTO>> getAllReservations() {
        return ResponseEntity.ok(reservationService.getAllReservations());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CUSTOMER','PROPERTY_OWNER','HOTEL_MANAGER','RECEPTIONIST','HOTEL_ADMIN','SUPER_ADMIN','ADMIN')")
    public ResponseEntity<ReservationDTO> getReservationById(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.getReservationById(id));
    }

    @GetMapping("/my-bookings")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<List<ReservationDTO>> getMyReservations(Authentication authentication) {
        return ResponseEntity.ok(reservationService.getMyReservations(authentication.getName()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<ReservationDTO> cancelMyReservation(
            Authentication authentication,
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        String username = authentication.getName();
        ReservationDTO response = mutationIdempotencyService.execute(
                mutationCommand("RESERVATION_CANCEL", username + ":" + id, idempotencyKey,
                        new CancellationPayload(id), httpRequest),
                HttpStatus.OK.value(),
                ReservationDTO.class,
                () -> reservationService.cancelMyReservation(id, username));
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/status")
    @Permission(function = FunctionCode.RESERVATION, action = ActionCode.UPDATE)
    public ResponseEntity<ReservationDTO> updateStatus(@PathVariable Long id, @RequestParam String status) {
        requireGenericStatusEndpoint(status);
        return ResponseEntity.ok(reservationService.updateReservationStatus(id, status));
    }

    @PutMapping("/{id}/rooms")
    @Permission(function = FunctionCode.RESERVATION_ASSIGNMENT, action = ActionCode.UPDATE)
    public ResponseEntity<ReservationDTO> assignRooms(@PathVariable Long id, @RequestBody AssignRoomsRequest request) {
        return ResponseEntity.ok(reservationService.assignRooms(id, request));
    }

    @GetMapping("/{id}/available-rooms")
    @Permission(function = FunctionCode.RESERVATION_ASSIGNMENT, action = ActionCode.VIEW)
    public ResponseEntity<List<RoomDTO>> availableRooms(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.getAvailableRooms(id));
    }

    @PostMapping("/{id}/assign-rooms")
    @Permission(function = FunctionCode.RESERVATION_ASSIGNMENT, action = ActionCode.UPDATE)
    public ResponseEntity<ReservationDTO> assignRoomsPost(@PathVariable Long id, @RequestBody AssignRoomsRequest request) {
        return ResponseEntity.ok(reservationService.assignRooms(id, request));
    }

    @PostMapping("/{id}/check-in")
    @Permission(function = FunctionCode.CHECKIN, action = ActionCode.UPDATE)
    public ResponseEntity<ReservationDTO> checkIn(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.checkIn(id));
    }

    @PostMapping("/{id}/cancel-operational")
    @Permission(function = FunctionCode.RESERVATION_CANCEL, action = ActionCode.UPDATE)
    public ResponseEntity<ReservationDTO> cancelOperational(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.cancelOperational(id));
    }

    @PostMapping("/{id}/no-show")
    @Permission(function = FunctionCode.RESERVATION_NO_SHOW, action = ActionCode.UPDATE)
    public ResponseEntity<ReservationDTO> markNoShow(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.markNoShow(id));
    }

    @PostMapping("/{id}/check-out")
    @Permission(function = FunctionCode.CHECKOUT, action = ActionCode.CREATE)
    public ResponseEntity<CheckoutResultDTO> checkOut(@PathVariable Long id,
            @RequestBody(required = false) CheckoutRequest request) {
        return ResponseEntity.ok(reservationService.checkout(id, request));
    }

    @PostMapping("/public/book")
    public ResponseEntity<ReservationDTO> createPublicReservation(
            @RequestBody ReservationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        String publicScope = "PUBLIC:" + httpRequest.getRemoteAddr();
        return createIdempotentReservation(publicScope, request, idempotencyKey, httpRequest, null);
    }

    @PostMapping("/book")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<ReservationDTO> createCustomerReservation(Authentication authentication,
                                                                    @Valid @RequestBody ReservationRequest request,
                                                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                                    HttpServletRequest httpRequest) {
        return createIdempotentReservation(authentication.getName(), request, idempotencyKey, httpRequest);
    }

    @PostMapping("/{id}/services")
    @Permission(function = FunctionCode.RESERVATION_SERVICE, action = ActionCode.CREATE)
    public ResponseEntity<Void> addExtraService(
            @PathVariable Long id, @RequestBody AddServiceRequest request) {
        return ResponseEntity.status(HttpStatus.GONE)
                .header("Deprecation", "true")
                .header("Link", "</api/management/reservations/" + id + "/charges/services>; rel=successor-version")
                .build();
    }

    private ResponseEntity<ReservationDTO> createIdempotentReservation(
            String username,
            ReservationRequest request,
            String idempotencyKey,
            HttpServletRequest httpRequest) {
        return createIdempotentReservation(username, request, idempotencyKey, httpRequest, username);
    }

    private ResponseEntity<ReservationDTO> createIdempotentReservation(
            String scope,
            ReservationRequest request,
            String idempotencyKey,
            HttpServletRequest httpRequest,
            String serviceUsername) {
        FinancialIdempotencyService.BeginCommand command =
                mutationCommand("RESERVATION_CREATE", scope, idempotencyKey, request, httpRequest);
        ReservationDTO response = mutationIdempotencyService.execute(
                command,
                HttpStatus.CREATED.value(),
                ReservationDTO.class,
                () -> reservationService.createReservation(
                        serviceUsername, request, command.scopeKey(), command.idempotencyKey()),
                () -> reservationService.findByBookingIdempotency(
                        command.scopeKey(), command.idempotencyKey()).orElse(null));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private FinancialIdempotencyService.BeginCommand mutationCommand(
            String operation,
            String scope,
            String idempotencyKey,
        Object payload,
            HttpServletRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required for reservation mutations.");
        }
        String normalizedKey = idempotencyKey.trim();
        return new FinancialIdempotencyService.BeginCommand(
                "PROPERTY_COMMERCE",
                operation,
                scope,
                normalizedKey,
                payload,
                null,
                null,
                CorrelationIdSupport.resolve(request));
    }

    private record CancellationPayload(Long reservationId) {
    }

    private void requireGenericStatusEndpoint(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Reservation status is required.");
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (DEDICATED_LIFECYCLE_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Use the dedicated reservation lifecycle endpoint for status " + normalized + ".");
        }
    }
}
