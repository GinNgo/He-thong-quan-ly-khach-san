package com.hotel.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthFilterAccountStatusTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsPreviouslyIssuedTokenWhenAccountIsNoLongerActive() throws Exception {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        JwtAuthFilter filter = new JwtAuthFilter(
                tokenProvider,
                userDetailsService,
                new JwtAuthenticationEntryPoint(objectMapper));
        when(tokenProvider.validateToken("previously-issued-token")).thenReturn(true);
        when(tokenProvider.getUsername("previously-issued-token")).thenReturn("disabled@example.com");
        when(userDetailsService.loadUserByUsername("disabled@example.com"))
                .thenThrow(new AccountDisabledAuthenticationException());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer previously-issued-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean(false);

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> continued.set(true));

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertEquals(401, response.getStatus());
        assertEquals(AccountDisabledAuthenticationException.ERROR_CODE, body.get("code").asText());
        assertFalse(body.get("retryable").asBoolean());
        assertFalse(continued.get());
    }
}
