package com.hotel.services.payment;

import com.hotel.config.VnpayConfig;
import com.hotel.entities.PaymentSession;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.security.MessageDigest;

@Service
public class VnpayPaymentGateway {

    private static final DateTimeFormatter VNPAY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final VnpayConfig config;

    public VnpayPaymentGateway(VnpayConfig config) {
        this.config = config;
    }

    public String createPaymentUrl(PaymentSession session, String clientIp, LocalDateTime createdAt) {
        long providerAmount = session.getExpectedAmount().movePointRight(2).longValueExact();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_Version", config.getVersion());
        fields.put("vnp_Command", config.getCommand());
        fields.put("vnp_TmnCode", config.getTmnCode());
        fields.put("vnp_Amount", Long.toString(providerAmount));
        fields.put("vnp_CurrCode", "VND");
        fields.put("vnp_TxnRef", session.getProviderReference());
        fields.put("vnp_OrderInfo", "Thanh toan dat phong " + session.getReservation().getId());
        fields.put("vnp_OrderType", "other");
        fields.put("vnp_Locale", "vn");
        fields.put("vnp_ReturnUrl", PaymentUrlSupport.appendQueryParameter(
                PaymentUrlSupport.appendQueryParameter(config.getReturnUrl(), "session", session.getPublicId()),
                "provider",
                "VNPAY"));
        fields.put("vnp_IpAddr", clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp);
        fields.put("vnp_CreateDate", formatVietnamTime(createdAt));
        fields.put("vnp_ExpireDate", formatVietnamTime(session.getExpiresAt()));

        String hashPayload = canonicalPayload(fields);
        return config.getPayUrl() + "?" + hashPayload
                + "&vnp_SecureHash=" + VnpayConfig.hmacSHA512(config.getHashSecret(), hashPayload);
    }

    String formatVietnamTime(LocalDateTime utcTime) {
        return utcTime.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(VIETNAM_ZONE)
                .format(VNPAY_TIME);
    }

    public String canonicalPayload(Map<String, String> fields) {
        List<String> names = new ArrayList<>(fields.keySet());
        Collections.sort(names);
        StringBuilder payload = new StringBuilder();
        for (String name : names) {
            String value = fields.get(name);
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!payload.isEmpty()) {
                payload.append('&');
            }
            payload.append(URLEncoder.encode(name, StandardCharsets.US_ASCII));
            payload.append('=');
            payload.append(URLEncoder.encode(value, StandardCharsets.US_ASCII));
        }
        return payload.toString();
    }

    public VnpayCallbackVerification verifyCallback(Map<String, String> requestFields) {
        if (requestFields == null || requestFields.isEmpty()) {
            return VnpayCallbackVerification.invalid("99", "Invalid request");
        }
        Map<String, String> signedFields = new LinkedHashMap<>();
        requestFields.forEach((name, value) -> {
            if (name != null && name.startsWith("vnp_")) {
                signedFields.put(name, value);
            }
        });
        String suppliedHash = signedFields.remove("vnp_SecureHash");
        signedFields.remove("vnp_SecureHashType");
        if (suppliedHash == null || suppliedHash.isBlank()) {
            return VnpayCallbackVerification.invalid("97", "Invalid Checksum");
        }

        String expectedHash = VnpayConfig.hmacSHA512(config.getHashSecret(), canonicalPayload(signedFields));
        if (!MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.US_ASCII),
                suppliedHash.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
            return VnpayCallbackVerification.invalid("97", "Invalid Checksum");
        }
        if (!config.getTmnCode().equals(signedFields.get("vnp_TmnCode"))) {
            return VnpayCallbackVerification.invalid("03", "Invalid Merchant");
        }

        String providerReference = signedFields.get("vnp_TxnRef");
        String providerTransactionId = signedFields.get("vnp_TransactionNo");
        String amountValue = signedFields.get("vnp_Amount");
        String responseCode = signedFields.get("vnp_ResponseCode");
        String transactionStatus = signedFields.get("vnp_TransactionStatus");
        if (providerReference == null || providerReference.isBlank()
                || amountValue == null || amountValue.isBlank()
                || responseCode == null || transactionStatus == null) {
            return VnpayCallbackVerification.invalid("99", "Invalid request");
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountValue).movePointLeft(2);
        } catch (NumberFormatException exception) {
            return VnpayCallbackVerification.invalid("04", "Invalid Amount");
        }
        boolean successful = "00".equals(responseCode) && "00".equals(transactionStatus);
        if (successful && (providerTransactionId == null || providerTransactionId.isBlank())) {
            return VnpayCallbackVerification.invalid("99", "Missing provider transaction");
        }
        return VnpayCallbackVerification.valid(new VnpayCallbackData(
                providerReference,
                providerTransactionId,
                amount,
                responseCode,
                transactionStatus,
                successful));
    }
}
