package com.hotel.integration;

import com.hotel.config.SecurityConfig;
import com.hotel.config.VnpayConfig;
import com.hotel.controllers.PaymentController;
import com.hotel.dtos.ReservationDTO;
import com.hotel.entities.User;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.JwtAuthFilter;
import com.hotel.security.JwtTokenProvider;
import com.hotel.services.PaymentService;
import com.hotel.services.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtTokenProvider.class})
class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private ReservationService reservationService;

    @MockBean
    private VnpayConfig vnpayConfig;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void createPaymentUrl_AsCustomer_ShouldUseReservationAmount() throws Exception {
        ReservationDTO reservation = new ReservationDTO();
        reservation.setId(42L);
        reservation.setTotalAmount(new BigDecimal("100000"));
        when(reservationService.getReservationById(42L)).thenReturn(reservation);

        mockMvc.perform(get("/api/payments/create-url")
                        .param("reservationId", "42")
                        .param("method", "MOMO")
                        .with(user(customer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(
                        org.hamcrest.Matchers.startsWith(
                                "http://localhost:4200/payment-simulator?reservationId=42&method=MOMO&amount=100000&transactionId=MOMO_42_")));
    }

    @Test
    void createPaymentUrl_WithoutAuth_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/payments/create-url")
                        .param("reservationId", "42")
                        .param("method", "MOMO"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createPaymentUrl_WithUnsupportedMethod_ShouldReturn400() throws Exception {
        ReservationDTO reservation = new ReservationDTO();
        reservation.setId(42L);
        reservation.setTotalAmount(new BigDecimal("100000"));
        when(reservationService.getReservationById(42L)).thenReturn(reservation);

        mockMvc.perform(get("/api/payments/create-url")
                        .param("reservationId", "42")
                        .param("method", "CASH")
                        .with(user(customer())))
                .andExpect(status().isBadRequest());
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
                new HashMap<>()
        );
    }
}