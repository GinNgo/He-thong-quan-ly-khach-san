package com.hotel.migrations;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyClaimOwnershipUniquenessMigrationTest {

    @Test
    void v74FailsClosedAndCreatesOnlyTheIntendedFilteredUniquenessConstraints() throws IOException {
        String sql;
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V74__property_claim_ownership_uniqueness.sql")) {
            assertTrue(input != null, "V74 migration must be packaged");
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();
        }

        for (String option : new String[]{
                "SET ANSI_NULLS ON", "SET QUOTED_IDENTIFIER ON", "SET ANSI_PADDING ON",
                "SET ANSI_WARNINGS ON", "SET ARITHABORT ON", "SET CONCAT_NULL_YIELDS_NULL ON",
                "SET NUMERIC_ROUNDABORT OFF"}) {
            assertTrue(sql.contains(option), option);
        }
        assertTrue(sql.contains("OBJECT_ID('DBO.PROPERTY_CLAIM_REQUESTS', 'U')"));
        assertTrue(sql.contains("COL_LENGTH('DBO.USER_PROPERTIES', 'IS_PRIMARY_OWNER')"));
        assertTrue(sql.contains("HAVING COUNT_BIG(*) > 1"));
        assertTrue(sql.contains("SYS.INDEX_COLUMNS"));
        assertTrue(sql.contains("I.IS_UNIQUE = 1"));
        assertEquals(3, occurrences(sql, "CREATE UNIQUE INDEX"));
        assertTrue(sql.contains("ON DBO.PROPERTY_CLAIM_REQUESTS(PROPERTY_ID, REQUESTER_USER_ID)"));
        assertTrue(sql.contains("ON DBO.USER_PROPERTIES(USER_ID, HOTEL_ID)"));
        assertTrue(sql.contains("ON DBO.USER_PROPERTIES(HOTEL_ID)"));
        assertFalse(sql.matches("(?s).*\\b(UPDATE|DELETE)\\b.*"));
        assertFalse(sql.contains("ON DBO.PROPERTY_CLAIM_REQUESTS(PROPERTY_ID)\n"),
                "Different requesters must be allowed to claim the same property concurrently");
    }

    private int occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }
}
