package com.hotel.services.payment;

import com.hotel.config.MomoPaymentConfig;
import com.hotel.dtos.MomoCallbackRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.PaymentSession;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MomoPaymentGatewayTest {

    @Mock
    private MomoPaymentConfig config;

    private MomoPaymentGateway gateway;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        when(config.isConfigured()).thenReturn(true);
        when(config.getPartnerCode()).thenReturn("MOMO_TEST");
        when(config.getAccessKey()).thenReturn("access-key");
        when(config.getSecretKey()).thenReturn("secret-key");
        when(config.getCreateUrl()).thenReturn("https://test-payment.momo.vn/v2/gateway/api/create");
        when(config.getQueryUrl()).thenReturn("https://test-payment.momo.vn/v2/gateway/api/query");
        when(config.getRefundUrl()).thenReturn("https://test-payment.momo.vn/v2/gateway/api/refund");
        when(config.getRefundQueryUrl()).thenReturn("https://test-payment.momo.vn/v2/gateway/api/refund/query");
        when(config.getRedirectUrl()).thenReturn("http://localhost:4200/payment-result");
        when(config.getIpnUrl()).thenReturn("https://merchant.test/api/payments/momo-ipn");
        when(config.getRequestType()).thenReturn("captureWallet");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new MomoPaymentGateway(config, builder);
    }

    @Test
    void createPaymentUrl_SignsServerOwnedOrderAndVerifiesProviderResponse() {
        PaymentSession session = session();
        String payUrl = "https://test-payment.momo.vn/v2/gateway/pay?t=opaque";
        long responseTime = 1_722_000_000_000L;
        String responsePayload = "accessKey=access-key&amount=350000&orderId=MOMO-order-1"
                + "&partnerCode=MOMO_TEST&payUrl=" + payUrl
                + "&requestId=session-1&responseTime=" + responseTime + "&resultCode=0";
        String responseSignature = PaymentSignature.hmacSha256("secret-key", responsePayload);

        server.expect(requestTo("https://test-payment.momo.vn/v2/gateway/api/create"))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "partnerCode":"MOMO_TEST",
                          "requestType":"captureWallet",
                          "orderId":"MOMO-order-1",
                          "amount":350000,
                          "requestId":"session-1"
                        }
                        """, false))
                .andRespond(withSuccess("""
                        {
                          "partnerCode":"MOMO_TEST",
                          "orderId":"MOMO-order-1",
                          "requestId":"session-1",
                          "amount":350000,
                          "responseTime":1722000000000,
                          "resultCode":0,
                          "payUrl":"https://test-payment.momo.vn/v2/gateway/pay?t=opaque",
                          "signature":"%s"
                        }
                        """.formatted(responseSignature), MediaType.APPLICATION_JSON));

        assertThat(gateway.createPaymentUrl(session, LocalDateTime.of(2026, 7, 29, 11, 0)))
                .isEqualTo(payUrl);
        server.verify();
    }

    @Test
    void verifyCallback_RejectsTamperAndAcceptsSignedServerBinding() {
        MomoCallbackRequest request = callbackRequest();
        request.setSignature(PaymentSignature.hmacSha256("secret-key", gateway.callbackSignaturePayload(request)));

        MomoCallbackVerification valid = gateway.verifyCallback(request);
        assertThat(valid.valid()).isTrue();
        assertThat(valid.data().providerReference()).isEqualTo("MOMO-order-1");
        assertThat(valid.data().amount()).isEqualByComparingTo("350000");

        request.setAmount(1L);
        assertThat(gateway.verifyCallback(request).valid()).isFalse();
    }

    @Test
    void queryTransaction_SignsOfficialContractAndValidatesResponseBinding() {
        String requestId = "session-1-query";
        String signature = PaymentSignature.hmacSha256(
                "secret-key",
                "accessKey=access-key&orderId=MOMO-order-1&partnerCode=MOMO_TEST&requestId=" + requestId);

        server.expect(requestTo("https://test-payment.momo.vn/v2/gateway/api/query"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {
                          "partnerCode":"MOMO_TEST",
                          "requestId":"session-1-query",
                          "orderId":"MOMO-order-1",
                          "signature":"%s",
                          "lang":"vi"
                        }
                        """.formatted(signature), true))
                .andRespond(withSuccess("""
                        {
                          "partnerCode":"MOMO_TEST",
                          "requestId":"session-1-query",
                          "orderId":"MOMO-order-1",
                          "amount":350000,
                          "transId":4088878653,
                          "resultCode":0,
                          "message":"Successful."
                        }
                        """, MediaType.APPLICATION_JSON));

        ProviderTransactionQueryResult result = gateway.queryTransaction("MOMO-order-1", requestId);

        assertThat(result.status()).isEqualTo(ProviderOperationStatus.SUCCEEDED);
        assertThat(result.providerTransactionId()).isEqualTo("4088878653");
        assertThat(result.amount()).isEqualByComparingTo("350000");
        server.verify();
    }

    @Test
    void requestRefund_BindsPersistedAmountAndTransactionToOfficialSignature() {
        ProviderRefundCommand command = new ProviderRefundCommand(
                "MOMO-R-refund-public-id",
                "REFUND-refund-public-id-1",
                "4088878653",
                new BigDecimal("250000"),
                "RESERVATION_CANCELLED");
        String signaturePayload = "accessKey=access-key&amount=250000&description=RESERVATION_CANCELLED"
                + "&orderId=MOMO-R-refund-public-id&partnerCode=MOMO_TEST"
                + "&requestId=REFUND-refund-public-id-1&transId=4088878653";
        String signature = PaymentSignature.hmacSha256("secret-key", signaturePayload);

        server.expect(requestTo("https://test-payment.momo.vn/v2/gateway/api/refund"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {
                          "partnerCode":"MOMO_TEST",
                          "orderId":"MOMO-R-refund-public-id",
                          "requestId":"REFUND-refund-public-id-1",
                          "amount":250000,
                          "transId":4088878653,
                          "description":"RESERVATION_CANCELLED",
                          "signature":"%s"
                        }
                        """.formatted(signature), false))
                .andRespond(withSuccess("""
                        {
                          "partnerCode":"MOMO_TEST",
                          "orderId":"MOMO-R-refund-public-id",
                          "requestId":"REFUND-refund-public-id-1",
                          "amount":250000,
                          "transId":0,
                          "resultCode":7000,
                          "message":"Transaction is being processed."
                        }
                        """, MediaType.APPLICATION_JSON));

        ProviderRefundResult result = gateway.requestRefund(command);

        assertThat(result.status()).isEqualTo(ProviderOperationStatus.PENDING);
        assertThat(result.providerRefundReference()).isEqualTo("MOMO-R-refund-public-id");
        server.verify();
    }

    @Test
    void queryRefund_RequiresMatchingRefundReferenceAndAmountBeforeSuccess() {
        ProviderRefundQuery query = new ProviderRefundQuery(
                "MOMO-R-refund-public-id",
                "Q-refund-public-id",
                new BigDecimal("250000"));
        String signature = PaymentSignature.hmacSha256(
                "secret-key",
                "accessKey=access-key&orderId=MOMO-R-refund-public-id&partnerCode=MOMO_TEST"
                        + "&requestId=Q-refund-public-id");

        server.expect(requestTo("https://test-payment.momo.vn/v2/gateway/api/refund/query"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {
                          "partnerCode":"MOMO_TEST",
                          "requestId":"Q-refund-public-id",
                          "orderId":"MOMO-R-refund-public-id",
                          "lang":"vi",
                          "signature":"%s"
                        }
                        """.formatted(signature), true))
                .andRespond(withSuccess("""
                        {
                          "partnerCode":"MOMO_TEST",
                          "requestId":"Q-refund-public-id",
                          "orderId":"MOMO-R-refund-public-id",
                          "resultCode":0,
                          "message":"Successful.",
                          "refundTrans":[{
                            "orderId":"MOMO-R-refund-public-id",
                            "amount":1,
                            "resultCode":0,
                            "transId":90001
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.queryRefund(query))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("amount");
        server.verify();
    }

    private PaymentSession session() {
        User user = new User();
        user.setId(7L);
        Hotel hotel = new Hotel();
        hotel.setId(3L);
        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setUser(user);
        reservation.setHotel(hotel);

        PaymentSession session = new PaymentSession();
        session.setPublicId("session-1");
        session.setReservation(reservation);
        session.setHotel(hotel);
        session.setOwner(user);
        session.setProvider("MOMO");
        session.setProviderReference("MOMO-order-1");
        session.setExpectedAmount(new BigDecimal("350000"));
        session.setExpiresAt(LocalDateTime.of(2026, 7, 29, 11, 15));
        return session;
    }

    private MomoCallbackRequest callbackRequest() {
        MomoCallbackRequest request = new MomoCallbackRequest();
        request.setPartnerCode("MOMO_TEST");
        request.setOrderId("MOMO-order-1");
        request.setRequestId("session-1");
        request.setAmount(350000L);
        request.setOrderInfo("Thanh toan dat phong 42");
        request.setOrderType("momo_wallet");
        request.setTransId(4088878653L);
        request.setResultCode(0);
        request.setMessage("Successful.");
        request.setPayType("qr");
        request.setResponseTime(1721720663942L);
        request.setExtraData("");
        return request;
    }
}
