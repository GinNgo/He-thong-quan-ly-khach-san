package com.hotel.platformbilling.reporting;

import com.hotel.paymentprovider.domain.FinancialStates.SubscriptionOrderState;
import com.hotel.paymentprovider.reporting.RevenueReportModels.BreakdownDimension;
import com.hotel.paymentprovider.reporting.RevenueReportModels.FinancialContext;
import com.hotel.paymentprovider.reporting.RevenueReportModels.NormalizedFilters;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RecognitionBasis;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.platformbilling.payment.PlatformFinancialTransaction;
import com.hotel.platformbilling.payment.PlatformPaymentAttempt;
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
class PlatformRevenueServiceTest {

    private static final Instant GENERATED_AT = Instant.parse("2026-08-02T00:00:00Z");

    @Mock
    private PlatformRevenueRepository repository;

    private PlatformRevenueService service;

    @BeforeEach
    void setUp() {
        service = new PlatformRevenueService(repository, Clock.fixed(GENERATED_AT, ZoneOffset.UTC));
    }

    @Test
    void calculatesSubscriptionGrossRefundCreditAndNetWithoutPropertyScope() {
        NormalizedFilters filters = filters(RecognitionBasis.NET);
        when(repository.load(filters)).thenReturn(new PlatformRevenueRepository.PlatformRevenueSource(
                List.of(
                        debit("purchase-1", "SUBSCRIPTION_PURCHASE", "1000000", 7L, "PRO"),
                        debit("renewal-1", "SUBSCRIPTION_RENEWAL", "500000", 8L, "BASIC"),
                        credit("refund-1", "SUBSCRIPTION_REFUND", "100000", 9L, "PRO"),
                        credit("credit-1", "DOWNGRADE_CREDIT", "50000", 10L, "BASIC")),
                List.of(),
                List.of(unpaidOrder()),
                List.of()));

        var report = service.generate(filters);

        assertEquals(new BigDecimal("1500000"), report.totals().grossRevenue());
        assertEquals(new BigDecimal("100000"), report.totals().refunds());
        assertEquals(new BigDecimal("50000"), report.totals().credits());
        assertEquals(new BigDecimal("1350000"), report.totals().netRevenue());
        assertEquals(new BigDecimal("1500000"), report.totals().cashCollected());
        assertEquals(new BigDecimal("300000"), report.totals().unpaidBalance());
        assertEquals(4, report.rows().size());
        assertTrue(report.breakdowns().stream().anyMatch(item ->
                item.dimension() == BreakdownDimension.PLAN && item.code().equals("PRO")));
        assertTrue(report.breakdowns().stream().anyMatch(item ->
                item.dimension() == BreakdownDimension.TRANSACTION_TYPE
                        && item.code().equals("SUBSCRIPTION_RENEWAL")));
    }

    @Test
    void countsSuccessFailureAndFlagsSuccessfulAttemptWithoutLedger() {
        NormalizedFilters filters = filters(RecognitionBasis.CASH_COLLECTED);
        when(repository.load(filters)).thenReturn(new PlatformRevenueRepository.PlatformRevenueSource(
                List.of(debit("purchase-1", "SUBSCRIPTION_PURCHASE", "1000000", 7L, "PRO")),
                List.of(
                        new PlatformRevenueRepository.AttemptSource(
                                7L, "attempt-ok", date(15), PlatformPaymentAttempt.Status.SUCCESS,
                                "MOMO", "QR", new BigDecimal("1000000"), "order-1", "PRO"),
                        new PlatformRevenueRepository.AttemptSource(
                                8L, "attempt-missing", date(16), PlatformPaymentAttempt.Status.SUCCESS,
                                "MOMO", "QR", new BigDecimal("200000"), "order-2", "PRO"),
                        new PlatformRevenueRepository.AttemptSource(
                                9L, "attempt-failed", date(17), PlatformPaymentAttempt.Status.FAILED,
                                "MOMO", "QR", new BigDecimal("500000"), "order-3", "PRO")),
                List.of(),
                List.of()));

        var report = service.generate(filters);

        assertEquals(1, report.totals().failedTransactionCount());
        assertEquals(1, report.totals().unreconciledTransactionCount());
        assertEquals(1, report.reconciliationIssues().size());
        assertEquals("PLATFORM_PAYMENT_LEDGER_MISSING", report.reconciliationIssues().getFirst().code());
        assertTrue(report.breakdowns().stream().anyMatch(item ->
                item.dimension() == BreakdownDimension.PROVIDER_STATUS && item.code().equals("FAILED")));
    }

    @Test
    void rejectsPropertyScopedFilters() {
        NormalizedFilters filters = new NormalizedFilters(
                FinancialContext.PROPERTY_COMMERCE,
                RecognitionBasis.NET,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                "UTC", 42L, null, null, null, null, null);

        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.generate(filters))
                .getMessage().contains("Platform Billing"));
    }

    private NormalizedFilters filters(RecognitionBasis basis) {
        return new NormalizedFilters(
                FinancialContext.PLATFORM_BILLING, basis,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                "Asia/Ho_Chi_Minh", null, null, null, null, null, null);
    }

    private PlatformRevenueRepository.TransactionSource debit(
            String publicId, String type, String amount, long attemptId, String planCode) {
        return new PlatformRevenueRepository.TransactionSource(
                publicId, date(15), PlatformFinancialTransaction.TransactionType.valueOf(type),
                PlatformFinancialTransaction.Direction.DEBIT, new BigDecimal(amount), "QR", "MOMO",
                "order-" + attemptId, "ORD-" + attemptId, planCode, planCode + " Plan",
                "MONTHLY",
                SubscriptionOrder.Operation.PURCHASE, SubscriptionOrderState.APPLIED, attemptId, null);
    }

    private PlatformRevenueRepository.TransactionSource credit(
            String publicId, String type, String amount, long orderId, String planCode) {
        return new PlatformRevenueRepository.TransactionSource(
                publicId, date(20), PlatformFinancialTransaction.TransactionType.valueOf(type),
                PlatformFinancialTransaction.Direction.CREDIT, new BigDecimal(amount), "QR", "MOMO",
                "order-" + orderId, "ORD-" + orderId, planCode, planCode + " Plan",
                "ONCE",
                SubscriptionOrder.Operation.REFUND, SubscriptionOrderState.REFUNDED, null, "purchase-1");
    }

    private PlatformRevenueRepository.OrderSource unpaidOrder() {
        return new PlatformRevenueRepository.OrderSource(
                55L, "unpaid", date(21), new BigDecimal("300000"),
                SubscriptionOrder.Operation.PURCHASE, SubscriptionOrderState.PENDING_PAYMENT,
                "BASIC", "Basic", "MONTHLY", 101L);
    }

    private LocalDateTime date(int day) {
        return LocalDateTime.of(2026, 7, day, 0, 0);
    }
}
