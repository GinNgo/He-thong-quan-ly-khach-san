package com.hotel.controllers;

import com.hotel.config.VnpayConfig;
import com.hotel.dtos.PaymentDTO;
import com.hotel.services.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.hotel.security.Permission;
import com.hotel.security.FunctionCode;
import com.hotel.security.ActionCode;
import org.springframework.web.bind.annotation.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;
    private final VnpayConfig vnpayConfig;

    @GetMapping("/reservation/{reservationId}")
    @Permission(function = FunctionCode.FINANCE, action = ActionCode.VIEW)
    public ResponseEntity<List<PaymentDTO>> getPaymentsByReservation(@PathVariable Long reservationId) {
        return ResponseEntity.ok(paymentService.getPaymentsByReservation(reservationId));
    }

    @PostMapping
    @Permission(function = FunctionCode.FINANCE, action = ActionCode.CREATE)
    public ResponseEntity<PaymentDTO> processPayment(@RequestBody PaymentDTO dto) {
        return ResponseEntity.ok(paymentService.processPayment(dto));
    }

    // Generate Online Payment URL
    @GetMapping("/create-url")
    @Permission(function = FunctionCode.FINANCE, action = ActionCode.CREATE)
    public ResponseEntity<java.util.Map<String, String>> createPaymentUrl(
            @RequestParam Long reservationId,
            @RequestParam String method,
            @RequestParam Double amount,
            HttpServletRequest request) {
        
        if ("VNPAY".equalsIgnoreCase(method)) {
            String vnp_Version = vnpayConfig.getVersion();
            String vnp_Command = vnpayConfig.getCommand();
            String vnp_OrderInfo = "Thanh toan dat phong " + reservationId;
            String orderType = "other";
            String vnp_TxnRef = reservationId + "_" + VnpayConfig.getRandomNumber(6);
            String vnp_IpAddr = VnpayConfig.getIpAddress(request);
            String vnp_TmnCode = vnpayConfig.getTmnCode();

            int amountParam = (int) (amount * 100);
            Map<String, String> vnp_Params = new HashMap<>();
            vnp_Params.put("vnp_Version", vnp_Version);
            vnp_Params.put("vnp_Command", vnp_Command);
            vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
            vnp_Params.put("vnp_Amount", String.valueOf(amountParam));
            vnp_Params.put("vnp_CurrCode", "VND");
            vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
            vnp_Params.put("vnp_OrderInfo", vnp_OrderInfo);
            vnp_Params.put("vnp_OrderType", orderType);
            vnp_Params.put("vnp_Locale", "vn");
            vnp_Params.put("vnp_ReturnUrl", vnpayConfig.getReturnUrl());
            vnp_Params.put("vnp_IpAddr", vnp_IpAddr);

            Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
            String vnp_CreateDate = formatter.format(cld.getTime());
            vnp_Params.put("vnp_CreateDate", vnp_CreateDate);
            
            cld.add(Calendar.MINUTE, 15);
            String vnp_ExpireDate = formatter.format(cld.getTime());
            vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

            // Build data to hash and querystring
            List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
            Collections.sort(fieldNames);
            StringBuilder hashData = new StringBuilder();
            StringBuilder query = new StringBuilder();
            Iterator<String> itr = fieldNames.iterator();
            while (itr.hasNext()) {
                String fieldName = itr.next();
                String fieldValue = vnp_Params.get(fieldName);
                if ((fieldValue != null) && (fieldValue.length() > 0)) {
                    //Build hash data
                    hashData.append(fieldName);
                    hashData.append('=');
                    hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                    //Build query
                    query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII));
                    query.append('=');
                    query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                    if (itr.hasNext()) {
                        query.append('&');
                        hashData.append('&');
                    }
                }
            }
            String queryUrl = query.toString();
            String vnp_SecureHash = VnpayConfig.hmacSHA512(vnpayConfig.getHashSecret(), hashData.toString());
            queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;
            String paymentUrl = vnpayConfig.getPayUrl() + "?" + queryUrl;
            
            return ResponseEntity.ok(Map.of("url", paymentUrl));
        }
        
        // Fallback simulator for other methods
        String baseUrl = "http://localhost:4200/payment-simulator";
        String redirectUrl = String.format("%s?reservationId=%d&method=%s&amount=%.0f", 
                baseUrl, reservationId, method, amount);
                
        return ResponseEntity.ok(Map.of("url", redirectUrl));
    }

    // Callback for VNPAY
    @GetMapping("/vnpay-callback")
    public ResponseEntity<Map<String, String>> vnpayCallback(HttpServletRequest request) {
        Map<String, String> fields = new HashMap<>();
        for (Enumeration<String> params = request.getParameterNames(); params.hasMoreElements();) {
            String fieldName = params.nextElement();
            String fieldValue = request.getParameter(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                fields.put(fieldName, fieldValue);
            }
        }

        String vnp_SecureHash = request.getParameter("vnp_SecureHash");
        if (fields.containsKey("vnp_SecureHashType")) {
            fields.remove("vnp_SecureHashType");
        }
        if (fields.containsKey("vnp_SecureHash")) {
            fields.remove("vnp_SecureHash");
        }

        // Check checksum
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = fields.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                hashData.append(fieldName);
                hashData.append('=');
                hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                if (itr.hasNext()) {
                    hashData.append('&');
                }
            }
        }
        String signValue = VnpayConfig.hmacSHA512(vnpayConfig.getHashSecret(), hashData.toString());
        
        if (signValue.equals(vnp_SecureHash)) {
            if ("00".equals(request.getParameter("vnp_ResponseCode"))) {
                // Success
                String txnRef = request.getParameter("vnp_TxnRef");
                Long reservationId = Long.parseLong(txnRef.split("_")[0]);
                paymentService.handleSuccessfulPayment(reservationId, "VNPAY");
                return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Payment processed successfully"));
            } else {
                return ResponseEntity.ok(Map.of("status", "FAILED", "message", "Payment failed at gateway"));
            }
        } else {
            return ResponseEntity.ok(Map.of("status", "INVALID_SIGNATURE", "message", "Invalid checksum"));
        }
    }

    // Callback for Simulator (MoMo, Stripe)
    @GetMapping("/callback")
    public ResponseEntity<Map<String, String>> mockCallback(
            @RequestParam Long reservationId,
            @RequestParam String status,
            @RequestParam String method) {
        
        if ("SUCCESS".equalsIgnoreCase(status)) {
            paymentService.handleSuccessfulPayment(reservationId, method);
        }
        
        return ResponseEntity.ok(Map.of("message", "Payment callback processed successfully"));
    }
}
