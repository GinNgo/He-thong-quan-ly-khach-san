package com.hotel.controllers;

import com.hotel.dtos.*;
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

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    @Permission(function = FunctionCode.RESERVATION, action = ActionCode.CREATE)
    public ResponseEntity<ReservationDTO> createReservation(Authentication authentication,
                                                             @RequestBody ReservationRequest request) {
        return new ResponseEntity<>(reservationService.createReservation(authentication.getName(), request), HttpStatus.CREATED);
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

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('PROPERTY_OWNER','HOTEL_MANAGER','RECEPTIONIST','HOTEL_ADMIN','SUPER_ADMIN','ADMIN')")
    public ResponseEntity<ReservationDTO> updateStatus(@PathVariable Long id, @RequestParam String status) {
        return ResponseEntity.ok(reservationService.updateReservationStatus(id, status));
    }

    @PutMapping("/{id}/rooms")
    @PreAuthorize("hasAnyAuthority('PROPERTY_OWNER','HOTEL_MANAGER','RECEPTIONIST','HOTEL_ADMIN','SUPER_ADMIN','ADMIN')")
    public ResponseEntity<ReservationDTO> assignRooms(@PathVariable Long id, @RequestBody AssignRoomsRequest request) {
        return ResponseEntity.ok(reservationService.assignRooms(id, request));
    }

    @GetMapping("/{id}/available-rooms")
    @PreAuthorize("hasAnyAuthority('PROPERTY_OWNER','HOTEL_MANAGER','RECEPTIONIST','HOTEL_ADMIN','SUPER_ADMIN','ADMIN')")
    public ResponseEntity<List<RoomDTO>> availableRooms(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.getAvailableRooms(id));
    }

    @PostMapping("/{id}/assign-rooms")
    @PreAuthorize("hasAnyAuthority('PROPERTY_OWNER','HOTEL_MANAGER','RECEPTIONIST','HOTEL_ADMIN','SUPER_ADMIN','ADMIN')")
    public ResponseEntity<ReservationDTO> assignRoomsPost(@PathVariable Long id, @RequestBody AssignRoomsRequest request) {
        return ResponseEntity.ok(reservationService.assignRooms(id, request));
    }

    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasAnyAuthority('PROPERTY_OWNER','HOTEL_MANAGER','RECEPTIONIST','HOTEL_ADMIN','SUPER_ADMIN','ADMIN')")
    public ResponseEntity<ReservationDTO> checkIn(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.updateReservationStatus(id, "CHECKED_IN"));
    }

    @PostMapping("/{id}/check-out")
    @PreAuthorize("hasAnyAuthority('PROPERTY_OWNER','HOTEL_MANAGER','RECEPTIONIST','HOTEL_ADMIN','SUPER_ADMIN','ADMIN')")
    public ResponseEntity<CheckoutResultDTO> checkOut(@PathVariable Long id,
            @RequestBody(required = false) CheckoutRequest request) {
        return ResponseEntity.ok(reservationService.checkout(id, request));
    }

    @PostMapping("/public/book")
    public ResponseEntity<ReservationDTO> createPublicReservation(@RequestBody ReservationRequest request) {
        return new ResponseEntity<>(reservationService.createReservation(null, request), HttpStatus.CREATED);
    }

    @PostMapping("/book")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<ReservationDTO> createCustomerReservation(Authentication authentication,
                                                                    @RequestBody ReservationRequest request) {
        return new ResponseEntity<>(reservationService.createReservation(authentication.getName(), request), HttpStatus.CREATED);
    }

    @PostMapping("/{id}/services")
    @PreAuthorize("hasAnyAuthority('PROPERTY_OWNER','HOTEL_MANAGER','RECEPTIONIST','HOTEL_ADMIN','SUPER_ADMIN','ADMIN')")
    public ResponseEntity<ReservationServiceItemDTO> addExtraService(
            @PathVariable Long id, @RequestBody AddServiceRequest request) {
        return ResponseEntity.ok(reservationService.addExtraService(id, request));
    }
}
