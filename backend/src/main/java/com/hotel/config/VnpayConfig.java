package com.hotel.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Configuration
public class VnpayConfig {

    @Value("${payment.vnpay.tmn-code}")
    private String vnp_TmnCode;

    @Value("${payment.vnpay.hash-secret}")
    private String vnp_HashSecret;

    @Value("${payment.vnpay.url}")
    private String vnp_PayUrl;

    @Value("${payment.vnpay.return-url}")
    private String vnp_ReturnUrl;

    @Value("${payment.vnpay.version}")
    private String vnp_Version;

    @Value("${payment.vnpay.command}")
    private String vnp_Command;

    public String getTmnCode() {
        return vnp_TmnCode;
    }

    public String getHashSecret() {
        return vnp_HashSecret;
    }

    public String getPayUrl() {
        return vnp_PayUrl;
    }

    public String getReturnUrl() {
        return vnp_ReturnUrl;
    }

    public String getVersion() {
        return vnp_Version;
    }

    public String getCommand() {
        return vnp_Command;
    }

    // Util functions for hash
    public static String hmacSHA512(final String key, final String data) {
        try {
            if (key == null || data == null) {
                throw new NullPointerException();
            }
            final Mac hmac512 = Mac.getInstance("HmacSHA512");
            byte[] hmacKeyBytes = key.getBytes();
            final SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA512");
            hmac512.init(secretKey);
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] result = hmac512.doFinal(dataBytes);
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    public static String getIpAddress(HttpServletRequest request) {
        String ipAdress;
        try {
            ipAdress = request.getHeader("X-FORWARDED-FOR");
            if (ipAdress == null) {
                ipAdress = request.getRemoteAddr();
            }
        } catch (Exception e) {
            ipAdress = "Invalid IP:" + e.getMessage();
        }
        return ipAdress;
    }

    public static String getRandomNumber(int len) {
        Random rnd = new Random();
        String chars = "0123456789";
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
