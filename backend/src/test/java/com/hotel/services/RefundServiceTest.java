package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.Payment;
import com.hotel.entities.RefundProviderAttempt;
import com.hotel.entities.RefundRequest;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.repositories.PaymentRepository;
import com.hotel.repositories.RefundProviderAttemptRepository;
import com.hotel.repositories.RefundRequestRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T02:00:00Z");

    @Mock
    private RefundRequestRepository requestRepository;
    @Mock
    private RefundProviderAttemptRepository attemptRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;

    private RefundService service;
    private Reservation reservation;
    private Payment charge;

    @BeforeEach
    void setUp() {
        service = new RefundService(
                requestRepository,
                attemptRepository,
                reservationRepository,
                paymentRepository,
                userRepository,
                notificationService,
                Clock.fixed(NOW, ZoneOffset.UTC));

        User user = new User();
        user.setId(7L);
        user.setUsername("refund-user");
        user.setPoints(10);
        Hotel hotel = new Hotel();
        hotel.setId(3L);

        reservation = new Reservation();
        reservation.setId(42L);
        reservation.setUser(user);
        reservation.setHotel(hotel);

        charge = new Payment();
        charge.setId(101L);
        charge.setReservation(reservation);
        charge.setAmount(new BigDecimal("250000"));
        charge.setPaymentMethod("MOMO");
        charge.setStatus("SUCCEEDED");
    }

    @Test
    void requestRefund_CreatesRequestAndAttemptWithoutPrematureFinancialMutation() {
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));
        when(paymentRepository.findByReservationId(42L)).thenReturn(java.util.List.of(charge));
        when(requestRepository.findByOriginalPaymentId(101L))
                .thenReturn(Optional.empty());
        when(requestRepository.save(any(RefundRequest.class))).thenAnswer(invocation -> {
            RefundRequest request = invocation.getArgument(0);
            request.setId(301L);
            return request;
        });

        RefundRequest request = service.requestRefundsForSuccessfulPayments(
                42L,
                "RESERVATION_CANCELLED").getFirst();

        assertEquals("REQUESTED", request.getStatus());
        assertEquals(new BigDecimal("250000"), request.getRequestedAmount());
        assertEquals(10, reservation.getUser().getPoints());
        assertNotNull(request.getRequestNotifiedAt());
        verify(attemptRepository).save(any(RefundProviderAttempt.class));
        verify(notificationService).sendUserNotificationOnce(
                eq("REFUND:" + request.getPublicId() + ":REQUESTED"),
                eq("refund-user"),
                eq(7L),
                eq("PAYMENT"),
                any(),
                any());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void providerSuccess_CreatesLedgerReversesPointsAndNotifiesExactlyOnceOnReplay() {
        RefundRequest request = request("PENDING_PROVIDER");
        RefundProviderAttempt attempt = attempt(request, "PENDING_PROVIDER");
        when(requestRepository.findByIdForUpdate(301L)).thenReturn(Optional.of(request));
        when(attemptRepository.findForUpdate(301L, 1)).thenReturn(Optional.of(attempt));
        when(paymentRepository.findByTransactionId("REFUND-" + request.getPublicId()))
                .thenReturn(Optional.empty());
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(reservation.getUser()));
        when(requestRepository.save(any(RefundRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.markProviderSucceeded(301L, 1, "MOMO-REFUND-1", "0");
        service.markProviderSucceeded(301L, 1, "MOMO-REFUND-1", "0");

        assertEquals("SUCCEEDED", request.getStatus());
        assertEquals(8, reservation.getUser().getPoints());
        assertNotNull(request.getPointsReversedAt());
        assertNotNull(request.getTerminalNotifiedAt());
        ArgumentCaptor<Payment> ledgerCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(ledgerCaptor.capture());
        assertEquals(new BigDecimal("-250000"), ledgerCaptor.getValue().getAmount());
        verify(userRepository).save(reservation.getUser());
        verify(notificationService).sendUserNotificationOnce(
                eq("REFUND:" + request.getPublicId() + ":SUCCEEDED"),
                eq("refund-user"),
                eq(7L),
                eq("PAYMENT"),
                any(),
                any());
        verify(attemptRepository, times(1)).findForUpdate(301L, 1);
    }

    @Test
    void providerFailure_RecordsTerminalStateWithoutLedgerOrPointMutation() {
        RefundRequest request = request("PENDING_PROVIDER");
        RefundProviderAttempt attempt = attempt(request, "PENDING_PROVIDER");
        when(requestRepository.findByIdForUpdate(301L)).thenReturn(Optional.of(request));
        when(attemptRepository.findForUpdate(301L, 1)).thenReturn(Optional.of(attempt));
        when(requestRepository.save(any(RefundRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.markProviderFailed(301L, 1, "MOMO-REFUND-1", "PROVIDER_REJECTED", "1006");

        assertEquals("FAILED", request.getStatus());
        assertEquals("PROVIDER_REJECTED", request.getFailureCode());
        assertEquals(10, reservation.getUser().getPoints());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(userRepository, never()).save(any(User.class));
        verify(notificationService).sendUserNotificationOnce(
                eq("REFUND:" + request.getPublicId() + ":FAILED"),
                eq("refund-user"),
                eq(7L),
                eq("PAYMENT"),
                any(),
                any());
    }

    @Test
    void succeededRefund_RejectsBackwardProviderFailure() {
        RefundRequest request = request("SUCCEEDED");
        when(requestRepository.findByIdForUpdate(301L)).thenReturn(Optional.of(request));

        assertThrows(IllegalStateException.class, () -> service.markProviderFailed(
                301L,
                1,
                "MOMO-REFUND-1",
                "LATE_FAILURE",
                "1006"));

        verify(attemptRepository, never()).findForUpdate(any(), any());
    }

    private RefundRequest request(String status) {
        RefundRequest request = new RefundRequest();
        request.setId(301L);
        request.setPublicId("refund-public-id");
        request.setReservation(reservation);
        request.setOriginalPayment(charge);
        request.setHotel(reservation.getHotel());
        request.setRequestedAmount(charge.getAmount());
        request.setCurrency("VND");
        request.setProvider("MOMO");
        request.setStatus(status);
        request.setIdempotencyKey("CANCEL-42-PAYMENT-101");
        return request;
    }

    private RefundProviderAttempt attempt(RefundRequest request, String status) {
        RefundProviderAttempt attempt = new RefundProviderAttempt();
        attempt.setId(401L);
        attempt.setRefundRequest(request);
        attempt.setHotel(reservation.getHotel());
        attempt.setProvider("MOMO");
        attempt.setAttemptNumber(1);
        attempt.setIdempotencyKey("REFUND-refund-public-id-1");
        attempt.setRequestedAmount(charge.getAmount());
        attempt.setStatus(status);
        return attempt;
    }
}
