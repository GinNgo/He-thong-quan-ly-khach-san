package com.hotel.security;

import com.hotel.BackendApplication;
import com.hotel.config.SecurityConfig;
import com.hotel.controllers.GlobalExceptionHandler;
import com.hotel.observability.OperationalMetrics;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.FinancialStates.PaymentState;
import com.hotel.paymentprovider.domain.FinancialStates.SubscriptionOrderState;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.platformbilling.payment.PlatformPaymentAttempt;
import com.hotel.platformbilling.payment.PlatformPaymentCallbackController;
import com.hotel.platformbilling.payment.PlatformPaymentCallbackService;
import com.hotel.propertycommerce.payment.ManualTransferConfirmationService;
import com.hotel.propertycommerce.payment.PropertyPaymentAttempt;
import com.hotel.propertycommerce.payment.PropertyPaymentAttemptService;
import com.hotel.propertycommerce.payment.PropertyPaymentCallbackCredentialsResolver;
import com.hotel.propertycommerce.payment.PropertyPaymentCallbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * FR-041 edge-contract tests for unauthenticated provider callbacks and authenticated polling.
 *
 * The abuse-control assertions intentionally exercise the HTTP boundary rather than provider
 * adapters. They should fail if the application has no shared request-size/rate-limit controls.
 */
@WebMvcTest(controllers = {
        com.hotel.propertycommerce.payment.PropertyPaymentController.class,
        PlatformPaymentCallbackController.class
})
@ContextConfiguration(classes = BackendApplication.class)
@Import({
        SecurityConfig.class,
        JwtAuthFilter.class,
        JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class
})
class PaymentCallbackAbuseIntegrationTest {

    private static final String PROPERTY_CALLBACK = "/api/payment-providers/property/SIMULATOR/callback";
    private static final String PLATFORM_CALLBACK = "/api/payment-providers/platform/SIMULATOR/callback";
    private static final int BURST_SIZE = 100;
    private static final int MAX_CALLBACK_BYTES = 1024 * 1024;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PropertyPaymentAttemptService attemptService;

    @MockBean
    private ManualTransferConfirmationService manualConfirmationService;

    @MockBean
    private PropertyPaymentCallbackService propertyCallbackService;

    @MockBean
    private PropertyPaymentCallbackCredentialsResolver callbackCredentialsResolver;

    @MockBean
    private PlatformPaymentCallbackService platformCallbackService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private TenantFilterInterceptor tenantFilterInterceptor;

    @MockBean
    private OperationalMetrics operationalMetrics;

    @BeforeEach
    void allowTenantInterceptorToContinue() throws Exception {
        when(tenantFilterInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void paymentAttemptPollingRequiresAuthenticationAndReturnsOnlyOwnedState() throws Exception {
        when(attemptService.getOwned("attempt-001")).thenReturn(attempt(false, PaymentState.PENDING_VERIFICATION));

        mockMvc.perform(get("/api/payment-attempts/attempt-001"))
                .andExpect(result -> assertEquals(401, result.getResponse().getStatus(),
                        "Payment-attempt polling must not expose state to anonymous callers"));

        mockMvc.perform(get("/api/payment-attempts/attempt-001")
                        .with(user(customer("owned-state-customer", 9001L))))
                .andExpect(result -> assertEquals(200, result.getResponse().getStatus()))
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("PENDING_VERIFICATION")));

        verify(attemptService).getOwned("attempt-001");
    }

    @Test
    void paymentAttemptPollingBurstIsRateLimitedPerAuthenticatedPrincipal() throws Exception {
        when(attemptService.getOwned("attempt-burst")).thenReturn(attempt(false, PaymentState.PENDING));

        List<Integer> statuses = new java.util.ArrayList<>();
        for (int index = 0; index < BURST_SIZE; index++) {
            int addressSuffix = index + 1;
            statuses.add(mockMvc.perform(get("/api/payment-attempts/attempt-burst")
                            .with(user(customer("polling-burst-customer", 9002L)))
                            .with(request -> {
                                request.setRemoteAddr("198.51.100." + addressSuffix);
                                return request;
                            }))
                    .andReturn().getResponse().getStatus());
        }

        assertTrue(statuses.contains(429),
                "Rapid payment-status polling must eventually return HTTP 429; statuses=" + statuses);
    }

    @Test
    void callbackReplayIsAcknowledgedForPropertyAndPlatformWithoutCustomerJwt() throws Exception {
        when(callbackCredentialsResolver.resolve(eq("SIMULATOR"), anyMap())).thenReturn(
                new PropertyPaymentCallbackCredentialsResolver.CallbackContext(
                        PaymentEnvironment.SIMULATOR, "PROPERTY-SIMULATOR", Map.of("signingSecret", "test-only")));
        when(propertyCallbackService.process(any()))
                .thenReturn(new PropertyPaymentCallbackService.CallbackResult(
                        true, false, null, "attempt-001", PaymentState.SUCCESS, "transaction-001"))
                .thenReturn(new PropertyPaymentCallbackService.CallbackResult(
                        true, true, null, "attempt-001", PaymentState.SUCCESS, "transaction-001"));
        when(platformCallbackService.process(any()))
                .thenReturn(new PlatformPaymentCallbackService.CallbackResult(
                        true, false, null, "platform-attempt-001", PlatformPaymentAttempt.Status.SUCCESS,
                        SubscriptionOrderState.APPLIED, "platform-transaction-001", "contract-001"))
                .thenReturn(new PlatformPaymentCallbackService.CallbackResult(
                        true, true, null, "platform-attempt-001", PlatformPaymentAttempt.Status.SUCCESS,
                        SubscriptionOrderState.APPLIED, "platform-transaction-001", "contract-001"));

        assertEquals(200, callback(PROPERTY_CALLBACK, "203.0.113.10").getResponse().getStatus());
        MvcResult propertyReplay = callback(PROPERTY_CALLBACK, "203.0.113.10");
        assertEquals(200, propertyReplay.getResponse().getStatus());
        assertTrue(propertyReplay.getResponse().getContentAsString().contains("\"replayed\":true"));

        assertEquals(200, callback(PLATFORM_CALLBACK, "203.0.113.11").getResponse().getStatus());
        MvcResult platformReplay = callback(PLATFORM_CALLBACK, "203.0.113.11");
        assertEquals(200, platformReplay.getResponse().getStatus());
        assertTrue(platformReplay.getResponse().getContentAsString().contains("\"replayed\":true"));
    }

    @Test
    void callbackBurstIsRateLimitedPerSourceIpEvenWhenForwardedForChanges() throws Exception {
        when(callbackCredentialsResolver.resolve(eq("SIMULATOR"), anyMap())).thenReturn(
                new PropertyPaymentCallbackCredentialsResolver.CallbackContext(
                        PaymentEnvironment.SIMULATOR, "PROPERTY-SIMULATOR", Map.of("signingSecret", "test-only")));
        when(propertyCallbackService.process(any())).thenReturn(
                new PropertyPaymentCallbackService.CallbackResult(
                        false, false,
                        com.hotel.paymentprovider.error.FinancialErrorCode.CALLBACK_SIGNATURE_INVALID,
                        "attempt-001", PaymentState.PENDING, null));

        List<Integer> statuses = new java.util.ArrayList<>();
        for (int index = 0; index < BURST_SIZE; index++) {
            statuses.add(mockMvc.perform(post(PROPERTY_CALLBACK)
                            .contentType(APPLICATION_JSON)
                            .content("{\"reference\":\"attempt-001\",\"eventId\":\"event-001\"}")
                            .header("X-Forwarded-For", "198.18.0." + (index + 1))
                            .with(request -> {
                                request.setRemoteAddr("198.51.100.32");
                                return request;
                            }))
                    .andReturn().getResponse().getStatus());
        }

        assertTrue(statuses.contains(429),
                "Callback flooding must be bounded by source IP, not attacker-controlled X-Forwarded-For; statuses="
                        + statuses);
    }

    @Test
    void oversizedCallbackIsRejectedBeforeBusinessProcessing() throws Exception {
        String oversizedPayload = "{\"payload\":\"" + "x".repeat(MAX_CALLBACK_BYTES) + "\"}";

        MvcResult result = mockMvc.perform(post(PLATFORM_CALLBACK)
                        .contentType(APPLICATION_JSON)
                        .content(oversizedPayload)
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.33");
                            return request;
                        }))
                .andReturn();

        assertEquals(413, result.getResponse().getStatus(),
                "Provider callbacks need a bounded request body before JSON/business processing");
        verifyNoInteractions(platformCallbackService);
    }

    private MvcResult callback(String path, String remoteAddress) throws Exception {
        return mockMvc.perform(post(path)
                        .contentType(APPLICATION_JSON)
                        .header("X-Payment-Signature", "signed-test-value")
                        .content("{\"reference\":\"attempt-001\",\"eventId\":\"event-001\",\"amount\":100000,\"currency\":\"VND\"}")
                        .with(request -> {
                            request.setRemoteAddr(remoteAddress);
                            return request;
                        }))
                .andReturn();
    }

    private PropertyPaymentAttemptService.AttemptResponse attempt(boolean replayed, PaymentState status) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 3, 12, 0);
        return new PropertyPaymentAttemptService.AttemptResponse(
                71L,
                "attempt-001",
                42L,
                PropertyPaymentAttempt.Purpose.DEPOSIT,
                status,
                PaymentEnvironment.SIMULATOR,
                BigDecimal.valueOf(100_000),
                "VND",
                now.plusMinutes(30),
                "SIMULATOR",
                "SIMULATOR",
                new PropertyPaymentAttemptService.ReceiverSnapshot(
                        "Test Bank", "TEST", "LUXESTAY", "****6789",
                        "VIETQR", null, "Instructions", "Instructions"),
                "{\"accountNumberMasked\":\"****6789\"}",
                "BOOKING LS42-ABUSE",
                replayed);
    }

    private CustomUserDetails customer(String username, Long userId) {
        return new CustomUserDetails(
                username,
                "not-a-real-password",
                Set.of(new SimpleGrantedAuthority("CUSTOMER")),
                new HashMap<>(),
                userId,
                null,
                new HashMap<>());
    }
}
