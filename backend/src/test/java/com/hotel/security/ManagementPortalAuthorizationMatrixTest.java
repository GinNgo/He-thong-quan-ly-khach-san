package com.hotel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.controllers.ManagementPortalController;
import com.hotel.dtos.BulkRoomRequest;
import com.hotel.dtos.PropertyProfileDTO;
import com.hotel.dtos.PropertyProfileUpdateRequest;
import com.hotel.dtos.RoomDTO;
import com.hotel.dtos.RoomTypeDTO;
import com.hotel.services.HotelManagementService;
import com.hotel.services.ManagementPortalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ManagementPortalAuthorizationMatrixTest {
    private final PermissionInterceptor interceptor = new PermissionInterceptor(new ObjectMapper());
    private final ManagementPortalController controller = new ManagementPortalController(
            mock(ManagementPortalService.class), mock(HotelManagementService.class));

    @AfterEach
    void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test
    void everyManagementEndpointDeclaresItsDirectApiFunctionAndAction() throws Exception {
        assertPermission("context", FunctionCode.HOTEL, ActionCode.VIEW, Long.class);
        assertPermission("properties", FunctionCode.HOTEL, ActionCode.VIEW);
        assertPermission("property", FunctionCode.HOTEL, ActionCode.VIEW, Long.class);
        assertPermission("createProperty", FunctionCode.HOTEL, ActionCode.CREATE, PropertyProfileDTO.class);
        assertPermission("updateProperty", FunctionCode.HOTEL, ActionCode.UPDATE, Long.class, PropertyProfileUpdateRequest.class);
        assertPermission("roomTypes", FunctionCode.ROOM_TYPE, ActionCode.VIEW, Long.class);
        assertPermission("createRoomType", FunctionCode.ROOM_TYPE, ActionCode.CREATE, RoomTypeDTO.class);
        assertPermission("updateRoomType", FunctionCode.ROOM_TYPE, ActionCode.UPDATE, Long.class, RoomTypeDTO.class);
        assertPermission("deleteRoomType", FunctionCode.ROOM_TYPE, ActionCode.DELETE, Long.class);
        assertPermission("rooms", FunctionCode.ROOM, ActionCode.VIEW, Long.class);
        assertPermission("createRoom", FunctionCode.ROOM, ActionCode.CREATE, RoomDTO.class);
        assertPermission("bulkRooms", FunctionCode.ROOM, ActionCode.CREATE, BulkRoomRequest.class);
        assertPermission("updateRoom", FunctionCode.ROOM, ActionCode.UPDATE, Long.class, RoomDTO.class);
        assertPermission("startRoomMaintenance", FunctionCode.ROOM, ActionCode.UPDATE, Long.class);
        assertPermission("completeRoomMaintenance", FunctionCode.ROOM, ActionCode.UPDATE, Long.class);
        assertPermission("deleteRoom", FunctionCode.ROOM, ActionCode.DELETE, Long.class);
        assertPermission("completeHousekeeping", FunctionCode.ROOM, ActionCode.UPDATE, Long.class);
    }

    @Test
    void eachActionRequiresItsExactBitAndUnrelatedFunctionIsDenied() throws Exception {
        assertActionBoundary("createProperty", new Class<?>[]{PropertyProfileDTO.class}, FunctionCode.HOTEL, ActionCode.CREATE);
        assertActionBoundary("updateProperty", new Class<?>[]{Long.class, PropertyProfileUpdateRequest.class}, FunctionCode.HOTEL, ActionCode.UPDATE);
        assertActionBoundary("deleteRoomType", new Class<?>[]{Long.class}, FunctionCode.ROOM_TYPE, ActionCode.DELETE);
        assertActionBoundary("bulkRooms", new Class<?>[]{BulkRoomRequest.class}, FunctionCode.ROOM, ActionCode.CREATE);
        assertActionBoundary("completeHousekeeping", new Class<?>[]{Long.class}, FunctionCode.ROOM, ActionCode.UPDATE);
    }

    private void assertPermission(String name, FunctionCode function, int action, Class<?>... parameters) throws Exception {
        Permission permission = ManagementPortalController.class.getMethod(name, parameters).getAnnotation(Permission.class);
        assertNotNull(permission, name + " must declare @Permission");
        assertEquals(function, permission.function(), name);
        assertEquals(action, permission.action(), name);
    }

    private void assertActionBoundary(String name, Class<?>[] parameters, FunctionCode function, int action) throws Exception {
        HandlerMethod handler = new HandlerMethod(controller, ManagementPortalController.class.getMethod(name, parameters));
        authenticate(Map.of(function, ActionCode.VIEW));
        assertFalse(interceptor.preHandle(new MockHttpServletRequest("POST", "/api/management/test"),
                new MockHttpServletResponse(), handler), name + " accepted VIEW for a mutation");
        authenticate(Map.of(FunctionCode.SYSTEM, action));
        assertFalse(interceptor.preHandle(new MockHttpServletRequest("POST", "/api/management/test"),
                new MockHttpServletResponse(), handler), name + " accepted an unrelated function");
        authenticate(Map.of(function, action));
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("POST", "/api/management/test"),
                new MockHttpServletResponse(), handler), name + " rejected its exact permission");
    }

    private void authenticate(Map<FunctionCode, Integer> permissions) {
        CustomUserDetails user = new CustomUserDetails("manager", "password",
                List.of(new SimpleGrantedAuthority("ROLE_HOTEL_MANAGER")), permissions, 10L, 20L, Map.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
