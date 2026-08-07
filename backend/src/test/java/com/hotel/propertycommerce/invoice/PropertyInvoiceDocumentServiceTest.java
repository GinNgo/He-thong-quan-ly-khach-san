package com.hotel.propertycommerce.invoice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PropertyInvoiceDocumentServiceTest {

    private final PropertyInvoiceDocumentService service =
            new PropertyInvoiceDocumentService(new ObjectMapper());

    @Test
    void rendersDeterministicItemizedSnapshotWithPaymentsRefundsAndTotals() throws Exception {
        Fixture fixture = fixture();

        byte[] first = service.renderPdf(
                fixture.invoice(), fixture.lines(), List.of(fixture.allocation()),
                List.of(fixture.creditNote()), List.of(fixture.creditNoteLine()));
        byte[] second = service.renderPdf(
                fixture.invoice(), fixture.lines(), List.of(fixture.allocation()),
                List.of(fixture.creditNote()), List.of(fixture.creditNoteLine()));

        assertThat(first).isEqualTo(second);
        String pdf = new String(first, StandardCharsets.ISO_8859_1);
        assertThat(pdf).startsWith("%PDF-1.4");
        assertThat(pdf).contains(
                "PROPERTY SNAPSHOT",
                "Luxe Beach Hotel",
                "CUSTOMER SNAPSHOT",
                "Nguyen Van A",
                "Line 1 [ROOM]: Deluxe room",
                "Line 2 [SERVICE]: Breakfast buffet",
                "Line 3 [MINIBAR]: Mineral water",
                "Line 4 [SURCHARGE]: Late checkout",
                "Line 5 [TAX]: VAT",
                "Line 6 [FEE]: Service fee",
                "Line 7 [DISCOUNT]: Member discount",
                "Quantity: 2 | Unit price VND: 50000",
                "Payment 1: Amount VND: 1200000",
                "Method: MANUAL_TRANSFER",
                "Refunds/credits snapshot VND: 20000",
                "Credit note: CN-3-88-1",
                "Subtotal VND: 1200000",
                "Tax VND: 100000",
                "Fees/surcharges VND: 30000",
                "Discount VND: 150000",
                "TOTAL VND: 1180000",
                "PAID VND: 1200000",
                "REFUNDED/CREDITED VND: 20000",
                "BALANCE VND: 0");

        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(first));
        assertThat(checksum).hasSize(64);
    }

    private Fixture fixture() {
        Hotel hotel = new Hotel();
        hotel.setId(3L);
        hotel.setCode("LUXE-BEACH");
        hotel.setName("Luxe Beach Hotel");
        hotel.setNameVi("Luxe Beach Hotel");
        hotel.setAddressLine("1 Beach Road");
        hotel.setCity("Da Nang");
        hotel.setCountry("VN");
        hotel.setPhone("0900000000");
        hotel.setEmail("billing@luxe.test");

        User customer = user(8L, "customer@example.test", "Nguyen Van A");
        customer.setPhone("0911111111");
        User staff = user(9L, "staff@example.test", "Invoice Staff");
        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setHotel(hotel);
        reservation.setUser(customer);

        PropertyInvoice invoice = PropertyInvoice.finalized(
                hotel,
                reservation,
                "INV-3-42",
                "{\"userId\":8,\"username\":\"customer@example.test\",\"email\":\"customer@example.test\","
                        + "\"fullName\":\"Nguyen Van A\",\"phone\":\"0911111111\"}",
                "{\"hotelId\":3,\"code\":\"LUXE-BEACH\",\"name\":\"Luxe Beach Hotel\","
                        + "\"nameVi\":\"Luxe Beach Hotel\",\"address\":\"1 Beach Road\","
                        + "\"city\":\"Da Nang\",\"country\":\"VN\",\"phone\":\"0900000000\","
                        + "\"email\":\"billing@luxe.test\"}",
                VndMoney.of(1_200_000),
                VndMoney.of(100_000),
                VndMoney.of(30_000),
                VndMoney.of(150_000),
                VndMoney.of(1_180_000),
                VndMoney.of(1_200_000),
                VndMoney.of(20_000),
                VndMoney.zero(),
                staff,
                LocalDateTime.of(2026, 8, 1, 10, 30));
        ReflectionTestUtils.setField(invoice, "id", 88L);

        List<PropertyInvoiceLine> lines = List.of(
                line(invoice, 1L, PropertyInvoiceLine.LineType.ROOM, "DLX", "Deluxe room",
                        1, 1_000_000, 0, 0, 1_000_000),
                line(invoice, 2L, PropertyInvoiceLine.LineType.SERVICE, "BREAKFAST", "Breakfast buffet",
                        2, 50_000, 0, 0, 100_000),
                line(invoice, 3L, PropertyInvoiceLine.LineType.MINIBAR, "WATER", "Mineral water",
                        3, 20_000, 0, 0, 60_000),
                line(invoice, 4L, PropertyInvoiceLine.LineType.SURCHARGE, "LATE", "Late checkout",
                        1, 40_000, 0, 0, 40_000),
                line(invoice, 5L, PropertyInvoiceLine.LineType.TAX, "VAT", "VAT",
                        1, 0, 100_000, 0, 100_000),
                line(invoice, 6L, PropertyInvoiceLine.LineType.FEE, "SERVICE_FEE", "Service fee",
                        1, 30_000, 0, 0, 30_000),
                line(invoice, 7L, PropertyInvoiceLine.LineType.DISCOUNT, "MEMBER", "Member discount",
                        1, 0, 0, 150_000, 150_000));

        PropertyFinancialTransaction payment = PropertyFinancialTransaction.record(
                "txn-public-1",
                hotel,
                reservation,
                invoice.getId(),
                null,
                null,
                PropertyFinancialTransaction.TransactionType.ROOM_PAYMENT,
                PropertyFinancialTransaction.Direction.DEBIT,
                VndMoney.of(1_200_000),
                "MANUAL_TRANSFER",
                "SIMULATOR",
                null,
                "BANK-REF-001",
                "invoice-payment-1",
                "USER",
                staff.getId(),
                "Settled stay",
                LocalDateTime.of(2026, 8, 1, 10, 0));
        ReflectionTestUtils.setField(payment, "id", 501L);
        PropertyInvoicePaymentAllocation allocation = PropertyInvoicePaymentAllocation.allocate(
                invoice, payment, VndMoney.of(1_200_000));
        ReflectionTestUtils.setField(allocation, "id", 601L);

        PropertyCreditNote note = PropertyCreditNote.issue(
                invoice,
                "CN-3-88-1",
                "Returned minibar item",
                VndMoney.of(20_000),
                staff,
                staff,
                LocalDateTime.of(2026, 8, 1, 11, 0));
        ReflectionTestUtils.setField(note, "id", 701L);
        PropertyCreditNoteLine noteLine = PropertyCreditNoteLine.snapshot(
                note, lines.get(2), "Mineral water returned", VndMoney.of(20_000));
        ReflectionTestUtils.setField(noteLine, "id", 702L);
        return new Fixture(invoice, lines, allocation, note, noteLine);
    }

    private PropertyInvoiceLine line(
            PropertyInvoice invoice,
            Long id,
            PropertyInvoiceLine.LineType type,
            String code,
            String name,
            long quantity,
            long unitPrice,
            long tax,
            long discount,
            long total) {
        PropertyInvoiceLine line = PropertyInvoiceLine.snapshot(
                invoice,
                type,
                null,
                code,
                name,
                null,
                BigDecimal.valueOf(quantity),
                VndMoney.of(unitPrice),
                VndMoney.of(tax),
                VndMoney.of(discount),
                VndMoney.of(total),
                LocalDateTime.of(2026, 8, 1, 8, 0),
                null);
        ReflectionTestUtils.setField(line, "id", id);
        return line;
    }

    private User user(Long id, String email, String name) {
        User user = new User();
        user.setId(id);
        user.setUsername(email);
        user.setEmail(email);
        user.setFullName(name);
        return user;
    }

    private record Fixture(
            PropertyInvoice invoice,
            List<PropertyInvoiceLine> lines,
            PropertyInvoicePaymentAllocation allocation,
            PropertyCreditNote creditNote,
            PropertyCreditNoteLine creditNoteLine) {
    }
}
