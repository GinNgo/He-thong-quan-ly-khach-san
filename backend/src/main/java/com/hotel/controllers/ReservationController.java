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
                                                             @RequestBody ReservationRequest request,
                                                             @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                             HttpServletRequest httpRequest) {
        return createIdempotentReservation(authentication.getName(), request, idempotencyKey, httpRequest);
    }

    @GetMapping
    @Permission(function = FunctionCode.RESERVATION, action = ActionCode.VIEW)
    public ResponseEntity<List<ReservationDTO>> getAllReservations() {
        return ResponseEntity.ok(reservationService.getAllReservations());
    }

    @GetMapping("/page")
    @Permission(function = FunctionCode.RESERVATION, action = ActionCode.VIEW)
    public ResponseEntity<ReservationPageDTO> searchReservations(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(reservationService.searchReservations(status, query, page, size));
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

    @GetMapping("/my-bookings/page")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<ReservationPageDTO> searchMyReservations(
            Authentication authentication,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(reservationService.searchMyReservations(
                authentication.getName(), status, page, size));
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
        return ResponseEntity.status(HttpStatus.GONE)
                .header("Deprecation", "true")
                .header("Link", "</api/reservations/" + id + "/room-assignment>; rel=successor-version")
                .build();
    }

    @GetMapping("/{id}/available-rooms")
    @Permission(function = FunctionCode.RESERVATION_ASSIGNMENT, action = ActionCode.VIEW)
    public ResponseEntity<List<RoomDTO>> availableRooms(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.getAvailableRooms(id));
    }

    @GetMapping("/{id}/available-rooms/context")
    @Permission(function = FunctionCode.RESERVATION_ASSIGNMENT, action = ActionCode.VIEW)
    public ResponseEntity<AvailableRoomContextDTO> availableRoomContext(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.getAvailableRoomContext(id));
    }

    @PostMapping("/{id}/assign-rooms")
    @Permission(function = FunctionCode.RESERVATION_ASSIGNMENT, action = ActionCode.UPDATE)
    public ResponseEntity<ReservationDTO> assignRoomsPost(@PathVariable Long id, @RequestBody AssignRoomsRequest request) {
        return ResponseEntity.status(HttpStatus.GONE)
                .header("Deprecation", "true")
                .header("Link", "</api/reservations/" + id + "/room-assignment>; rel=successor-version")
                .build();
    }

    @PostMapping("/{id}/room-assignment")
    @Permission(function = FunctionCode.RESERVATION_ASSIGNMENT, action = ActionCode.UPDATE)
    public ResponseEntity<ReservationDTO> updateRoomAssignment(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody RoomAssignmentMutationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        ReservationDTO response = mutationIdempotencyService.execute(
                mutationCommand("RESERVATION_ROOM_ASSIGNMENT", authentication.getName() + ":" + id,
                idempotencyKey, request, httpRequest),
                HttpStatus.OK.value(),
                ReservationDTO.class,
                () -> reservationService.updateRoomAssignment(id, request),
                () -> reservationService.findRoomAssignmentReplay(id, request.roomIds()).orElse(null));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/room-assignment/release")
    @Permission(function = FunctionCode.RESERVATION_ASSIGNMENT, action = ActionCode.UPDATE)
    public ResponseEntity<ReservationDTO> releaseRoomAssignment(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody RoomAssignmentReleaseRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        ReservationDTO response = mutationIdempotencyService.execute(
                mutationCommand("RESERVATION_ROOM_RELEASE", authentication.getName() + ":" + id,
                idempotencyKey, request, httpRequest),
                HttpStatus.OK.value(),
                ReservationDTO.class,
                () -> reservationService.releaseRoomAssignment(id, request),
                () -> reservationService.findRoomReleaseReplay(id).orElse(null));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/check-in-readiness")
    @Permission(function = FunctionCode.CHECKIN, action = ActionCode.VIEW)
    public ResponseEntity<CheckInReadinessDTO> checkInReadiness(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.getCheckInReadiness(id));
    }

    @PostMapping("/{id}/check-in")
    @Permission(function = FunctionCode.CHECKIN, action = ActionCode.UPDATE)
    public ResponseEntity<ReservationDTO> checkIn(
            Authentication authentication,
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        ReservationDTO response = mutationIdempotencyService.execute(
                mutationCommand("RESERVATION_CHECK_IN", authentication.getName() + ":" + id,
                        idempotencyKey, new CheckInPayload(id), httpRequest),
                HttpStatus.OK.value(),
                ReservationDTO.class,
                () -> reservationService.checkIn(id),
                () -> reservationService.findCheckInReplay(id).orElse(null));
        return ResponseEntity.ok(response);
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
                                                                    @RequestBody ReservationRequest request,
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

    private record CheckInPayload(Long reservationId) {
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
