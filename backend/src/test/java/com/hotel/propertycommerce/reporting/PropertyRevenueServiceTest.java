package com.hotel.propertycommerce.reporting;

import com.hotel.paymentprovider.reporting.RevenueReportModels.BreakdownDimension;
import com.hotel.paymentprovider.reporting.RevenueReportModels.FinancialContext;
import com.hotel.paymentprovider.reporting.RevenueReportModels.NormalizedFilters;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RecognitionBasis;
import com.hotel.propertycommerce.invoice.PropertyInvoiceLine;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
import com.hotel.propertycommerce.reporting.PropertyRevenueRepository.AllocationSource;
import com.hotel.propertycommerce.reporting.PropertyRevenueRepository.CreditNoteLineSource;
import com.hotel.propertycommerce.reporting.PropertyRevenueRepository.CreditNoteSource;
import com.hotel.propertycommerce.reporting.PropertyRevenueRepository.InvoiceLineSource;
import com.hotel.propertycommerce.reporting.PropertyRevenueRepository.InvoiceSource;
import com.hotel.propertycommerce.reporting.PropertyRevenueRepository.PropertyRevenueSource;
import com.hotel.propertycommerce.reporting.PropertyRevenueRepository.TransactionSource;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyRevenueServiceTest {

    private static final Instant GENERATED_AT = Instant.parse("2026-08-02T00:00:00Z");

    @Mock
    private PropertyRevenueRepository repository;

    private PropertyRevenueService service;

    @BeforeEach
    void setUp() {
        service = new PropertyRevenueService(
                repository,
                Clock.fixed(GENERATED_AT, ZoneOffset.UTC));
    }

    @Test
    void calculatesCashRefundNetUnpaidAndHeldDepositWithoutCountingAllocationsTwice() {
        NormalizedFilters filters = filters(RecognitionBasis.CASH_COLLECTED);
        when(repository.load(filters)).thenReturn(reconciledSource());

        var report = service.generate(filters);

        assertEquals(new BigDecimal("1000000"), report.totals().grossRevenue());
        assertEquals(new BigDecimal("100000"), report.totals().refunds());
        assertEquals(new BigDecimal("900000"), report.totals().netRevenue());
        assertEquals(new BigDecimal("1000000"), report.totals().cashCollected());
        assertEquals(new BigDecimal("1000000"), report.totals().invoicedRevenue());
        assertEquals(new BigDecimal("200000"), report.totals().unpaidBalance());
        assertEquals(new BigDecimal("100000"), report.totals().heldDeposits());
        assertEquals(0, report.totals().unreconciledTransactionCount());
        assertEquals(3, report.rows().size());
        assertEquals(GENERATED_AT, report.generatedAt());
    }

    @Test
    void invoicedBasisUsesCreditNotesAndProducesRoomAndServiceBreakdowns() {
        NormalizedFilters filters = filters(RecognitionBasis.INVOICED);
        PropertyRevenueSource source = sourceWithCreditNote();
        when(repository.load(filters)).thenReturn(source);

        var report = service.generate(filters);

        assertEquals(new BigDecimal("1000000"), report.totals().grossRevenue());
        assertEquals(new BigDecimal("150000"), report.totals().credits());
        assertEquals(new BigDecimal("850000"), report.totals().netRevenue());
        assertEquals(new BigDecimal("850000"), report.totals().invoicedRevenue());
        assertEquals(1, report.rows().size());
        assertEquals(new BigDecimal("150000"), report.rows().getFirst().creditAmount());
        assertTrue(report.breakdowns().stream().anyMatch(item ->
                item.dimension() == BreakdownDimension.ROOM_TYPE
                        && item.code().equals("ROOM-TYPE:9")));
        assertTrue(report.breakdowns().stream().anyMatch(item ->
                item.dimension() == BreakdownDimension.SERVICE
                        && item.netRevenue().compareTo(new BigDecimal("50000")) == 0));
    }

    @Test
    void reportsAllocationInvoiceLineAndCreditNoteMismatches() {
        NormalizedFilters filters = filters(RecognitionBasis.NET);
        PropertyRevenueSource source = new PropertyRevenueSource(
                List.of(debit("tx-1", "ROOM_PAYMENT", "500000")),
                List.of(invoice("500000", "500000", "0")),
                List.of(roomLine("400000")),
                List.of(new AllocationSource(
                        1L, 81L, "tx-1",
                        PropertyFinancialTransaction.TransactionType.ROOM_PAYMENT,
                        new BigDecimal("500000"),
                        LocalDateTime.of(2026, 7, 15, 0, 0),
                        new BigDecimal("600000"))),
                List.of(new CreditNoteSource(
                        91L, "CN-1", 81L, LocalDateTime.of(2026, 7, 21, 0, 0),
                        new BigDecimal("100000"))),
                List.of(new CreditNoteLineSource(92L, 91L, 71L, "Correction", new BigDecimal("50000"))));
        when(repository.load(filters)).thenReturn(source);

        var report = service.generate(filters);

        assertEquals(4, report.reconciliationIssues().size());
        assertEquals(2, report.totals().unreconciledTransactionCount());
        assertTrue(report.rows().getFirst().reconciliationStatus().name().equals("MISMATCH"));
    }

    @Test
    void invoicedBasisKeepsCreditNotesForInvoicesOutsideTheSelectedInvoicePeriod() {
        NormalizedFilters filters = filters(RecognitionBasis.INVOICED);
        PropertyRevenueSource source = new PropertyRevenueSource(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new CreditNoteSource(
                        91L, "CN-OLD-INVOICE", 77L, LocalDateTime.of(2026, 7, 21, 0, 0),
                        new BigDecimal("150000"))),
                List.of(new CreditNoteLineSource(
                        92L, 91L, null, "Prior-period correction", new BigDecimal("150000"))));
        when(repository.load(filters)).thenReturn(source);

        var report = service.generate(filters);

        assertEquals(BigDecimal.ZERO.setScale(0), report.totals().grossRevenue());
        assertEquals(new BigDecimal("150000"), report.totals().credits());
        assertEquals(new BigDecimal("-150000"), report.totals().netRevenue());
        assertEquals("CREDIT_NOTE", report.rows().getFirst().transactionType());
        assertEquals(new BigDecimal("-150000"), report.rows().getFirst().netAmount());
    }

    private PropertyRevenueSource reconciledSource() {
        return new PropertyRevenueSource(
                List.of(
                        debit("deposit-1", "BOOKING_DEPOSIT", "300000"),
                        debit("payment-1", "ROOM_PAYMENT", "700000"),
                        credit("refund-1", "payment-1", "100000")),
                List.of(invoice("1000000", "800000", "200000")),
                List.of(
                        roomLine("800000"),
                        serviceLine("200000")),
                List.of(
                        allocation(1L, "deposit-1", "300000", "200000", "BOOKING_DEPOSIT"),
                        allocation(2L, "payment-1", "700000", "600000", "ROOM_PAYMENT")),
                List.of(),
                List.of());
    }

    private PropertyRevenueSource sourceWithCreditNote() {
        PropertyRevenueSource base = reconciledSource();
        return new PropertyRevenueSource(
                base.transactions(),
                base.invoices(),
                base.invoiceLines(),
                base.allocations(),
                List.of(new CreditNoteSource(
                        91L, "CN-1", 81L, LocalDateTime.of(2026, 7, 21, 0, 0),
                        new BigDecimal("150000"))),
                List.of(new CreditNoteLineSource(
                        92L, 91L, 72L, "Service recovery", new BigDecimal("150000"))));
    }

    private NormalizedFilters filters(RecognitionBasis basis) {
        return new NormalizedFilters(
                FinancialContext.PROPERTY_COMMERCE,
                basis,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                "Asia/Ho_Chi_Minh",
                42L,
                null,
                null,
                null,
                null,
                null);
    }

    private TransactionSource debit(String publicId, String type, String amount) {
        return new TransactionSource(
                publicId,
                LocalDateTime.of(2026, 7, 15, 0, 0),
                PropertyFinancialTransaction.TransactionType.valueOf(type),
                PropertyFinancialTransaction.Direction.DEBIT,
                new BigDecimal(amount),
                "BANK_QR",
                "MOMO",
                61L,
                null,
                null);
    }

    private TransactionSource credit(String publicId, String originalId, String amount) {
        return new TransactionSource(
                publicId,
                LocalDateTime.of(2026, 7, 20, 0, 0),
                PropertyFinancialTransaction.TransactionType.REFUND,
                PropertyFinancialTransaction.Direction.CREDIT,
                new BigDecimal(amount),
                "BANK_QR",
                "MOMO",
                61L,
                81L,
                originalId);
    }

    private InvoiceSource invoice(String total, String paid, String balance) {
        return new InvoiceSource(
                81L,
                "INV-81",
                61L,
                LocalDateTime.of(2026, 7, 20, 0, 0),
                new BigDecimal(total),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal(total),
                new BigDecimal(paid),
                BigDecimal.ZERO,
                new BigDecimal(balance));
    }

    private InvoiceLineSource roomLine(String amount) {
        return new InvoiceLineSource(
                71L,
                81L,
                PropertyInvoiceLine.LineType.ROOM,
                "ROOM-TYPE:9",
                "Deluxe",
                BigDecimal.ONE,
                new BigDecimal(amount),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal(amount),
                new BigDecimal(amount));
    }

    private InvoiceLineSource serviceLine(String amount) {
        return new InvoiceLineSource(
                72L,
                81L,
                PropertyInvoiceLine.LineType.SERVICE,
                "BREAKFAST",
                "Breakfast",
                BigDecimal.ONE,
                new BigDecimal(amount),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal(amount),
                new BigDecimal(amount));
    }

    private AllocationSource allocation(
            Long id,
            String transactionId,
            String transactionAmount,
            String allocatedAmount,
            String type) {
        return new AllocationSource(
                id,
                81L,
                transactionId,
                PropertyFinancialTransaction.TransactionType.valueOf(type),
                new BigDecimal(transactionAmount),
                LocalDateTime.of(2026, 7, 15, 0, 0),
                new BigDecimal(allocatedAmount));
    }
}
