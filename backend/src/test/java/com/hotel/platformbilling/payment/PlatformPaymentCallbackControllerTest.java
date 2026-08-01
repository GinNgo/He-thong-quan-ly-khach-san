package com.hotel.platformbilling.payment;

import com.hotel.controllers.GlobalExceptionHandler;
import com.hotel.paymentprovider.domain.FinancialStates.SubscriptionOrderState;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PlatformPaymentCallbackControllerTest {

    @Mock
    private PlatformPaymentCallbackService callbackService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PlatformPaymentCallbackController(callbackService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mapsAcceptedProviderCallbackToStableResponse() throws Exception {
        when(callbackService.process(any())).thenReturn(new PlatformPaymentCallbackService.CallbackResult(
                true,
                false,
                null,
                "attempt-public",
                PlatformPaymentAttempt.Status.SUCCESS,
                SubscriptionOrderState.APPLIED,
                "transaction-public",
                "contract-public"));

        mockMvc.perform(post("/api/payment-providers/platform/SIMULATOR/callback")
                        .header("X-Payment-Signature", "signature")
                        .header("X-Correlation-ID", "correlation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference\":\"provider-reference\",\"status\":\"SUCCEEDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.attemptId").value("attempt-public"))
                .andExpect(jsonPath("$.orderStatus").value("APPLIED"))
                .andExpect(jsonPath("$.contractPublicId").value("contract-public"));

        verify(callbackService).process(any());
    }

    @Test
    void mapsRejectedProviderCallbackToFinancialErrorStatus() throws Exception {
        when(callbackService.process(any())).thenReturn(new PlatformPaymentCallbackService.CallbackResult(
                false,
                false,
                FinancialErrorCode.CALLBACK_AMOUNT_MISMATCH,
                "attempt-public",
                PlatformPaymentAttempt.Status.PENDING,
                SubscriptionOrderState.PENDING_PAYMENT,
                null,
                null));

        mockMvc.perform(post("/api/payment-providers/platform/SIMULATOR/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference\":\"provider-reference\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.errorCode").value("CALLBACK_AMOUNT_MISMATCH"));
    }
}
