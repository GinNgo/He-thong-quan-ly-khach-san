package com.hotel.platformbilling.reporting;

import com.hotel.BackendApplication;
import com.hotel.entities.Hotel;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.entities.User;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.FinancialStates.SubscriptionOrderState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.reporting.RevenueReportModels.FinancialContext;
import com.hotel.paymentprovider.reporting.RevenueReportModels.NormalizedFilters;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RecognitionBasis;
import com.hotel.paymentprovider.reporting.RevenueExportService;
import com.hotel.paymentprovider.reporting.RevenueExportService.Format;
import com.hotel.platformbilling.config.PlatformPaymentConfiguration;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.platformbilling.payment.PlatformFinancialTransaction;
import com.hotel.platformbilling.payment.PlatformPaymentAttempt;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PlatformRevenueRepository.class, PlatformRevenueService.class, RevenueExportService.class})
@ContextConfiguration(classes = BackendApplication.class)
@TestPropertySource(properties = {
        "spring.datasource.url=${PLATFORM_REVENUE_DATABASE_URL:jdbc:h2:mem:platform-revenue-reconciliation;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE}",
        "spring.datasource.username=${PLATFORM_REVENUE_DATABASE_USERNAME:sa}",
        "spring.datasource.password=${PLATFORM_REVENUE_DATABASE_PASSWORD:}",
        "spring.datasource.driver-class-name=${PLATFORM_REVENUE_DATABASE_DRIVER:org.h2.Driver}",
        "spring.jpa.database-platform=${PLATFORM_REVENUE_DATABASE_DIALECT:org.hibernate.dialect.H2Dialect}",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class PlatformRevenueReconciliationIntegrationTest {

    @Autowired private EntityManager entityManager;
    @Autowired private PlatformRevenueService revenueService;
    @Autowired private RevenueExportService exportService;

    @Test
    void reportMatchesSystemLedgerToOneVndAndRespectsPlanFilter() {
        User owner = persistOwner();
        Hotel hotel = persistHotel("platform-target");
        PlatformPaymentConfiguration configuration = persistConfiguration();
        SubscriptionPlan pro = persistPlan("PRO", "MONTHLY", 1_000_000);
        SubscriptionPlan basic = persistPlan("BASIC", "YEARLY", 7_000_000);

        SubscriptionOrder proOrder = persistPaidOrder(owner, hotel, pro, "pro-order", 1_000_000);
        PlatformPaymentAttempt proAttempt = persistSuccessfulAttempt(proOrder, configuration, "pro-attempt", 1_000_000);
        PlatformFinancialTransaction debit = persistTransaction(
                "pro-debit", proOrder, proAttempt, null,
                PlatformFinancialTransaction.TransactionType.SUBSCRIPTION_PURCHASE,
                PlatformFinancialTransaction.Direction.DEBIT, 1_000_000);
        persistTransaction(
                "pro-refund", proOrder, null, debit,
                PlatformFinancialTransaction.TransactionType.SUBSCRIPTION_REFUND,
                PlatformFinancialTransaction.Direction.CREDIT, 100_000);
        persistTransaction(
                "pro-credit", proOrder, null, debit,
                PlatformFinancialTransaction.TransactionType.DOWNGRADE_CREDIT,
                PlatformFinancialTransaction.Direction.CREDIT, 50_000);

        SubscriptionOrder basicOrder = persistPaidOrder(owner, hotel, basic, "basic-order", 7_000_000);
        PlatformPaymentAttempt basicAttempt = persistSuccessfulAttempt(
                basicOrder, configuration, "basic-attempt", 7_000_000);
        persistTransaction(
                "basic-debit", basicOrder, basicAttempt, null,
                PlatformFinancialTransaction.TransactionType.SUBSCRIPTION_PURCHASE,
                PlatformFinancialTransaction.Direction.DEBIT, 7_000_000);
        entityManager.flush();
        entityManager.clear();

        var report = revenueService.generate(filters("PRO"));
        Object[] databaseTotals = (Object[]) entityManager.createNativeQuery("""
                select
                  sum(case when financial_transaction.direction = 'DEBIT' then financial_transaction.amount else 0 end),
                  sum(case when financial_transaction.transaction_type = 'SUBSCRIPTION_REFUND' then financial_transaction.amount else 0 end),
                  sum(case when financial_transaction.transaction_type = 'DOWNGRADE_CREDIT' then financial_transaction.amount else 0 end),
                  count(*)
                from platform_financial_transactions financial_transaction
                join platform_subscription_orders orders on orders.id = financial_transaction.order_id
                where orders.plan_code = :planCode
                  and financial_transaction.occurred_at >= :fromInclusive
                  and financial_transaction.occurred_at < :toExclusive
                """)
                .setParameter("planCode", "PRO")
                .setParameter("fromInclusive", LocalDateTime.of(2026, 7, 1, 0, 0))
                .setParameter("toExclusive", LocalDateTime.of(2026, 9, 1, 0, 0))
                .getSingleResult();

        BigDecimal databaseGross = amount(databaseTotals[0]);
        BigDecimal databaseRefunds = amount(databaseTotals[1]);
        BigDecimal databaseCredits = amount(databaseTotals[2]);
        assertEquals(databaseGross, report.totals().grossRevenue());
        assertEquals(databaseRefunds, report.totals().refunds());
        assertEquals(databaseCredits, report.totals().credits());
        assertEquals(databaseGross.subtract(databaseRefunds).subtract(databaseCredits),
                report.totals().netRevenue());
        assertEquals(((Number) databaseTotals[3]).longValue(), report.totalRowCount());
        assertEquals(0, report.totals().unreconciledTransactionCount());
        assertEquals(1, report.totals().successfulTransactionCount());

        BigDecimal exportedRowNet = report.rows().stream()
                .map(row -> row.netAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(report.totals().netRevenue(), exportedRowNet);
        assertTrue(report.rows().stream().allMatch(row -> row.propertyId() == null));

        var csv = exportService.export(report, Format.CSV);
        String csvText = new String(csv.content(), StandardCharsets.UTF_8);
        assertEquals(report.totalRowCount(), csv.rowCount());
        assertEquals(exportService.checksum(report), csv.checksum());
        assertEquals(csv.checksum(), exportService.export(report, Format.CSV).checksum());
        assertTrue(csvText.contains(csv.checksum()));
        assertTrue(csvText.contains("pro-debit"));
        assertTrue(csvText.contains("pro-refund"));
        assertTrue(csvText.contains("pro-credit"));
        assertFalse(csvText.contains("basic-debit"));
        assertTrue(csvText.contains(report.totals().grossRevenue().toString()));
        assertTrue(csvText.contains(report.totals().netRevenue().toString()));
    }

    private NormalizedFilters filters(String planCode) {
        return new NormalizedFilters(
                FinancialContext.PLATFORM_BILLING, RecognitionBasis.NET,
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
                "UTC", null, null, null, null, null, planCode);
    }

    private User persistOwner() {
        User user = new User();
        user.setUsername("platform-owner-" + UUID.randomUUID());
        user.setEmail("platform-owner-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("hash");
        user.setStatus("ACTIVE");
        entityManager.persist(user);
        return user;
    }

    private Hotel persistHotel(String prefix) {
        Hotel hotel = new Hotel();
        hotel.setName(prefix + '-' + UUID.randomUUID());
        hotel.setAddressLine("Address");
        hotel.setCity("City");
        hotel.setCountry("VN");
        hotel.setStatus("ACTIVE");
        hotel.setOperationStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        entityManager.persist(hotel);
        return hotel;
    }

    private SubscriptionPlan persistPlan(String code, String billingType, long price) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setCode(code);
        plan.setNameVi(code + " Plan");
        plan.setNameEn(code + " Plan");
        plan.setBillingType(billingType);
        plan.setPrice(BigDecimal.valueOf(price));
        plan.setIsLifetime(false);
        plan.setStatus("ACTIVE");
        entityManager.persist(plan);
        return plan;
    }

    private PlatformPaymentConfiguration persistConfiguration() {
        PlatformPaymentConfiguration configuration = PlatformPaymentConfiguration.create(
                "MOMO", PaymentEnvironment.SIMULATOR);
        configuration.configure(true, "SIMULATOR", null, null, null, null);
        entityManager.persist(configuration);
        return configuration;
    }

    private SubscriptionOrder persistPaidOrder(
            User owner, Hotel hotel, SubscriptionPlan plan, String prefix, long price) {
        SubscriptionOrder order = SubscriptionOrder.create(
                prefix + '-' + UUID.randomUUID(), "ORD-" + prefix + '-' + UUID.randomUUID(),
                owner, hotel, SubscriptionOrder.Operation.PURCHASE, plan, "v1", plan.getCode(),
                plan.getNameVi(), VndMoney.of(price), plan.getBillingType(), 1,
                SubscriptionOrder.DurationUnit.MONTH, "{}", "idem-" + prefix,
                "hash-" + prefix, LocalDateTime.of(2026, 9, 30, 0, 0));
        order.transitionTo(SubscriptionOrderState.PENDING_PAYMENT, LocalDateTime.of(2026, 7, 10, 0, 0));
        order.transitionTo(SubscriptionOrderState.PAID, LocalDateTime.of(2026, 7, 10, 0, 1));
        entityManager.persist(order);
        return order;
    }

    private PlatformPaymentAttempt persistSuccessfulAttempt(
            SubscriptionOrder order,
            PlatformPaymentConfiguration configuration,
            String prefix,
            long amount) {
        PlatformPaymentAttempt attempt = PlatformPaymentAttempt.create(
                prefix + '-' + UUID.randomUUID(), order, configuration, "QR", VndMoney.of(amount),
                "idem-" + prefix, "hash-" + prefix, LocalDateTime.of(2026, 9, 1, 0, 0));
        attempt.markSucceeded("provider-" + prefix, "event-" + prefix,
                LocalDateTime.of(2026, 7, 15, 0, 0));
        entityManager.persist(attempt);
        return attempt;
    }

    private PlatformFinancialTransaction persistTransaction(
            String publicId,
            SubscriptionOrder order,
            PlatformPaymentAttempt attempt,
            PlatformFinancialTransaction original,
            PlatformFinancialTransaction.TransactionType type,
            PlatformFinancialTransaction.Direction direction,
            long amount) {
        PlatformFinancialTransaction transaction = PlatformFinancialTransaction.record(
                publicId, order, attempt, original, type, direction, VndMoney.of(amount),
                "QR", "MOMO", PaymentEnvironment.SIMULATOR, "provider-" + publicId,
                "effect-" + publicId, "SYSTEM", null, "Reconciliation fixture",
                LocalDateTime.of(2026, 7, direction == PlatformFinancialTransaction.Direction.DEBIT ? 15 : 20, 0, 0));
        entityManager.persist(transaction);
        return transaction;
    }

    private BigDecimal amount(Object value) {
        return new BigDecimal(value.toString()).setScale(0);
    }
}
