package com.hotel.services;

import com.hotel.dtos.ReservationRequest;
import com.hotel.entities.Reservation;
import com.hotel.entities.ReservationDetail;
import com.hotel.entities.Room;
import com.hotel.entities.User;
import com.hotel.repositories.ReservationDetailRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationDetailRepository reservationDetailRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    public ReservationService(ReservationRepository reservationRepository,
                              ReservationDetailRepository reservationDetailRepository,
                              RoomRepository roomRepository,
                              UserRepository userRepository) {
        this.reservationRepository = reservationRepository;
        this.reservationDetailRepository = reservationDetailRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Reservation createReservation(String username, ReservationRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new RuntimeException("Room not found"));

        // Simplistic total calculation for mockup: price * 1 night (should calculate difference in days)
        BigDecimal totalAmount = room.getRoomType().getBasePrice().multiply(BigDecimal.valueOf(1.15)); // adding taxes

        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setCheckInDate(request.getCheckInDate());
        reservation.setCheckOutDate(request.getCheckOutDate());
        reservation.setGuests(request.getGuests());
        reservation.setStatus("CONFIRMED"); // For mockup. Real app might be PENDING_PAYMENT
        reservation.setPaymentMethod(request.getPaymentMethod());
        reservation.setSpecialRequests(request.getSpecialRequests());
        reservation.setTotalAmount(totalAmount);
        reservation.setCreatedAt(LocalDateTime.now());

        Reservation savedReservation = reservationRepository.save(reservation);

        ReservationDetail detail = new ReservationDetail();
        detail.setReservation(savedReservation);
        detail.setRoom(room);
        detail.setPrice(room.getRoomType().getBasePrice());
        reservationDetailRepository.save(detail);

        // Update room status
        room.setStatus("RESERVED");
        roomRepository.save(room);

        return savedReservation;
    }
}
