package com.hotel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionInterceptorTest {

    private PermissionInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @Mock
    private HandlerMethod handlerMethod;

    @Mock
    private Permission permissionAnnotation;

    @Mock
    private RequireFeature requireFeatureAnnotation;

    @BeforeEach
    void setUp() {
        interceptor = new PermissionInterceptor(new ObjectMapper());
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    private void setupSecurityContext(Object principal) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        if (principal instanceof CustomUserDetails) {
            context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, ((CustomUserDetails) principal).getAuthorities()));
        } else if (principal instanceof AnonymousAuthenticationToken) {
            context.setAuthentication((AnonymousAuthenticationToken) principal);
        } else if (principal != null) {
            context.setAuthentication(new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        }
        SecurityContextHolder.setContext(context);
    }

    private CustomUserDetails createMockUser(Map<FunctionCode, Integer> masks, Map<String, Integer> featureLimits) {
        return new CustomUserDetails("testuser", "password",
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                masks, 1L, 1L, featureLimits);
    }

    private CustomUserDetails createSuperAdminUser() {
        return new CustomUserDetails("admin", "password",
                List.of(new SimpleGrantedAuthority("SUPER_ADMIN")),
                Collections.emptyMap(), 1L, null, Collections.emptyMap());
    }

    @Test
    void T1_preHandle_hasCorrectPermissionMask_returnsTrue() throws Exception {
        when(handlerMethod.getMethodAnnotation(Permission.class)).thenReturn(permissionAnnotation);
        when(permissionAnnotation.function()).thenReturn(FunctionCode.RESERVATION);
        when(permissionAnnotation.action()).thenReturn(ActionCode.VIEW);
        when(handlerMethod.getMethodAnnotation(RequireFeature.class)).thenReturn(null);
        when(handlerMethod.getBeanType()).thenReturn((Class) PermissionInterceptorTest.class);

        CustomUserDetails user = createMockUser(
                Map.of(FunctionCode.RESERVATION, ActionCode.VIEW | ActionCode.CREATE),
                Collections.emptyMap()
        );
        setupSecurityContext(user);

        assertTrue(interceptor.preHandle(request, response, handlerMethod));
    }

    @Test
    void T2_preHandle_incorrectPermissionMask_returnsFalseAnd403() throws Exception {
        when(handlerMethod.getMethodAnnotation(Permission.class)).thenReturn(permissionAnnotation);
        when(permissionAnnotation.function()).thenReturn(FunctionCode.RESERVATION);
        when(permissionAnnotation.action()).thenReturn(ActionCode.UPDATE);
        when(handlerMethod.getMethodAnnotation(RequireFeature.class)).thenReturn(null);
        when(handlerMethod.getBeanType()).thenReturn((Class) PermissionInterceptorTest.class);

        CustomUserDetails user = createMockUser(
                Map.of(FunctionCode.RESERVATION, ActionCode.VIEW),
                Collections.emptyMap()
        );
        setupSecurityContext(user);

        assertFalse(interceptor.preHandle(request, response, handlerMethod));
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        assertTrue(response.getContentAsString().contains("FORBIDDEN_PERMISSION"));
        assertEquals("Bạn không có quyền chỉnh sửa đặt phòng.",
                new ObjectMapper().readTree(response.getContentAsByteArray()).get("message").asText());
    }

    @Test
    void T3_preHandle_isSuperAdmin_bypassesPermissionCheckAndReturnsTrue() throws Exception {
        when(handlerMethod.getMethodAnnotation(Permission.class)).thenReturn(permissionAnnotation);
        when(handlerMethod.getMethodAnnotation(RequireFeature.class)).thenReturn(null);
        when(handlerMethod.getBeanType()).thenReturn((Class) PermissionInterceptorTest.class);

        CustomUserDetails user = createSuperAdminUser();
        setupSecurityContext(user);

        assertTrue(interceptor.preHandle(request, response, handlerMethod));
    }

    @Test
    void T4_preHandle_featureGateMissingKey_returnsFalseAnd403() throws Exception {
        when(handlerMethod.getMethodAnnotation(Permission.class)).thenReturn(null);
        when(handlerMethod.getMethodAnnotation(RequireFeature.class)).thenReturn(requireFeatureAnnotation);
        when(requireFeatureAnnotation.value()).thenReturn("HOTEL");
        when(handlerMethod.getBeanType()).thenReturn((Class) PermissionInterceptorTest.class);

        CustomUserDetails user = createMockUser(
                Collections.emptyMap(),
                Collections.emptyMap()
        );
        setupSecurityContext(user);

        assertFalse(interceptor.preHandle(request, response, handlerMethod));
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        assertTrue(response.getContentAsString().contains("FORBIDDEN_FEATURE"));
    }

    @Test
    void T5_preHandle_featureGateUnlimited_returnsTrue() throws Exception {
        when(handlerMethod.getMethodAnnotation(Permission.class)).thenReturn(null);
        when(handlerMethod.getMethodAnnotation(RequireFeature.class)).thenReturn(requireFeatureAnnotation);
        when(requireFeatureAnnotation.value()).thenReturn("HOTEL");
        when(handlerMethod.getBeanType()).thenReturn((Class) PermissionInterceptorTest.class);

        CustomUserDetails user = createMockUser(
                Collections.emptyMap(),
                Map.of("HOTEL", -1)
        );
        setupSecurityContext(user);

        assertTrue(interceptor.preHandle(request, response, handlerMethod));
    }

    @Test
    void T6_preHandle_notCustomUserDetails_returnsFalseAnd403() throws Exception {
        when(handlerMethod.getMethodAnnotation(Permission.class)).thenReturn(permissionAnnotation);
        when(handlerMethod.getMethodAnnotation(RequireFeature.class)).thenReturn(null);
        when(handlerMethod.getBeanType()).thenReturn((Class) PermissionInterceptorTest.class);

        setupSecurityContext("justAStringPrincipal");

        assertFalse(interceptor.preHandle(request, response, handlerMethod));
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        assertTrue(response.getContentAsString().contains("ACCESS_DENIED"));
    }

    @Test
    void T7_preHandle_unauthenticated_returnsFalseAnd401() throws Exception {
        when(handlerMethod.getMethodAnnotation(Permission.class)).thenReturn(permissionAnnotation);
        when(handlerMethod.getMethodAnnotation(RequireFeature.class)).thenReturn(null);
        when(handlerMethod.getBeanType()).thenReturn((Class) PermissionInterceptorTest.class);

        SecurityContextHolder.clearContext();

        assertFalse(interceptor.preHandle(request, response, handlerMethod));
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentAsString().contains("UNAUTHORIZED"));
    }
}
