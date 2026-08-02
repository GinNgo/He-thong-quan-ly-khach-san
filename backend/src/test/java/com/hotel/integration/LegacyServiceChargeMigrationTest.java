package com.hotel.integration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyServiceChargeMigrationTest {

    @Test
    void migrationBackfillsWithStableIdentityAndFailsClosedOnAmbiguousRows() throws IOException {
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V43__legacy_service_charge_reconciliation.sql"),
                StandardCharsets.UTF_8);

        assertThat(migration).contains("legacy_service_item_id");
        assertThat(migration).contains("LEGACY-SERVICE-ITEM:");
        assertThat(migration).contains("THROW 51043");
        assertThat(migration).contains("UX_charge_lines_legacy_service_item");
        assertThat(migration).contains("legacy.total_amount <> legacy.price * legacy.quantity");
    }
}
