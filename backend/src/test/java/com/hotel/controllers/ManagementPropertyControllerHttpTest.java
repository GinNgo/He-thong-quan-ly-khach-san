package com.hotel.controllers;

import com.hotel.dtos.PropertyUpdateRequest;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.services.HotelManagementService;
import com.hotel.services.ManagementPortalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ManagementPropertyControllerHttpTest {

    @Mock private ManagementPortalService managementPortalService;
    @Mock private HotelManagementService hotelManagementService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ManagementPortalController controller = new ManagementPortalController(
                managementPortalService, hotelManagementService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void ownerUpdateRequiresValidatedReasonBeforeServiceMutation() throws Exception {
        mockMvc.perform(put("/api/management/properties/{id}", 12L)
                        .contentType("application/json")
                        .content("""
                                {"nameVi":"Updated without reason"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.reason").exists());
        verify(hotelManagementService, never()).updateOwnedHotel(any(), any());
    }

    @Test
    void ownerUpdatePassesOnlyEditableProfileFieldsAndReason() throws Exception {
        mockMvc.perform(put("/api/management/properties/{id}", 12L)
                        .contentType("application/json")
                        .content("""
                                {"nameVi":"Updated","reason":"Correct profile","operationStatus":"ACTIVE"}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<PropertyUpdateRequest> request = ArgumentCaptor.forClass(PropertyUpdateRequest.class);
        verify(hotelManagementService).updateOwnedHotel(org.mockito.ArgumentMatchers.eq(12L), request.capture());
        assertEquals("Updated", request.getValue().getNameVi());
        assertEquals("Correct profile", request.getValue().getReason());
    }

    @Test
    void crossPropertyOwnerEditReturnsNotFoundWithoutLeakingTenantExistence() throws Exception {
        doThrow(new ResourceNotFoundException("Property not found."))
                .when(hotelManagementService).updateOwnedHotel(org.mockito.ArgumentMatchers.eq(99L), any());

        mockMvc.perform(put("/api/management/properties/{id}", 99L)
                        .contentType("application/json")
                        .content("""
                                {"nameVi":"Unauthorized","reason":"Cross property edit"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
