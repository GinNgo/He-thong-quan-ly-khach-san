package com.hotel.propertycommerce.invoice;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceImmutabilityIntegrationTest {

    @Test
    void finalizedInvoiceAndLinesRejectUpdateAndDelete() {
        Fixture fixture = fixture();
        PropertyInvoice invoice = fixture.invoice();
        PropertyInvoiceLine line = PropertyInvoiceLine.snapshot(
                invoice,
                PropertyInvoiceLine.LineType.ROOM,
                null,
                "ROOM-101",
                "Room 101",
                "Immutable room snapshot",
                java.math.BigDecimal.ONE,
                VndMoney.of(1_000_000),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.of(1_000_000),
                null,
                null);

        assertThat(invoice.getStatus()).isEqualTo(PropertyInvoice.Status.FINALIZED);
        assertThatThrownBy(invoice::rejectUpdate).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(invoice::rejectDelete).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(line::rejectUpdate).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(line::rejectDelete).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void paymentAllocationIsASeparateImmutableEvidenceRecord() {
        Fixture fixture = fixture();
        PropertyFinancialTransaction payment = PropertyFinancialTransaction.record(
                "tx-1",
                fixture.hotel(),
                fixture.reservation(),
                null,
                null,
                null,
                PropertyFinancialTransaction.TransactionType.ROOM_PAYMENT,
                PropertyFinancialTransaction.Direction.DEBIT,
                VndMoney.of(1_000_000),
                "CASH",
                "INTERNAL",
                null,
                "provider-1",
                "effect-1",
                "USER",
                fixture.staff().getId(),
                "Checkout payment",
                LocalDateTime.of(2026, 8, 1, 1, 0));
        PropertyInvoicePaymentAllocation allocation = PropertyInvoicePaymentAllocation.allocate(
                fixture.invoice(), payment, VndMoney.of(1_000_000));

        assertThat(allocation.getAllocatedAmount()).isEqualByComparingTo("1000000");
        assertThat(allocation.getFinancialTransaction()).isSameAs(payment);
        assertThatThrownBy(allocation::rejectUpdate).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(allocation::rejectDelete).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void creditNoteAppendsCorrectionWithoutChangingFinalizedInvoice() {
        Fixture fixture = fixture();
        PropertyInvoice invoice = fixture.invoice();
        PropertyInvoiceLine roomLine = PropertyInvoiceLine.snapshot(
                invoice,
                PropertyInvoiceLine.LineType.ROOM,
                null,
                "ROOM-101",
                "Room 101",
                "Room charge",
                java.math.BigDecimal.ONE,
                VndMoney.of(1_000_000),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.of(1_000_000),
                null,
                null);
        PropertyCreditNote note = PropertyCreditNote.issue(
                invoice,
                "CN-3-42-1",
                "Approved service recovery",
                VndMoney.of(150_000),
                fixture.staff(),
                fixture.staff(),
                LocalDateTime.of(2026, 8, 1, 2, 0));
        PropertyCreditNoteLine noteLine = PropertyCreditNoteLine.snapshot(
                note, roomLine, "Room recovery", VndMoney.of(150_000));

        assertThat(invoice.getStatus()).isEqualTo(PropertyInvoice.Status.FINALIZED);
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("1000000");
        assertThat(note.getAmount()).isEqualByComparingTo("150000");
        assertThat(noteLine.getInvoiceLine()).isSameAs(roomLine);
        assertThatThrownBy(note::rejectUpdate).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(note::rejectDelete).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(noteLine::rejectUpdate).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(noteLine::rejectDelete).isInstanceOf(IllegalStateException.class);
    }

    private Fixture fixture() {
        Hotel hotel = new Hotel();
        hotel.setId(3L);
        hotel.setName("Luxe Hotel");
        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setHotel(hotel);
        User customer = new User();
        customer.setId(8L);
        customer.setUsername("customer@example.com");
        customer.setEmail("customer@example.com");
        reservation.setUser(customer);
        User staff = new User();
        staff.setId(9L);
        staff.setUsername("staff@example.com");
        staff.setEmail("staff@example.com");
        PropertyInvoice invoice = PropertyInvoice.finalized(
                hotel,
                reservation,
                "INV-3-42",
                "{}",
                "{}",
                VndMoney.of(1_000_000),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.of(1_000_000),
                VndMoney.of(1_000_000),
                VndMoney.zero(),
                VndMoney.zero(),
                staff,
                LocalDateTime.of(2026, 8, 1, 0, 0));
        return new Fixture(hotel, reservation, staff, invoice);
    }

    private record Fixture(
            Hotel hotel,
            Reservation reservation,
            User staff,
            PropertyInvoice invoice) {
    }
}
