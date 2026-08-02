package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.config.SecurityConfig;
import com.hotel.controllers.PropertyClaimController;
import com.hotel.entities.PropertyClaimRequest;
import com.hotel.repositories.PropertyClaimRequestRepository;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.security.JwtAccessDeniedHandler;
import com.hotel.security.JwtAuthFilter;
import com.hotel.security.JwtAuthenticationEntryPoint;
import com.hotel.security.JwtTokenProvider;
import com.hotel.security.TenantFilterInterceptor;
import com.hotel.services.PropertyClaimService;
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
    private PropertyClaimRequestRepository claimRepository;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private TenantFilterInterceptor tenantFilterInterceptor;

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
        PropertyClaimRequest claim = claim(81L, "PENDING");
        claim.setVerificationMethod("EMAIL");
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
                .andExpect(jsonPath("$.verificationMethod").value("EMAIL"));

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
        when(claimService.approveClaim(8L, 71L)).thenReturn(claim(8L, "APPROVED"));

        mockMvc.perform(post("/api/admin/property-claims/{id}/approve", 8L)
                        .with(user(principal(71L, "PROPERTY_CLAIM_APPROVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(8L))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(claimService).approveClaim(8L, 71L);
    }

    @Test
    void approverAuthorityPassesAuthenticatedAdminIdAndRejectReason() throws Exception {
        PropertyClaimRequest rejected = claim(9L, "REJECTED");
        rejected.setRejectionReason("Ownership evidence is incomplete");
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
        when(claimRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/admin/property-claims")
                        .with(user(principal(74L, "SUPER_ADMIN"))))
                .andExpect(status().isOk());
    }

    private PropertyClaimRequest claim(Long id, String status) {
        PropertyClaimRequest claim = new PropertyClaimRequest();
        claim.setId(id);
        claim.setStatus(status);
        return claim;
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
