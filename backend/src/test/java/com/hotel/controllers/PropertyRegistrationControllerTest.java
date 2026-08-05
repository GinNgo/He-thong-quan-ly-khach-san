package com.hotel.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hotel.dtos.PartnerRegistrationResponse;
import com.hotel.dtos.PartnerRegistrationStatusResponse;
import com.hotel.dtos.PropertyApprovalSubmissionResponse;
import com.hotel.dtos.PropertyReviewHistoryItem;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.security.CustomUserDetails;
import com.hotel.services.PropertyApprovalWorkflowService;
import com.hotel.services.PropertyRegistrationService;
import com.hotel.services.PropertyReviewHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PropertyRegistrationControllerTest {

    @Mock
    private PropertyRegistrationService registrationService;

    @Mock
    private PropertyApprovalWorkflowService propertyApprovalWorkflowService;

    @Mock
    private PropertyReviewHistoryService propertyReviewHistoryService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PropertyRegistrationController(
                        registrationService, propertyApprovalWorkflowService, propertyReviewHistoryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
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

    @Test
    void authenticatedConversionUsesOnlyAuthoritativePrincipalUserId() throws Exception {
        when(registrationService.convertExistingCustomer(any(), any()))
                .thenReturn(new PartnerRegistrationResponse(77L, 91L, "DRAFT"));

        mockMvc.perform(post("/api/partner/convert")
                        .principal(authoritativeAuthentication(77L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validConversionBody())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(77))
                .andExpect(jsonPath("$.propertyId").value(91))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        ArgumentCaptor<Long> userId = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<com.hotel.dtos.PartnerConversionRequest> request =
                ArgumentCaptor.forClass(com.hotel.dtos.PartnerConversionRequest.class);
        verify(registrationService).convertExistingCustomer(userId.capture(), request.capture());
        assertEquals(77L, userId.getValue());
        assertEquals("Existing Customer Hotel", request.getValue().getPropertyName());
    }

    @Test
    void anonymousConversionIsDenied() throws Exception {
        mockMvc.perform(post("/api/partner/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validConversionBody())))
                .andExpect(status().isUnauthorized());

        verify(registrationService, never()).convertExistingCustomer(any(), any());
    }

    @Test
    void nonAuthoritativePrincipalIsDenied() throws Exception {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "customer@example.com", "n/a", java.util.List.of());

        mockMvc.perform(post("/api/partner/convert")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validConversionBody())))
                .andExpect(status().isForbidden());

        verify(registrationService, never()).convertExistingCustomer(any(), any());
    }

    @Test
    void accountAndCredentialFieldsAreRejectedInsteadOfIgnored() throws Exception {
        var body = validConversionBody();
        body.put("email", "victim@example.com");
        body.put("password", "takeover-secret");
        body.put("fullName", "Victim Account");

        mockMvc.perform(post("/api/partner/convert")
                        .principal(authoritativeAuthentication(77L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        verify(registrationService, never()).convertExistingCustomer(any(), any());
    }

    @Test
    void registrationStatusUsesAuthoritativeUserIdInsteadOfPrincipalName() throws Exception {
        var response = new PartnerRegistrationStatusResponse(
                "APPROVED",
                1,
                java.util.List.of(new PartnerRegistrationStatusResponse.PropertyStatus(
                        91L, "Harbor Hotel", "APPROVED", "APPROVED", "ACTIVE", "ACTIVE", null)));
        when(registrationService.registrationStatus(77L)).thenReturn(response);

        mockMvc.perform(get("/api/partner/registration-status")
                        .principal(authoritativeAuthentication(77L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus").value("APPROVED"))
                .andExpect(jsonPath("$.propertyCount").value(1))
                .andExpect(jsonPath("$.properties[0].propertyId").value(91));

        verify(registrationService).registrationStatus(77L);
    }

    @Test
    void anonymousRegistrationStatusIsDenied() throws Exception {
        mockMvc.perform(get("/api/partner/registration-status"))
                .andExpect(status().isUnauthorized());

        verify(registrationService, never()).registrationStatus(any());
    }

    @Test
    void nonAuthoritativeRegistrationStatusPrincipalIsDenied() throws Exception {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "customer@example.com", "n/a", java.util.List.of());

        mockMvc.perform(get("/api/partner/registration-status").principal(authentication))
                .andExpect(status().isForbidden());

        verify(registrationService, never()).registrationStatus(any());
    }

    @Test
    void submitPropertyUsesOnlyAuthoritativePrincipalUserId() throws Exception {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 8, 4, 3, 15);
        when(propertyApprovalWorkflowService.submitDraft(77L, 91L))
                .thenReturn(new PropertyApprovalSubmissionResponse(
                        91L, "PENDING_APPROVAL", "PENDING_APPROVAL", "INACTIVE", 77L, submittedAt));

        mockMvc.perform(post("/api/partner/properties/91/submit")
                        .principal(authoritativeAuthentication(77L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propertyId").value(91))
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.approvalStatus").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.operationStatus").value("INACTIVE"))
                .andExpect(jsonPath("$.submittedByUserId").value(77))
                .andExpect(jsonPath("$.submittedAt").value("2026-08-04T03:15:00"));

        verify(propertyApprovalWorkflowService).submitDraft(77L, 91L);
    }

    @Test
    void anonymousPropertySubmissionIsDenied() throws Exception {
        mockMvc.perform(post("/api/partner/properties/91/submit"))
                .andExpect(status().isUnauthorized());

        verify(propertyApprovalWorkflowService, never()).submitDraft(any(), any());
    }

    @Test
    void nonAuthoritativePropertySubmissionPrincipalIsDenied() throws Exception {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "customer@example.com", "n/a", java.util.List.of());

        mockMvc.perform(post("/api/partner/properties/91/submit").principal(authentication))
                .andExpect(status().isForbidden());

        verify(propertyApprovalWorkflowService, never()).submitDraft(any(), any());
    }

    @Test
    void ownerHistoryUsesAuthoritativePrincipalAndReturnsSafeArrayContract() throws Exception {
        when(propertyReviewHistoryService.ownerHistory(77L, 91L)).thenReturn(java.util.List.of(
                new PropertyReviewHistoryItem(
                        501L,
                        91L,
                        "PROPERTY_REJECTED",
                        "ADMIN",
                        "Address evidence is incomplete.",
                        new PropertyReviewHistoryItem.StatusTriplet(
                                "PENDING_APPROVAL", "PENDING_APPROVAL", "INACTIVE", "PENDING"),
                        new PropertyReviewHistoryItem.StatusTriplet(
                                "REJECTED", "REJECTED", "INACTIVE", "INACTIVE"),
                        LocalDateTime.of(2026, 8, 4, 8, 0))));

        mockMvc.perform(get("/api/partner/properties/91/history")
                        .principal(authoritativeAuthentication(77L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].eventId").value(501))
                .andExpect(jsonPath("$[0].actorKind").value("ADMIN"))
                .andExpect(jsonPath("$[0].beforeState.ownershipStatus").value("PENDING"))
                .andExpect(jsonPath("$[0].afterState.status").value("REJECTED"))
                .andExpect(jsonPath("$[0].actorId").doesNotExist());

        verify(propertyReviewHistoryService).ownerHistory(77L, 91L);
    }

    @Test
    void ownerHistoryHidesCrossAccountPropertyAsNotFound() throws Exception {
        when(propertyReviewHistoryService.ownerHistory(77L, 91L))
                .thenThrow(new ResourceNotFoundException("Property history was not found."));

        mockMvc.perform(get("/api/partner/properties/91/history")
                        .principal(authoritativeAuthentication(77L)))
                .andExpect(status().isNotFound());

        verify(propertyReviewHistoryService).ownerHistory(77L, 91L);
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

    private java.util.Map<String, Object> validConversionBody() {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("propertyName", "Existing Customer Hotel");
        body.put("provinceId", 10L);
        body.put("wardId", 11L);
        body.put("address", "34 Tran Phu");
        return body;
    }

    private UsernamePasswordAuthenticationToken authoritativeAuthentication(Long userId) {
        CustomUserDetails principal = new CustomUserDetails(
                "customer@example.com", "hash", java.util.List.of(), java.util.Map.of(),
                userId, null, java.util.Map.of());
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
    }
}
