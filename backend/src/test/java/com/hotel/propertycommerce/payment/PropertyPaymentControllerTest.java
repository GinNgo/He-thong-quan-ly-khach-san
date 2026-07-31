package com.hotel.propertycommerce.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.controllers.GlobalExceptionHandler;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.FinancialStates.BookingFinancialState;
import com.hotel.paymentprovider.domain.FinancialStates.PaymentState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.propertycommerce.booking.BookingFinancialSummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PropertyPaymentControllerTest {

    @Mock private PropertyPaymentAttemptService attemptService;
    @Mock private ManualTransferConfirmationService manualConfirmationService;
    @Mock private PropertyPaymentCallbackService callbackService;
    @Mock private PropertyPaymentCallbackCredentialsResolver callbackCredentialsResolver;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PropertyPaymentController controller = new PropertyPaymentController(
                attemptService,
                manualConfirmationService,
                callbackService,
                callbackCredentialsResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void exposesSummaryCreationStatusAndCancellationWithoutCallerOwnedAmounts() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 12, 0);
        when(attemptService.financialSummary(42L)).thenReturn(new BookingFinancialSummaryService.Summary(
                42L, 3L, VndMoney.of(1_200_000), VndMoney.of(360_000),
                VndMoney.zero(), VndMoney.zero(), BigDecimal.valueOf(1_200_000),
                BookingFinancialState.UNPAID, 0, now));
        when(attemptService.create(any())).thenReturn(attempt(false, PaymentState.PENDING_VERIFICATION, now));
        when(attemptService.getOwned("attempt-001"))
                .thenReturn(attempt(false, PaymentState.PENDING_VERIFICATION, now));
        when(attemptService.cancelOwned(any())).thenReturn(attempt(false, PaymentState.CANCELLED, now));

        mockMvc.perform(get("/api/reservations/42/financial-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossCharges").value(1_200_000))
                .andExpect(jsonPath("$.remainingBalance").value(1_200_000))
                .andExpect(jsonPath("$.currency").value("VND"));

        mockMvc.perform(post("/api/reservations/42/payment-attempts")
                        .header("Idempotency-Key", "create-key")
                        .header("X-Correlation-ID", "corr-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "purpose": "deposit",
                                  "method": "manual_transfer",
                                  "expectedAmount": 1,
                                  "hotelId": 999
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptId").value("attempt-001"))
                .andExpect(jsonPath("$.expectedAmount").value(360_000))
                .andExpect(jsonPath("$.receiver.accountNumberMasked").value("****6789"))
                .andExpect(jsonPath("$.receiverSnapshotJson").doesNotExist());

        ArgumentCaptor<PropertyPaymentAttemptService.CreateCommand> createCaptor =
                ArgumentCaptor.forClass(PropertyPaymentAttemptService.CreateCommand.class);
        verify(attemptService).create(createCaptor.capture());
        assertEquals(42L, createCaptor.getValue().reservationId());
        assertEquals(PropertyPaymentAttempt.Purpose.DEPOSIT, createCaptor.getValue().purpose());
        assertEquals("manual_transfer", createCaptor.getValue().method());

        mockMvc.perform(get("/api/payment-attempts/attempt-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));

        mockMvc.perform(post("/api/payment-attempts/attempt-001/cancel")
                        .header("Idempotency-Key", "cancel-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void exposesPermissionedManualConfirmation() throws Exception {
        LocalDateTime confirmedAt = LocalDateTime.of(2026, 7, 31, 12, 5);
        when(manualConfirmationService.confirm(any())).thenReturn(
                new ManualTransferConfirmationService.ConfirmationResult(
                        "attempt-001", "transaction-001", PaymentState.SUCCESS,
                        BigDecimal.valueOf(360_000), confirmedAt, false));

        mockMvc.perform(post("/api/management/payment-attempts/attempt-001/confirm-manual")
                        .header("Idempotency-Key", "manual-key")
                        .header("X-Correlation-ID", "corr-manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Bank statement reviewed",
                                  "evidenceReference": "BANK-TRACE-001",
                                  "hotelId": 999
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").value("attempt-001"))
                .andExpect(jsonPath("$.transactionId").value("transaction-001"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        ArgumentCaptor<ManualTransferConfirmationService.ConfirmCommand> captor =
                ArgumentCaptor.forClass(ManualTransferConfirmationService.ConfirmCommand.class);
        verify(manualConfirmationService).confirm(captor.capture());
        assertEquals("BANK-TRACE-001", captor.getValue().evidenceReference());
        assertEquals("manual-key", captor.getValue().idempotencyKey());
    }

    @Test
    void callbackUsesOnlyServerResolvedEnvironmentMerchantAndCredentials() throws Exception {
        Map<String, ?> serverCredentials = Map.of("signingSecret", "server-only-secret");
        when(callbackCredentialsResolver.resolve(eq("simulator"), any())).thenReturn(
                new PropertyPaymentCallbackCredentialsResolver.CallbackContext(
                        PaymentEnvironment.SIMULATOR, "PROPERTY-SIMULATOR", serverCredentials));
        when(callbackService.process(any())).thenReturn(new PropertyPaymentCallbackService.CallbackResult(
                true, false, null, "attempt-001", PaymentState.SUCCESS, "transaction-001"));

        mockMvc.perform(post("/api/payment-providers/property/simulator/callback")
                        .header("X-Payment-Signature", "signed-value")
                        .header("X-Correlation-ID", "corr-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantId": "PROPERTY-SIMULATOR",
                                  "reference": "attempt-001",
                                  "transactionId": "provider-001",
                                  "eventId": "event-001",
                                  "amount": 360000,
                                  "currency": "VND",
                                  "status": "SUCCESS",
                                  "environment": "PRODUCTION",
                                  "credentials": {"signingSecret": "attacker-secret"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.transactionId").value("transaction-001"));

        ArgumentCaptor<PropertyPaymentCallbackService.CallbackCommand> captor =
                ArgumentCaptor.forClass(PropertyPaymentCallbackService.CallbackCommand.class);
        verify(callbackService).process(captor.capture());
        assertEquals(PaymentEnvironment.SIMULATOR, captor.getValue().environment());
        assertEquals("PROPERTY-SIMULATOR", captor.getValue().expectedMerchantId());
        assertEquals(serverCredentials, captor.getValue().credentials());
    }

    @Test
    void callbackReturnsStableProviderDenialStatus() throws Exception {
        when(callbackCredentialsResolver.resolve(eq("SIMULATOR"), any())).thenReturn(
                new PropertyPaymentCallbackCredentialsResolver.CallbackContext(
                        PaymentEnvironment.SIMULATOR,
                        "PROPERTY-SIMULATOR",
                        Map.of("signingSecret", "server-only-secret")));
        when(callbackService.process(any())).thenReturn(new PropertyPaymentCallbackService.CallbackResult(
                false, false, FinancialErrorCode.CALLBACK_AMOUNT_MISMATCH,
                "attempt-001", PaymentState.PENDING, null));

        mockMvc.perform(post("/api/payment-providers/property/SIMULATOR/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference\":\"attempt-001\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.errorCode").value("CALLBACK_AMOUNT_MISMATCH"));
    }

    private PropertyPaymentAttemptService.AttemptResponse attempt(
            boolean replayed,
            PaymentState status,
            LocalDateTime now) {
        return new PropertyPaymentAttemptService.AttemptResponse(
                71L,
                "attempt-001",
                42L,
                PropertyPaymentAttempt.Purpose.DEPOSIT,
                status,
                PaymentEnvironment.SIMULATOR,
                BigDecimal.valueOf(360_000),
                "VND",
                now.plusMinutes(30),
                "MANUAL_TRANSFER",
                "BANK",
                new PropertyPaymentAttemptService.ReceiverSnapshot(
                        "Test Bank", "TEST", "LUXESTAY", "****6789",
                        "VIETQR", null, "Huong dan", "Instructions"),
                "{\"accountNumberMasked\":\"****6789\"}",
                "BOOKING LS42-ABC12345",
                replayed);
    }
}
