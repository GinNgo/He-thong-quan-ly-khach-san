package com.hotel.platformbilling;

import com.hotel.dtos.SubscriptionPlanDTO;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.platformbilling.config.PlatformPaymentConfigurationService;
import com.hotel.platformbilling.order.SubscriptionOrderService;
import com.hotel.platformbilling.payment.PlatformPaymentAttemptService;
import com.hotel.platformbilling.subscription.SubscriptionPolicyService;
import com.hotel.platformbilling.subscription.SubscriptionRenewalService;
import com.hotel.platformbilling.subscription.SubscriptionUpgradeService;
import com.hotel.platformbilling.subscription.SubscriptionLifecycleService;
import com.hotel.exceptions.CorrelationIdSupport;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.SubscriptionCatalogService;
import com.hotel.services.PropertySubscriptionEntitlementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

@RestController
@RequestMapping("/api/platform")
public class PlatformBillingController {

    private final SubscriptionCatalogService catalogService;
    private final SubscriptionOrderService orderService;
    private final PlatformPaymentAttemptService attemptService;
    private final SubscriptionRenewalService renewalService;
    private final SubscriptionUpgradeService upgradeService;
    private final SubscriptionPolicyService policyService;
    private final PlatformPaymentConfigurationService configurationService;
    private final PlatformBillingQueryService queryService;
    private final PropertySubscriptionEntitlementService entitlementService;
    private final SubscriptionLifecycleService lifecycleService;

    @Autowired
    public PlatformBillingController(
            SubscriptionCatalogService catalogService,
            SubscriptionOrderService orderService,
            PlatformPaymentAttemptService attemptService,
            SubscriptionRenewalService renewalService,
            SubscriptionUpgradeService upgradeService,
            SubscriptionPolicyService policyService,
            PlatformPaymentConfigurationService configurationService,
            PlatformBillingQueryService queryService,
            PropertySubscriptionEntitlementService entitlementService,
            SubscriptionLifecycleService lifecycleService) {
        this.catalogService = catalogService;
        this.orderService = orderService;
        this.attemptService = attemptService;
        this.renewalService = renewalService;
        this.upgradeService = upgradeService;
        this.policyService = policyService;
        this.configurationService = configurationService;
        this.queryService = queryService;
        this.entitlementService = entitlementService;
        this.lifecycleService = lifecycleService;
    }

    /** Compatibility constructor retained for focused controller tests and integrations. */
    public PlatformBillingController(
            SubscriptionCatalogService catalogService,
            SubscriptionOrderService orderService,
            PlatformPaymentAttemptService attemptService,
            SubscriptionRenewalService renewalService,
            SubscriptionUpgradeService upgradeService,
            SubscriptionPolicyService policyService,
            PlatformPaymentConfigurationService configurationService,
            PlatformBillingQueryService queryService) {
        this(catalogService, orderService, attemptService, renewalService, upgradeService, policyService,
                configurationService, queryService, null, null);
    }

    @GetMapping("/subscription-plans")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SubscriptionPlanDTO>> catalog() {
        return ResponseEntity.ok(catalogService.getActivePlans());
    }

    @PostMapping("/subscription-orders")
    @Permission(function = FunctionCode.PLATFORM_BILLING, action = ActionCode.CREATE)
    public ResponseEntity<SubscriptionOrderService.OrderResponse> createPurchaseOrder(
            @RequestBody PurchaseOrderRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ResponseEntity.ok(orderService.createPurchaseOrder(
                new SubscriptionOrderService.CreatePurchaseOrderCommand(
                        request.targetHotelId(), request.planId(), idempotencyKey)));
    }

    @GetMapping("/subscription-orders/{orderId}")
    @Permission(function = FunctionCode.PLATFORM_BILLING, action = ActionCode.VIEW)
    public ResponseEntity<PlatformBillingQueryService.OrderDetails> getOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(queryService.getOrder(orderId));
    }

    @PostMapping("/subscription-orders/{orderId}/payment-attempts")
    @Permission(function = FunctionCode.PLATFORM_BILLING, action = ActionCode.CREATE)
    public ResponseEntity<PlatformPaymentAttemptService.AttemptResponse> createAttempt(
            @PathVariable String orderId,
            @RequestBody PaymentAttemptRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ResponseEntity.ok(attemptService.create(new PlatformPaymentAttemptService.CreateAttemptCommand(
                orderId, request.provider(), request.method(), idempotencyKey)));
    }

    @PostMapping("/subscription-orders/{orderId}/cancel")
    @Permission(function = FunctionCode.PLATFORM_BILLING, action = ActionCode.UPDATE)
    public ResponseEntity<PlatformBillingQueryService.OrderDetails> cancelOrder(
            @PathVariable String orderId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        return ResponseEntity.ok(queryService.cancelOrder(orderId, correlationId));
    }

    @PostMapping("/subscriptions/{targetHotelId}/renewal-orders")
    @Permission(function = FunctionCode.PLATFORM_BILLING, action = ActionCode.CREATE)
    public ResponseEntity<SubscriptionOrderService.OrderResponse> createRenewalOrder(
            @PathVariable Long targetHotelId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ResponseEntity.ok(renewalService.createRenewalOrder(
                new SubscriptionRenewalService.RenewalOrderCommand(targetHotelId, idempotencyKey)));
    }

    @PostMapping("/subscriptions/{targetHotelId}/upgrade-orders")
    @Permission(function = FunctionCode.PLATFORM_BILLING, action = ActionCode.CREATE)
    public ResponseEntity<SubscriptionOrderService.OrderResponse> createUpgradeOrder(
            @PathVariable Long targetHotelId,
            @RequestBody PlanChangeRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ResponseEntity.ok(upgradeService.createUpgradeOrder(
                new SubscriptionUpgradeService.UpgradeOrderCommand(
                        targetHotelId, request.targetPlanId(), idempotencyKey)));
    }

    @PostMapping("/subscriptions/{targetHotelId}/downgrade-orders")
    @Permission(function = FunctionCode.PLATFORM_BILLING, action = ActionCode.CREATE)
    public ResponseEntity<SubscriptionOrderService.OrderResponse> createDowngradeOrder(
            @PathVariable Long targetHotelId,
            @RequestBody PlanChangeRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ResponseEntity.ok(policyService.createDowngradeOrder(
                new SubscriptionPolicyService.DowngradeOrderCommand(
                        targetHotelId, request.targetPlanId(), idempotencyKey)));
    }

    @GetMapping("/subscriptions/{targetHotelId}/history")
    @Permission(function = FunctionCode.PLATFORM_BILLING, action = ActionCode.VIEW)
    public ResponseEntity<List<PlatformBillingQueryService.HistoryItem>> history(
            @PathVariable Long targetHotelId) {
        return ResponseEntity.ok(queryService.history(targetHotelId));
    }

    @GetMapping("/subscriptions/{targetHotelId}/entitlement")
    @Permission(function = FunctionCode.PLATFORM_BILLING, action = ActionCode.VIEW)
    public ResponseEntity<PropertySubscriptionEntitlementService.EntitlementView> entitlement(
            @PathVariable Long targetHotelId) {
        return ResponseEntity.ok(entitlementService.getCurrent(targetHotelId));
    }

    @GetMapping(value = "/subscriptions/{targetHotelId}/history/export", produces = "text/csv;charset=UTF-8")
    @Permission(function = FunctionCode.PLATFORM_BILLING, action = ActionCode.VIEW)
    public ResponseEntity<String> exportHistory(@PathVariable Long targetHotelId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"subscription-history-" + targetHotelId + ".csv\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(queryService.historyCsv(targetHotelId));
    }

    @PostMapping("/subscriptions/{targetHotelId}/revoke")
    @Permission(function = FunctionCode.PLATFORM_BILLING, action = ActionCode.UPDATE)
    public ResponseEntity<SubscriptionLifecycleService.LifecycleResult> revoke(
            @PathVariable Long targetHotelId,
            @RequestBody RevokeRequest request,
            HttpServletRequest servletRequest) {
        return ResponseEntity.ok(lifecycleService.revoke(targetHotelId, request.reason(),
                servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent"),
                CorrelationIdSupport.resolve(servletRequest)));
    }

    @GetMapping("/subscription-policies")
    @Permission(function = FunctionCode.PLATFORM_BILLING, action = ActionCode.VIEW)
    public ResponseEntity<SubscriptionPolicyService.PolicyAvailability> policies() {
        return ResponseEntity.ok(policyService.availability());
    }

    @GetMapping("/payment-configuration")
    @Permission(function = FunctionCode.PAYMENT_READINESS, action = ActionCode.VIEW)
    public ResponseEntity<List<PlatformPaymentConfigurationService.ConfigurationResponse>> configurations() {
        return ResponseEntity.ok(configurationService.list());
    }

    @PutMapping("/payment-configuration")
    @Permission(function = FunctionCode.PAYMENT_READINESS, action = ActionCode.UPDATE)
    public ResponseEntity<PlatformPaymentConfigurationService.ConfigurationResponse> configure(
            @RequestBody PlatformPaymentConfigurationService.ConfigurationCommand command) {
        return ResponseEntity.ok(configurationService.configure(command));
    }

    @PostMapping("/payment-configuration/validate")
    @Permission(function = FunctionCode.PAYMENT_READINESS, action = ActionCode.UPDATE)
    public ResponseEntity<com.hotel.paymentprovider.config.PaymentEnvironmentGuard.Readiness> validateConfiguration(
            @RequestParam String provider) {
        return ResponseEntity.ok(configurationService.requireReady(provider).readiness());
    }

    @GetMapping("/payment-configuration/{provider}/{environment}")
    @Permission(function = FunctionCode.PAYMENT_READINESS, action = ActionCode.VIEW)
    public ResponseEntity<PlatformPaymentConfigurationService.ConfigurationResponse> configuration(
            @PathVariable String provider,
            @PathVariable PaymentEnvironment environment) {
        return ResponseEntity.ok(configurationService.get(provider, environment));
    }

    public record PurchaseOrderRequest(Long targetHotelId, Long planId) {
    }

    public record PaymentAttemptRequest(String provider, String method) {
    }

    public record PlanChangeRequest(Long targetPlanId) {
    }

    public record RevokeRequest(String reason) {
    }
}
