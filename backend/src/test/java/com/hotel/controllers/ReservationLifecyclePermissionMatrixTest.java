package com.hotel.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.dtos.AssignRoomsRequest;
import com.hotel.paymentprovider.idempotency.MutationIdempotencyService;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.security.PermissionInterceptor;
import com.hotel.services.ReservationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationLifecyclePermissionMatrixTest {

    @Mock private ReservationService reservationService;
    @Mock private MutationIdempotencyService mutationIdempotencyService;

    private ReservationController controller;
    private PermissionInterceptor permissionInterceptor;

    @BeforeEach
    void setUp() {
        controller = new ReservationController(reservationService, mutationIdempotencyService);
        permissionInterceptor = new PermissionInterceptor(new ObjectMapper());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void dedicatedEndpointsDeclareIndependentActionPermissions() throws Exception {
        assertPermission("assignRooms", FunctionCode.RESERVATION_ASSIGNMENT, ActionCode.UPDATE,
                Long.class, AssignRoomsRequest.class);
        assertPermission("availableRooms", FunctionCode.RESERVATION_ASSIGNMENT, ActionCode.VIEW, Long.class);
        assertPermission("checkIn", FunctionCode.CHECKIN, ActionCode.UPDATE, Long.class);
        assertPermission("cancelOperational", FunctionCode.RESERVATION_CANCEL, ActionCode.UPDATE, Long.class);
        assertPermission("markNoShow", FunctionCode.RESERVATION_NO_SHOW, ActionCode.UPDATE, Long.class);
    }

    @Test
    void receptionistWithExactActionIsAllowedWhileAccountantMaskIsDenied() throws Exception {
        for (EndpointPermission endpoint : operationalEndpoints()) {
            authenticate("RECEPTIONIST", Map.of(endpoint.function(), endpoint.action()));
            assertThat(intercept(endpoint.method())).isTrue();

            authenticate("ACCOUNTANT", Map.of(FunctionCode.RESERVATION, ActionCode.VIEW));
            MockHttpServletResponse denied = new MockHttpServletResponse();
            assertThat(permissionInterceptor.preHandle(
                    request(endpoint.method()), denied, new HandlerMethod(controller, endpoint.method())))
                    .isFalse();
            assertThat(denied.getStatus()).isEqualTo(403);
            assertThat(denied.getContentAsString()).contains("FORBIDDEN_PERMISSION");
        }
    }

    @Test
    void superAdminBypassesDedicatedMasks() throws Exception {
        authenticate("SUPER_ADMIN", Map.of());

        for (EndpointPermission endpoint : operationalEndpoints()) {
            assertThat(intercept(endpoint.method())).isTrue();
        }
    }

    @Test
    void genericStatusEndpointRejectsDedicatedLifecycleTransitions() {
        assertThatThrownBy(() -> controller.updateStatus(7L, "CHECKED_IN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dedicated reservation lifecycle endpoint");
        assertThatThrownBy(() -> controller.updateStatus(7L, "cancelled"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.updateStatus(7L, "NO_SHOW"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(reservationService, never()).updateReservationStatus(7L, "CHECKED_IN");
    }

    @Test
    void dedicatedControllerCommandsDelegateWithoutGenericStatusMutation() {
        controller.checkIn(11L);
        controller.cancelOperational(12L);
        controller.markNoShow(13L);

        verify(reservationService).checkIn(11L);
        verify(reservationService).cancelOperational(12L);
        verify(reservationService).markNoShow(13L);
    }

    private void assertPermission(
            String methodName,
            FunctionCode function,
            int action,
            Class<?>... parameterTypes) throws Exception {
        Permission permission = ReservationController.class
                .getMethod(methodName, parameterTypes)
                .getAnnotation(Permission.class);
        assertThat(permission).isNotNull();
        assertThat(permission.function()).isEqualTo(function);
        assertThat(permission.action()).isEqualTo(action);
    }

    private List<EndpointPermission> operationalEndpoints() throws Exception {
        return List.of(
                endpoint("assignRooms", FunctionCode.RESERVATION_ASSIGNMENT, ActionCode.UPDATE,
                        Long.class, AssignRoomsRequest.class),
                endpoint("checkIn", FunctionCode.CHECKIN, ActionCode.UPDATE, Long.class),
                endpoint("cancelOperational", FunctionCode.RESERVATION_CANCEL, ActionCode.UPDATE, Long.class),
                endpoint("markNoShow", FunctionCode.RESERVATION_NO_SHOW, ActionCode.UPDATE, Long.class));
    }

    private EndpointPermission endpoint(
            String name,
            FunctionCode function,
            int action,
            Class<?>... parameterTypes) throws Exception {
        return new EndpointPermission(
                ReservationController.class.getMethod(name, parameterTypes), function, action);
    }

    private boolean intercept(Method method) throws Exception {
        return permissionInterceptor.preHandle(
                request(method), new MockHttpServletResponse(), new HandlerMethod(controller, method));
    }

    private MockHttpServletRequest request(Method method) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/reservations/lifecycle/" + method.getName());
        return request;
    }

    private void authenticate(String role, Map<FunctionCode, Integer> masks) {
        CustomUserDetails principal = new CustomUserDetails(
                role.toLowerCase(),
                "password",
                List.of(new SimpleGrantedAuthority(role)),
                masks,
                1L,
                1L,
                Map.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private record EndpointPermission(Method method, FunctionCode function, int action) {
    }
}
