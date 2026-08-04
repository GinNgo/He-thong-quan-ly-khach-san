package com.hotel.services;

import com.hotel.entities.Room;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomAvailabilityPolicyTest {

    private final RoomAvailabilityPolicy policy = new RoomAvailabilityPolicy();

    @Test
    void currentPoolRequiresAvailableCleanOrInspectedAndNoMaintenance() {
        assertTrue(policy.isInAvailabilityPool(room("AVAILABLE", "CLEAN", "NONE"), false));
        assertTrue(policy.isInAvailabilityPool(room("AVAILABLE", "INSPECTED", "NONE"), false));

        for (String status : new String[]{"RESERVED", "OCCUPIED", "MAINTENANCE", "OUT_OF_SERVICE", "CLEANING"}) {
            assertFalse(policy.isInAvailabilityPool(room(status, "CLEAN", "NONE"), false));
        }
        for (String housekeeping : new String[]{"DIRTY", "CLEANING", "MAINTENANCE", null}) {
            assertFalse(policy.isInAvailabilityPool(room("AVAILABLE", housekeeping, "NONE"), false));
        }
        for (String maintenance : new String[]{"MAINTENANCE", "OUT_OF_SERVICE", null}) {
            assertFalse(policy.isInAvailabilityPool(room("AVAILABLE", "CLEAN", maintenance), false));
        }
    }

    @Test
    void datedPoolAddsReservedAndOccupiedButKeepsOperationalStateGuards() {
        for (String status : new String[]{"AVAILABLE", "RESERVED", "OCCUPIED"}) {
            assertTrue(policy.isInAvailabilityPool(room(status, "CLEAN", "NONE"), true));
            assertTrue(policy.isInAvailabilityPool(room(status, "INSPECTED", "NONE"), true));
        }
        for (String status : new String[]{"MAINTENANCE", "OUT_OF_SERVICE", "CLEANING", "DIRTY"}) {
            assertFalse(policy.isInAvailabilityPool(room(status, "CLEAN", "NONE"), true));
        }
        assertFalse(policy.isInAvailabilityPool(room("RESERVED", "DIRTY", "NONE"), true));
        assertFalse(policy.isInAvailabilityPool(room("OCCUPIED", "CLEAN", "MAINTENANCE"), true));
    }

    @Test
    void nativeSqlPredicateMatchesTheSameExplicitStateSets() {
        String current = policy.sqlPredicate("r", false);
        String dated = policy.sqlPredicate("r", true);

        assertTrue(current.contains("r.status IN ('AVAILABLE')"));
        assertTrue(dated.contains("r.status IN ('AVAILABLE','RESERVED','OCCUPIED')"));
        assertTrue(current.contains("r.housekeeping_status IN ('CLEAN','INSPECTED')"));
        assertTrue(dated.contains("r.maintenance_status='NONE'"));
    }

    private Room room(String status, String housekeepingStatus, String maintenanceStatus) {
        Room room = new Room();
        room.setStatus(status);
        room.setHousekeepingStatus(housekeepingStatus);
        room.setMaintenanceStatus(maintenanceStatus);
        return room;
    }
}
