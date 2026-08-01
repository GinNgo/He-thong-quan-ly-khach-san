package com.hotel.platformbilling.reporting;

import com.hotel.BackendApplication;
import com.hotel.paymentprovider.reporting.RevenueReportModels.FinancialContext;
import com.hotel.paymentprovider.reporting.RevenueReportModels.NormalizedFilters;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RecognitionBasis;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@Import(PlatformRevenueRepository.class)
@ContextConfiguration(classes = BackendApplication.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:platform-revenue-query;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class PlatformRevenueRepositoryTest {

    @Autowired
    private PlatformRevenueRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void executesEverySystemScopedSourceQueryForAnEmptyPlatformScope() {
        PlatformRevenueRepository.PlatformRevenueSource source = repository.load(filters(null, null, null, null));

        assertEquals(0, source.transactions().size());
        assertEquals(0, source.attempts().size());
        assertEquals(0, source.orders().size());
        assertEquals(0, source.entitlements().size());
    }

    @Test
    void executesProviderMethodTransactionAndPlanFiltersWithoutPropertyScope() {
        PlatformRevenueRepository.PlatformRevenueSource source = repository.load(
                filters("momo", "qr", "subscription_purchase", "pro"));

        assertEquals(0, source.transactions().size());
        assertEquals(0, source.attempts().size());
        assertEquals(0, source.orders().size());
    }

    @Test
    void rejectsPropertyContextAndPropertyOnlyFilters() {
        assertThrows(IllegalArgumentException.class, () -> repository.load(new NormalizedFilters(
                FinancialContext.PROPERTY_COMMERCE,
                RecognitionBasis.NET,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                "UTC", 42L, null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> repository.load(new NormalizedFilters(
                FinancialContext.PLATFORM_BILLING,
                RecognitionBasis.NET,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                "UTC", null, null, null, null, "DELUXE", null)));
    }

    private NormalizedFilters filters(String provider, String method, String transactionType, String planCode) {
        return new NormalizedFilters(
                FinancialContext.PLATFORM_BILLING,
                RecognitionBasis.NET,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                "UTC", null, provider, method, transactionType, null, planCode);
    }
}
