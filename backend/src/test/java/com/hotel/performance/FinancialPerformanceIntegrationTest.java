package com.hotel.performance;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.adapters.PaymentProviderAdapterRegistry;
import com.hotel.paymentprovider.adapters.SimulatorPaymentProviderAdapter;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.FinancialStates.PaymentState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.reporting.RevenueReportModels.FinancialContext;
import com.hotel.paymentprovider.reporting.RevenueReportModels.NormalizedFilters;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RecognitionBasis;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RevenueReportResult;
import com.hotel.propertycommerce.payment.PropertyFinancialTransactionRepository;
import com.hotel.propertycommerce.payment.PropertyPaymentAttempt;
import com.hotel.propertycommerce.payment.PropertyPaymentAttemptRepository;
import com.hotel.propertycommerce.payment.PropertyPaymentCallbackService;
import com.hotel.propertycommerce.reporting.PropertyRevenueRepository;
import com.hotel.propertycommerce.reporting.PropertyRevenueService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("performance")
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:financial-performance;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@Import({
        PropertyPaymentCallbackService.class,
        PaymentProviderAdapterRegistry.class,
        SimulatorPaymentProviderAdapter.class,
        PropertyRevenueRepository.class,
        PropertyRevenueService.class
})
class FinancialPerformanceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-31T11:15:00Z");
    private static final String SIGNING_SECRET = "local-performance-signing-secret-32-chars";
    private static final int CALLBACK_SAMPLES = 20;
    private static final int REPORT_ROWS = 100_000;
    private static final int REPORT_BATCHES = 3;
    private static final int REPORT_SAMPLES = 20;
    private static final long CALLBACK_BUDGET_NANOS = TimeUnit.SECONDS.toNanos(2);
    private static final long REPORT_BUDGET_NANOS = TimeUnit.SECONDS.toNanos(3);

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PropertyPaymentAttemptRepository attemptRepository;

    @Autowired
    private PropertyFinancialTransactionRepository transactionRepository;

    @Autowired
    private PropertyPaymentCallbackService callbackService;

    @Autowired
    private PropertyRevenueService revenueService;

    @MockBean
    private FinancialAuditService auditService;

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void callbackAndIdempotentReplayP95StayWithinTwoSeconds() {
        FinancialOwner owner = persistFinancialOwner("callback");
        List<Map<String, Object>> payloads = new ArrayList<>();

        for (int sample = -1; sample < CALLBACK_SAMPLES; sample++) {
            String suffix = sample < 0 ? "warmup" : Integer.toString(sample);
            String publicId = "perf-callback-" + suffix;
            persistPendingAttempt(owner, publicId, "perf-idem-" + suffix);
            payloads.add(signedPayload(
                    publicId,
                    "perf-event-" + suffix,
                    "perf-provider-tx-" + suffix));
        }
        entityManager.flush();
        entityManager.clear();

        Map<String, Object> warmupPayload = payloads.getFirst();
        assertTrue(callbackService.process(callbackCommand(warmupPayload, "warmup-fresh")).accepted());
        assertTrue(callbackService.process(callbackCommand(warmupPayload, "warmup-replay")).replayed());

        List<Long> freshDurations = new ArrayList<>(CALLBACK_SAMPLES);
        for (int sample = 0; sample < CALLBACK_SAMPLES; sample++) {
            Map<String, Object> payload = payloads.get(sample + 1);
            TimedCallback measurement = measureCallback(payload, "fresh-" + sample);
            assertTrue(measurement.result().accepted());
            assertTrue(!measurement.result().replayed());
            freshDurations.add(measurement.nanos());
        }

        entityManager.clear();
        List<Long> replayDurations = new ArrayList<>(CALLBACK_SAMPLES);
        for (int sample = 0; sample < CALLBACK_SAMPLES; sample++) {
            Map<String, Object> payload = payloads.get(sample + 1);
            TimedCallback measurement = measureCallback(payload, "replay-" + sample);
            assertTrue(measurement.result().accepted());
            assertTrue(measurement.result().replayed());
            replayDurations.add(measurement.nanos());
        }

        assertEquals(CALLBACK_SAMPLES + 1L, transactionRepository.count());
        assertWithinBudget("fresh callback", freshDurations, CALLBACK_BUDGET_NANOS);
        assertWithinBudget("idempotent callback replay", replayDurations, CALLBACK_BUDGET_NANOS);
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    void filteredOneHundredThousandRowReportP95StaysWithinThreeSeconds() {
        FinancialOwner owner = persistFinancialOwner("report");
        entityManager.flush();
        entityManager.clear();
        insertReportRows(owner.hotelId(), owner.reservationId());

        Integer persistedRows = jdbcTemplate.queryForObject(
                "select count(*) from property_financial_transactions where hotel_id = ?",
                Integer.class,
                owner.hotelId());
        assertEquals(REPORT_ROWS, persistedRows);

        NormalizedFilters filters = new NormalizedFilters(
                FinancialContext.PROPERTY_COMMERCE,
                RecognitionBasis.CASH_COLLECTED,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                "Asia/Ho_Chi_Minh",
                owner.hotelId(),
                "SIMULATOR",
                "BANK_QR",
                "ROOM_PAYMENT",
                null,
                null);

        for (int warmup = 0; warmup < 2; warmup++) {
            assertEquals(REPORT_ROWS, measureReport(filters).rowCount());
        }

        List<Long> batchP95s = new ArrayList<>(REPORT_BATCHES);
        for (int batch = 0; batch < REPORT_BATCHES; batch++) {
            List<Long> durations = new ArrayList<>(REPORT_SAMPLES);
            for (int sample = 0; sample < REPORT_SAMPLES; sample++) {
                ReportMeasurement measurement = measureReport(filters);
                assertEquals(REPORT_ROWS, measurement.rowCount());
                durations.add(measurement.nanos());
            }
            printMetrics("filtered 100,000-row report batch " + (batch + 1), durations, REPORT_BUDGET_NANOS);
            batchP95s.add(percentile(durations, 95));
        }

        assertMedianP95WithinBudget(batchP95s, REPORT_BUDGET_NANOS);
    }

    private TimedCallback measureCallback(Map<String, Object> payload, String correlationSuffix) {
        long startedAt = System.nanoTime();
        PropertyPaymentCallbackService.CallbackResult result = callbackService.process(
                callbackCommand(payload, correlationSuffix));
        return new TimedCallback(System.nanoTime() - startedAt, result);
    }

    private ReportMeasurement measureReport(NormalizedFilters filters) {
        entityManager.clear();
        try {
            long startedAt = System.nanoTime();
            RevenueReportResult report = revenueService.generate(filters);
            long elapsed = System.nanoTime() - startedAt;
            assertEquals(REPORT_ROWS, report.rows().size());
            return new ReportMeasurement(elapsed, report.totalRowCount());
        } finally {
            // Persistence-context cleanup is deliberately outside the report latency window.
            entityManager.clear();
        }
    }

    private void insertReportRows(Long hotelId, Long reservationId) {
        String sql = """
                insert into property_financial_transactions (
                    public_id, hotel_id, reservation_id, transaction_type, direction, amount, currency,
                    method, provider, environment, provider_transaction_ref, idempotency_identity,
                    actor_type, reason, occurred_at, recorded_at, legacy_reconciliation_required
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 15, 8, 0);
        LocalDateTime recordedAt = LocalDateTime.of(2026, 7, 15, 8, 1);
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                String suffix = Integer.toString(index);
                statement.setString(1, "perf-report-tx-" + suffix);
                statement.setLong(2, hotelId);
                statement.setLong(3, reservationId);
                statement.setString(4, "ROOM_PAYMENT");
                statement.setString(5, "DEBIT");
                statement.setBigDecimal(6, BigDecimal.valueOf(125_000));
                statement.setString(7, "VND");
                statement.setString(8, "BANK_QR");
                statement.setString(9, "SIMULATOR");
                statement.setString(10, "SIMULATOR");
                statement.setString(11, "perf-provider-report-" + suffix);
                statement.setString(12, "perf-report-effect-" + suffix);
                statement.setString(13, "SYSTEM");
                statement.setString(14, "Local deterministic performance fixture");
                statement.setTimestamp(15, Timestamp.valueOf(occurredAt));
                statement.setTimestamp(16, Timestamp.valueOf(recordedAt));
                statement.setBoolean(17, false);
            }

            @Override
            public int getBatchSize() {
                return REPORT_ROWS;
            }
        });
    }

    private FinancialOwner persistFinancialOwner(String suffix) {
        Hotel hotel = new Hotel();
        hotel.setName("Performance Hotel " + suffix);
        hotel.setAddressLine("1 Local Fixture Street");
        hotel.setCity("Da Nang");
        hotel.setCountry("VN");
        hotel.setStatus("ACTIVE");
        hotel.setOperationStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        entityManager.persist(hotel);

        User customer = new User();
        customer.setUsername("performance-" + suffix);
        customer.setEmail("performance-" + suffix + "@example.test");
        customer.setPasswordHash("local-test-hash");
        customer.setStatus("ACTIVE");
        entityManager.persist(customer);

        Reservation reservation = new Reservation();
        reservation.setUser(customer);
        reservation.setHotel(hotel);
        reservation.setCheckInDate(LocalDate.of(2026, 7, 10));
        reservation.setCheckOutDate(LocalDate.of(2026, 7, 12));
        reservation.setGuests(2);
        reservation.setTotalAmount(BigDecimal.valueOf(2_500_000));
        reservation.setStatus("CONFIRMED");
        reservation.setPaymentMethod("BANK_QR");
        entityManager.persist(reservation);
        entityManager.flush();
        return new FinancialOwner(hotel.getId(), reservation.getId(), hotel, reservation);
    }

    private void persistPendingAttempt(FinancialOwner owner, String publicId, String idempotencyKey) {
        PropertyPaymentAttempt attempt = PropertyPaymentAttempt.create(
                publicId,
                owner.hotel(),
                owner.reservation(),
                null,
                null,
                PropertyPaymentAttempt.Purpose.DEPOSIT,
                "BANK_QR",
                "SIMULATOR",
                PaymentEnvironment.SIMULATOR,
                VndMoney.of(350_000),
                null,
                null,
                idempotencyKey,
                "local-request-hash-" + publicId,
                LocalDateTime.ofInstant(NOW.plusSeconds(900), ZoneOffset.UTC));
        attempt.bindProviderOrderReference(publicId);
        attempt.transitionTo(
                PaymentState.PENDING,
                LocalDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC),
                null,
                null);
        attemptRepository.save(attempt);
    }

    private PropertyPaymentCallbackService.CallbackCommand callbackCommand(
            Map<String, Object> payload,
            String correlationSuffix) {
        return new PropertyPaymentCallbackService.CallbackCommand(
                "SIMULATOR",
                PaymentEnvironment.SIMULATOR,
                "SIM-HOTEL-PERFORMANCE",
                payload.get("signature").toString(),
                payload,
                Map.of("signingSecret", SIGNING_SECRET),
                NOW,
                "performance-" + correlationSuffix);
    }

    private Map<String, Object> signedPayload(String reference, String eventId, String transactionId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("merchantId", "SIM-HOTEL-PERFORMANCE");
        payload.put("eventId", eventId);
        payload.put("transactionId", transactionId);
        payload.put("reference", reference);
        payload.put("amount", 350_000);
        payload.put("currency", "VND");
        payload.put("occurredAt", NOW.toString());
        payload.put("status", "SUCCEEDED");
        payload.put("signature", hmac(canonical(payload)));
        return Map.copyOf(payload);
    }

    private String canonical(Map<String, Object> payload) {
        return payload.entrySet().stream()
                .filter(entry -> !"signature".equals(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SIGNING_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign local callback fixture", exception);
        }
    }

    private void assertWithinBudget(String operation, List<Long> samples, long budgetNanos) {
        printMetrics(operation, samples, budgetNanos);
        long p95 = percentile(samples, 95);
        assertTrue(p95 <= budgetNanos, () -> operation + " p95 was " + millis(p95)
                + " ms, budget is " + millis(budgetNanos) + " ms, samples=" + sampleMillis(samples));
    }

    private void assertMedianP95WithinBudget(List<Long> batchP95s, long budgetNanos) {
        long medianP95 = percentile(batchP95s, 50);
        System.out.println("PERFORMANCE_BATCH_P95 median=" + millis(medianP95)
                + " budget=" + millis(budgetNanos)
                + " batchP95s=" + sampleMillis(batchP95s));
        assertTrue(medianP95 <= budgetNanos, () -> "filtered 100,000-row report median p95 was "
                + millis(medianP95) + " ms, budget is " + millis(budgetNanos)
                + " ms, batchP95s=" + sampleMillis(batchP95s));
    }

    private void printMetrics(String operation, List<Long> samples, long budgetNanos) {
        System.out.println("PERFORMANCE_METRICS " + operation
                + " p50=" + millis(percentile(samples, 50))
                + " p90=" + millis(percentile(samples, 90))
                + " p95=" + millis(percentile(samples, 95))
                + " p99=" + millis(percentile(samples, 99))
                + " min=" + millis(samples.stream().mapToLong(Long::longValue).min().orElseThrow())
                + " max=" + millis(samples.stream().mapToLong(Long::longValue).max().orElseThrow())
                + " average=" + String.format(Locale.ROOT, "%.1f",
                        samples.stream().mapToLong(Long::longValue).average().orElseThrow() / 1_000_000.0d)
                + " budget=" + millis(budgetNanos)
                + " samples=" + sampleMillis(samples));
    }

    private long percentile(List<Long> samples, int percentile) {
        long[] sorted = samples.stream().mapToLong(Long::longValue).sorted().toArray();
        int nearestRank = (int) Math.ceil(sorted.length * (percentile / 100.0d));
        return sorted[Math.max(0, nearestRank - 1)];
    }

    private String sampleMillis(List<Long> samples) {
        return Arrays.toString(samples.stream()
                .mapToDouble(value -> value / 1_000_000.0d)
                .map(value -> Math.round(value * 10.0d) / 10.0d)
                .toArray());
    }

    private String millis(long nanos) {
        return String.format(Locale.ROOT, "%.1f", nanos / 1_000_000.0d);
    }

    private record FinancialOwner(Long hotelId, Long reservationId, Hotel hotel, Reservation reservation) {
    }

    private record TimedCallback(long nanos, PropertyPaymentCallbackService.CallbackResult result) {
    }

    private record ReportMeasurement(long nanos, long rowCount) {
    }
}
