package com.hotel.propertycommerce.refund;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.FinancialStates.RefundState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
import com.hotel.propertycommerce.payment.PropertyFinancialTransactionRepository;
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyRefundServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-02T02:00:00Z");

    @Mock private PropertyFinancialTransactionRepository transactionRepository;
    @Mock private PropertyRefundRequestRepository requestRepository;
    @Mock private PropertyAccessService propertyAccessService;
    @Mock private FinancialAuditService auditService;

    private User actor;
    private PropertyFinancialTransaction original;
    private PropertyRefundService service;

    @BeforeEach
    void setUp() {
        Hotel hotel = new Hotel();
        hotel.setId(10L);
        actor = new User();
        actor.setId(7L);
        Reservation reservation = new Reservation();
        reservation.setId(20L);
        reservation.setHotel(hotel);
        reservation.setUser(actor);
        original = PropertyFinancialTransaction.record(
                "property-transaction", hotel, reservation, null, null, null,
                PropertyFinancialTransaction.TransactionType.BOOKING_DEPOSIT,
                PropertyFinancialTransaction.Direction.DEBIT, VndMoney.of(1_000_000),
                "MOMO", "SIMULATOR", PaymentEnvironment.SIMULATOR, "provider-payment",
                "payment-effect", "PROVIDER", null, "Booking deposit",
                LocalDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC));
        ReflectionTestUtils.setField(original, "id", 100L);
        service = new PropertyRefundService(
                transactionRepository, requestRepository, propertyAccessService, auditService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void reservesPartialAmountAndRejectsRequestsAboveTheLockedBalance() {
        arrangeEmptyBalance();
        when(requestRepository.saveAndFlush(any(PropertyRefundRequest.class))).thenAnswer(invocation -> {
            PropertyRefundRequest request = invocation.getArgument(0);
            ReflectionTestUtils.setField(request, "id", 200L);
            return request;
        });

        PropertyRefundService.RefundResult result = service.request(new PropertyRefundService.RequestCommand(
                original.getPublicId(), BigDecimal.valueOf(300_000), "Guest cancellation",
                "refund-request-1", "correlation-1"));

        assertEquals(RefundState.REQUESTED, result.status());
        assertEquals(0, result.requestedAmount().compareTo(BigDecimal.valueOf(300_000)));
        assertEquals(0, result.remainingRefundableAmount().compareTo(BigDecimal.valueOf(700_000)));

        FinancialException exception = assertThrows(FinancialException.class, () -> service.request(
                new PropertyRefundService.RequestCommand(original.getPublicId(), BigDecimal.valueOf(1_000_001),
                        "Too much", "refund-request-2", "correlation-2")));
        assertEquals(FinancialErrorCode.REFUND_EXCEEDS_BALANCE, exception.code());
    }

    @Test
    void cancellationCreatesARequestForTheRemainingCanonicalPaymentBalance() {
        arrangeEmptyBalance();
        when(transactionRepository.findByReservationIdOrderByOccurredAtAsc(20L)).thenReturn(List.of(original));
        when(requestRepository.saveAndFlush(any(PropertyRefundRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<PropertyRefundService.RefundResult> results = service.requestCancellationRefunds(
                20L, "Guest cancelled reservation", "reservation-cancellation:20");

        assertEquals(1, results.size());
        assertEquals(0, results.getFirst().requestedAmount().compareTo(BigDecimal.valueOf(1_000_000)));
        assertEquals(RefundState.REQUESTED, results.getFirst().status());
    }

    @Test
    void successfulProviderEffectCreatesOneCreditLedgerAndEquivalentReplayReturnsIt() {
        PropertyRefundRequest refund = PropertyRefundRequest.request(
                original.getHotel(), original, VndMoney.of(400_000), "Partial refund", actor,
                "refund-request", "request-hash", LocalDateTime.ofInstant(NOW.minusSeconds(30), ZoneOffset.UTC));
        ReflectionTestUtils.setField(refund, "id", 200L);
        refund.approve(actor);
        when(requestRepository.findByPublicIdForUpdate(refund.getPublicId())).thenReturn(Optional.of(refund));
        when(transactionRepository.findByPublicIdForUpdate(original.getPublicId())).thenReturn(Optional.of(original));
        when(transactionRepository.findByIdempotencyIdentity("PROPERTY-REFUND:" + refund.getPublicId()))
                .thenReturn(Optional.empty());
        when(transactionRepository.findByOriginalTransactionIdOrderByOccurredAtAsc(100L)).thenReturn(List.of());
        when(transactionRepository.saveAndFlush(any(PropertyFinancialTransaction.class))).thenAnswer(invocation -> {
            PropertyFinancialTransaction effect = invocation.getArgument(0);
            ReflectionTestUtils.setField(effect, "id", 101L);
            return effect;
        });
        when(requestRepository.saveAndFlush(any(PropertyRefundRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PropertyRefundService.RefundResult first = service.completeSucceeded(
                refund.getPublicId(), "provider-refund", "correlation-success");

        assertEquals(RefundState.SUCCEEDED, first.status());
        ArgumentCaptor<PropertyFinancialTransaction> effectCaptor = ArgumentCaptor.forClass(PropertyFinancialTransaction.class);
        org.mockito.Mockito.verify(transactionRepository).saveAndFlush(effectCaptor.capture());
        assertEquals(PropertyFinancialTransaction.TransactionType.REFUND, effectCaptor.getValue().getTransactionType());
        assertEquals(PropertyFinancialTransaction.Direction.CREDIT, effectCaptor.getValue().getDirection());

        PropertyFinancialTransaction effect = effectCaptor.getValue();
        when(transactionRepository.findByIdempotencyIdentity("PROPERTY-REFUND:" + refund.getPublicId()))
                .thenReturn(Optional.of(effect));
        when(transactionRepository.findByOriginalTransactionIdOrderByOccurredAtAsc(100L)).thenReturn(List.of(effect));
        PropertyRefundService.RefundResult replay = service.completeSucceeded(
                refund.getPublicId(), "provider-refund", "correlation-replay");

        assertTrue(replay.replayed());
        assertEquals(0, replay.remainingRefundableAmount().compareTo(BigDecimal.valueOf(600_000)));
    }

    @Test
    void aggregateRejectsSuccessBeforeProviderApproval() {
        PropertyRefundRequest refund = PropertyRefundRequest.request(
                original.getHotel(), original, VndMoney.of(100_000), "Invalid transition", actor,
                "refund-transition", "transition-hash", LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThrows(IllegalStateException.class, () -> refund.markSucceeded(
                VndMoney.of(100_000), LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)));
    }

    @Test
    void singleSourceRefundSelectsTheOnlyPaymentThatCanCoverTheAmount() {
        PropertyFinancialTransaction smaller = payment("property-transaction-small", 101L, 250_000);
        arrangeSingleSourceBalances(List.of(original, smaller));
        when(requestRepository.findByHotelIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(requestRepository.saveAndFlush(any(PropertyRefundRequest.class))).thenAnswer(invocation -> {
            PropertyRefundRequest request = invocation.getArgument(0);
            ReflectionTestUtils.setField(request, "id", 201L);
            return request;
        });

        PropertyRefundService.RefundResult result = service.requestSingleSource(
                new PropertyRefundService.SingleSourceCommand(
                        20L,
                        BigDecimal.valueOf(700_000),
                        "Reservation amendment decrease",
                        "amendment-refund-1",
                        "correlation-amendment"));

        assertEquals(original.getPublicId(), result.originalTransactionPublicId());
        assertEquals(0, BigDecimal.valueOf(700_000).compareTo(result.requestedAmount()));
    }

    @Test
    void ambiguousOrSplitRefundSourceFailsWithoutInventingPolicy() {
        PropertyFinancialTransaction secondCovering = payment("property-transaction-second", 101L, 900_000);
        arrangeSingleSourceBalances(List.of(original, secondCovering));

        FinancialException ambiguous = assertThrows(FinancialException.class,
                () -> service.requestSingleSource(new PropertyRefundService.SingleSourceCommand(
                        20L,
                        BigDecimal.valueOf(500_000),
                        "Ambiguous source",
                        "amendment-refund-ambiguous",
                        "correlation-ambiguous")));
        assertEquals(FinancialErrorCode.POLICY_NOT_CONFIGURED, ambiguous.code());

        PropertyFinancialTransaction firstPartial = payment("property-transaction-first-partial", 102L, 400_000);
        PropertyFinancialTransaction secondPartial = payment("property-transaction-second-partial", 103L, 400_000);
        arrangeSingleSourceBalances(List.of(firstPartial, secondPartial));

        FinancialException split = assertThrows(FinancialException.class,
                () -> service.requestSingleSource(new PropertyRefundService.SingleSourceCommand(
                        20L,
                        BigDecimal.valueOf(700_000),
                        "Split source",
                        "amendment-refund-split",
                        "correlation-split")));
        assertEquals(FinancialErrorCode.POLICY_NOT_CONFIGURED, split.code());
    }

    private void arrangeEmptyBalance() {
        when(propertyAccessService.currentUser()).thenReturn(actor);
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(10L));
        when(transactionRepository.findByPublicIdForUpdate(original.getPublicId())).thenReturn(Optional.of(original));
        when(requestRepository.findByHotelIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(requestRepository.findByOriginalTransactionIdOrderByRequestedAtAsc(100L)).thenReturn(List.of());
        when(transactionRepository.findByOriginalTransactionIdOrderByOccurredAtAsc(100L)).thenReturn(List.of());
    }

    private void arrangeSingleSourceBalances(List<PropertyFinancialTransaction> sources) {
        when(propertyAccessService.currentUser()).thenReturn(actor);
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(10L));
        when(transactionRepository.findBookingDebitsByReservationIdForUpdate(
                20L,
                PropertyFinancialTransaction.Direction.DEBIT,
                Set.of(PropertyFinancialTransaction.TransactionType.BOOKING_DEPOSIT,
                        PropertyFinancialTransaction.TransactionType.ROOM_PAYMENT)))
                .thenReturn(sources);
        for (PropertyFinancialTransaction source : sources) {
            when(requestRepository.findByOriginalTransactionIdOrderByRequestedAtAsc(source.getId()))
                    .thenReturn(List.of());
            when(transactionRepository.findByOriginalTransactionIdOrderByOccurredAtAsc(source.getId()))
                    .thenReturn(List.of());
        }
    }

    private PropertyFinancialTransaction payment(String publicId, Long id, long amount) {
        PropertyFinancialTransaction payment = PropertyFinancialTransaction.record(
                publicId,
                original.getHotel(),
                original.getReservation(),
                null,
                null,
                null,
                PropertyFinancialTransaction.TransactionType.ROOM_PAYMENT,
                PropertyFinancialTransaction.Direction.DEBIT,
                VndMoney.of(amount),
                "MOMO",
                "SIMULATOR",
                PaymentEnvironment.SIMULATOR,
                "provider-" + publicId,
                "effect-" + publicId,
                "PROVIDER",
                null,
                "Room payment",
                LocalDateTime.ofInstant(NOW.minusSeconds(30), ZoneOffset.UTC));
        ReflectionTestUtils.setField(payment, "id", id);
        return payment;
    }
}
