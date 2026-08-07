package com.hotel.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.domain.payment.PaymentProvider;
import com.hotel.entities.Hotel;
import com.hotel.entities.Payment;
import com.hotel.entities.PaymentSession;
import com.hotel.entities.RefundProviderAttempt;
import com.hotel.entities.RefundRequest;
import com.hotel.entities.Reservation;
import com.hotel.repositories.PaymentSessionRepository;
import com.hotel.repositories.RefundProviderAttemptRepository;
import com.hotel.services.payment.MomoPaymentGateway;
import com.hotel.services.payment.ProviderOperationStatus;
import com.hotel.services.payment.ProviderRefundCommand;
import com.hotel.services.payment.ProviderRefundResult;
import com.hotel.services.payment.ProviderTransactionQueryResult;
import com.hotel.services.payment.ZaloPayPaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentProviderRecoveryServiceTest {

    @Mock
    private PaymentSessionRepository sessionRepository;
    @Mock
    private RefundProviderAttemptRepository attemptRepository;
    @Mock
    private PaymentSessionService paymentSessionService;
    @Mock
    private RefundService refundService;
    @Mock
    private MomoPaymentGateway momoGateway;
    @Mock
    private ZaloPayPaymentGateway zaloPayGateway;

    private PaymentProviderRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new PaymentProviderRecoveryService(
                sessionRepository,
                attemptRepository,
                paymentSessionService,
                refundService,
                momoGateway,
                zaloPayGateway,
                new ObjectMapper(),
                true,
                60_000,
                Clock.fixed(Instant.parse("2026-07-30T02:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void recoverPayments_UsesProviderQueryAsAuthoritativeCallbackRecovery() {
        PaymentSession session = paymentSession("MOMO");
        when(momoGateway.isConfigured()).thenReturn(true);
        when(sessionRepository.findTop50ByStatusInAndProviderInAndCreatedAtBeforeOrderByCreatedAtAsc(
                any(), any(), any())).thenReturn(List.of(session));
        when(momoGateway.queryTransaction("MOMO-order-1", "session-1-query"))
                .thenReturn(new ProviderTransactionQueryResult(
                        PaymentProvider.MOMO,
                        ProviderOperationStatus.SUCCEEDED,
                        "MOMO-order-1",
                        "4088878653",
                        new BigDecimal("350000"),
                        "0",
                        "Successful."));

        service.recoverProviderState();

        ArgumentCaptor<com.hotel.services.payment.ProviderCallbackData> callbackCaptor =
                ArgumentCaptor.forClass(com.hotel.services.payment.ProviderCallbackData.class);
        verify(paymentSessionService).processProviderCallback(callbackCaptor.capture());
        assertThat(callbackCaptor.getValue().providerReference()).isEqualTo("MOMO-order-1");
        assertThat(callbackCaptor.getValue().amount()).isEqualByComparingTo("350000");
    }

    @Test
    void recoverPayments_FailedQueryUsesPersistedAmountInsteadOfProviderZeroAmount() {
        PaymentSession session = paymentSession("MOMO");
        when(momoGateway.isConfigured()).thenReturn(true);
        when(sessionRepository.findTop50ByStatusInAndProviderInAndCreatedAtBeforeOrderByCreatedAtAsc(
                any(), any(), any())).thenReturn(List.of(session));
        when(momoGateway.queryTransaction("MOMO-order-1", "session-1-query"))
                .thenReturn(new ProviderTransactionQueryResult(
                        PaymentProvider.MOMO,
                        ProviderOperationStatus.FAILED,
                        "MOMO-order-1",
                        "0",
                        BigDecimal.ZERO,
                        "1006",
                        "Denied."));

        service.recoverProviderState();

        ArgumentCaptor<com.hotel.services.payment.ProviderCallbackData> callbackCaptor =
                ArgumentCaptor.forClass(com.hotel.services.payment.ProviderCallbackData.class);
        verify(paymentSessionService).processProviderCallback(callbackCaptor.capture());
        assertThat(callbackCaptor.getValue().successful()).isFalse();
        assertThat(callbackCaptor.getValue().amount()).isEqualByComparingTo("350000");
    }

    @Test
    void recoverRequestedRefund_UsesOnlyPersistedAmountAndProviderTransaction() {
        RefundProviderAttempt attempt = refundAttempt("REQUESTED", "MOMO");
        when(momoGateway.isConfigured()).thenReturn(true);
        when(attemptRepository.findTop50ByStatusInOrderByRequestedAtAsc(any()))
                .thenReturn(List.of(attempt));
        when(momoGateway.refundReference("refund-public-id"))
                .thenReturn("MOMO-R-refund-public-id");
        when(momoGateway.requestRefund(any())).thenReturn(new ProviderRefundResult(
                PaymentProvider.MOMO,
                ProviderOperationStatus.SUCCEEDED,
                "MOMO-R-refund-public-id",
                "90001",
                new BigDecimal("250000"),
                "0",
                "Successful."));

        service.recoverProviderState();

        ArgumentCaptor<ProviderRefundCommand> commandCaptor = ArgumentCaptor.forClass(ProviderRefundCommand.class);
        verify(momoGateway).requestRefund(commandCaptor.capture());
        assertThat(commandCaptor.getValue().amount()).isEqualByComparingTo("250000");
        assertThat(commandCaptor.getValue().originalProviderTransactionId()).isEqualTo("4088878653");
        verify(refundService).markProviderPending(301L, 1, "MOMO-R-refund-public-id", null, null);
        verify(refundService).markProviderSucceeded(
                eq(301L),
                eq(1),
                eq("MOMO-R-refund-public-id"),
                eq("0"),
                org.mockito.ArgumentMatchers.contains("90001"));
    }

    @Test
    void recoverRequestedRefund_OnTimeoutLeavesAttemptPendingForSafeQueryRetry() {
        RefundProviderAttempt attempt = refundAttempt("REQUESTED", "MOMO");
        when(momoGateway.isConfigured()).thenReturn(true);
        when(attemptRepository.findTop50ByStatusInOrderByRequestedAtAsc(any()))
                .thenReturn(List.of(attempt));
        when(momoGateway.refundReference("refund-public-id"))
                .thenReturn("MOMO-R-refund-public-id");
        when(momoGateway.requestRefund(any())).thenThrow(new ResourceAccessException("timeout"));

        service.recoverProviderState();

        verify(refundService).markProviderPending(301L, 1, "MOMO-R-refund-public-id", null, null);
        verify(refundService).markProviderPending(
                301L,
                1,
                "MOMO-R-refund-public-id",
                "NETWORK_RETRY",
                "Provider request outcome is unknown; query will retry.");
        verify(refundService, never()).markProviderFailed(any(), any(), any(), any(), any());
    }

    private PaymentSession paymentSession(String provider) {
        PaymentSession session = new PaymentSession();
        session.setPublicId("session-1");
        session.setProvider(provider);
        session.setProviderReference("MOMO-order-1");
        session.setExpectedAmount(new BigDecimal("350000"));
        session.setStatus("PENDING");
        session.setCreatedAt(LocalDateTime.of(2026, 7, 30, 8, 0));
        return session;
    }

    private RefundProviderAttempt refundAttempt(String status, String provider) {
        Hotel hotel = new Hotel();
        hotel.setId(3L);
        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setHotel(hotel);
        Payment payment = new Payment();
        payment.setId(101L);
        payment.setReservation(reservation);
        payment.setAmount(new BigDecimal("250000"));
        payment.setPaymentMethod(provider);
        payment.setStatus("SUCCEEDED");
        payment.setTransactionId("4088878653");

        RefundRequest request = new RefundRequest();
        request.setId(301L);
        request.setPublicId("refund-public-id");
        request.setReservation(reservation);
        request.setOriginalPayment(payment);
        request.setHotel(hotel);
        request.setRequestedAmount(new BigDecimal("250000"));
        request.setProvider(provider);
        request.setReason("RESERVATION_CANCELLED");
        request.setRequestedAt(LocalDateTime.of(2026, 7, 30, 8, 30));
        request.setStatus(status);

        RefundProviderAttempt attempt = new RefundProviderAttempt();
        attempt.setId(401L);
        attempt.setRefundRequest(request);
        attempt.setHotel(hotel);
        attempt.setProvider(provider);
        attempt.setAttemptNumber(1);
        attempt.setIdempotencyKey("REFUND-refund-public-id-1");
        attempt.setRequestedAmount(new BigDecimal("250000"));
        attempt.setRequestedAt(LocalDateTime.of(2026, 7, 30, 8, 30));
        attempt.setStatus(status);
        return attempt;
    }
}
