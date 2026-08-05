package com.hotel.controllers;

import com.hotel.dtos.CheckInReadinessDTO;
import com.hotel.dtos.ReservationDTO;
import com.hotel.paymentprovider.idempotency.MutationIdempotencyService;
import com.hotel.services.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReservationCheckInHttpContractTest {

    @Mock private ReservationService reservationService;
    @Mock private MutationIdempotencyService mutationIdempotencyService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReservationController(reservationService, mutationIdempotencyService))
                .build();
    }

    @Test
    void readinessEndpointReturnsTypedAuthoritativeJson() throws Exception {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-04T14:00:00+07:00");
        when(reservationService.getCheckInReadiness(42L)).thenReturn(new CheckInReadinessDTO(
                42L, "CONFIRMED", false, false, now, now, now.minusMinutes(5), now.plusDays(2),
                "Asia/Ho_Chi_Minh", 5, "CHECK_IN_POLICY_V1", 1, List.of(),
                List.of(new com.hotel.dtos.CheckInReadinessIssueDTO(
                        "MISSING_ROOM_ASSIGNMENT", "Assign a physical room."))));

        mockMvc.perform(get("/api/reservations/42/check-in-readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(42))
                .andExpect(jsonPath("$.zoneId").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.blockers[0].code").value("MISSING_ROOM_ASSIGNMENT"));
    }

    @Test
    void checkInEndpointPropagatesMandatoryIdempotencyKeyToLedger() throws Exception {
        ReservationDTO response = new ReservationDTO();
        response.setId(42L);
        response.setStatus("CHECKED_IN");
        when(reservationService.checkIn(42L)).thenReturn(response);
        when(mutationIdempotencyService.execute(any(), eq(200), eq(ReservationDTO.class), any(), any()))
                .thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(3)).get());
        org.springframework.security.core.Authentication principal =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "receptionist", "unused", List.of());

        mockMvc.perform(post("/api/reservations/42/check-in")
                        .principal(principal)
                        .header("Idempotency-Key", "check-in-http-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.status").value("CHECKED_IN"));

        verify(mutationIdempotencyService).execute(any(), eq(200), eq(ReservationDTO.class), any(), any());
    }
}
