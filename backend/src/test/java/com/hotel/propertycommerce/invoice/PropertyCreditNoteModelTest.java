package com.hotel.propertycommerce.invoice;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.domain.VndMoney;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PropertyCreditNoteModelTest {

    @Test
    void snapshotsApprovedCreditWithoutChangingInvoice() {
        PropertyInvoice invoice = invoice(3L, 88L);
        PropertyInvoiceLine invoiceLine = invoiceLine(invoice, 201L, 400_000);
        User actor = user(9L);

        PropertyCreditNote note = PropertyCreditNote.issue(
                invoice,
                "CN-3-88-1",
                "Post-checkout correction",
                VndMoney.of(100_000),
                actor,
                actor,
                LocalDateTime.of(2026, 8, 1, 1, 0));
        PropertyCreditNoteLine line = PropertyCreditNoteLine.snapshot(
                note,
                invoiceLine,
                "Correct room charge",
                VndMoney.of(100_000));

        assertThat(note.getHotel()).isSameAs(invoice.getHotel());
        assertThat(note.getInvoice()).isSameAs(invoice);
        assertThat(note.getAmount()).isEqualByComparingTo("100000");
        assertThat(line.getHotel()).isSameAs(invoice.getHotel());
        assertThat(line.getInvoiceLine()).isSameAs(invoiceLine);
        assertThat(invoice.getStatus()).isEqualTo(PropertyInvoice.Status.FINALIZED);
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("1000000");
    }

    @Test
    void rejectsCrossInvoiceAndExcessiveLineCredits() {
        PropertyInvoice invoice = invoice(3L, 88L);
        PropertyInvoice otherInvoice = invoice(3L, 89L);
        PropertyInvoiceLine otherLine = invoiceLine(otherInvoice, 202L, 200_000);
        User actor = user(9L);
        PropertyCreditNote note = PropertyCreditNote.issue(
                invoice,
                "CN-3-88-1",
                "Correction",
                VndMoney.of(100_000),
                actor,
                actor,
                LocalDateTime.of(2026, 8, 1, 1, 0));

        assertThatThrownBy(() -> PropertyCreditNoteLine.snapshot(
                note, otherLine, "Wrong invoice", VndMoney.of(100_000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("corrected invoice");

        PropertyInvoiceLine targetLine = invoiceLine(invoice, 203L, 100_000);
        assertThatThrownBy(() -> PropertyCreditNoteLine.snapshot(
                note, targetLine, "Too much", VndMoney.of(100_001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceed");
    }

    @Test
    void noteAndLinesRejectMutationAndDeletion() {
        PropertyInvoice invoice = invoice(3L, 88L);
        User actor = user(9L);
        PropertyCreditNote note = PropertyCreditNote.issue(
                invoice,
                "CN-3-88-1",
                "Correction",
                VndMoney.of(50_000),
                actor,
                actor,
                LocalDateTime.of(2026, 8, 1, 1, 0));
        PropertyCreditNoteLine line = PropertyCreditNoteLine.snapshot(
                note, null, "General credit", VndMoney.of(50_000));

        assertThatThrownBy(note::rejectUpdate).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(note::rejectDelete).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(line::rejectUpdate).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(line::rejectDelete).isInstanceOf(IllegalStateException.class);
    }

    private PropertyInvoice invoice(Long hotelId, Long invoiceId) {
        Hotel hotel = new Hotel();
        hotel.setId(hotelId);
        Reservation reservation = new Reservation();
        reservation.setId(invoiceId + 100L);
        reservation.setHotel(hotel);
        PropertyInvoice invoice = PropertyInvoice.finalized(
                hotel,
                reservation,
                "INV-" + hotelId + "-" + invoiceId,
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
                user(9L),
                LocalDateTime.of(2026, 8, 1, 0, 0));
        ReflectionTestUtils.setField(invoice, "id", invoiceId);
        return invoice;
    }

    private PropertyInvoiceLine invoiceLine(PropertyInvoice invoice, Long id, long amount) {
        PropertyInvoiceLine line = PropertyInvoiceLine.snapshot(
                invoice,
                PropertyInvoiceLine.LineType.ROOM,
                null,
                "ROOM",
                "Room charge",
                null,
                BigDecimal.ONE,
                VndMoney.of(amount),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.of(amount),
                null,
                null);
        ReflectionTestUtils.setField(line, "id", id);
        return line;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
