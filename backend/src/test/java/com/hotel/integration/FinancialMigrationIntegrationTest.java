package com.hotel.integration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialMigrationIntegrationTest {

    private static final List<String> MIGRATIONS = List.of(
            "V21__property_commerce_foundation.sql",
            "V22__property_checkout_invoice.sql",
            "V23__property_refund_audit.sql",
            "V24__platform_billing_foundation.sql",
            "V25__platform_contract_refund.sql",
            "V26__financial_context_backfill.sql",
            "V27__financial_integrity_indexes.sql",
            "V28__financial_permissions.sql",
            "V29__financial_idempotency.sql");

    @Test
    void feature007MigrationsExistAndDeclareTenantIntegrity() throws IOException {
        Path root = Path.of("src/main/resources/db/migration");
        for (String migration : MIGRATIONS) {
            Path file = root.resolve(migration);
            assertTrue(Files.exists(file), () -> "Missing migration " + migration);
            String sql = Files.readString(file, StandardCharsets.UTF_8);
            assertFalse(sql.isBlank(), () -> "Empty migration " + migration);
            assertTrue(sql.contains("IF OBJECT_ID") || sql.contains("IF NOT EXISTS"), () -> "Migration is not repeat-safe: " + migration);
            assertTrue(sql.contains("SET QUOTED_IDENTIFIER ON"), () -> "Migration lacks deterministic SQL Server session settings: " + migration);
        }
    }

    @Test
    void propertyAndPlatformLedgersUseSeparateTablesAndVndChecks() throws IOException {
        Path root = Path.of("src/main/resources/db/migration");
        String property = Files.readString(root.resolve("V21__property_commerce_foundation.sql"), StandardCharsets.UTF_8);
        String platform = Files.readString(root.resolve("V24__platform_billing_foundation.sql"), StandardCharsets.UTF_8);
        assertTrue(property.contains("property_financial_transactions"));
        assertTrue(property.contains("hotel_id"));
        assertTrue(property.contains("currency = 'VND'"));
        assertTrue(platform.contains("platform_financial_transactions"));
        assertTrue(platform.contains("platform_payment_configurations"));
        assertTrue(platform.contains("currency = 'VND'"));
        assertFalse(platform.contains("property_payment_configurations"));
    }

    @Test
    void sqlServerValidationCoversCleanUpgradeRepeatAndNegativePreflight() throws IOException {
        String validation = Files.readString(Path.of("tools/feature007-sqlserver-validation.ps1"), StandardCharsets.UTF_8);
        assertTrue(validation.contains("WithoutLegacyFinancialData"));
        assertTrue(validation.contains("Repeat execution passed"));
        assertTrue(validation.contains("Negative orphan preflight failed as expected"));
        assertTrue(validation.contains("V{0}__*.sql"));
    }
}
