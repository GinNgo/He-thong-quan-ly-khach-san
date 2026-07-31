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
            "V29__financial_idempotency.sql",
            "V30__booking_deposit_policy_snapshot.sql",
            "V31__property_attempt_transfer_content_uniqueness.sql",
            "V32__credit_note_line_tenant_ownership.sql",
            "V33__housekeeping_checkout_idempotency.sql");

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

    @Test
    void bookingDepositSnapshotMigrationIsAdditiveAndConstrained() throws IOException {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V30__booking_deposit_policy_snapshot.sql"),
                StandardCharsets.UTF_8);
        assertTrue(sql.contains("COL_LENGTH('dbo.reservations', 'deposit_policy_type') IS NULL"));
        assertTrue(sql.contains("deposit_configuration_version"));
        assertTrue(sql.contains("deposit_booking_total"));
        assertTrue(sql.contains("deposit_required"));
        assertTrue(sql.contains("deposit_currency = 'VND'"));
        assertTrue(sql.contains("deposit_required <= deposit_booking_total"));
        assertFalse(sql.contains("DROP COLUMN"));
    }

    @Test
    void propertyAttemptTransferContentMigrationFailsClosedBeforeAddingUniqueIndex() throws IOException {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V31__property_attempt_transfer_content_uniqueness.sql"),
                StandardCharsets.UTF_8);
        assertTrue(sql.contains("HAVING COUNT_BIG(*) > 1"));
        assertTrue(sql.contains("THROW 51031"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX UX_property_attempt_transfer_content"));
        assertTrue(sql.contains("hotel_id, unique_transfer_content"));
        assertTrue(sql.contains("WHERE unique_transfer_content IS NOT NULL"));
        assertFalse(sql.contains("DELETE FROM"));
        assertFalse(sql.contains("DROP TABLE"));
    }

    @Test
    void creditNoteLineOwnershipMigrationBackfillsAndFailsClosed() throws IOException {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V32__credit_note_line_tenant_ownership.sql"),
                StandardCharsets.UTF_8);
        assertTrue(sql.contains("ADD hotel_id BIGINT NULL"));
        assertTrue(sql.contains("SET hotel_id = note.hotel_id"));
        assertTrue(sql.contains("THROW 51032"));
        assertTrue(sql.contains("THROW 51033"));
        assertTrue(sql.contains("THROW 51034"));
        assertTrue(sql.contains("ALTER COLUMN hotel_id BIGINT NOT NULL"));
        assertTrue(sql.contains("FK_property_credit_note_line_hotel"));
        assertTrue(sql.contains("hotel_id, credit_note_id, invoice_line_id"));
        assertFalse(sql.contains("DELETE FROM"));
        assertFalse(sql.contains("DROP TABLE"));
    }

    @Test
    void housekeepingCheckoutMigrationAddsAUniqueTenantEffectKey() throws IOException {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V33__housekeeping_checkout_idempotency.sql"),
                StandardCharsets.UTF_8);
        assertTrue(sql.contains("ADD checkout_effect_key VARCHAR(120) NULL"));
        assertTrue(sql.contains("HAVING COUNT_BIG(*) > 1"));
        assertTrue(sql.contains("THROW 51035"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX UX_housekeeping_checkout_effect"));
        assertTrue(sql.contains("hotel_id, checkout_effect_key"));
        assertTrue(sql.contains("WHERE checkout_effect_key IS NOT NULL"));
        assertFalse(sql.contains("DELETE FROM"));
        assertFalse(sql.contains("DROP TABLE"));
    }
}
