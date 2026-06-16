package com.hotel.controllers;

import com.hotel.dtos.ReservationRequest;
import com.hotel.entities.Reservation;
import com.hotel.services.ReservationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.hotel.security.Permission;
import com.hotel.security.FunctionCode;
import com.hotel.security.ActionCode;
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
}
