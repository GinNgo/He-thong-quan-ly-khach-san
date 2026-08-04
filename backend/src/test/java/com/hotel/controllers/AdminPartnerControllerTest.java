package com.hotel.controllers;

import com.hotel.BackendApplication;
import com.hotel.config.SecurityConfig;
import com.hotel.dtos.PropertyApprovalDecisionResponse;
import com.hotel.dtos.PropertyApprovalQueueItem;
import com.hotel.observability.OperationalMetrics;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.JwtAccessDeniedHandler;
import com.hotel.security.JwtAuthFilter;
import com.hotel.security.JwtAuthenticationEntryPoint;
import com.hotel.security.JwtTokenProvider;
import com.hotel.security.TenantFilterInterceptor;
import com.hotel.services.PropertyApprovalWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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

@WebMvcTest(AdminPartnerController.class)
@ContextConfiguration(classes = BackendApplication.class)
@Import({
        SecurityConfig.class,
        JwtAuthFilter.class,
        JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class
})
class AdminPartnerControllerTest {

    private static final LocalDateTime REVIEWED_AT = LocalDateTime.of(2026, 8, 4, 6, 30);

    @Autowired private MockMvc mockMvc;

    @MockBean private JdbcTemplate jdbcTemplate;
    @MockBean private PropertyApprovalWorkflowService workflowService;
    @MockBean private OperationalMetrics operationalMetrics;
    @MockBean private TenantFilterInterceptor tenantFilterInterceptor;
    @MockBean private UserDetailsService userDetailsService;

    @BeforeEach
    void allowRequestsThroughTenantInterceptor() throws Exception {
        when(tenantFilterInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void queueUsesTypedContractAndExcludesImportedClaimShape() throws Exception {
        when(workflowService.pendingApprovals()).thenReturn(List.of(new PropertyApprovalQueueItem(
                51L, "HARBOR", "Harbor Hotel", "12 Test Street", "HOTEL",
                "PENDING_APPROVAL", "PENDING_APPROVAL", "INACTIVE", "PENDING",
                7L, "Owner Seven", "owner7@example.test", null, null, null, null, null)));

        mockMvc.perform(get("/api/admin/property-approvals").with(user(admin(99L, "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].propertyId").value(51))
                .andExpect(jsonPath("$[0].name").value("Harbor Hotel"))
                .andExpect(jsonPath("$[0].ownerId").value(7))
                .andExpect(jsonPath("$[0].approvalStatus").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$[0].submittedByUserId").doesNotExist());
    }

    @Test
    void approvalUsesOnlyAuthoritativeCustomPrincipalReviewerId() throws Exception {
        when(workflowService.approve(99L, 51L)).thenReturn(new PropertyApprovalDecisionResponse(
                51L, "ACTIVE", "APPROVED", "ACTIVE", "ACTIVE", 99L, REVIEWED_AT, null));

        mockMvc.perform(post("/api/admin/property-approvals/51/approve")
                        .with(user(admin(99L, "ADMIN")))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propertyId").value(51))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.ownershipStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.reviewedByUserId").value(99))
                .andExpect(jsonPath("$.reviewedAt").value("2026-08-04T06:30:00"));

        verify(workflowService).approve(99L, 51L);
    }

    @Test
    void rejectionTrimsAndValidatesReasonBeforeWorkflow() throws Exception {
        when(workflowService.reject(99L, 51L, "Address evidence is incomplete."))
                .thenReturn(new PropertyApprovalDecisionResponse(
                        51L, "REJECTED", "REJECTED", "INACTIVE", "INACTIVE",
                        99L, REVIEWED_AT, "Address evidence is incomplete."));

        mockMvc.perform(post("/api/admin/property-approvals/51/reject")
                        .with(user(admin(99L, "SUPER_ADMIN")))
                        .contentType("application/json")
                        .content("{\"reason\":\"  Address evidence is incomplete.  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("Address evidence is incomplete."));

        verify(workflowService).reject(99L, 51L, "Address evidence is incomplete.");

        mockMvc.perform(post("/api/admin/property-approvals/51/reject")
                        .with(user(admin(99L, "ADMIN")))
                        .contentType("application/json")
                        .content("{\"reason\":\"  short  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(workflowService, never()).reject(99L, 51L, "short");
    }

    @Test
    void customerRoleCannotReadOrMutateApprovalQueue() throws Exception {
        CustomUserDetails customer = admin(7L, "CUSTOMER");

        mockMvc.perform(get("/api/admin/property-approvals").with(user(customer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/property-approvals/51/approve")
                        .with(user(customer))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());

        verify(workflowService, never()).pendingApprovals();
        verify(workflowService, never()).approve(any(), any());
    }

    private CustomUserDetails admin(Long userId, String authority) {
        return new CustomUserDetails(
                "admin@example.test",
                "hash",
                List.of(new SimpleGrantedAuthority(authority)),
                Map.of(),
                userId,
                null,
                Map.of());
    }
}
