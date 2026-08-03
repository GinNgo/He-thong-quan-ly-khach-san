package com.hotel.entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleSystemIntegrityTest {

    @Test
    void seededCodeRepairsStaleFlagAndInactiveStatusBeforePersistence() {
        Role role = role(" receptionist ", false, "INACTIVE");

        role.enforceSystemIntegrity();

        assertTrue(role.getSystemRole());
        assertEquals(Role.ACTIVE_STATUS, role.getStatus());
        assertTrue(role.isGovernedSystemRole());
    }

    @Test
    void explicitSystemFlagKeepsLegacySystemRoleActive() {
        Role role = role("LEGACY_PLATFORM_OPERATOR", true, "INACTIVE");

        role.enforceSystemIntegrity();

        assertTrue(role.getSystemRole());
        assertEquals(Role.ACTIVE_STATUS, role.getStatus());
    }

    @Test
    void customRoleLifecycleRemainsServiceControlled() {
        Role role = role("NIGHT_AUDITOR", false, "INACTIVE");

        role.enforceSystemIntegrity();

        assertFalse(role.getSystemRole());
        assertEquals("INACTIVE", role.getStatus());
        assertFalse(role.isGovernedSystemRole());
    }

    private Role role(String code, boolean systemRole, String status) {
        Role role = new Role();
        role.setCode(code);
        role.setSystemRole(systemRole);
        role.setStatus(status);
        return role;
    }
}
