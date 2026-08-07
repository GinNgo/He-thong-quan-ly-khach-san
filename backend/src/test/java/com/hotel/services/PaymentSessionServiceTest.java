package com.hotel.services;

import com.hotel.domain.payment.PaymentCompletionResult;
import com.hotel.dtos.PaymentSessionResponse;
import com.hotel.entities.Hotel;
import com.hotel.entities.PaymentSession;
import com.hotel.entities.Reservation;
import com.hotel.entities.ReservationHold;
import com.hotel.entities.User;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.PaymentSessionRepository;
import com.hotel.repositories.ReservationHoldRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.services.payment.DemoPaymentTokenService;
import com.hotel.services.payment.MomoPaymentGateway;
import com.hotel.services.payment.ProviderCallbackData;
import com.hotel.services.payment.ProviderCallbackOutcome;
import com.hotel.services.payment.VerifiedDemoToken;
import com.hotel.services.payment.VnpayPaymentGateway;
import com.hotel.services.payment.VnpayCallbackData;
import com.hotel.services.payment.VnpayIpnResponse;
import com.hotel.services.payment.ZaloPayPaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-29T04:00:00Z");

    @Mock
    private PaymentSessionRepository sessionRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ReservationHoldRepository holdRepository;
    @Mock
    private PropertyAccessService propertyAccessService;
    @Mock
    private VnpayPaymentGateway vnpayGateway;
    @Mock
    private MomoPaymentGateway momoGateway;
    @Mock
    private ZaloPayPaymentGateway zaloPayGateway;
    @Mock
    private DemoPaymentTokenService demoTokenService;
    @Mock
    private PaymentService paymentService;

    private PaymentSessionService service;

    @BeforeEach
    void setUp() {
        service = new PaymentSessionService(
                sessionRepository,
                reservationRepository,
                holdRepository,
                propertyAccessService,
                vnpayGateway,
                momoGateway,
                zaloPayGateway,
                demoTokenService,
                paymentService,
                true,
                "http://localhost:4200/payment-simulator",
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createSession_BindsServerOwnedReservationAmountAndOpaqueSimulatorToken() {
        Reservation reservation = pendingReservation(42L, 7L, "350000");
        ReservationHold hold = activeHold(reservation, LocalDateTime.ofInstant(NOW.plusSeconds(600), ZoneOffset.UTC));

        when(propertyAccessService.currentUser()).thenReturn(reservation.getUser());
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));
        when(sessionRepository.findByOwnerIdAndIdempotencyKeyForUpdate(7L, "idem-12345678"))
                .thenReturn(Optional.empty());
        when(sessionRepository.findActiveByReservationIdForUpdate(42L)).thenReturn(Optional.empty());
        when(holdRepository.findActiveByReservationIdForUpdate(42L)).thenReturn(Optional.of(hold));
        when(sessionRepository.save(any(PaymentSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(demoTokenService.issue(any(PaymentSession.class))).thenReturn("signed-token");

        PaymentSessionResponse response = service.createSession(
                42L,
                "MOMO",
                "idem-12345678",
                "127.0.0.1");

        assertEquals(42L, response.getReservationId());
        assertEquals(new BigDecimal("350000"), response.getAmount());
        assertEquals("MOMO", response.getProvider());
        assertEquals("SIMULATOR", response.getMode());
        assertEquals("http://localhost:4200/payment-simulator?token=signed-token", response.getUrl());
    }

    @Test
    void createSession_ForAnotherCustomersReservation_IsRejected() {
        Reservation reservation = pendingReservation(42L, 7L, "350000");
        User attacker = new User();
        attacker.setId(8L);

        when(propertyAccessService.currentUser()).thenReturn(attacker);
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));

        assertThrows(ResourceNotFoundException.class, () -> service.createSession(
                42L,
                "MOMO",
                "idem-12345678",
                "127.0.0.1"));

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void createSession_ReplayReturnsOriginalSessionButPayloadReuseIsRejected() {
        Reservation reservation = pendingReservation(42L, 7L, "350000");
        PaymentSession existing = pendingSession(reservation, "MOMO", "idem-12345678");
        existing.setCheckoutUrl("http://localhost:4200/payment-simulator?token=signed-token");

        when(propertyAccessService.currentUser()).thenReturn(reservation.getUser());
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));
        when(sessionRepository.findByOwnerIdAndIdempotencyKeyForUpdate(7L, "idem-12345678"))
                .thenReturn(Optional.of(existing));
        PaymentSessionResponse replay = service.createSession(
                42L,
                "MOMO",
                "idem-12345678",
                "127.0.0.1");

        assertEquals(existing.getPublicId(), replay.getSessionId());
        verify(sessionRepository, never()).save(any());

        assertThrows(IllegalArgumentException.class, () -> service.createSession(
                42L,
                "ZALOPAY",
                "idem-12345678",
                "127.0.0.1"));
    }

    @Test
    void confirmDemoPayment_RejectsExpiredTokenWithoutFinancialMutation() {
        when(demoTokenService.verify("expired-token"))
                .thenReturn(new VerifiedDemoToken("session-1", NOW.minusSeconds(1)));

        assertThrows(IllegalArgumentException.class, () -> service.confirmDemoPayment("expired-token"));

        verify(reservationRepository, never()).findByIdForUpdate(any());
        verify(paymentService, never()).handleSuccessfulPayment(any(), any(), any());
    }

    @Test
    void confirmDemoPayment_ReplayIsIdempotent() {
        Reservation reservation = pendingReservation(42L, 7L, "350000");
        PaymentSession session = pendingSession(reservation, "MOMO", "idem-12345678");
        session.setStatus("SUCCEEDED");

        when(demoTokenService.verify("signed-token"))
                .thenReturn(new VerifiedDemoToken(session.getPublicId(), NOW.plusSeconds(600)));
        when(sessionRepository.findReservationIdByPublicId(session.getPublicId()))
                .thenReturn(Optional.of(42L));
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));
        when(sessionRepository.findByPublicIdForUpdate(session.getPublicId())).thenReturn(Optional.of(session));
        when(propertyAccessService.currentUser()).thenReturn(reservation.getUser());

        assertEquals(PaymentCompletionResult.IDEMPOTENT, service.confirmDemoPayment("signed-token"));
        verify(paymentService, never()).handleSuccessfulPayment(any(), any(), any());
    }

    @Test
    void processVnpayCallback_RejectsAmountMismatchBeforeFinancialMutation() {
        Reservation reservation = pendingReservation(42L, 7L, "350000");
        PaymentSession session = pendingSession(reservation, "VNPAY", "idem-vnpay-1");
        session.setProviderReference("VNPAY-reference");

        when(sessionRepository.findReservationIdByProviderReference("VNPAY-reference"))
                .thenReturn(Optional.of(42L));
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));
        when(sessionRepository.findByProviderReferenceForUpdate("VNPAY-reference"))
                .thenReturn(Optional.of(session));

        VnpayIpnResponse response = service.processVnpayCallback(new VnpayCallbackData(
                "VNPAY-reference",
                "14927984",
                new BigDecimal("1"),
                "00",
                "00",
                true));

        assertEquals("04", response.responseCode());
        verify(paymentService, never()).handleSuccessfulPayment(any(), any(), any(), any());
    }

    @Test
    void processVnpayCallback_PersistsLateSuccessAsReconciliation() {
        Reservation reservation = pendingReservation(42L, 7L, "350000");
        reservation.setStatus("EXPIRED");
        PaymentSession session = pendingSession(reservation, "VNPAY", "idem-vnpay-1");
        session.setProviderReference("VNPAY-reference");
        session.setStatus("EXPIRED");

        when(sessionRepository.findReservationIdByProviderReference("VNPAY-reference"))
                .thenReturn(Optional.of(42L));
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));
        when(sessionRepository.findByProviderReferenceForUpdate("VNPAY-reference"))
                .thenReturn(Optional.of(session));
        when(paymentService.handleSuccessfulPayment(42L, "VNPAY", "14927984", com.hotel.domain.lifecycle.PaymentStatus.EXPIRED))
                .thenReturn(PaymentCompletionResult.RECONCILIATION_REQUIRED);

        VnpayIpnResponse response = service.processVnpayCallback(new VnpayCallbackData(
                "VNPAY-reference",
                "14927984",
                new BigDecimal("350000"),
                "00",
                "00",
                true));

        assertEquals("00", response.responseCode());
        assertEquals("SUCCEEDED", session.getStatus());
        assertEquals(true, session.isReconciliationRequired());
        verify(sessionRepository).save(session);
    }

    @Test
    void processProviderCallback_UsesTheSameAmountAndReplayPolicyForZaloPay() {
        Reservation reservation = pendingReservation(42L, 7L, "350000");
        PaymentSession session = pendingSession(reservation, "ZALOPAY", "idem-zalopay-1");
        session.setProviderReference("260729_order1");

        when(sessionRepository.findReservationIdByProviderReference("260729_order1"))
                .thenReturn(Optional.of(42L));
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));
        when(sessionRepository.findByProviderReferenceForUpdate("260729_order1"))
                .thenReturn(Optional.of(session));
        when(paymentService.handleSuccessfulPayment(42L, "ZALOPAY", "230407000006575", com.hotel.domain.lifecycle.PaymentStatus.PENDING))
                .thenReturn(PaymentCompletionResult.APPLIED);

        ProviderCallbackOutcome outcome = service.processProviderCallback(new ProviderCallbackData(
                com.hotel.domain.payment.PaymentProvider.ZALOPAY,
                "260729_order1",
                "230407000006575",
                new BigDecimal("350000"),
                true,
                null));

        assertEquals(ProviderCallbackOutcome.CONFIRMED, outcome);
        assertEquals("SUCCEEDED", session.getStatus());
        assertEquals("230407000006575", session.getProviderTransactionId());
        verify(sessionRepository).save(session);
    }

    private Reservation pendingReservation(Long reservationId, Long userId, String amount) {
        User user = new User();
        user.setId(userId);
        Hotel hotel = new Hotel();
        hotel.setId(3L);

        Reservation reservation = new Reservation();
        reservation.setId(reservationId);
        reservation.setUser(user);
        reservation.setHotel(hotel);
        reservation.setTotalAmount(new BigDecimal(amount));
        reservation.setStatus("PENDING_PAYMENT");
        return reservation;
    }

    private ReservationHold activeHold(Reservation reservation, LocalDateTime expiresAt) {
        ReservationHold hold = new ReservationHold();
        hold.setReservation(reservation);
        hold.setHotel(reservation.getHotel());
        hold.setStatus("ACTIVE");
        hold.setExpiresAt(expiresAt);
        return hold;
    }

    private PaymentSession pendingSession(Reservation reservation, String provider, String idempotencyKey) {
        PaymentSession session = new PaymentSession();
        session.setPublicId("session-1");
        session.setReservation(reservation);
        session.setHotel(reservation.getHotel());
        session.setOwner(reservation.getUser());
        session.setProvider(provider);
        session.setMethod(provider);
        session.setExpectedAmount(reservation.getTotalAmount());
        session.setCurrency("VND");
        session.setIdempotencyKey(idempotencyKey);
        session.setProviderReference("MOMO-session-1");
        session.setStatus("PENDING");
        session.setExpiresAt(LocalDateTime.ofInstant(NOW.plusSeconds(600), ZoneOffset.UTC));
        return session;
    }
}
