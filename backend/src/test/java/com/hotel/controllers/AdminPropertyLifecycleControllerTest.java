package com.hotel.controllers;

import com.hotel.BackendApplication;
import com.hotel.config.SecurityConfig;
import com.hotel.dtos.PropertyLifecycleDecisionResponse;
import com.hotel.dtos.PropertyLifecycleSummary;
import com.hotel.dtos.PropertyReviewHistoryItem;
import com.hotel.observability.OperationalMetrics;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.security.JwtAccessDeniedHandler;
import com.hotel.security.JwtAuthFilter;
import com.hotel.security.JwtAuthenticationEntryPoint;
import com.hotel.security.JwtTokenProvider;
import com.hotel.security.TenantFilterInterceptor;
import com.hotel.services.PropertyLifecycleWorkflowService;
import com.hotel.services.PropertyReviewHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminPropertyLifecycleController.class)
@ContextConfiguration(classes = BackendApplication.class)
@Import({
        SecurityConfig.class,
        JwtAuthFilter.class,
        JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class
})
class AdminPropertyLifecycleControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private PropertyLifecycleWorkflowService workflowService;
    @MockBean private PropertyReviewHistoryService propertyReviewHistoryService;
    @MockBean private OperationalMetrics operationalMetrics;
    @MockBean private TenantFilterInterceptor tenantFilterInterceptor;
    @MockBean private UserDetailsService userDetailsService;

    @BeforeEach
    void allowRequestsThroughTenantInterceptor() throws Exception {
        when(tenantFilterInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void listReturnsTypedServerDerivedTransitions() throws Exception {
        when(workflowService.properties()).thenReturn(List.of(new PropertyLifecycleSummary(
                7L, "HARBOR", "Harbor Hotel", "12 Test Street", "HOTEL",
                "ACTIVE", "APPROVED", "ACTIVE", null, null, null, null,
                List.of("SUSPEND", "CLOSE"))));

        mockMvc.perform(get("/api/admin/properties/lifecycle")
                        .with(user(principal(99L, "ADMIN", ActionCode.VIEW))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].propertyId").value(7))
                .andExpect(jsonPath("$[0].allowedTransitions[0]").value("SUSPEND"))
                .andExpect(jsonPath("$[0].allowedTransitions[1]").value("CLOSE"));
    }

    @Test
    void adminHistoryReturnsSafeArrayWithViewPermission() throws Exception {
        when(propertyReviewHistoryService.adminHistory(7L)).thenReturn(List.of(
                new PropertyReviewHistoryItem(
                        501L,
                        7L,
                        "PROPERTY_SUSPENDED",
                        "ADMIN",
                        "Safety inspection is required.",
                        new PropertyReviewHistoryItem.StatusTriplet(
                                "ACTIVE", "APPROVED", "ACTIVE", "ACTIVE"),
                        new PropertyReviewHistoryItem.StatusTriplet(
                                "SUSPENDED", "APPROVED", "SUSPENDED", "ACTIVE"),
                        LocalDateTime.of(2026, 8, 4, 8, 0))));

        mockMvc.perform(get("/api/admin/properties/7/history")
                        .with(user(principal(99L, "ADMIN", ActionCode.VIEW))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].eventType").value("PROPERTY_SUSPENDED"))
                .andExpect(jsonPath("$[0].actorKind").value("ADMIN"))
                .andExpect(jsonPath("$[0].actorId").doesNotExist());
    }

    @Test
    void suspendUsesAuthoritativeActorAndTrimmedReason() throws Exception {
        when(workflowService.suspend(99L, 7L, "Safety inspection is required."))
                .thenReturn(new PropertyLifecycleDecisionResponse(
                        7L, "SUSPENDED", "APPROVED", "SUSPENDED", "SUSPEND", true,
                        99L, LocalDateTime.of(2026, 8, 4, 8, 30),
                        "Safety inspection is required."));

        mockMvc.perform(post("/api/admin/properties/7/suspend")
                        .with(user(principal(99L, "ADMIN", ActionCode.APPROVE)))
                        .contentType("application/json")
                        .content("{\"reason\":\"  Safety inspection is required.  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.actorUserId").value(99));

        verify(workflowService).suspend(99L, 7L, "Safety inspection is required.");
    }

    @Test
    void reasonValidationRunsBeforeWorkflow() throws Exception {
        mockMvc.perform(post("/api/admin/properties/7/close")
                        .with(user(principal(99L, "ADMIN", ActionCode.APPROVE)))
                        .contentType("application/json")
                        .content("{\"reason\":\" short \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(workflowService, never()).close(any(), any(), any());
    }

    @Test
    void adminWithoutLifecycleMaskIsDenied() throws Exception {
        mockMvc.perform(post("/api/admin/properties/7/reactivate")
                        .with(user(principal(99L, "ADMIN", 0)))
                        .contentType("application/json")
                        .content("{\"reason\":\"Inspection issues were resolved.\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_PERMISSION"));

        verify(workflowService, never()).reactivate(any(), any(), any());
    }

    @Test
    void customerCannotReadOrMutateLifecycle() throws Exception {
        CustomUserDetails customer = principal(7L, "CUSTOMER", ActionCode.VIEW | ActionCode.APPROVE);

        mockMvc.perform(get("/api/admin/properties/lifecycle").with(user(customer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/properties/7/close")
                        .with(user(customer))
                        .contentType("application/json")
                        .content("{\"reason\":\"Property operations ended permanently.\"}"))
                .andExpect(status().isForbidden());

        verify(workflowService, never()).properties();
        verify(workflowService, never()).close(any(), any(), any());
    }

    private CustomUserDetails principal(Long userId, String authority, int lifecycleMask) {
        Map<FunctionCode, Integer> permissions = lifecycleMask == 0
                ? Map.of()
                : Map.of(FunctionCode.PROPERTY_LIFECYCLE, lifecycleMask);
        return new CustomUserDetails(
                authority.toLowerCase() + "@example.test",
                "hash",
                List.of(new SimpleGrantedAuthority(authority)),
                permissions,
                userId,
                null,
                Map.of());
    }
}
