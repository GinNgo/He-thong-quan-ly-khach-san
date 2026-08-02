package com.hotel.propertycommerce.booking;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.FinancialStates.BookingFinancialState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingFinancialSummaryServiceTest {

    private final BookingFinancialSummaryService service = new BookingFinancialSummaryService(null, null, null);

    @Test
    void unpaidBookingKeepsFullServerOwnedBalance() {
        Reservation reservation = reservation(hotel(7L));

        BookingFinancialSummaryService.Summary summary = service.calculate(reservation, List.of());

        assertMoney(1_200_000, summary.grossCharges());
        assertMoney(360_000, summary.depositRequired());
        assertMoney(0, summary.successfulPayments());
        assertMoney(0, summary.successfulRefunds());
        assertAmount(1_200_000, summary.remainingBalance());
        assertEquals(BookingFinancialState.UNPAID, summary.financialState());
    }

    @Test
    void multiplePaymentsCanSatisfyTheDepositWithoutMarkingBookingPaid() {
        Reservation reservation = reservation(hotel(7L));

        BookingFinancialSummaryService.Summary summary = service.calculate(reservation, List.of(
                payment(reservation, 200_000, "effect-1"),
                payment(reservation, 160_000, "effect-2")));

        assertMoney(360_000, summary.successfulPayments());
        assertAmount(840_000, summary.remainingBalance());
        assertEquals(BookingFinancialState.DEPOSIT_PAID, summary.financialState());
    }

    @Test
    void exactAndExcessPaymentsProducePaidAndOverpaidStates() {
        Reservation exactReservation = reservation(hotel(7L));
        Reservation excessReservation = reservation(hotel(8L));

        BookingFinancialSummaryService.Summary paid = service.calculate(exactReservation,
                List.of(payment(exactReservation, 1_200_000, "effect-paid")));
        BookingFinancialSummaryService.Summary overpaid = service.calculate(excessReservation,
                List.of(payment(excessReservation, 1_250_000, "effect-overpaid")));

        assertEquals(BookingFinancialState.PAID, paid.financialState());
        assertAmount(0, paid.remainingBalance());
        assertEquals(BookingFinancialState.OVERPAID, overpaid.financialState());
        assertAmount(-50_000, overpaid.remainingBalance());
    }

    @Test
    void refundsPreserveGrossPaymentsAndExposeNetBalance() {
        Reservation partialReservation = reservation(hotel(7L));
        PropertyFinancialTransaction partialPayment = payment(partialReservation, 1_200_000, "payment-partial");
        Reservation fullReservation = reservation(hotel(8L));
        PropertyFinancialTransaction fullPayment = payment(fullReservation, 1_200_000, "payment-full");

        BookingFinancialSummaryService.Summary partial = service.calculate(partialReservation, List.of(
                partialPayment,
                refund(partialReservation, partialPayment, 200_000, "refund-partial")));
        BookingFinancialSummaryService.Summary full = service.calculate(fullReservation, List.of(
                fullPayment,
                refund(fullReservation, fullPayment, 1_200_000, "refund-full")));

        assertMoney(1_200_000, partial.successfulPayments());
        assertMoney(200_000, partial.successfulRefunds());
        assertAmount(200_000, partial.remainingBalance());
        assertEquals(BookingFinancialState.PARTIALLY_REFUNDED, partial.financialState());
        assertAmount(1_200_000, full.remainingBalance());
        assertEquals(BookingFinancialState.REFUNDED, full.financialState());
    }

    @Test
    void crossPropertyLedgerEvidenceIsRejected() {
        Reservation reservation = reservation(hotel(7L));
        Reservation other = reservation(hotel(8L));

        assertThrows(IllegalArgumentException.class,
                () -> service.calculate(reservation, List.of(payment(other, 100_000, "other-property"))));
    }

    @Test
    void unverifiedLegacySettlementEvidenceDoesNotChangeAuthoritativeBalance() {
        Reservation reservation = reservation(hotel(7L));
        PropertyFinancialTransaction unverified = payment(reservation, 1_200_000, "legacy-unverified");
        setField(unverified, "legacyReconciliationRequired", true);

        BookingFinancialSummaryService.Summary summary = service.calculate(
                reservation,
                List.of(unverified));

        assertMoney(0, summary.successfulPayments());
        assertAmount(1_200_000, summary.remainingBalance());
        assertEquals(BookingFinancialState.UNPAID, summary.financialState());
    }

    private PropertyFinancialTransaction payment(Reservation reservation, long amount, String identity) {
        return PropertyFinancialTransaction.record(
                "txn-" + identity,
                reservation.getHotel(),
                reservation,
                null,
                null,
                null,
                PropertyFinancialTransaction.TransactionType.BOOKING_DEPOSIT,
                PropertyFinancialTransaction.Direction.DEBIT,
                VndMoney.of(amount),
                "MANUAL_TRANSFER",
                "BANK",
                PaymentEnvironment.SIMULATOR,
                "provider-" + identity,
                identity,
                "SYSTEM",
                null,
                "Successful payment",
                LocalDateTime.now());
    }

    private PropertyFinancialTransaction refund(
            Reservation reservation,
            PropertyFinancialTransaction original,
            long amount,
            String identity) {
        return PropertyFinancialTransaction.record(
                "txn-" + identity,
                reservation.getHotel(),
                reservation,
                null,
                null,
                original,
                PropertyFinancialTransaction.TransactionType.REFUND,
                PropertyFinancialTransaction.Direction.CREDIT,
                VndMoney.of(amount),
                original.getMethod(),
                original.getProvider(),
                original.getEnvironment(),
                "provider-" + identity,
                identity,
                "SYSTEM",
                null,
                "Successful refund",
                LocalDateTime.now());
    }

    private Reservation reservation(Hotel hotel) {
        Reservation reservation = new Reservation();
        reservation.setId(hotel.getId() + 100L);
        reservation.setHotel(hotel);
        reservation.setTotalAmount(BigDecimal.valueOf(1_200_000));
        setField(reservation, "depositBookingTotal", BigDecimal.valueOf(1_200_000));
        setField(reservation, "depositRequired", BigDecimal.valueOf(360_000));
        return reservation;
    }

    private Hotel hotel(Long id) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        return hotel;
    }

    private void assertMoney(long expected, VndMoney actual) {
        assertAmount(expected, actual.amount());
    }

    private void assertAmount(long expected, BigDecimal actual) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual));
    }

    private void setField(Object target, String field, Object value) {
        org.springframework.test.util.ReflectionTestUtils.setField(target, field, value);
    }
}
