package com.hotel.services.payment;

import com.hotel.config.VnpayConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VnpayPaymentGatewayTest {

    @Mock
    private VnpayConfig config;

    private VnpayPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        when(config.getHashSecret()).thenReturn("test_hash_secret");
        when(config.getTmnCode()).thenReturn("TEST_TMN");
        gateway = new VnpayPaymentGateway(config);
    }

    @Test
    void verifyCallback_RequiresSignatureMerchantAmountAndBothSuccessStatuses() {
        Map<String, String> fields = successfulFields();
        sign(fields);

        VnpayCallbackVerification verification = gateway.verifyCallback(fields);

        assertTrue(verification.valid());
        assertTrue(verification.data().successful());
        assertEquals("VNPAY-reference", verification.data().providerReference());
        assertEquals("350000.00", verification.data().amount().setScale(2).toPlainString());
    }

    @Test
    void verifyCallback_RejectsTamperingAndWrongMerchant() {
        Map<String, String> tampered = successfulFields();
        sign(tampered);
        tampered.put("vnp_Amount", "1");

        VnpayCallbackVerification invalidSignature = gateway.verifyCallback(tampered);
        assertFalse(invalidSignature.valid());
        assertEquals("97", invalidSignature.responseCode());

        Map<String, String> wrongMerchant = successfulFields();
        wrongMerchant.put("vnp_TmnCode", "OTHER_TMN");
        sign(wrongMerchant);
        VnpayCallbackVerification invalidMerchant = gateway.verifyCallback(wrongMerchant);
        assertFalse(invalidMerchant.valid());
        assertEquals("03", invalidMerchant.responseCode());
    }

    @Test
    void verifyCallback_DoesNotTreatResponseCodeAloneAsSuccess() {
        Map<String, String> fields = successfulFields();
        fields.put("vnp_TransactionStatus", "01");
        sign(fields);

        VnpayCallbackVerification verification = gateway.verifyCallback(fields);

        assertTrue(verification.valid());
        assertFalse(verification.data().successful());
    }

    @Test
    void verifyCallback_IgnoresMerchantReturnPageQueryParameters() {
        Map<String, String> fields = successfulFields();
        sign(fields);
        fields.put("session", "opaque-session");
        fields.put("provider", "VNPAY");

        assertTrue(gateway.verifyCallback(fields).valid());
    }

    private Map<String, String> successfulFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_TmnCode", "TEST_TMN");
        fields.put("vnp_TxnRef", "VNPAY-reference");
        fields.put("vnp_Amount", "35000000");
        fields.put("vnp_ResponseCode", "00");
        fields.put("vnp_TransactionStatus", "00");
        fields.put("vnp_TransactionNo", "14927984");
        fields.put("vnp_PayDate", "20260729110000");
        return fields;
    }

    private void sign(Map<String, String> fields) {
        Map<String, String> unsigned = new LinkedHashMap<>(fields);
        unsigned.remove("vnp_SecureHash");
        fields.put("vnp_SecureHash", VnpayConfig.hmacSHA512(
                "test_hash_secret",
                gateway.canonicalPayload(unsigned)));
    }
}
