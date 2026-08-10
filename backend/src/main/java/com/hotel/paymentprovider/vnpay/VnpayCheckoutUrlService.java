package com.hotel.paymentprovider.vnpay;

import com.hotel.propertycommerce.payment.PropertyPaymentAttemptService;
import com.hotel.platformbilling.config.PlatformMerchantCredentialResolver.ResolvedMerchantCredentials;
import com.hotel.platformbilling.payment.PlatformPaymentAttemptService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.StringJoiner;

@Service
public class VnpayCheckoutUrlService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final Environment environment;

    public VnpayCheckoutUrlService(Environment environment) { this.environment = environment; }

    public String create(PropertyPaymentAttemptService.AttemptResponse attempt) {
        if (attempt == null || !"VNPAY".equalsIgnoreCase(attempt.provider())) return null;
        String secret = environment.getProperty("payment.vnpay.hash-secret", "");
        String endpoint = environment.getProperty("payment.vnpay.url", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        if (secret.isBlank() || endpoint.isBlank()) return null;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Version", "2.1.0"); params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", environment.getProperty("payment.vnpay.tmn-code", ""));
        params.put("vnp_Amount", attempt.expectedAmount().movePointRight(2).toBigInteger().toString());
        params.put("vnp_CurrCode", "VND"); params.put("vnp_TxnRef", attempt.publicId());
        params.put("vnp_OrderInfo", "Thanh toan dat phong " + attempt.reservationId());
        params.put("vnp_OrderType", "190000"); params.put("vnp_Locale", "vn");
        params.put("vnp_CreateDate", LocalDateTime.now().format(TIME)); params.put("vnp_ExpireDate", attempt.expiresAt().format(TIME));
        String query = canonical(params);
        return endpoint + "?" + query + "&vnp_SecureHash=" + hmac(secret, query);
    }

    public String create(
            PlatformPaymentAttemptService.AttemptResponse attempt,
            ResolvedMerchantCredentials credentials,
            String returnUrl) {
        if (attempt == null || !"VNPAY".equalsIgnoreCase(attempt.provider())
                || credentials == null || credentials.endpoint() == null) return null;
        String secret = credentials.secrets().getOrDefault("hashSecret", "");
        if (secret.isBlank() || credentials.merchantId() == null || credentials.merchantId().isBlank()
                || returnUrl == null || returnUrl.isBlank()) return null;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Version", "2.1.0"); params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", credentials.merchantId());
        params.put("vnp_Amount", attempt.expectedAmount().movePointRight(2).toBigInteger().toString());
        params.put("vnp_CurrCode", "VND"); params.put("vnp_TxnRef", attempt.providerOrderReference());
        params.put("vnp_OrderInfo", "Thanh toan goi phan mem " + attempt.orderPublicId());
        params.put("vnp_OrderType", "other"); params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl); params.put("vnp_IpAddr", "127.0.0.1");
        params.put("vnp_CreateDate", vietnamTime(LocalDateTime.now()));
        params.put("vnp_ExpireDate", vietnamTime(attempt.expiresAt()));
        String query = canonical(params);
        return credentials.endpoint() + "?" + query + "&vnp_SecureHash=" + hmac(secret, query);
    }

    private String vietnamTime(LocalDateTime utcTime) {
        return utcTime.atZone(ZoneOffset.UTC).withZoneSameInstant(VIETNAM_ZONE).format(TIME);
    }

    private String canonical(Map<String, String> values) {
        StringJoiner joiner = new StringJoiner("&");
        values.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> joiner.add(enc(e.getKey()) + "=" + enc(e.getValue())));
        return joiner.toString();
    }
    private String enc(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.US_ASCII); }
    private String hmac(String secret, String value) {
        try { Mac mac = Mac.getInstance("HmacSHA512"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            StringBuilder out = new StringBuilder(); for (byte b : mac.doFinal(value.getBytes(StandardCharsets.US_ASCII))) out.append(String.format("%02x", b)); return out.toString();
        } catch (Exception ex) { throw new IllegalStateException("Unable to sign VNPay request", ex); }
    }
}
