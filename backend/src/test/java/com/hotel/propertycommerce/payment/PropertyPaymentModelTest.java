package com.hotel.propertycommerce.payment;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.FinancialStates.PaymentState;
import com.hotel.paymentprovider.domain.VndMoney;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyPaymentModelTest {

    @Test
    void attemptKeepsServerOwnedAmountAndUsesControlledTransitions() {
        Hotel hotel = hotel(7L);
        Reservation reservation = reservation(17L, hotel);
        PropertyPaymentAttempt attempt = attempt(hotel, reservation);

        assertEquals(0, VndMoney.of(360_000).amount().compareTo(attempt.getExpectedAmount()));
        assertEquals("VND", attempt.getCurrency());
        assertEquals(PaymentState.CREATED, attempt.getStatus());
        assertTrue(attempt.transitionTo(PaymentState.PENDING, LocalDateTime.now(), null, null));
        assertFalse(attempt.transitionTo(PaymentState.PENDING, LocalDateTime.now(), null, null));
        assertThrows(IllegalStateException.class,
                () -> attempt.transitionTo(PaymentState.REFUNDED, LocalDateTime.now(), null, null));
    }

    @Test
    void attemptRejectsFractionalOrCrossPropertyEvidence() {
        Hotel first = hotel(7L);
        Hotel second = hotel(8L);
        Reservation reservation = reservation(17L, second);

        assertThrows(IllegalArgumentException.class, () -> attempt(first, reservation));
        assertThrows(ArithmeticException.class, () -> VndMoney.of(new java.math.BigDecimal("1000.50")));
    }

    @Test
    void providerIdentitiesAreWriteOnceAndIdempotent() {
        PropertyPaymentAttempt attempt = attempt(hotel(7L), reservation(17L, hotel(7L)));

        attempt.bindProviderEventId("event-1");
        attempt.bindProviderEventId("event-1");

        assertEquals("event-1", attempt.getProviderEventId());
        assertThrows(IllegalStateException.class, () -> attempt.bindProviderEventId("event-2"));
    }

    @Test
    void ledgerRequiresPositiveVndAndMatchingOwnership() {
        Hotel first = hotel(7L);
        Hotel second = hotel(8L);
        Reservation firstReservation = reservation(17L, first);
        Reservation secondReservation = reservation(18L, second);

        PropertyFinancialTransaction transaction = transaction(first, firstReservation, "effect-1");

        assertEquals("VND", transaction.getCurrency());
        assertEquals(0, VndMoney.of(360_000).amount().compareTo(transaction.getAmount()));
        assertThrows(IllegalArgumentException.class,
                () -> transaction(first, secondReservation, "effect-2"));
        assertThrows(IllegalArgumentException.class, () -> PropertyFinancialTransaction.record(
                "txn-refund", first, firstReservation, null, null, null,
                PropertyFinancialTransaction.TransactionType.REFUND,
                PropertyFinancialTransaction.Direction.CREDIT,
                VndMoney.of(100_000), "MANUAL_TRANSFER", "BANK", PaymentEnvironment.SIMULATOR,
                null, "refund-effect", "SYSTEM", null, "Refund", LocalDateTime.now()));
    }

    @Test
    void ledgerLifecycleRejectsMutationAndDeletion() {
        PropertyFinancialTransaction transaction = transaction(
                hotel(7L), reservation(17L, hotel(7L)), "effect-1");

        assertThrows(IllegalStateException.class, transaction::rejectUpdate);
        assertThrows(IllegalStateException.class, transaction::rejectDelete);
    }

    private PropertyPaymentAttempt attempt(Hotel hotel, Reservation reservation) {
        return PropertyPaymentAttempt.create(
                "attempt-1",
                hotel,
                reservation,
                null,
                reservation.getUser(),
                PropertyPaymentAttempt.Purpose.DEPOSIT,
                "manual_transfer",
                "bank",
                PaymentEnvironment.SIMULATOR,
                VndMoney.of(360_000),
                "BOOKING-17",
                "{\"account\":\"****6789\"}",
                "idem-1",
                "request-hash",
                LocalDateTime.now().plusMinutes(15));
    }

    private PropertyFinancialTransaction transaction(Hotel hotel, Reservation reservation, String identity) {
        return PropertyFinancialTransaction.record(
                "txn-" + identity,
                hotel,
                reservation,
                null,
                null,
                null,
                PropertyFinancialTransaction.TransactionType.BOOKING_DEPOSIT,
                PropertyFinancialTransaction.Direction.DEBIT,
                VndMoney.of(360_000),
                "manual_transfer",
                "bank",
                PaymentEnvironment.SIMULATOR,
                "provider-transaction-1",
                identity,
                "system",
                null,
                "Verified deposit",
                LocalDateTime.now());
    }

    private Hotel hotel(Long id) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        return hotel;
    }

    private Reservation reservation(Long id, Hotel hotel) {
        Reservation reservation = new Reservation();
        reservation.setId(id);
        reservation.setHotel(hotel);
        return reservation;
    }
}
