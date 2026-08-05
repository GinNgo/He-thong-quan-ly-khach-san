package com.hotel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.controllers.HotelServiceController;
import com.hotel.dtos.HotelServiceDTO;
import com.hotel.services.HotelServiceLogic;
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

class HotelServiceAuthorizationParityTest {
    private final PermissionInterceptor interceptor = new PermissionInterceptor(new ObjectMapper());
    private final HotelServiceController controller = new HotelServiceController(mock(HotelServiceLogic.class));

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void everyServiceRouteUsesDedicatedFunctionAndMatchingAction() throws Exception {
        assertPermission("getAllServices", new Class<?>[]{Long.class}, ActionCode.VIEW);
        assertPermission("getServiceById", new Class<?>[]{Long.class}, ActionCode.VIEW);
        assertPermission("createService", new Class<?>[]{Long.class, HotelServiceDTO.class}, ActionCode.CREATE);
        assertPermission("updateService", new Class<?>[]{Long.class, HotelServiceDTO.class}, ActionCode.UPDATE);
        assertPermission("deleteService", new Class<?>[]{Long.class, String.class}, ActionCode.DELETE);
    }

    @Test
    void hotelPermissionCannotAuthorizeServiceRead() throws Exception {
        authenticate(Map.of(FunctionCode.HOTEL, ActionCode.VIEW));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request("GET", "/api/services"), response,
                handler("getAllServices", Long.class)));
        assertTrue(response.getContentAsString().contains("FORBIDDEN_PERMISSION"));
    }

    @Test
    void viewCannotAuthorizeCreateButCreateCan() throws Exception {
        HandlerMethod create = handler("createService", Long.class, HotelServiceDTO.class);
        authenticate(Map.of(FunctionCode.HOTEL_SERVICE, ActionCode.VIEW));
        assertFalse(interceptor.preHandle(request("POST", "/api/services"),
                new MockHttpServletResponse(), create));

        authenticate(Map.of(FunctionCode.HOTEL_SERVICE, ActionCode.CREATE));
        assertTrue(interceptor.preHandle(request("POST", "/api/services"),
                new MockHttpServletResponse(), create));
    }

    @Test
    void updateCannotAuthorizeDeleteButDeleteCan() throws Exception {
        HandlerMethod delete = handler("deleteService", Long.class, String.class);
        authenticate(Map.of(FunctionCode.HOTEL_SERVICE, ActionCode.UPDATE));
        assertFalse(interceptor.preHandle(request("DELETE", "/api/services/1"),
                new MockHttpServletResponse(), delete));

        authenticate(Map.of(FunctionCode.HOTEL_SERVICE, ActionCode.DELETE));
        assertTrue(interceptor.preHandle(request("DELETE", "/api/services/1"),
                new MockHttpServletResponse(), delete));
    }

    private void assertPermission(String name, Class<?>[] parameters, int action) throws Exception {
        Method method = HotelServiceController.class.getMethod(name, parameters);
        Permission permission = method.getAnnotation(Permission.class);
        assertNotNull(permission);
        assertEquals(FunctionCode.HOTEL_SERVICE, permission.function());
        assertEquals(action, permission.action());
    }

    private HandlerMethod handler(String name, Class<?>... parameters) throws NoSuchMethodException {
        return new HandlerMethod(controller, HotelServiceController.class.getMethod(name, parameters));
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }

    private void authenticate(Map<FunctionCode, Integer> permissions) {
        CustomUserDetails user = new CustomUserDetails("manager", "password",
                List.of(new SimpleGrantedAuthority("ROLE_HOTEL_MANAGER")), permissions, 10L, 20L, Map.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
