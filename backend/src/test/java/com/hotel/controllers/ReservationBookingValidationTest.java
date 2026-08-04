package com.hotel.controllers;

import com.hotel.dtos.ReservationDTO;
import com.hotel.paymentprovider.idempotency.MutationIdempotencyService;
import com.hotel.services.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReservationBookingValidationTest {

    @Test
    void authenticatedBookingRejectsInvalidPayloadBeforeAnyMutation() throws Exception {
        ReservationService reservationService = mock(ReservationService.class);
        MutationIdempotencyService idempotencyService = mock(MutationIdempotencyService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new ReservationController(reservationService, idempotencyService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        mockMvc.perform(post("/api/reservations/book")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "customer@example.test", "n/a"))
                        .header("Idempotency-Key", "validation-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomTypeId": -1,
                                  "checkInDate": "2030-08-12",
                                  "checkOutDate": "2030-08-10",
                                  "quantity": 0,
                                  "adults": 0,
                                  "children": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.roomTypeId").exists())
                .andExpect(jsonPath("$.fieldErrors.quantity").exists())
                .andExpect(jsonPath("$.fieldErrors.adults").exists())
                .andExpect(jsonPath("$.fieldErrors.children").exists())
                .andExpect(jsonPath("$.fieldErrors.stayRangeValid").exists());

        verify(reservationService, never()).createReservation(any(), any(), any(), any());
        verify(idempotencyService, never()).execute(any(), any(Integer.class), any(Class.class), any(), any());
    }

    @Test
    void customerBookingRetainsExplicitCustomerAuthority() throws Exception {
        PreAuthorize authorization = ReservationController.class
                .getMethod("createCustomerReservation",
                        org.springframework.security.core.Authentication.class,
                        com.hotel.dtos.ReservationRequest.class,
                        String.class,
                        jakarta.servlet.http.HttpServletRequest.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAuthority('CUSTOMER')");
    }
}
