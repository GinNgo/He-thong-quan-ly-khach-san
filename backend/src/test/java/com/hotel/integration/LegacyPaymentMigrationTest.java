package com.hotel.integration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyPaymentMigrationTest {

    @Test
    void migrationPreservesEvidenceAndQuarantinesUnverifiedSettlement() throws Exception {
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V44__legacy_payment_reconciliation.sql"),
                StandardCharsets.UTF_8);

        assertThat(migration).contains("legacy_reconciliation_required");
        assertThat(migration).contains("LEGACY_PAYMENT_INVALID");
        assertThat(migration).contains("LEGACY_REFUND_UNLINKED");
        assertThat(migration).contains("LEGACY_SETTLEMENT_UNVERIFIED");
        assertThat(migration).contains("session_row.expected_amount = payment.amount");
        assertThat(migration).contains("session_row.method");
        assertThat(migration).contains("session_row.provider_transaction_id");
        assertThat(migration).contains("ledger.provider_transaction_ref");
        assertThat(migration).contains("source_status=");
        assertThat(migration).contains("WHERE legacy_reconciliation_required = 1");
        assertThat(migration).doesNotContain("DELETE FROM");
        assertThat(migration).doesNotContain("DROP TABLE");
    }
}
