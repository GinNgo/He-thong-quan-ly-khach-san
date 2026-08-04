package com.hotel.controllers;

import com.hotel.services.HotelManagementService;
import com.hotel.services.PropertyRegistrationService;
import com.hotel.services.RoomTypeService;
import com.hotel.entities.Hotel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PropertyAdministrationControllerHttpTest {

    @Mock private HotelManagementService hotelManagementService;
    @Mock private RoomTypeService roomTypeService;
    @Mock private PropertyRegistrationService propertyRegistrationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        HotelController controller = new HotelController(
                hotelManagementService, roomTypeService, propertyRegistrationService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createRejectsInvalidEntityShapedPayloadBeforeServiceMutation() throws Exception {
        mockMvc.perform(post("/api/v1/hotels")
                        .contentType("application/json")
                        .content("""
                                {"nameVi":"","status":"ACTIVE","approvalStatus":"APPROVED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verify(hotelManagementService, never()).createHotel(any());
    }

    @Test
    void legacyDeleteRequiresClosureReason() throws Exception {
        mockMvc.perform(delete("/api/v1/hotels/{id}", 7L))
                .andExpect(status().isBadRequest());
        verify(hotelManagementService, never()).closeHotel(any(), any());
    }

    @Test
    void closedPropertyIsRetainedButNoLongerPubliclyVisible() throws Exception {
        Hotel closed = new Hotel();
        closed.setId(7L);
        closed.setStatus("CLOSED");
        closed.setApprovalStatus("APPROVED");
        closed.setOperationStatus("CLOSED");
        when(hotelManagementService.getHotelById(7L)).thenReturn(java.util.Optional.of(closed));

        mockMvc.perform(get("/api/v1/hotels/public/{id}", 7L))
                .andExpect(status().isNotFound());
    }
}
