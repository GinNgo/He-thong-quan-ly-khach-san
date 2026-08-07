package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.config.SecurityConfig;
import com.hotel.config.VnpayConfig;
import com.hotel.controllers.PaymentController;
import com.hotel.dtos.ReservationDTO;
import com.hotel.dtos.PaymentSessionResponse;
import com.hotel.dtos.PaymentSessionStatusResponse;
import com.hotel.domain.payment.PaymentProvider;
import com.hotel.entities.User;
import com.hotel.observability.OperationalMetrics;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.JwtAccessDeniedHandler;
import com.hotel.security.JwtAuthFilter;
import com.hotel.security.JwtAuthenticationEntryPoint;
import com.hotel.security.JwtTokenProvider;
import com.hotel.security.TenantFilterInterceptor;
<<<<<<< HEAD
import com.hotel.observability.OperationalMetrics;
=======
>>>>>>> codex/ui-functional-audit-polish
import com.hotel.services.PaymentService;
import com.hotel.services.PaymentSessionService;
import com.hotel.services.payment.VnpayPaymentGateway;
import com.hotel.services.payment.MomoPaymentGateway;
import com.hotel.services.payment.ZaloPayPaymentGateway;
import com.hotel.services.payment.MomoCallbackVerification;
import com.hotel.services.payment.ProviderCallbackData;
import com.hotel.services.payment.ProviderCallbackOutcome;
import com.hotel.services.payment.ZaloPayCallbackVerification;
import com.hotel.services.payment.VnpayCallbackData;
import com.hotel.services.payment.VnpayCallbackVerification;
import com.hotel.services.payment.VnpayIpnResponse;
import com.hotel.services.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@ContextConfiguration(classes = BackendApplication.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private ReservationService reservationService;

    @MockBean
    private PaymentSessionService paymentSessionService;

    @MockBean
    private VnpayPaymentGateway vnpayPaymentGateway;

    @MockBean
    private MomoPaymentGateway momoPaymentGateway;

    @MockBean
    private ZaloPayPaymentGateway zaloPayPaymentGateway;

    @MockBean
    private VnpayConfig vnpayConfig;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private TenantFilterInterceptor tenantFilterInterceptor;
<<<<<<< HEAD

    @MockBean
    private OperationalMetrics operationalMetrics;

    @BeforeEach
    void allowRequestsThroughTenantInterceptor() throws Exception {
        when(tenantFilterInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void createPaymentUrl_AsCustomer_ShouldUseReservationAmount() throws Exception {
        ReservationDTO reservation = new ReservationDTO();
        reservation.setId(42L);
        reservation.setTotalAmount(new BigDecimal("100000"));
        when(reservationService.getReservationById(42L)).thenReturn(reservation);
=======
>>>>>>> codex/ui-functional-audit-polish

    @MockBean
    private OperationalMetrics operationalMetrics;

    @BeforeEach
    void allowTenantInterceptorToContinue() throws Exception {
        when(tenantFilterInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void createPaymentSession_AsCustomer_ShouldReturnServerBoundSession() throws Exception {
        PaymentSessionResponse response = PaymentSessionResponse.builder()
                .sessionId("session-123")
                .reservationId(42L)
                .provider("MOMO")
                .method("MOMO")
                .amount(new BigDecimal("100000"))
                .currency("VND")
                .status("PENDING")
                .mode("SIMULATOR")
                .expiresAt(LocalDateTime.of(2026, 7, 29, 12, 0))
                .url("http://localhost:4200/payment-simulator?token=signed-token")
                .build();
        when(paymentSessionService.createSession(42L, "MOMO", "idem-12345678", "127.0.0.1"))
                .thenReturn(response);

        mockMvc.perform(post("/api/payments/sessions")
                        .contentType(APPLICATION_JSON)
                        .header("Idempotency-Key", "idem-12345678")
                        .content("{\"reservationId\":42,\"provider\":\"MOMO\"}")
                        .with(request -> { request.setRemoteAddr("127.0.0.1"); return request; })
                        .with(user(customer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-123"))
                .andExpect(jsonPath("$.amount").value(100000))
                .andExpect(jsonPath("$.url").value("http://localhost:4200/payment-simulator?token=signed-token"));
    }

    @Test
    void createPaymentUrl_WithoutAuth_ShouldReturn401() throws Exception {
        mockMvc.perform(post("/api/payments/sessions")
                        .contentType(APPLICATION_JSON)
                        .header("Idempotency-Key", "idem-12345678")
                        .content("{\"reservationId\":42,\"provider\":\"MOMO\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyDirectMutation_IsRetiredAndCannotCreatePaymentFromCallerPayload() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(APPLICATION_JSON)
                        .content("{\"reservationId\":42,\"amount\":1,\"paymentMethod\":\"CASH\",\"transactionId\":\"replay\"}")
                        .with(user(financeCreator())))
                .andExpect(status().isGone());

        verifyNoInteractions(paymentService);
    }

    @Test
    void legacyDirectMutation_WithoutAuthentication_IsRejectedBeforeRetirementResponse() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(APPLICATION_JSON)
                        .content("{\"reservationId\":42,\"amount\":999999999,\"paymentMethod\":\"MOMO\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(paymentService);
    }

    @Test
    void createPaymentSession_WithoutIdempotencyKey_ShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/payments/sessions")
                        .contentType(APPLICATION_JSON)
                        .content("{\"reservationId\":42,\"provider\":\"MOMO\"}")
                        .with(user(customer())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void vnpayBrowserReturn_IsDisplayOnlyAndNeverConfirmsFinancialState() throws Exception {
        VnpayCallbackData data = new VnpayCallbackData(
                "VNPAY-reference",
                "14927984",
                new BigDecimal("100000"),
                "00",
                "00",
                true);
        when(vnpayPaymentGateway.verifyCallback(org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(VnpayCallbackVerification.valid(data));

        mockMvc.perform(get("/api/payments/vnpay-callback")
                        .param("vnp_TxnRef", "VNPAY-reference"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));

        verifyNoInteractions(paymentService);
    }

    @Test
    void vnpayIpn_ReturnsProviderAcknowledgement() throws Exception {
        VnpayCallbackData data = new VnpayCallbackData(
                "VNPAY-reference",
                "14927984",
                new BigDecimal("100000"),
                "00",
                "00",
                true);
        when(vnpayPaymentGateway.verifyCallback(org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(VnpayCallbackVerification.valid(data));
        when(paymentSessionService.processVnpayCallback(data))
                .thenReturn(new VnpayIpnResponse("00", "Confirm Success"));

        mockMvc.perform(get("/api/payments/vnpay-ipn")
                        .param("vnp_TxnRef", "VNPAY-reference"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"))
                .andExpect(jsonPath("$.Message").value("Confirm Success"));
    }

    @Test
    void paymentSessionStatus_RequiresOwnerAuthenticationAndReturnsAuthoritativeState() throws Exception {
        when(paymentSessionService.getOwnedSessionStatus("session-123"))
                .thenReturn(PaymentSessionStatusResponse.builder()
                        .sessionId("session-123")
                        .reservationId(42L)
                        .provider("ZALOPAY")
                        .amount(new BigDecimal("100000"))
                        .currency("VND")
                        .status("PENDING")
                        .expiresAt(LocalDateTime.of(2026, 7, 29, 12, 0))
                        .build());

        mockMvc.perform(get("/api/payments/sessions/session-123").with(user(customer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.provider").value("ZALOPAY"));

        mockMvc.perform(get("/api/payments/sessions/session-123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void momoIpn_UsesVerifiedServerCallbackAndReturnsRequired204() throws Exception {
        ProviderCallbackData data = new ProviderCallbackData(
                PaymentProvider.MOMO,
                "MOMO-order-1",
                "4088878653",
                new BigDecimal("100000"),
                true,
                null);
        when(momoPaymentGateway.verifyCallback(any())).thenReturn(MomoCallbackVerification.valid(data));
        when(paymentSessionService.processProviderCallback(data)).thenReturn(ProviderCallbackOutcome.CONFIRMED);

        mockMvc.perform(post("/api/payments/momo-ipn")
                        .contentType(APPLICATION_JSON)
                        .content("{\"partnerCode\":\"MOMO_TEST\"}"))
                .andExpect(status().isNoContent());

        verify(paymentSessionService).processProviderCallback(data);
    }

    @Test
    void zaloPayCallback_AcknowledgesDuplicateButRejectsInvalidSignature() throws Exception {
        ProviderCallbackData data = new ProviderCallbackData(
                PaymentProvider.ZALOPAY,
                "260729_order1",
                "230407000006575",
                new BigDecimal("100000"),
                true,
                null);
        when(zaloPayPaymentGateway.verifyCallback(any()))
                .thenReturn(ZaloPayCallbackVerification.valid(data))
                .thenReturn(ZaloPayCallbackVerification.invalid("Invalid callback signature"));
        when(paymentSessionService.processProviderCallback(data)).thenReturn(ProviderCallbackOutcome.DUPLICATE);

        mockMvc.perform(post("/api/payments/zalopay-callback")
                        .contentType(APPLICATION_JSON)
                        .content("{\"data\":\"{}\",\"mac\":\"signed\",\"type\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.return_code").value(1));

        mockMvc.perform(post("/api/payments/zalopay-callback")
                        .contentType(APPLICATION_JSON)
                        .content("{\"data\":\"{}\",\"mac\":\"tampered\",\"type\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.return_code").value(2));
    }

    private CustomUserDetails customer() {
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("customer1");
        mockUser.setPasswordHash("hash");
        mockUser.setRoles(new HashSet<>());

        return new CustomUserDetails(
                mockUser.getUsername(),
                mockUser.getPasswordHash(),
                Set.of(new SimpleGrantedAuthority("CUSTOMER")),
                new HashMap<>(),
                mockUser.getId(),
                null,
                new HashMap<>()
        );
    }
<<<<<<< HEAD
=======

    private CustomUserDetails financeCreator() {
        return new CustomUserDetails(
                "finance-user",
                "hash",
                Set.of(new SimpleGrantedAuthority("ACCOUNTANT")),
                Map.of(com.hotel.security.FunctionCode.FINANCE, com.hotel.security.ActionCode.CREATE),
                2L,
                3L,
                new HashMap<>());
    }
>>>>>>> codex/ui-functional-audit-polish
}
