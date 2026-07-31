package com.hotel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialPermissionIntegrationTest {

    private final PermissionInterceptor interceptor = new PermissionInterceptor(new ObjectMapper());

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void financialMutationRequiresItsDedicatedFunctionAndAction() throws Exception {
        HandlerMethod handler = new HandlerMethod(new FinancialController(),
                FinancialController.class.getMethod("approveRefund"));
        authenticate(Map.of(FunctionCode.PROPERTY_REFUND, ActionCode.VIEW));
        MockHttpServletResponse denied = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(new MockHttpServletRequest("POST", "/api/refunds/1/approve"), denied, handler));
        assertTrue(denied.getContentAsString().contains("FORBIDDEN_PERMISSION"));

        authenticate(Map.of(FunctionCode.PROPERTY_REFUND, ActionCode.APPROVE));
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("POST", "/api/refunds/1/approve"),
                new MockHttpServletResponse(), handler));
    }

    @Test
    void platformBillingPermissionCannotAuthorizePropertyConfiguration() throws Exception {
        HandlerMethod handler = new HandlerMethod(new FinancialController(),
                FinancialController.class.getMethod("configurePropertyPayment"));
        authenticate(Map.of(FunctionCode.PLATFORM_BILLING, ActionCode.UPDATE));

        assertFalse(interceptor.preHandle(new MockHttpServletRequest("PUT", "/api/management/payment-config"),
                new MockHttpServletResponse(), handler));
    }

    private void authenticate(Map<FunctionCode, Integer> permissions) {
        CustomUserDetails user = new CustomUserDetails("owner", "password",
                List.of(new SimpleGrantedAuthority("ROLE_OWNER")), permissions, 10L, 20L, Map.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    static class FinancialController {
        @Permission(function = FunctionCode.PROPERTY_REFUND, action = ActionCode.APPROVE)
        public void approveRefund() {
        }

        @Permission(function = FunctionCode.PROPERTY_PAYMENT_CONFIG, action = ActionCode.UPDATE)
        public void configurePropertyPayment() {
        }
    }
}
