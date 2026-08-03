package com.hotel.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.dtos.PartnerRegistrationResponse;
import com.hotel.services.PropertyRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PropertyRegistrationControllerTest {

    @Mock
    private PropertyRegistrationService registrationService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PropertyRegistrationController(registrationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void validAnonymousRequestUsesValidatedFlatDto() throws Exception {
        when(registrationService.registerAnonymousPartner(any()))
                .thenReturn(new PartnerRegistrationResponse(7L, 9L, "DRAFT"));

        mockMvc.perform(post("/api/partner/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBody())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.propertyId").value(9))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        ArgumentCaptor<com.hotel.dtos.PartnerRegistrationRequest> request =
                ArgumentCaptor.forClass(com.hotel.dtos.PartnerRegistrationRequest.class);
        verify(registrationService).registerAnonymousPartner(request.capture());
        assertEquals(10L, request.getValue().getProvinceId());
        assertEquals(11L, request.getValue().getWardId());
        assertEquals("12 Bach Dang", request.getValue().getAddress());
    }

    @Test
    void malformedPayloadReturnsFieldValidationErrorsWithoutCallingService() throws Exception {
        var body = validBody();
        body.put("email", "not-an-email");
        body.put("password", "short");
        body.put("provinceId", null);
        body.put("wardId", null);
        body.put("address", " ");

        mockMvc.perform(post("/api/partner/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.provinceId").exists())
                .andExpect(jsonPath("$.fieldErrors.wardId").exists())
                .andExpect(jsonPath("$.fieldErrors.address").exists());

        verify(registrationService, never()).registerAnonymousPartner(any());
    }

    @Test
    void authenticatedAccountCannotUseAnonymousRegistrationPath() throws Exception {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "customer@example.com", "n/a", java.util.List.of());

        mockMvc.perform(post("/api/partner/register")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBody())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(registrationService, never()).registerAnonymousPartner(any());
    }

    private java.util.Map<String, Object> validBody() {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("email", "owner@example.com");
        body.put("password", "secret123");
        body.put("fullName", "Partner Owner");
        body.put("phone", "0900000000");
        body.put("propertyName", "Seaside Hotel");
        body.put("provinceId", 10L);
        body.put("wardId", 11L);
        body.put("address", "12 Bach Dang");
        return body;
    }
}
