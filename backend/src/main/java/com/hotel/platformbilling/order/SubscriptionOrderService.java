package com.hotel.platformbilling.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Hotel;
import com.hotel.entities.PlanFeature;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.entities.User;
import com.hotel.paymentprovider.domain.FinancialStates.SubscriptionOrderState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.repositories.UserRepository;
import com.hotel.services.PropertyAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class SubscriptionOrderService {

    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PlatformSubscriptionPlanCatalogRepository planRepository;
    private final PlatformSubscriptionOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final PropertyAccessService propertyAccessService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final long orderExpiryMinutes;

    @Autowired
    public SubscriptionOrderService(
            PlatformSubscriptionPlanCatalogRepository planRepository,
            PlatformSubscriptionOrderRepository orderRepository,
            UserRepository userRepository,
            PropertyAccessService propertyAccessService,
            ObjectMapper objectMapper,
            @Value("${platform.billing.order-expiry-minutes:30}") long orderExpiryMinutes) {
        this(
                planRepository,
                orderRepository,
                userRepository,
                propertyAccessService,
                objectMapper,
                Clock.systemUTC(),
                orderExpiryMinutes);
    }

    public SubscriptionOrderService(
            PlatformSubscriptionPlanCatalogRepository planRepository,
            PlatformSubscriptionOrderRepository orderRepository,
            UserRepository userRepository,
            PropertyAccessService propertyAccessService,
            ObjectMapper objectMapper,
            Clock clock,
            long orderExpiryMinutes) {
        this.planRepository = planRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.propertyAccessService = propertyAccessService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        if (orderExpiryMinutes < 1 || orderExpiryMinutes > 1440) {
            throw new IllegalArgumentException("Platform order expiry must be between 1 and 1440 minutes.");
        }
        this.orderExpiryMinutes = orderExpiryMinutes;
    }

    @Transactional
    public OrderResponse createPurchaseOrder(CreatePurchaseOrderCommand command) {
        validate(command);
        String idempotencyKey = normalizeIdempotencyKey(command.idempotencyKey());
        User authenticated = propertyAccessService.currentUser();
        User owner = userRepository.findByIdForUpdate(authenticated.getId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        Hotel targetHotel = propertyAccessService.requireManagedHotel(command.targetHotelId());
        String requestHash = requestHash(command.targetHotelId(), command.planId(), SubscriptionOrder.Operation.PURCHASE);

        var existing = orderRepository.findByOwnerIdAndIdempotencyKeyForUpdate(owner.getId(), idempotencyKey);
        if (existing.isPresent()) {
            verifyReplay(existing.get(), requestHash);
            return response(existing.get(), true);
        }

        SubscriptionPlan plan = planRepository.findByIdForSnapshot(command.planId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        CatalogSnapshot snapshot = snapshot(plan);
        OrderTerms terms = terms(plan);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        String publicId = UUID.randomUUID().toString();
        String orderCode = "SUB-" + ORDER_DATE.format(now) + '-'
                + publicId.substring(0, 8).toUpperCase(Locale.ROOT);

        SubscriptionOrder order = SubscriptionOrder.create(
                publicId,
                orderCode,
                owner,
                targetHotel,
                SubscriptionOrder.Operation.PURCHASE,
                plan,
                version(plan, snapshot),
                plan.getCode(),
                displayName(plan),
                terms.price(),
                terms.billingPeriod(),
                terms.durationValue(),
                terms.durationUnit(),
                writeSnapshot(snapshot),
                idempotencyKey,
                requestHash,
                now.plusMinutes(orderExpiryMinutes));

        return response(orderRepository.saveAndFlush(order), false);
    }

    private CatalogSnapshot snapshot(SubscriptionPlan plan) {
        if (!"ACTIVE".equals(normalizeCode(plan.getStatus(), "plan status"))) {
            throw new FinancialException(FinancialErrorCode.INVALID_STATE_TRANSITION,
                    "The selected subscription plan is not active.");
        }
        List<FeatureSnapshot> features = plan.getFeatures() == null
                ? List.of()
                : plan.getFeatures().stream()
                        .filter(Objects::nonNull)
                        .map(this::featureSnapshot)
                        .sorted(Comparator.comparing(FeatureSnapshot::code))
                        .toList();
        return new CatalogSnapshot(
                normalizeCode(plan.getCode(), "plan code"),
                requireText(plan.getNameVi(), "plan name", 255),
                normalizeOptional(plan.getNameEn(), 255),
                Boolean.TRUE.equals(plan.getIsLifetime()),
                features);
    }

    private FeatureSnapshot featureSnapshot(PlanFeature feature) {
        String code = normalizeCode(feature.getFeatureCode(), "feature code");
        int limit = feature.getLimitValue() == null ? 1 : feature.getLimitValue();
        if (limit < -1) {
            throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "The selected plan contains an invalid feature limit.");
        }
        return new FeatureSnapshot(code, limit);
    }

    private OrderTerms terms(SubscriptionPlan plan) {
        VndMoney price;
        try {
            price = VndMoney.of(plan.getPrice());
        } catch (RuntimeException exception) {
            throw new FinancialException(FinancialErrorCode.INVALID_AMOUNT,
                    FinancialErrorCode.INVALID_AMOUNT.defaultMessage(), null, null, exception);
        }
        if (price.amount().signum() <= 0) {
            throw new FinancialException(FinancialErrorCode.INVALID_AMOUNT,
                    "The selected plan does not have a positive billable price.");
        }

        String billingPeriod = normalizeCode(plan.getBillingType(), "billing period");
        if (Boolean.TRUE.equals(plan.getIsLifetime())) {
            return new OrderTerms(price, billingPeriod, 1, SubscriptionOrder.DurationUnit.LIFETIME);
        }
        return switch (billingPeriod) {
            case "MONTHLY" -> new OrderTerms(price, billingPeriod, 1, SubscriptionOrder.DurationUnit.MONTH);
            case "YEARLY" -> new OrderTerms(price, billingPeriod, 1, SubscriptionOrder.DurationUnit.YEAR);
            default -> throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "The selected plan billing period has no approved duration mapping.");
        };
    }

    private String version(SubscriptionPlan plan, CatalogSnapshot snapshot) {
        String material = plan.getId() + "|" + normalizeCode(plan.getBillingType(), "billing period")
                + "|" + plan.getPrice().toPlainString() + "|" + writeSnapshot(snapshot);
        return "PLAN-" + plan.getId() + '-' + sha256(material).substring(0, 16).toUpperCase(Locale.ROOT);
    }

    private String writeSnapshot(CatalogSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize the platform catalog snapshot.", exception);
        }
    }

    private String requestHash(Long hotelId, Long planId, SubscriptionOrder.Operation operation) {
        return sha256(hotelId + "|" + planId + "|" + operation.name());
    }

    private String sha256(String material) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private void verifyReplay(SubscriptionOrder order, String requestHash) {
        boolean equivalent = MessageDigest.isEqual(
                order.getRequestHash().getBytes(StandardCharsets.UTF_8),
                requestHash.getBytes(StandardCharsets.UTF_8));
        if (!equivalent) {
            throw new FinancialException(FinancialErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
    }

    private OrderResponse response(SubscriptionOrder order, boolean replayed) {
        return new OrderResponse(
                order.getId(),
                order.getPublicId(),
                order.getOrderCode(),
                order.getOwner().getId(),
                order.getTargetHotel().getId(),
                order.getOperation(),
                order.getPlan().getId(),
                order.getPlanVersion(),
                order.getPlanCode(),
                order.getPlanName(),
                order.getPrice(),
                order.getCurrency(),
                order.getBillingPeriod(),
                order.getDurationValue(),
                order.getDurationUnit(),
                order.getFeatureSnapshotJson(),
                order.getStatus(),
                order.getExpiresAt(),
                replayed);
    }

    private String displayName(SubscriptionPlan plan) {
        if (plan.getNameVi() != null && !plan.getNameVi().isBlank()) {
            return plan.getNameVi().trim();
        }
        return requireText(plan.getNameEn(), "plan name", 255);
    }

    private void validate(CreatePurchaseOrderCommand command) {
        if (command == null || command.targetHotelId() == null || command.planId() == null) {
            throw new IllegalArgumentException("Target property and subscription plan are required.");
        }
    }

    private String normalizeIdempotencyKey(String value) {
        return requireText(value, "idempotency key", 160);
    }

    private String normalizeCode(String value, String field) {
        return requireText(value, field, 80).toUpperCase(Locale.ROOT);
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("value is too long.");
        }
        return normalized;
    }

    private String requireText(String value, String field, int maxLength) {
        String normalized = normalizeOptional(value, maxLength);
        if (normalized == null) {
            throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    field + " is not configured.");
        }
        return normalized;
    }

    public record CreatePurchaseOrderCommand(Long targetHotelId, Long planId, String idempotencyKey) {
    }

    public record FeatureSnapshot(String code, int limit) {
    }

    public record CatalogSnapshot(
            String planCode,
            String nameVi,
            String nameEn,
            boolean lifetime,
            List<FeatureSnapshot> features) {
    }

    private record OrderTerms(
            VndMoney price,
            String billingPeriod,
            int durationValue,
            SubscriptionOrder.DurationUnit durationUnit) {
    }

    public record OrderResponse(
            Long id,
            String publicId,
            String orderCode,
            Long ownerUserId,
            Long targetHotelId,
            SubscriptionOrder.Operation operation,
            Long planId,
            String planVersion,
            String planCode,
            String planName,
            BigDecimal price,
            String currency,
            String billingPeriod,
            int durationValue,
            SubscriptionOrder.DurationUnit durationUnit,
            String featureSnapshotJson,
            SubscriptionOrderState status,
            LocalDateTime expiresAt,
            boolean replayed) {
    }
}
