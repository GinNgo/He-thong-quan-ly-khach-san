package com.hotel.security;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomTypeAuthorizationParityTest {
    @Test
    void managementRoomTypeRoutesUseTheSameActionPermissionsAsDirectRoutes() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hotel/controllers/ManagementPortalController.java"), StandardCharsets.UTF_8);
        assertTrue(source.contains("@Permission(function = FunctionCode.ROOM_TYPE, action = ActionCode.VIEW)"));
        assertTrue(source.contains("@Permission(function = FunctionCode.ROOM_TYPE, action = ActionCode.CREATE)"));
        assertTrue(source.contains("@Permission(function = FunctionCode.ROOM_TYPE, action = ActionCode.UPDATE)"));
        assertTrue(source.contains("@Permission(function = FunctionCode.ROOM_TYPE, action = ActionCode.DELETE)"));
        assertTrue(source.contains("@DeleteMapping(\"/room-types/{id}\")"));
    }
}
