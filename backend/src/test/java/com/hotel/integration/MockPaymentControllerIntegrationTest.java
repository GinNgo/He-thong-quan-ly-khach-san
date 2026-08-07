package com.hotel.integration;

import com.hotel.config.SecurityConfig;
import com.hotel.controllers.MockPaymentController;
import com.hotel.domain.payment.PaymentCompletionResult;
import com.hotel.entities.User;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.JwtAccessDeniedHandler;
import com.hotel.security.JwtAuthFilter;
import com.hotel.security.JwtAuthenticationEntryPoint;
import com.hotel.security.JwtTokenProvider;
import com.hotel.services.PaymentSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MockPaymentController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class MockPaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentSessionService paymentSessionService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void confirmPayment_AcceptsOnlySignedTokenPayload() throws Exception {
        when(paymentSessionService.confirmDemoPayment("signed-token"))
                .thenReturn(PaymentCompletionResult.APPLIED);

        mockMvc.perform(post("/api/payments/simulator/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"signed-token\"}")
                        .with(user(customer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"));

        verify(paymentSessionService).confirmDemoPayment("signed-token");
    }

    @Test
    void confirmPayment_RejectsCallerControlledLegacyQueryContract() throws Exception {
        mockMvc.perform(post("/api/payments/simulator/confirm")
                        .param("reservationId", "42")
                        .param("amount", "1")
                        .param("method", "MOMO")
                        .param("status", "SUCCESS")
                        .with(user(customer())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmPayment_WithoutAuthentication_Returns401() throws Exception {
        mockMvc.perform(post("/api/payments/simulator/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"signed-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    private CustomUserDetails customer() {
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("customer1");
        mockUser.setPasswordHash("hash");
        mockUser.setRoles(new HashSet<>());

        return new CustomUserDetails(
                mockUser.getUsername(),
                mockUser.getPasswordHash(),
                Set.of(new SimpleGrantedAuthority("CUSTOMER")),
                new HashMap<>(),
                mockUser.getId(),
                null,
                new HashMap<>());
    }
}
