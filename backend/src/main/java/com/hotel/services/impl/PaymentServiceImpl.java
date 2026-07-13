package com.hotel.services.impl;

import com.hotel.dtos.PaymentDTO;
import com.hotel.services.PaymentService;
import com.hotel.entities.Payment;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.repositories.PaymentRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;

    public PaymentServiceImpl(PaymentRepository paymentRepository, ReservationRepository reservationRepository, UserRepository userRepository) {
        this.paymentRepository = paymentRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentDTO> getPaymentsByReservation(Long reservationId) {
        return paymentRepository.findByReservationId(reservationId).stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PaymentDTO processPayment(PaymentDTO dto) {
        Reservation reservation = reservationRepository.findById(dto.getReservationId())
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        Payment payment = new Payment();
        payment.setReservation(reservation);
        payment.setAmount(dto.getAmount());
        payment.setPaymentMethod(dto.getPaymentMethod());
        payment.setStatus("SUCCESS");
        payment.setTransactionId(UUID.randomUUID().toString());
        payment.setPaymentDate(LocalDateTime.now());
        
        Payment saved = paymentRepository.save(payment);

        // Loyalty Points: 100,000 VND = 1 point
        if (reservation.getUser() != null) {
            User user = reservation.getUser();
            int earnedPoints = payment.getAmount().divide(new java.math.BigDecimal(100000), java.math.RoundingMode.DOWN).intValue();
            user.setPoints((user.getPoints() == null ? 0 : user.getPoints()) + earnedPoints);
            userRepository.save(user);
        }

        return mapToDTO(saved);
    }

    @Override
    @Transactional
    public void handleSuccessfulPayment(Long reservationId, String method) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));
        
        // Update Reservation Status
        reservation.setStatus("CONFIRMED");
        reservation.setPaymentMethod(method);
        reservationRepository.save(reservation);
        
        // Create Payment Record
        PaymentDTO dto = new PaymentDTO();
        dto.setReservationId(reservationId);
        dto.setAmount(reservation.getTotalAmount());
        dto.setPaymentMethod(method);
        
        // Call existing method to create payment and handle loyalty points
        this.processPayment(dto);
    }

    private PaymentDTO mapToDTO(Payment payment) {
        PaymentDTO dto = new PaymentDTO();
        dto.setId(payment.getId());
        dto.setReservationId(payment.getReservation().getId());
        dto.setAmount(payment.getAmount());
        dto.setPaymentMethod(payment.getPaymentMethod());
        dto.setStatus(payment.getStatus());
        dto.setTransactionId(payment.getTransactionId());
        dto.setPaymentDate(payment.getPaymentDate());
        return dto;
    }
}
