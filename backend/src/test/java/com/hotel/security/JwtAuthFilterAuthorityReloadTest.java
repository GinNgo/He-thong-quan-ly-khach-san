package com.hotel.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthFilterAuthorityReloadTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reloadsRolesAndMasksFromServerAndIgnoresSpoofedClientPermissionHeaders() throws Exception {
        JwtTokenProvider tokens = mock(JwtTokenProvider.class);
        UserDetailsService users = mock(UserDetailsService.class);
        JwtAuthenticationEntryPoint entryPoint = mock(JwtAuthenticationEntryPoint.class);
        FilterChain chain = mock(FilterChain.class);
        JwtAuthFilter filter = new JwtAuthFilter(tokens, users, entryPoint);

        CustomUserDetails serverDetails = new CustomUserDetails(
                "staff@example.com",
                "hash",
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("RECEPTIONIST")),
                Map.of(FunctionCode.BOOKING, ActionCode.VIEW),
                2L,
                3L,
                Map.of());
        when(tokens.validateToken("signed-token")).thenReturn(true);
        when(tokens.getUsername("signed-token")).thenReturn("staff@example.com");
        when(users.loadUserByUsername("staff@example.com")).thenReturn(serverDetails);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/management/reservations");
        request.addHeader("Authorization", "Bearer signed-token");
        request.addHeader("X-Permission-Masks", "SYSTEM:63");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        CustomUserDetails principal = (CustomUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        assertSame(serverDetails, principal);
        assertEquals(ActionCode.VIEW, principal.getPermissionMasks().get(FunctionCode.BOOKING));
        assertFalse(principal.getPermissionMasks().containsKey(FunctionCode.SYSTEM));
        verify(users).loadUserByUsername("staff@example.com");
        verify(chain).doFilter(request, response);
    }
}
