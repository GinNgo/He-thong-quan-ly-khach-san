package com.hotel.paymentprovider.simulator;

import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.paymentprovider.refund.RefundProviderOrchestrator;
import com.hotel.platformbilling.PlatformBillingQueryService;
import com.hotel.platformbilling.config.PlatformPaymentConfigurationService;
import com.hotel.platformbilling.payment.PlatformPaymentCallbackService;
import com.hotel.propertycommerce.payment.PropertyPaymentAttemptService;
import com.hotel.propertycommerce.payment.PropertyPaymentCallbackService;
import com.hotel.propertycommerce.refund.PropertyRefundAttempt;
import com.hotel.propertycommerce.refund.PropertyRefundAttemptRepository;
import com.hotel.propertycommerce.refund.PropertyRefundRequest;
import com.hotel.propertycommerce.refund.PropertyRefundRequestRepository;
import com.hotel.propertycommerce.refund.PropertyRefundService;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class FinancialSimulatorController {

    private final PropertyPaymentAttemptService propertyAttemptService;
    private final PropertyPaymentCallbackService propertyCallbackService;
    private final PlatformBillingQueryService platformQueryService;
    private final PlatformPaymentConfigurationService platformConfigurationService;
    private final PlatformPaymentCallbackService platformCallbackService;
    private final PropertyRefundService propertyRefundService;
    private final PropertyRefundRequestRepository propertyRefundRequestRepository;
    private final PropertyRefundAttemptRepository propertyRefundAttemptRepository;
    private final RefundProviderOrchestrator refundProviderOrchestrator;
    private final Environment environment;

    public FinancialSimulatorController(
            PropertyPaymentAttemptService propertyAttemptService,
            PropertyPaymentCallbackService propertyCallbackService,
            PlatformBillingQueryService platformQueryService,
            PlatformPaymentConfigurationService platformConfigurationService,
            PlatformPaymentCallbackService platformCallbackService,
            PropertyRefundService propertyRefundService,
            PropertyRefundRequestRepository propertyRefundRequestRepository,
            PropertyRefundAttemptRepository propertyRefundAttemptRepository,
            RefundProviderOrchestrator refundProviderOrchestrator,
            Environment environment) {
        this.propertyAttemptService = propertyAttemptService;
        this.propertyCallbackService = propertyCallbackService;
        this.platformQueryService = platformQueryService;
        this.platformConfigurationService = platformConfigurationService;
        this.platformCallbackService = platformCallbackService;
        this.propertyRefundService = propertyRefundService;
        this.propertyRefundRequestRepository = propertyRefundRequestRepository;
        this.propertyRefundAttemptRepository = propertyRefundAttemptRepository;
        this.refundProviderOrchestrator = refundProviderOrchestrator;
        this.environment = environment;
    }

    @PostMapping("/api/financial-simulator/property-payment-attempts/{attemptId}/confirm")
    public PropertyPaymentCallbackService.CallbackResult confirmPropertyPayment(@PathVariable String attemptId) {
        PropertyPaymentAttemptService.AttemptResponse attempt = propertyAttemptService.getOwned(attemptId);
        requireSimulator(attempt.provider(), attempt.environment());
        SimulatorCredentials credentials = propertyCredentials();
        Map<String, Object> payload = paymentPayload(
                credentials.merchantId(), attempt.publicId(), attempt.expectedAmount(), attempt.currency(),
                "SIM-PROPERTY-TXN-" + attempt.publicId(), "SIM-PROPERTY-EVENT-" + attempt.publicId());
        String signature = sign(payload, credentials.signingSecret());
        return propertyCallbackService.process(new PropertyPaymentCallbackService.CallbackCommand(
                "SIMULATOR", PaymentEnvironment.SIMULATOR, credentials.merchantId(), signature,
                payload, Map.of("signingSecret", credentials.signingSecret()), null,
                "financial-simulator:property:" + attempt.publicId()));
    }

    @PostMapping("/api/financial-simulator/platform-orders/{orderId}/attempts/{attemptId}/confirm")
    public PlatformPaymentCallbackService.CallbackResult confirmPlatformPayment(
            @PathVariable String orderId,
            @PathVariable String attemptId) {
        PlatformBillingQueryService.OrderDetails order = platformQueryService.getOrder(orderId);
        PlatformBillingQueryService.AttemptItem attempt = order.attempts().stream()
                .filter(item -> item.publicId().equals(attemptId))
                .findFirst()
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        requireSimulator(attempt.provider(), attempt.environment());
        PlatformPaymentConfigurationService.ReadyConfiguration ready =
                platformConfigurationService.requireReady("SIMULATOR");
        if (ready.credentials() == null
                || ready.credentials().merchantId() == null
                || ready.credentials().secrets().get("signingSecret") == null) {
            throw new FinancialException(FinancialErrorCode.PROVIDER_UNAVAILABLE);
        }
        String reference = attempt.providerOrderReference() == null
                ? attempt.publicId() : attempt.providerOrderReference();
        Map<String, Object> payload = paymentPayload(
                ready.credentials().merchantId(), reference, attempt.expectedAmount(), attempt.currency(),
                "SIM-PLATFORM-TXN-" + attempt.publicId(), "SIM-PLATFORM-EVENT-" + attempt.publicId());
        String signature = sign(payload, ready.credentials().secrets().get("signingSecret"));
        return platformCallbackService.process(new PlatformPaymentCallbackService.CallbackCommand(
                "SIMULATOR", signature, payload, null,
                "financial-simulator:platform:" + attempt.publicId()));
    }

    @PostMapping("/api/financial-simulator/property-refunds/{refundId}/confirm")
    public RefundProviderOrchestrator.CallbackResult confirmPropertyRefund(@PathVariable String refundId) {
        propertyRefundService.get(refundId);
        PropertyRefundRequest refund = propertyRefundRequestRepository.findByPublicId(refundId)
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        PropertyRefundAttempt attempt = propertyRefundAttemptRepository
                .findByRefundRequestIdOrderByAttemptNumberAsc(refund.getId()).stream()
                .filter(item -> item.getStatus() == com.hotel.paymentprovider.domain.FinancialStates.RefundState.PENDING_PROVIDER)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new FinancialException(
                        FinancialErrorCode.INVALID_STATE_TRANSITION,
                        "A pending simulator refund attempt is required."));
        requireSimulator(attempt.getProvider(), attempt.getEnvironment());
        SimulatorCredentials credentials = propertyCredentials();
        Map<String, Object> payload = paymentPayload(
                credentials.merchantId(), attempt.getProviderReference(), refund.getRequestedAmount(), refund.getCurrency(),
                "SIM-REFUND-TXN-" + refund.getPublicId(),
                "SIM-REFUND-EVENT-" + refund.getPublicId() + '-' + attempt.getAttemptNumber());
        String signature = sign(payload, credentials.signingSecret());
        return refundProviderOrchestrator.processPropertyCallback(
                new RefundProviderOrchestrator.CallbackCommand(
                        "SIMULATOR", credentials.merchantId(), signature, payload,
                        Map.of("signingSecret", credentials.signingSecret()), null,
                        "financial-simulator:refund:" + refund.getPublicId()));
    }

    private Map<String, Object> paymentPayload(
            String merchantId,
            String reference,
            BigDecimal amount,
            String currency,
            String transactionId,
            String eventId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("merchantId", merchantId);
        payload.put("reference", reference);
        payload.put("transactionId", transactionId);
        payload.put("eventId", eventId);
        payload.put("amount", amount);
        payload.put("currency", currency);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("status", "SUCCESS");
        return payload;
    }

    private void requireSimulator(String provider, PaymentEnvironment paymentEnvironment) {
        if (!"SIMULATOR".equalsIgnoreCase(provider) || paymentEnvironment != PaymentEnvironment.SIMULATOR) {
            throw new FinancialException(
                    FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "Only SIMULATOR attempts can be confirmed by the internal simulator.");
        }
    }

    private SimulatorCredentials propertyCredentials() {
        String merchantId = property("payment.property.simulator.merchant-id", "PROPERTY-SIMULATOR");
        String signingSecret = property("payment.demo.signing-secret", property("jwt.secret", ""));
        if (merchantId.isBlank() || signingSecret.isBlank()) {
            throw new FinancialException(FinancialErrorCode.PROVIDER_UNAVAILABLE);
        }
        return new SimulatorCredentials(merchantId, signingSecret);
    }

    private String sign(Map<String, Object> payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonicalQuery(payload).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot sign simulator callback.", exception);
        }
    }

    private String canonicalQuery(Map<String, Object> payload) {
        List<String> names = new ArrayList<>(payload.keySet());
        names.removeIf(name -> name == null || payload.get(name) == null || payload.get(name).toString().isBlank());
        names.sort(String::compareTo);
        StringBuilder result = new StringBuilder();
        for (String name : names) {
            if (result.length() > 0) result.append('&');
            result.append(URLEncoder.encode(name, StandardCharsets.UTF_8));
            result.append('=');
            result.append(URLEncoder.encode(payload.get(name).toString(), StandardCharsets.UTF_8));
        }
        return result.toString();
    }

    private String property(String key, String fallback) {
        String value = environment.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record SimulatorCredentials(String merchantId, String signingSecret) {
    }
}
