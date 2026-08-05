package com.hotel.controllers;

import com.hotel.dtos.AvailableRoomContextDTO;
import com.hotel.dtos.RoomDTO;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.paymentprovider.idempotency.MutationIdempotencyService;
import com.hotel.services.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReservationAvailableRoomHttpTest {

    @Mock private ReservationService reservationService;
    @Mock private MutationIdempotencyService mutationIdempotencyService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ReservationController controller = new ReservationController(
                reservationService, mutationIdempotencyService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsTypedMultiRoomContextOverHttp() throws Exception {
        RoomDTO room = new RoomDTO();
        room.setId(11L);
        room.setHotelId(3L);
        room.setRoomTypeId(7L);
        room.setRoomNumber("101");
        room.setFloor(1);
        room.setStatus("AVAILABLE");
        room.setHousekeepingStatus("CLEAN");
        room.setMaintenanceStatus("NONE");
        when(reservationService.getAvailableRoomContext(88L)).thenReturn(new AvailableRoomContextDTO(
                88L, 3L, 7L, "Deluxe",
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12),
                2, List.of(), List.of(19L), List.of(room)));

        mockMvc.perform(get("/api/reservations/88/available-rooms/context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(88))
                .andExpect(jsonPath("$.requiredQuantity").value(2))
                .andExpect(jsonPath("$.assignedRoomIds[0]").value(19))
                .andExpect(jsonPath("$.candidates[0].roomNumber").value("101"));
    }

    @Test
    void preservesTenantIdorAsHttpNotFound() throws Exception {
        when(reservationService.getAvailableRoomContext(99L))
                .thenThrow(new ResourceNotFoundException("booking not found"));

        mockMvc.perform(get("/api/reservations/99/available-rooms/context"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("booking not found"));
    }
}
