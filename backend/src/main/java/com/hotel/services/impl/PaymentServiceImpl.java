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
        Reservation reservation = reservationRepository.findByIdForUpdate(dto.getReservationId())
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        String transactionId = dto.getTransactionId();
        if (transactionId == null || transactionId.isBlank()) {
            transactionId = UUID.randomUUID().toString();
        } else {
            transactionId = transactionId.trim();
            var existing = paymentRepository.findByTransactionId(transactionId);
            if (existing.isPresent()) {
                if (!existing.get().getReservation().getId().equals(dto.getReservationId())) {
                    throw new IllegalArgumentException("Transaction ID belongs to another reservation.");
                }
                return mapToDTO(existing.get());
            }
        }

        Payment payment = new Payment();
        payment.setReservation(reservation);
        payment.setAmount(dto.getAmount());
        payment.setPaymentMethod(dto.getPaymentMethod());
        payment.setStatus("SUCCESS");
        payment.setTransactionId(transactionId);
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
    public void refundSuccessfulPayments(Long reservationId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        List<Payment> successfulPayments = paymentRepository.findByReservationId(reservationId).stream()
                .filter(payment -> "SUCCESS".equals(payment.getStatus()))
                .toList();

        for (Payment originalPayment : successfulPayments) {
            String refundTransactionId = "REFUND-" + originalPayment.getId();
            if (paymentRepository.findByTransactionId(refundTransactionId).isPresent()) {
                continue;
            }

            Payment refund = new Payment();
            refund.setReservation(reservation);
            refund.setAmount(originalPayment.getAmount().negate());
            refund.setPaymentMethod(originalPayment.getPaymentMethod());
            refund.setStatus("REFUNDED");
            refund.setTransactionId(refundTransactionId);
            refund.setPaymentDate(LocalDateTime.now());
            paymentRepository.save(refund);

            if (reservation.getUser() != null) {
                User user = reservation.getUser();
                int earnedPoints = originalPayment.getAmount()
                        .divide(new java.math.BigDecimal(100000), java.math.RoundingMode.DOWN)
                        .intValue();
                int currentPoints = user.getPoints() == null ? 0 : user.getPoints();
                user.setPoints(Math.max(0, currentPoints - earnedPoints));
                userRepository.save(user);
            }
        }
    }

    @Override
    @Transactional
    public void handleSuccessfulPayment(Long reservationId, String method, String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("Transaction ID is required.");
        }

        String normalizedTransactionId = transactionId.trim();
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        var existing = paymentRepository.findByTransactionId(normalizedTransactionId);
        if (existing.isPresent()) {
            if (!existing.get().getReservation().getId().equals(reservationId)) {
                throw new IllegalArgumentException("Transaction ID belongs to another reservation.");
            }
            return;
        }

        reservation.setStatus("CONFIRMED");
        reservation.setPaymentMethod(method);
        reservationRepository.save(reservation);

        PaymentDTO dto = new PaymentDTO();
        dto.setReservationId(reservationId);
        dto.setAmount(reservation.getTotalAmount());
        dto.setPaymentMethod(method);
        dto.setTransactionId(normalizedTransactionId);
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
