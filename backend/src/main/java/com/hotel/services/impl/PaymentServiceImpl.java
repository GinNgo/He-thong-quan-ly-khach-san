package com.hotel.services.impl;

import com.hotel.domain.lifecycle.PaymentStatus;
import com.hotel.domain.lifecycle.ReservationStatus;
import com.hotel.domain.lifecycle.BookingLifecyclePolicy;
import com.hotel.domain.lifecycle.TransitionDecision;
import com.hotel.domain.payment.PaymentCompletionResult;
import com.hotel.dtos.PaymentDTO;
import com.hotel.services.PaymentService;
import com.hotel.services.ReservationHoldService;
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

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final ReservationHoldService reservationHoldService;

    public PaymentServiceImpl(
            PaymentRepository paymentRepository,
            ReservationRepository reservationRepository,
            UserRepository userRepository,
            ReservationHoldService reservationHoldService) {
        this.paymentRepository = paymentRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.reservationHoldService = reservationHoldService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentDTO> getPaymentsByReservation(Long reservationId) {
        return paymentRepository.findByReservationId(reservationId).stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PaymentCompletionResult handleSuccessfulPayment(Long reservationId, String method, String transactionId) {
        return handleSuccessfulPayment(reservationId, method, transactionId, PaymentStatus.PENDING);
    }

    @Override
    @Transactional
    public PaymentCompletionResult handleSuccessfulPayment(
            Long reservationId,
            String method,
            String transactionId,
            PaymentStatus currentPaymentStatus) {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("Transaction ID is required.");
        }
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("Payment method is required.");
        }

        String normalizedTransactionId = transactionId.trim();
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        var existing = paymentRepository.findByTransactionId(normalizedTransactionId);
        if (existing.isPresent()) {
            if (!existing.get().getReservation().getId().equals(reservationId)) {
                throw new IllegalArgumentException("Transaction ID belongs to another reservation.");
            }
            return PaymentCompletionResult.IDEMPOTENT;
        }

        ReservationStatus reservationStatus = ReservationStatus.fromStorage(reservation.getStatus());
        TransitionDecision decision = BookingLifecyclePolicy.paymentSuccess(reservationStatus, currentPaymentStatus);
        if (decision == TransitionDecision.REJECT) {
            throw new IllegalStateException("Payment success is not valid for the current booking lifecycle.");
        }

        boolean anotherSuccessfulChargeExists = paymentRepository.findByReservationId(reservationId).stream()
                .anyMatch(payment -> payment.getAmount() != null
                        && payment.getAmount().signum() > 0
                        && PaymentStatus.fromStorage(payment.getStatus()) == PaymentStatus.SUCCEEDED);
        boolean reconciliationRequired = decision == TransitionDecision.RECONCILIATION_REQUIRED
                || anotherSuccessfulChargeExists;

        Payment payment = new Payment();
        payment.setReservation(reservation);
        payment.setAmount(reservation.getTotalAmount());
        payment.setPaymentMethod(method.trim().toUpperCase(java.util.Locale.ROOT));
        payment.setStatus(PaymentStatus.SUCCEEDED.name());
        payment.setTransactionId(normalizedTransactionId);
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);

        if (reconciliationRequired) {
            return PaymentCompletionResult.RECONCILIATION_REQUIRED;
        }

        reservation.setStatus(ReservationStatus.CONFIRMED.name());
        reservation.setPaymentMethod(payment.getPaymentMethod());
        reservationRepository.save(reservation);
        reservationHoldService.consumeActiveHold(reservationId, LocalDateTime.now());
        awardLoyaltyPoints(reservation, payment.getAmount());
        return PaymentCompletionResult.APPLIED;
    }

    private void awardLoyaltyPoints(Reservation reservation, java.math.BigDecimal amount) {
        if (reservation.getUser() == null) {
            return;
        }
        User user = reservation.getUser();
        int earnedPoints = amount.divide(new java.math.BigDecimal(100000), java.math.RoundingMode.DOWN).intValue();
        user.setPoints((user.getPoints() == null ? 0 : user.getPoints()) + earnedPoints);
        userRepository.save(user);
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
