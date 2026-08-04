package com.hotel.platformbilling.subscription;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class SubscriptionPlanGovernanceMigrationTest {
    @Test void migrationIsNonDestructiveVersionedAndEnforcesCatalogInvariants() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V89__subscription_plan_version_governance.sql"));
        assertTrue(sql.contains("UQ_subscription_plan_family_version"));
        assertTrue(sql.contains("UX_subscription_plan_active_family"));
        assertTrue(sql.contains("UX_plan_feature_code"));
        assertTrue(sql.contains("subscription_plan_admin_operations"));
        assertTrue(sql.contains("THROW 51000"));
        assertFalse(sql.toUpperCase().contains("DROP TABLE"));
        assertFalse(sql.toUpperCase().contains("DELETE FROM"));
    }
}
