package com.hotel.integration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialBackfillSafetyIntegrationTest {

    @Test
    void preflightAndBackfillCaptureAmbiguousRowsBeforeLedgerInsert() throws Exception {
        String preflight = Files.readString(Path.of("src/main/resources/db/preflight/feature007_financial_preflight.sql"), StandardCharsets.UTF_8);
        String backfill = Files.readString(Path.of("src/main/resources/db/migration/V26__financial_context_backfill.sql"), StandardCharsets.UTF_8);
        assertTrue(preflight.contains("PROPERTY_PAYMENT_ORPHAN"));
        assertTrue(preflight.contains("THROW 51007"));
        assertTrue(backfill.contains("financial_migration_exceptions"));
        assertTrue(backfill.contains("PROPERTY_OWNER_UNRESOLVED"));
        assertTrue(backfill.contains("PLATFORM_TARGET_PROPERTY_UNRESOLVED"));
        assertTrue(backfill.contains("NOT EXISTS"));
    }
}
