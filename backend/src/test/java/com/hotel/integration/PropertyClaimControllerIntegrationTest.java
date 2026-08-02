package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.config.SecurityConfig;
import com.hotel.controllers.PropertyClaimController;
import com.hotel.dtos.PropertyClaimResponseDTO;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.security.JwtAccessDeniedHandler;
import com.hotel.security.JwtAuthFilter;
import com.hotel.security.JwtAuthenticationEntryPoint;
import com.hotel.security.JwtTokenProvider;
import com.hotel.security.TenantFilterInterceptor;
import com.hotel.services.PropertyClaimService;
import com.hotel.observability.OperationalMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PropertyClaimController.class)
@ContextConfiguration(classes = BackendApplication.class)
@Import({
        SecurityConfig.class,
        JwtAuthFilter.class,
        JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class
})
class PropertyClaimControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PropertyClaimService claimService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private TenantFilterInterceptor tenantFilterInterceptor;

    @MockBean
    private OperationalMetrics operationalMetrics;

    @BeforeEach
    void allowMvcRequestsThroughTenantFilter() {
        when(tenantFilterInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void unauthenticatedClaimRequestIsDenied() throws Exception {
        mockMvc.perform(post("/api/properties/{propertyId}/claim", 17L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void claimRequestUsesAuthenticatedUserIdInsteadOfPayloadIdentity() throws Exception {
        PropertyClaimResponseDTO claim = claim(81L, "PENDING", "EMAIL", null);
        when(claimService.requestClaim(
                17L,
                42L,
                "EMAIL",
                "owner@example.com",
                "Please verify"))
                .thenReturn(claim);

        mockMvc.perform(post("/api/properties/{propertyId}/claim", 17L)
                        .with(user(principal(42L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "999",
                                  "verificationMethod": "EMAIL",
                                  "verificationData": "owner@example.com",
                                  "note": "Please verify"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(81L))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.verificationMethod").value("EMAIL"))
                .andExpect(jsonPath("$.requesterUser.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.requesterUser.roles").doesNotExist())
                .andExpect(jsonPath("$.property.userProperties").doesNotExist());

        verify(claimService).requestClaim(
                17L,
                42L,
                "EMAIL",
                "owner@example.com",
                "Please verify");
    }

    @Test
    void malformedClaimPayloadIsRejectedBeforeLifecycleServiceRuns() throws Exception {
        mockMvc.perform(post("/api/properties/{propertyId}/claim", 17L)
                        .with(user(principal(42L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(claimService);
    }

    @Test
    void approverAuthorityPassesAuthenticatedAdminId() throws Exception {
        when(claimService.approveClaim(8L, 71L)).thenReturn(claim(8L, "APPROVED", null, null));

        mockMvc.perform(post("/api/admin/property-claims/{id}/approve", 8L)
                        .with(user(principal(71L, "PROPERTY_CLAIM_APPROVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(8L))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(claimService).approveClaim(8L, 71L);
    }

    @Test
    void approverAuthorityPassesAuthenticatedAdminIdAndRejectReason() throws Exception {
        PropertyClaimResponseDTO rejected = claim(
                9L, "REJECTED", null, "Ownership evidence is incomplete");
        when(claimService.rejectClaim(9L, 72L, "Ownership evidence is incomplete"))
                .thenReturn(rejected);

        mockMvc.perform(post("/api/admin/property-claims/{id}/reject", 9L)
                        .with(user(principal(72L, "PROPERTY_CLAIM_APPROVE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Ownership evidence is incomplete"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9L))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Ownership evidence is incomplete"));

        verify(claimService).rejectClaim(9L, 72L, "Ownership evidence is incomplete");
    }

    @Test
    void requesterCanCancelOwnPendingClaim() throws Exception {
        when(claimService.cancelClaim(12L, 42L)).thenReturn(claim(12L, "CANCELLED", null, null));

        mockMvc.perform(post("/api/property-claims/{id}/cancel", 12L)
                        .with(user(principal(42L, "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(claimService).cancelClaim(12L, 42L);
    }

    @Test
    void actorWithoutClaimAuthorityCannotViewApproveOrReject() throws Exception {
        CustomUserDetails actor = principal(73L, "STAFF");

        mockMvc.perform(get("/api/admin/property-claims").with(user(actor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/property-claims/{id}/approve", 10L).with(user(actor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/property-claims/{id}/reject", 10L)
                        .with(user(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Denied\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void superAdminAuthorityCanViewClaims() throws Exception {
        when(claimService.listClaims(any(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/admin/property-claims")
                        .with(user(principal(74L, "SUPER_ADMIN"))))
                .andExpect(status().isOk());
    }

    private PropertyClaimResponseDTO claim(Long id, String status, String verificationMethod, String rejectionReason) {
        return new PropertyClaimResponseDTO(
                id,
                new PropertyClaimResponseDTO.PropertySummary(17L, "HOTEL-17", "Safe Hotel", "PENDING_APPROVAL", "INACTIVE"),
                new PropertyClaimResponseDTO.UserSummary(42L, "claim-user-42", "owner@example.com", "Claim User"),
                verificationMethod,
                verificationMethod == null ? null : "owner@example.com",
                null,
                status,
                null,
                null,
                rejectionReason,
                null);
    }

    private CustomUserDetails principal(Long userId, String... authorities) {
        Set<SimpleGrantedAuthority> grantedAuthorities = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new CustomUserDetails(
                "claim-user-" + userId,
                "hash",
                grantedAuthorities,
                Map.<FunctionCode, Integer>of(),
                userId,
                null,
                Map.of());
    }
}
