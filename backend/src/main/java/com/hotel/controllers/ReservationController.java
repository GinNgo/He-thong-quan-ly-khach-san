package com.hotel.controllers;

import com.hotel.dtos.ReservationRequest;
import com.hotel.entities.Reservation;
import com.hotel.services.ReservationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.hotel.security.Permission;
import com.hotel.security.FunctionCode;
import com.hotel.security.ActionCode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @Permission(function = FunctionCode.RESERVATION, action = ActionCode.CREATE)
    public ResponseEntity<Reservation> createReservation(Authentication authentication, 
                                                         @RequestBody ReservationRequest request) {
        String username = authentication.getName();
        Reservation reservation = reservationService.createReservation(username, request);
        return new ResponseEntity<>(reservation, HttpStatus.CREATED);
    }

    @GetMapping
    @Permission(function = FunctionCode.RESERVATION, action = ActionCode.VIEW)
    public ResponseEntity<java.util.List<com.hotel.dtos.ReservationDTO>> getAllReservations() {
        return ResponseEntity.ok(reservationService.getAllReservations());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER') or hasAuthority('RECEPTIONIST') or hasAuthority('HOTEL_ADMIN')")
    public ResponseEntity<com.hotel.dtos.ReservationDTO> getReservationById(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.getReservationById(id));
    }

    @GetMapping("/my-bookings")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<java.util.List<com.hotel.dtos.ReservationDTO>> getMyReservations(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(reservationService.getMyReservations(username));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('RECEPTIONIST') or hasAuthority('HOTEL_ADMIN')")
    public ResponseEntity<Reservation> updateStatus(@PathVariable Long id, @RequestParam String status) {
        return ResponseEntity.ok(reservationService.updateReservationStatus(id, status));
    }

    @PostMapping("/public/book")
    public ResponseEntity<Reservation> createPublicReservation(@RequestBody ReservationRequest request) {
        // Null username indicates a guest booking
        Reservation reservation = reservationService.createReservation(null, request);
        return new ResponseEntity<>(reservation, HttpStatus.CREATED);
    }

    @PostMapping("/book")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<Reservation> createCustomerReservation(Authentication authentication, @RequestBody ReservationRequest request) {
        String username = authentication.getName();
        Reservation reservation = reservationService.createReservation(username, request);
        return new ResponseEntity<>(reservation, HttpStatus.CREATED);
    }
}
