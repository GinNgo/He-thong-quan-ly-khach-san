package com.hotel.security;

import com.hotel.controllers.ManagementPortalController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagementPortalAuthorizationTest {

    @Test
    void propertyCreationIsRestrictedToOwnersAndSuperAdmins() throws Exception {
        Method method = method("createProperty");
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);

        assertNotNull(authorization);
        assertTrue(authorization.value().contains("PROPERTY_OWNER"));
        assertTrue(authorization.value().contains("SUPER_ADMIN"));
        assertTrue(!authorization.value().contains("HOUSEKEEPING"));
        assertTrue(!authorization.value().contains("HOTEL_MANAGER"));
    }

    @Test
    void inventoryEndpointsRequireActionLevelPermissions() throws Exception {
        Map<String, ExpectedPermission> expected = Map.of(
                "roomTypes", new ExpectedPermission(FunctionCode.ROOM_TYPE, ActionCode.VIEW),
                "createRoomType", new ExpectedPermission(FunctionCode.ROOM_TYPE, ActionCode.CREATE),
                "updateRoomType", new ExpectedPermission(FunctionCode.ROOM_TYPE, ActionCode.UPDATE),
                "rooms", new ExpectedPermission(FunctionCode.ROOM, ActionCode.VIEW),
                "createRoom", new ExpectedPermission(FunctionCode.ROOM, ActionCode.CREATE),
                "bulkRooms", new ExpectedPermission(FunctionCode.ROOM, ActionCode.CREATE),
                "updateRoom", new ExpectedPermission(FunctionCode.ROOM, ActionCode.UPDATE),
                "startRoomMaintenance", new ExpectedPermission(FunctionCode.ROOM, ActionCode.UPDATE),
                "completeRoomMaintenance", new ExpectedPermission(FunctionCode.ROOM, ActionCode.UPDATE)
        );

        for (Map.Entry<String, ExpectedPermission> entry : expected.entrySet()) {
            Permission permission = method(entry.getKey()).getAnnotation(Permission.class);
            assertNotNull(permission, entry.getKey() + " must declare @Permission");
            assertEquals(entry.getValue().function(), permission.function(), entry.getKey());
            assertEquals(entry.getValue().action(), permission.action(), entry.getKey());
        }
    }

    private Method method(String name) {
        return java.util.Arrays.stream(ManagementPortalController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private record ExpectedPermission(FunctionCode function, int action) {
    }
}
