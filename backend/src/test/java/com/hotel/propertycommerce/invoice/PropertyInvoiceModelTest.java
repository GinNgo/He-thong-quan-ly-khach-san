package com.hotel.propertycommerce.invoice;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PropertyInvoiceModelTest {

    @Test
    void finalizedInvoicePreservesExactReconciledVndSnapshot() {
        PropertyInvoice invoice = invoice(VndMoney.of(800_000), VndMoney.zero(), VndMoney.of(200_000));

        assertThat(invoice.getStatus()).isEqualTo(PropertyInvoice.Status.FINALIZED);
        assertThat(invoice.getCurrency()).isEqualTo("VND");
        assertThat(invoice.getSubtotal()).isEqualByComparingTo("1000000");
        assertThat(invoice.getTaxAmount()).isEqualByComparingTo("100000");
        assertThat(invoice.getFeeAmount()).isEqualByComparingTo("50000");
        assertThat(invoice.getDiscountAmount()).isEqualByComparingTo("150000");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("1000000");
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("800000");
        assertThat(invoice.getBalanceAmount()).isEqualByComparingTo("200000");
        assertThat(invoice.getCustomerSnapshotJson()).contains("customer@example.com");
        assertThat(invoice.getPropertySnapshotJson()).contains("Luxe Hotel");
    }

    @Test
    void invoiceRejectsBrokenTotalsRefundsAndBalanceEquations() {
        Hotel hotel = hotel(3L);
        Reservation reservation = reservation(hotel, 42L);
        User actor = user(9L);
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 6, 0);

        assertThatThrownBy(() -> PropertyInvoice.finalized(
                hotel, reservation, "INV-BAD-TOTAL", "{}", "{}",
                VndMoney.of(1_000_000), VndMoney.of(100_000), VndMoney.of(50_000),
                VndMoney.of(150_000), VndMoney.of(999_999), VndMoney.of(800_000),
                VndMoney.zero(), VndMoney.of(199_999), actor, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("total");

        assertThatThrownBy(() -> PropertyInvoice.finalized(
                hotel, reservation, "INV-BAD-REFUND", "{}", "{}",
                VndMoney.of(1_000_000), VndMoney.zero(), VndMoney.zero(), VndMoney.zero(),
                VndMoney.of(1_000_000), VndMoney.of(100_000), VndMoney.of(200_000),
                VndMoney.of(1_100_000), actor, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("refunds");

        assertThatThrownBy(() -> PropertyInvoice.finalized(
                hotel, reservation, "INV-BAD-BALANCE", "{}", "{}",
                VndMoney.of(1_000_000), VndMoney.zero(), VndMoney.zero(), VndMoney.zero(),
                VndMoney.of(1_000_000), VndMoney.of(800_000), VndMoney.zero(),
                VndMoney.of(100_000), actor, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("balance");
    }

    @Test
    void invoiceLinesSnapshotNormalAndDiscountEffects() {
        PropertyInvoice invoice = invoice(VndMoney.of(800_000), VndMoney.zero(), VndMoney.of(200_000));

        PropertyInvoiceLine room = PropertyInvoiceLine.snapshot(
                invoice,
                PropertyInvoiceLine.LineType.ROOM,
                null,
                "ROOM-STAY",
                "Room stay",
                "Two nights",
                BigDecimal.valueOf(2),
                VndMoney.of(500_000),
                VndMoney.of(50_000),
                VndMoney.of(50_000),
                VndMoney.of(1_000_000),
                LocalDateTime.of(2026, 8, 1, 14, 0),
                LocalDateTime.of(2026, 8, 3, 12, 0));
        PropertyInvoiceLine discount = PropertyInvoiceLine.snapshot(
                invoice,
                PropertyInvoiceLine.LineType.DISCOUNT,
                null,
                "GOODWILL",
                "Goodwill discount",
                null,
                BigDecimal.ONE,
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.of(150_000),
                VndMoney.of(150_000),
                null,
                null);

        assertThat(room.getQuantity()).isEqualByComparingTo("2.000");
        assertThat(room.getTotalAmount()).isEqualByComparingTo("1000000");
        assertThat(discount.getDiscountAmount()).isEqualByComparingTo("150000");
        assertThat(discount.getTotalAmount()).isEqualByComparingTo("150000");
    }

    @Test
    void invoiceLineRejectsBrokenEquationAndUsageRange() {
        PropertyInvoice invoice = invoice(VndMoney.of(800_000), VndMoney.zero(), VndMoney.of(200_000));

        assertThatThrownBy(() -> PropertyInvoiceLine.snapshot(
                invoice, PropertyInvoiceLine.LineType.SERVICE, null, "SPA", "Spa", null,
                BigDecimal.ONE, VndMoney.of(100_000), VndMoney.zero(), VndMoney.zero(),
                VndMoney.of(99_000), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("total");

        assertThatThrownBy(() -> PropertyInvoiceLine.snapshot(
                invoice, PropertyInvoiceLine.LineType.SERVICE, null, "SPA", "Spa", null,
                BigDecimal.ONE, VndMoney.of(100_000), VndMoney.zero(), VndMoney.zero(),
                VndMoney.of(100_000), LocalDateTime.of(2026, 8, 2, 10, 0),
                LocalDateTime.of(2026, 8, 2, 9, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("usage end");
    }

    @Test
    void allocationBindsOnePropertyReservationAndSuccessfulDebit() {
        PropertyInvoice invoice = invoice(VndMoney.of(800_000), VndMoney.zero(), VndMoney.of(200_000));
        PropertyFinancialTransaction transaction = payment(invoice.getHotel(), invoice.getReservation(), 800_000);

        PropertyInvoicePaymentAllocation allocation = PropertyInvoicePaymentAllocation.allocate(
                invoice,
                transaction,
                VndMoney.of(800_000));

        assertThat(allocation.getHotel()).isSameAs(invoice.getHotel());
        assertThat(allocation.getFinancialTransaction()).isSameAs(transaction);
        assertThat(allocation.getAllocatedAmount()).isEqualByComparingTo("800000");

        assertThatThrownBy(() -> PropertyInvoicePaymentAllocation.allocate(
                invoice,
                transaction,
                VndMoney.of(800_001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceed");
    }

    @Test
    void allocationRejectsCrossPropertyAndRefundTransactions() {
        PropertyInvoice invoice = invoice(VndMoney.of(800_000), VndMoney.zero(), VndMoney.of(200_000));
        Hotel otherHotel = hotel(99L);
        Reservation otherReservation = reservation(otherHotel, 77L);
        PropertyFinancialTransaction otherPayment = payment(otherHotel, otherReservation, 800_000);

        assertThatThrownBy(() -> PropertyInvoicePaymentAllocation.allocate(
                invoice,
                otherPayment,
                VndMoney.of(100_000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("property");

        PropertyFinancialTransaction original = payment(invoice.getHotel(), invoice.getReservation(), 800_000);
        PropertyFinancialTransaction refund = PropertyFinancialTransaction.record(
                "txn-refund-1",
                invoice.getHotel(),
                invoice.getReservation(),
                null,
                null,
                original,
                PropertyFinancialTransaction.TransactionType.REFUND,
                PropertyFinancialTransaction.Direction.CREDIT,
                VndMoney.of(100_000),
                "VNPAY",
                "VNPAY",
                null,
                "provider-refund-1",
                "refund-effect-1",
                "SYSTEM",
                null,
                "Partial refund",
                LocalDateTime.of(2026, 8, 1, 7, 0));
        assertThatThrownBy(() -> PropertyInvoicePaymentAllocation.allocate(
                invoice,
                refund,
                VndMoney.of(100_000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payment debits");
    }

    @Test
    void finalizedSnapshotsAndChildrenRejectMutationAndDeletion() {
        PropertyInvoice invoice = invoice(VndMoney.of(800_000), VndMoney.zero(), VndMoney.of(200_000));
        PropertyInvoiceLine line = PropertyInvoiceLine.snapshot(
                invoice, PropertyInvoiceLine.LineType.FEE, null, "FEE", "Service fee", null,
                BigDecimal.ONE, VndMoney.of(50_000), VndMoney.zero(), VndMoney.zero(),
                VndMoney.of(50_000), null, null);
        PropertyInvoicePaymentAllocation allocation = PropertyInvoicePaymentAllocation.allocate(
                invoice,
                payment(invoice.getHotel(), invoice.getReservation(), 800_000),
                VndMoney.of(800_000));

        assertThatThrownBy(invoice::rejectUpdate).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(invoice::rejectDelete).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(line::rejectUpdate).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(line::rejectDelete).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(allocation::rejectUpdate).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(allocation::rejectDelete).isInstanceOf(IllegalStateException.class);
    }

    private PropertyInvoice invoice(VndMoney paid, VndMoney refunded, VndMoney balance) {
        Hotel hotel = hotel(3L);
        Reservation reservation = reservation(hotel, 42L);
        return PropertyInvoice.finalized(
                hotel,
                reservation,
                "INV-2026-000042",
                "{\"userId\":8,\"email\":\"customer@example.com\"}",
                "{\"hotelId\":3,\"name\":\"Luxe Hotel\"}",
                VndMoney.of(1_000_000),
                VndMoney.of(100_000),
                VndMoney.of(50_000),
                VndMoney.of(150_000),
                VndMoney.of(1_000_000),
                paid,
                refunded,
                balance,
                user(9L),
                LocalDateTime.of(2026, 8, 1, 6, 0));
    }

    private PropertyFinancialTransaction payment(Hotel hotel, Reservation reservation, long amount) {
        return PropertyFinancialTransaction.record(
                "txn-payment-" + hotel.getId() + "-" + reservation.getId(),
                hotel,
                reservation,
                null,
                null,
                null,
                PropertyFinancialTransaction.TransactionType.ROOM_PAYMENT,
                PropertyFinancialTransaction.Direction.DEBIT,
                VndMoney.of(amount),
                "VNPAY",
                "VNPAY",
                null,
                "provider-payment-" + reservation.getId(),
                "payment-effect-" + hotel.getId() + "-" + reservation.getId(),
                "SYSTEM",
                null,
                "Successful payment",
                LocalDateTime.of(2026, 8, 1, 5, 30));
    }

    private Hotel hotel(Long id) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        return hotel;
    }

    private Reservation reservation(Hotel hotel, Long id) {
        Reservation reservation = new Reservation();
        reservation.setId(id);
        reservation.setHotel(hotel);
        return reservation;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
