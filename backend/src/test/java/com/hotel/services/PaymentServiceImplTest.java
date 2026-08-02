package com.hotel.services;

import com.hotel.entities.Payment;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.repositories.PaymentRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.services.impl.PaymentServiceImpl;
import com.hotel.domain.lifecycle.PaymentStatus;
import com.hotel.domain.payment.PaymentCompletionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReservationHoldService reservationHoldService;

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(
                paymentRepository,
                reservationRepository,
                userRepository,
                reservationHoldService
        );
    }

    @Test
    void handleSuccessfulPayment_WithNewTransaction_ShouldConfirmAndAwardPoints() {
        User user = new User();
        user.setPoints(10);

        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setUser(user);
        reservation.setTotalAmount(new BigDecimal("250000"));
        reservation.setStatus("PENDING_PAYMENT");

        when(reservationRepository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(reservation));
        when(paymentRepository.findByTransactionId("TX_123"))
                .thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.handleSuccessfulPayment(42L, "MOMO", "TX_123");

        assertEquals("CONFIRMED", reservation.getStatus());
        assertEquals("MOMO", reservation.getPaymentMethod());
        assertEquals(12, user.getPoints());
        verify(reservationRepository).save(reservation);
        verify(paymentRepository).save(any(Payment.class));
        verify(userRepository).save(user);
    }

    @Test
    void handleSuccessfulPayment_WithDuplicateTransaction_ShouldNotDuplicatePaymentOrPoints() {
        User user = new User();
        user.setPoints(10);

        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setUser(user);
        reservation.setStatus("CONFIRMED");

        Payment existingPayment = new Payment();
        existingPayment.setReservation(reservation);
        existingPayment.setTransactionId("TX_123");

        when(reservationRepository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(reservation));
        when(paymentRepository.findByTransactionId("TX_123"))
                .thenReturn(Optional.of(existingPayment));

        paymentService.handleSuccessfulPayment(42L, "MOMO", "TX_123");

        assertEquals(10, user.getPoints());
        verify(reservationRepository, never()).save(any(Reservation.class));
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void handleSuccessfulPayment_WithTransactionFromAnotherReservation_ShouldReject() {
        Reservation requestedReservation = new Reservation();
        requestedReservation.setId(42L);

        Reservation ownerReservation = new Reservation();
        ownerReservation.setId(99L);

        Payment existingPayment = new Payment();
        existingPayment.setReservation(ownerReservation);
        existingPayment.setTransactionId("TX_123");

        when(reservationRepository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(requestedReservation));
        when(paymentRepository.findByTransactionId("TX_123"))
                .thenReturn(Optional.of(existingPayment));

        assertThrows(
                IllegalArgumentException.class,
                () -> paymentService.handleSuccessfulPayment(42L, "MOMO", "TX_123")
        );

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void handleSuccessfulPayment_WithoutTransactionId_ShouldReject() {
        assertThrows(
                IllegalArgumentException.class,
                () -> paymentService.handleSuccessfulPayment(42L, "MOMO", " ")
        );

        verify(reservationRepository, never()).findByIdForUpdate(any());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void handleSuccessfulPayment_AfterReservationExpiry_ShouldRecordChargeForReconciliationOnly() {
        User user = new User();
        user.setPoints(10);
        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setUser(user);
        reservation.setTotalAmount(new BigDecimal("250000"));
        reservation.setStatus("EXPIRED");

        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));
        when(paymentRepository.findByTransactionId("TX_LATE")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentCompletionResult result = paymentService.handleSuccessfulPayment(
                42L,
                "VNPAY",
                "TX_LATE",
                PaymentStatus.EXPIRED);

        assertEquals(PaymentCompletionResult.RECONCILIATION_REQUIRED, result);
        assertEquals("EXPIRED", reservation.getStatus());
        assertEquals(10, user.getPoints());
        verify(paymentRepository).save(any(Payment.class));
        verify(reservationRepository, never()).save(any(Reservation.class));
        verify(reservationHoldService, never()).consumeActiveHold(any(), any());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void refundSuccessfulPayments_ShouldRefundEverySuccessfulPaymentAndDeductPoints() {
        User user = new User();
        user.setPoints(10);

        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setUser(user);

        Payment firstPayment = new Payment();
        firstPayment.setId(101L);
        firstPayment.setReservation(reservation);
        firstPayment.setAmount(new BigDecimal("100000"));
        firstPayment.setPaymentMethod("MOMO");
        firstPayment.setStatus("SUCCESS");

        Payment secondPayment = new Payment();
        secondPayment.setId(102L);
        secondPayment.setReservation(reservation);
        secondPayment.setAmount(new BigDecimal("250000"));
        secondPayment.setPaymentMethod("CARD");
        secondPayment.setStatus("SUCCESS");

        when(reservationRepository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(reservation));
        when(paymentRepository.findByReservationId(42L))
                .thenReturn(java.util.List.of(firstPayment, secondPayment));
        when(paymentRepository.findByTransactionId("REFUND-101"))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByTransactionId("REFUND-102"))
                .thenReturn(Optional.empty());

        paymentService.refundSuccessfulPayments(42L);

        assertEquals(7, user.getPoints());
        verify(paymentRepository, times(2)).save(any(Payment.class));
        verify(userRepository, times(2)).save(user);
    }

    @Test
    void refundSuccessfulPayments_WithExistingRefund_ShouldNotRefundOrDeductPointsAgain() {
        User user = new User();
        user.setPoints(10);

        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setUser(user);

        Payment originalPayment = new Payment();
        originalPayment.setId(101L);
        originalPayment.setReservation(reservation);
        originalPayment.setAmount(new BigDecimal("200000"));
        originalPayment.setPaymentMethod("MOMO");
        originalPayment.setStatus("SUCCESS");

        when(reservationRepository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(reservation));
        when(paymentRepository.findByReservationId(42L))
                .thenReturn(java.util.List.of(originalPayment));
        when(paymentRepository.findByTransactionId("REFUND-101"))
                .thenReturn(Optional.of(new Payment()));

        paymentService.refundSuccessfulPayments(42L);

        assertEquals(10, user.getPoints());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(userRepository, never()).save(any(User.class));
    }

}
