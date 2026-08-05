package com.hotel.migrations;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class OwnershipLifecycleMigrationTest {
    @Test
    void v87AddsSoftHistorySingleUseInvitationsAndSinglePendingTransferWithoutDataRewrite() throws Exception {
        try (var input = getClass().getResourceAsStream("/db/migration/V87__ownership_lifecycle.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();
            assertThat(sql).contains("SET ANSI_NULLS ON", "THROW 51087", "OWNER_INVITATIONS", "OWNERSHIP_TRANSFERS",
                    "TOKEN_HASH CHAR(64)", "ACCEPTED_AT", "LEFT_AT", "REMOVED_AT", "REMOVED_BY_USER_ID",
                    "OWNER_EXIT_REASON", "BILLING_ADMIN", "UX_OWNER_INVITATION_TOKEN_HASH",
                    "UX_OWNER_INVITATION_PENDING_EMAIL", "UX_OWNERSHIP_TRANSFER_PENDING_HOTEL");
            assertThat(sql).doesNotContain(" DELETE ", " UPDATE ");
        }
    }
}
