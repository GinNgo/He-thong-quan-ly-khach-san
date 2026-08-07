package com.hotel.services.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.config.ZaloPayConfig;
import com.hotel.dtos.ZaloPayCallbackRequest;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZaloPayPaymentGatewayTest {

    @Mock
    private ZaloPayConfig config;

    private ZaloPayPaymentGateway gateway;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        when(config.isConfigured()).thenReturn(true);
        when(config.requireAppId()).thenReturn(2553);
        when(config.getKey1()).thenReturn("key-one");
        when(config.getKey2()).thenReturn("key-two");
        when(config.getCreateUrl()).thenReturn("https://sb-openapi.zalopay.vn/v2/create");
        when(config.getQueryUrl()).thenReturn("https://sb-openapi.zalopay.vn/v2/query");
        when(config.getRefundUrl()).thenReturn("https://sb-openapi.zalopay.vn/v2/refund");
        when(config.getRefundQueryUrl()).thenReturn("https://sb-openapi.zalopay.vn/v2/query_refund");
        when(config.getRedirectUrl()).thenReturn("http://localhost:4200/payment-result");
        when(config.getCallbackUrl()).thenReturn("https://merchant.test/api/payments/zalopay-callback");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new ZaloPayPaymentGateway(config, new ObjectMapper(), builder);
    }

    @Test
    void createPaymentUrl_UsesOfficialFormContractAndServerOwnedReference() {
        server.expect(requestTo("https://sb-openapi.zalopay.vn/v2/create"))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("app_trans_id=260729_order1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("amount=350000")))
                .andRespond(withSuccess("""
                        {"return_code":1,"return_message":"Success","order_url":"https://sbgateway.zalopay.vn/openinapp?order=opaque"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(gateway.createPaymentUrl(session(), LocalDateTime.of(2026, 7, 29, 11, 0)))
                .isEqualTo("https://sbgateway.zalopay.vn/openinapp?order=opaque");
        server.verify();
    }

    @Test
    void verifyCallback_ValidatesRawDataWithKey2AndRejectsTampering() {
        String data = """
                {"app_id":2553,"app_trans_id":"260729_order1","amount":350000,"zp_trans_id":230407000006575}
                """.trim();
        ZaloPayCallbackRequest request = new ZaloPayCallbackRequest();
        request.setType(1);
        request.setData(data);
        request.setMac(PaymentSignature.hmacSha256("key-two", data));

        ZaloPayCallbackVerification valid = gateway.verifyCallback(request);
        assertThat(valid.valid()).isTrue();
        assertThat(valid.data().providerReference()).isEqualTo("260729_order1");
        assertThat(valid.data().amount()).isEqualByComparingTo("350000");

        request.setData(data.replace("350000", "1"));
        assertThat(gateway.verifyCallback(request).valid()).isFalse();
    }

    @Test
    void queryTransaction_UsesOfficialMacAndMapsProcessingState() {
        String mac = PaymentSignature.hmacSha256("key-one", "2553|260729_order1|key-one");
        server.expect(requestTo("https://sb-openapi.zalopay.vn/v2/query"))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("app_id=2553")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("app_trans_id=260729_order1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("mac=" + mac)))
                .andRespond(withSuccess("""
                        {"return_code":3,"return_message":"Processing","is_processing":true}
                        """, MediaType.APPLICATION_JSON));

        ProviderTransactionQueryResult result = gateway.queryTransaction("260729_order1");

        assertThat(result.status()).isEqualTo(ProviderOperationStatus.PENDING);
        server.verify();
    }

    @Test
    void requestRefund_UsesPersistedTransactionAndReturnsPendingUntilQuery() {
        long timestamp = Instant.parse("2026-07-30T02:00:00Z").toEpochMilli();
        ProviderRefundCommand command = new ProviderRefundCommand(
                "260730_2553_refundpublicid",
                "unused-for-zalopay",
                "230407000006575",
                new BigDecimal("250000"),
                "RESERVATION_CANCELLED");
        String mac = PaymentSignature.hmacSha256(
                "key-one",
                "2553|230407000006575|250000|RESERVATION_CANCELLED|" + timestamp);

        server.expect(requestTo("https://sb-openapi.zalopay.vn/v2/refund"))
                .andExpect(method(POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("m_refund_id=260730_2553_refundpublicid")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("zp_trans_id=230407000006575")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("amount=250000")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("mac=" + mac)))
                .andRespond(withSuccess("""
                        {"return_code":1,"return_message":"Success","refund_id":99112233}
                        """, MediaType.APPLICATION_JSON));

        ProviderRefundResult result = gateway.requestRefund(command, timestamp);

        assertThat(result.status()).isEqualTo(ProviderOperationStatus.PENDING);
        assertThat(result.providerTransactionId()).isEqualTo("99112233");
        server.verify();
    }

    @Test
    void queryRefund_MapsSuccessAndUnknownReferenceWithoutClaimingCompletion() {
        long timestamp = LocalDateTime.of(2026, 7, 30, 9, 0)
                .toInstant(ZoneOffset.ofHours(7)).toEpochMilli();
        String mac = PaymentSignature.hmacSha256(
                "key-one",
                "2553|260730_2553_refundpublicid|" + timestamp);
        server.expect(requestTo("https://sb-openapi.zalopay.vn/v2/query_refund"))
                .andExpect(method(POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("mac=" + mac)))
                .andRespond(withSuccess("""
                        {
                          "return_code":2,
                          "return_message":"Fail",
                          "sub_return_code":-101,
                          "sub_return_message":"M_REFUND_ID_NOT_FOUND"
                        }
                        """, MediaType.APPLICATION_JSON));

        ProviderRefundResult result = gateway.queryRefund(
                new ProviderRefundQuery(
                        "260730_2553_refundpublicid",
                        "unused-for-zalopay",
                        new BigDecimal("250000")),
                timestamp);

        assertThat(result.status()).isEqualTo(ProviderOperationStatus.NOT_FOUND);
        assertThat(result.responseCode()).isEqualTo("2/-101");
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
        session.setProvider("ZALOPAY");
        session.setProviderReference("260729_order1");
        session.setExpectedAmount(new BigDecimal("350000"));
        session.setExpiresAt(LocalDateTime.of(2026, 7, 29, 11, 15));
        return session;
    }
}
