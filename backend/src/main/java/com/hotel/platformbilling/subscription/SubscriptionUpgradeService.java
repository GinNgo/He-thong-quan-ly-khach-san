package com.hotel.platformbilling.subscription;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.PlanFeature;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.domain.FinancialStates.SubscriptionOrderState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.platformbilling.order.PlatformSubscriptionOrderRepository;
import com.hotel.platformbilling.order.PlatformSubscriptionPlanCatalogRepository;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.platformbilling.order.SubscriptionOrderService;
import com.hotel.platformbilling.payment.PlatformFinancialTransaction;
import com.hotel.platformbilling.payment.PlatformFinancialTransactionRepository;
import com.hotel.platformbilling.payment.PlatformPaymentAttempt;
import com.hotel.services.PropertyAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class SubscriptionUpgradeService {

    static final String POLICY_VERSION = "FULL_PRICE_PRESERVE_REMAINING_TERM_V1";

    private final PlatformSubscriptionOrderRepository orderRepository;
    private final PlatformFinancialTransactionRepository transactionRepository;
    private final PlatformSoftwareContractRepository contractRepository;
    private final SubscriptionEntitlementRepository entitlementRepository;
    private final PlatformSubscriptionHistoryRepository historyRepository;
    private final PlatformSubscriptionPlanCatalogRepository planRepository;
    private final SubscriptionOrderService orderService;
    private final PropertyAccessService propertyAccessService;
    private final PlatformSubscriptionUsageRepository usageRepository;
    private final FinancialAuditService auditService;
    private final ObjectMapper objectMapper;

    public SubscriptionUpgradeService(
            PlatformSubscriptionOrderRepository orderRepository,
            PlatformFinancialTransactionRepository transactionRepository,
            PlatformSoftwareContractRepository contractRepository,
            SubscriptionEntitlementRepository entitlementRepository,
            PlatformSubscriptionHistoryRepository historyRepository,
            PlatformSubscriptionPlanCatalogRepository planRepository,
            SubscriptionOrderService orderService,
            PropertyAccessService propertyAccessService,
            PlatformSubscriptionUsageRepository usageRepository,
            FinancialAuditService auditService,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.transactionRepository = transactionRepository;
        this.contractRepository = contractRepository;
        this.entitlementRepository = entitlementRepository;
        this.historyRepository = historyRepository;
        this.planRepository = planRepository;
        this.orderService = orderService;
        this.propertyAccessService = propertyAccessService;
        this.usageRepository = usageRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SubscriptionOrderService.OrderResponse createUpgradeOrder(UpgradeOrderCommand command) {
        if (command == null || command.targetHotelId() == null || command.targetPlanId() == null) {
            throw new IllegalArgumentException("Target property and upgrade plan are required.");
        }
        propertyAccessService.requireAssignedHotel(command.targetHotelId());
        SubscriptionEntitlement entitlement = entitlementRepository
                .findByTargetHotelIdForUpdate(command.targetHotelId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        SoftwareContract currentContract = entitlement.getContract();
        validateActiveEntitlement(entitlement, currentContract);
        SubscriptionPlan targetPlan = planRepository.findByIdForSnapshot(command.targetPlanId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        Map<String, Integer> targetLimits = validateUpgradePlan(currentContract, targetPlan);
        validateUsage(targetLimits, currentContract.getOwner().getId(), command.targetHotelId());

        return orderService.createLifecycleOrder(new SubscriptionOrderService.CreateLifecycleOrderCommand(
                command.targetHotelId(),
                targetPlan.getId(),
                SubscriptionOrder.Operation.UPGRADE,
                requireText(command.idempotencyKey(), "idempotencyKey")));
    }

    @Transactional
    public UpgradeApplicationResult applyPaidUpgrade(
            String orderPublicId,
            String transactionPublicId,
            String correlationId) {
        SubscriptionOrder order = orderRepository.findByPublicIdForUpdate(requireText(orderPublicId, "orderId"))
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        PlatformFinancialTransaction transaction = transactionRepository
                .findByPublicId(requireText(transactionPublicId, "transactionId"))
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        validatePaymentEvidence(order, transaction);

        SoftwareContract existingContract = contractRepository.findByOrderId(order.getId()).orElse(null);
        if (order.getStatus() == SubscriptionOrderState.APPLIED) {
            return replay(order, transaction, existingContract);
        }
        if (order.getStatus() != SubscriptionOrderState.PAID) {
            throw new FinancialException(
                    FinancialErrorCode.INVALID_STATE_TRANSITION,
                    "Only a paid subscription upgrade order can be applied.",
                    null,
                    order.getStatus().name(),
                    null);
        }
        if (existingContract != null) {
            return repair(order, transaction, existingContract, correlationId);
        }

        SubscriptionEntitlement entitlement = entitlementRepository
                .findByTargetHotelIdForUpdate(order.getTargetHotel().getId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        SoftwareContract currentContract = entitlement.getContract();
        validateActiveEntitlement(entitlement, currentContract);
        Map<String, Integer> targetLimits = validateUpgradeSnapshots(
                currentContract.getFeatureSnapshotJson(), order.getFeatureSnapshotJson());
        validateTermDirection(currentContract, order.getDurationUnit() == SubscriptionOrder.DurationUnit.LIFETIME);
        validateUsage(targetLimits, currentContract.getOwner().getId(), order.getTargetHotel().getId());

        String previousState = entitlementState(entitlement);
        UpgradePeriod period = upgradePeriod(currentContract, order, transaction.getOccurredAt());
        SoftwareContract upgradedContract = SoftwareContract.activate(
                UUID.randomUUID().toString(),
                order,
                transaction,
                currentContract,
                planSnapshot(order),
                order.getFeatureSnapshotJson(),
                period.effectiveFrom(),
                period.effectiveUntil(),
                period.lifetime(),
                VndMoney.of(order.getPrice()));
        SoftwareContract savedContract = contractRepository.saveAndFlush(upgradedContract);
        currentContract.transitionTo(SoftwareContract.Status.SUPERSEDED);
        contractRepository.saveAndFlush(currentContract);
        entitlement.applyContract(savedContract);
        SubscriptionEntitlement savedEntitlement = entitlementRepository.saveAndFlush(entitlement);
        SubscriptionHistory history = ensureHistory(
                order, transaction, savedContract, savedEntitlement, previousState);
        order.transitionTo(SubscriptionOrderState.APPLIED, transaction.getOccurredAt());
        orderRepository.saveAndFlush(order);
        audit(order, transaction, savedContract, correlationId, false);
        return response(order, transaction, savedContract, savedEntitlement, history, false);
    }

    private UpgradeApplicationResult repair(
            SubscriptionOrder order,
            PlatformFinancialTransaction transaction,
            SoftwareContract upgradedContract,
            String correlationId) {
        validateContractEvidence(order, transaction, upgradedContract);
        SoftwareContract previousContract = upgradedContract.getSupersedesContract();
        SubscriptionEntitlement entitlement = entitlementRepository
                .findByTargetHotelIdForUpdate(order.getTargetHotel().getId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION));
        String previousState = activeContractState(previousContract);
        if (previousContract.getStatus() == SoftwareContract.Status.ACTIVE) {
            previousContract.transitionTo(SoftwareContract.Status.SUPERSEDED);
            contractRepository.saveAndFlush(previousContract);
        } else if (previousContract.getStatus() != SoftwareContract.Status.SUPERSEDED) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "Upgrade previous contract has an incompatible stored state.");
        }
        SubscriptionEntitlement savedEntitlement = entitlement;
        if (!sameContract(entitlement.getContract(), upgradedContract)) {
            entitlement.applyContract(upgradedContract);
            savedEntitlement = entitlementRepository.saveAndFlush(entitlement);
        }
        SubscriptionHistory history = ensureHistory(
                order, transaction, upgradedContract, savedEntitlement, previousState);
        order.transitionTo(SubscriptionOrderState.APPLIED, transaction.getOccurredAt());
        orderRepository.saveAndFlush(order);
        audit(order, transaction, upgradedContract, correlationId, true);
        return response(order, transaction, upgradedContract, savedEntitlement, history, true);
    }

    private UpgradeApplicationResult replay(
            SubscriptionOrder order,
            PlatformFinancialTransaction transaction,
            SoftwareContract contract) {
        if (contract == null) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "Applied upgrade order is missing its contract evidence.");
        }
        validateContractEvidence(order, transaction, contract);
        SubscriptionEntitlement entitlement = entitlementRepository
                .findByTargetHotelIdForUpdate(order.getTargetHotel().getId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION));
        if (!sameContract(entitlement.getContract(), contract)
                || !historyRepository.existsByOrderIdAndActionType(
                        order.getId(), SubscriptionHistory.ActionType.UPGRADED)) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "Applied upgrade evidence is incomplete.");
        }
        SubscriptionHistory history = historyRepository.findByOrderIdOrderByOccurredAtAsc(order.getId()).stream()
                .filter(item -> item.getActionType() == SubscriptionHistory.ActionType.UPGRADED)
                .findFirst()
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION));
        return response(order, transaction, contract, entitlement, history, true);
    }

    private SubscriptionHistory ensureHistory(
            SubscriptionOrder order,
            PlatformFinancialTransaction transaction,
            SoftwareContract contract,
            SubscriptionEntitlement entitlement,
            String previousState) {
        if (historyRepository.existsByOrderIdAndActionType(order.getId(), SubscriptionHistory.ActionType.UPGRADED)) {
            return historyRepository.findByOrderIdOrderByOccurredAtAsc(order.getId()).stream()
                    .filter(item -> item.getActionType() == SubscriptionHistory.ActionType.UPGRADED)
                    .findFirst()
                    .orElseThrow(() -> new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION));
        }
        return historyRepository.saveAndFlush(SubscriptionHistory.record(
                order,
                contract,
                transaction,
                SubscriptionHistory.ActionType.UPGRADED,
                previousState,
                entitlementState(entitlement),
                "PROVIDER",
                null,
                "Applied approved upgrade policy " + POLICY_VERSION,
                transaction.getOccurredAt()));
    }

    private Map<String, Integer> validateUpgradePlan(
            SoftwareContract currentContract,
            SubscriptionPlan targetPlan) {
        if (!"ACTIVE".equals(normalizeCode(targetPlan.getStatus()))) {
            throw new FinancialException(FinancialErrorCode.INVALID_STATE_TRANSITION,
                    "The target upgrade plan is not active.");
        }
        if (sameEntity(
                currentContract.getPlan().getId(), targetPlan.getId(), currentContract.getPlan(), targetPlan)) {
            throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "Select a strictly higher plan; the current plan cannot upgrade to itself.");
        }
        validateTermDirection(currentContract, Boolean.TRUE.equals(targetPlan.getIsLifetime()));
        Map<String, Integer> currentLimits = parseFeatureSnapshot(currentContract.getFeatureSnapshotJson());
        Map<String, Integer> targetLimits = planFeatures(targetPlan);
        validateFeatureProgression(currentLimits, targetLimits);
        return targetLimits;
    }

    private Map<String, Integer> validateUpgradeSnapshots(String currentSnapshot, String targetSnapshot) {
        Map<String, Integer> currentLimits = parseFeatureSnapshot(currentSnapshot);
        Map<String, Integer> targetLimits = parseFeatureSnapshot(targetSnapshot);
        validateFeatureProgression(currentLimits, targetLimits);
        return targetLimits;
    }

    private void validateFeatureProgression(
            Map<String, Integer> currentLimits,
            Map<String, Integer> targetLimits) {
        boolean improved = false;
        for (Map.Entry<String, Integer> current : currentLimits.entrySet()) {
            int target = targetLimits.getOrDefault(current.getKey(), 0);
            if (lessThan(target, current.getValue())) {
                throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                        "Upgrade plan cannot reduce feature limit " + current.getKey() + '.');
            }
            improved |= greaterThan(target, current.getValue());
        }
        for (Map.Entry<String, Integer> target : targetLimits.entrySet()) {
            improved |= greaterThan(target.getValue(), currentLimits.getOrDefault(target.getKey(), 0));
        }
        if (!improved) {
            throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "Upgrade plan must strictly improve at least one feature limit.");
        }
    }

    private void validateUsage(Map<String, Integer> targetLimits, Long ownerId, Long hotelId) {
        for (Map.Entry<String, Integer> entry : targetLimits.entrySet()) {
            int limit = entry.getValue();
            if (limit == -1) {
                continue;
            }
            if (limit < -1) {
                throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                        "Upgrade plan contains an invalid feature limit.");
            }
            Long usage = currentUsage(entry.getKey(), ownerId, hotelId);
            if (usage != null && usage > limit) {
                throw new FinancialException(
                        FinancialErrorCode.INVALID_STATE_TRANSITION,
                        "Current usage exceeds target limit for " + entry.getKey() + '.',
                        null,
                        "USAGE=" + usage + ",LIMIT=" + limit,
                        null);
            }
        }
    }

    private Long currentUsage(String code, Long ownerId, Long hotelId) {
        return switch (code) {
            case "MAX_PROPERTIES" -> usageRepository.countActiveOwnedProperties(ownerId);
            case "MAX_ROOM_TYPES" -> usageRepository.countRoomTypes(hotelId);
            case "MAX_ROOMS" -> usageRepository.countRooms(hotelId);
            case "MAX_IMAGES" -> usageRepository.countPropertyImages(hotelId)
                    + usageRepository.countRoomTypeImages(hotelId)
                    + usageRepository.countRoomImages(hotelId);
            case "MAX_STAFF" -> usageRepository.countActiveStaff(hotelId);
            default -> null;
        };
    }

    private Map<String, Integer> planFeatures(SubscriptionPlan plan) {
        Map<String, Integer> limits = new LinkedHashMap<>();
        if (plan.getFeatures() == null) {
            return limits;
        }
        for (PlanFeature feature : plan.getFeatures()) {
            if (feature == null || feature.getFeatureCode() == null || feature.getFeatureCode().isBlank()) {
                continue;
            }
            String code = normalizeCode(feature.getFeatureCode());
            int limit = feature.getLimitValue() == null ? 1 : feature.getLimitValue();
            mergeLimit(limits, code, limit);
        }
        return limits;
    }

    private Map<String, Integer> parseFeatureSnapshot(String json) {
        try {
            JsonNode features = objectMapper.readTree(requireText(json, "featureSnapshot")).path("features");
            if (!features.isArray()) {
                throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION,
                        "Subscription feature snapshot is malformed.");
            }
            Map<String, Integer> limits = new LinkedHashMap<>();
            for (JsonNode feature : features) {
                String code = normalizeCode(feature.path("code").asText(null));
                if (code == null) {
                    throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION,
                            "Subscription feature snapshot contains a blank code.");
                }
                mergeLimit(limits, code, feature.path("limit").asInt(1));
            }
            return limits;
        } catch (JsonProcessingException exception) {
            throw new FinancialException(
                    FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "Subscription feature snapshot cannot be read.",
                    null,
                    null,
                    exception);
        }
    }

    private void mergeLimit(Map<String, Integer> limits, String code, int candidate) {
        if (candidate < -1) {
            throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "Feature limits cannot be less than -1.");
        }
        limits.merge(code, candidate, (left, right) -> greaterThan(right, left) ? right : left);
    }

    private void validateActiveEntitlement(
            SubscriptionEntitlement entitlement,
            SoftwareContract currentContract) {
        if (entitlement.getStatus() != SubscriptionEntitlement.Status.ACTIVE
                || currentContract == null
                || currentContract.getStatus() != SoftwareContract.Status.ACTIVE) {
            throw new FinancialException(FinancialErrorCode.INVALID_STATE_TRANSITION,
                    "Only an active subscription contract can be upgraded.");
        }
    }

    private void validateTermDirection(SoftwareContract currentContract, boolean targetLifetime) {
        if (currentContract.isLifetime() && !targetLifetime) {
            throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "A lifetime contract cannot upgrade to a finite-duration plan.");
        }
    }

    private void validatePaymentEvidence(
            SubscriptionOrder order,
            PlatformFinancialTransaction transaction) {
        if (order.getOperation() != SubscriptionOrder.Operation.UPGRADE
                || !sameOrder(transaction.getOrder(), order)
                || transaction.getDirection() != PlatformFinancialTransaction.Direction.DEBIT
                || transaction.getTransactionType()
                != PlatformFinancialTransaction.TransactionType.SUBSCRIPTION_UPGRADE
                || transaction.getAmount().compareTo(order.getPrice()) != 0
                || !"VND".equals(transaction.getCurrency())) {
            throw new FinancialException(FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH,
                    "Platform upgrade payment evidence does not match the subscription order.");
        }
        PlatformPaymentAttempt attempt = transaction.getAttempt();
        if (attempt == null || attempt.getStatus() != PlatformPaymentAttempt.Status.SUCCESS
                || !sameOrder(attempt.getOrder(), order)) {
            throw new FinancialException(FinancialErrorCode.INVALID_STATE_TRANSITION,
                    "Subscription upgrade requires a successful platform payment attempt.");
        }
    }

    private void validateContractEvidence(
            SubscriptionOrder order,
            PlatformFinancialTransaction transaction,
            SoftwareContract contract) {
        if (!sameOrder(contract.getOrder(), order)
                || !sameTransaction(contract.getOriginatingTransaction(), transaction)
                || !sameHotel(contract.getTargetHotel(), order.getTargetHotel())
                || contract.getSupersedesContract() == null) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "Stored upgrade contract evidence does not match the paid order.");
        }
    }

    private UpgradePeriod upgradePeriod(
            SoftwareContract currentContract,
            SubscriptionOrder order,
            LocalDateTime paidAt) {
        if (order.getDurationUnit() == SubscriptionOrder.DurationUnit.LIFETIME) {
            return new UpgradePeriod(paidAt, null, true);
        }
        if (currentContract.isLifetime()) {
            throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "A lifetime contract cannot upgrade to a finite-duration plan.");
        }
        LocalDateTime currentEnd = currentContract.getEffectiveUntil();
        if (currentEnd == null) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "Finite current contract is missing its effective end.");
        }
        LocalDateTime base = currentEnd.isAfter(paidAt) ? currentEnd : paidAt;
        try {
            LocalDateTime effectiveUntil = switch (order.getDurationUnit()) {
                case DAY -> base.plusDays(order.getDurationValue());
                case MONTH -> base.plusMonths(order.getDurationValue());
                case YEAR -> base.plusYears(order.getDurationValue());
                case LIFETIME -> throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED);
            };
            return new UpgradePeriod(paidAt, effectiveUntil, false);
        } catch (DateTimeException | ArithmeticException exception) {
            throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "The snapshotted upgrade duration cannot be applied.");
        }
    }

    private boolean lessThan(int candidate, int baseline) {
        if (baseline == -1) {
            return candidate != -1;
        }
        return candidate != -1 && candidate < baseline;
    }

    private boolean greaterThan(int candidate, int baseline) {
        if (candidate == -1) {
            return baseline != -1;
        }
        return baseline != -1 && candidate > baseline;
    }

    private String planSnapshot(SubscriptionOrder order) {
        return writeJson(new PlanSnapshot(
                order.getPlan().getId(),
                order.getPlanVersion(),
                order.getPlanCode(),
                order.getPlanName(),
                order.getPrice(),
                order.getCurrency(),
                order.getBillingPeriod(),
                order.getDurationValue(),
                order.getDurationUnit(),
                POLICY_VERSION));
    }

    private String entitlementState(SubscriptionEntitlement entitlement) {
        return writeJson(new EntitlementState(
                entitlement.getTargetHotel().getId(),
                entitlement.getContract().getPublicId(),
                entitlement.getPlan().getId(),
                entitlement.getFeatureSnapshotJson(),
                entitlement.getEffectiveFrom(),
                entitlement.getEffectiveUntil(),
                entitlement.isLifetime(),
                entitlement.getStatus()));
    }

    private String activeContractState(SoftwareContract contract) {
        return writeJson(new EntitlementState(
                contract.getTargetHotel().getId(),
                contract.getPublicId(),
                contract.getPlan().getId(),
                contract.getFeatureSnapshotJson(),
                contract.getEffectiveFrom(),
                contract.getEffectiveUntil(),
                contract.isLifetime(),
                SubscriptionEntitlement.Status.ACTIVE));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize platform upgrade evidence.", exception);
        }
    }

    private void audit(
            SubscriptionOrder order,
            PlatformFinancialTransaction transaction,
            SoftwareContract contract,
            String correlationId,
            boolean repaired) {
        auditService.append(new FinancialAuditService.AuditCommand(
                "PLATFORM_BILLING",
                null,
                "PLATFORM_SUBSCRIPTION_UPGRADE",
                order.getPublicId(),
                "PROVIDER",
                null,
                transaction.getProvider() == null ? "PLATFORM" : transaction.getProvider(),
                SubscriptionOrderState.PAID.name(),
                SubscriptionOrderState.APPLIED.name(),
                repaired ? "Repaired idempotent platform subscription upgrade"
                        : "Applied approved platform subscription upgrade",
                "PLAT-UPGRADE:" + order.getPublicId(),
                transaction.getProviderTransactionReference(),
                correlationId,
                Map.of(
                        "contractPublicId", contract.getPublicId(),
                        "policyVersion", POLICY_VERSION,
                        "durationValue", order.getDurationValue(),
                        "durationUnit", order.getDurationUnit().name(),
                        "repaired", repaired)));
    }

    private UpgradeApplicationResult response(
            SubscriptionOrder order,
            PlatformFinancialTransaction transaction,
            SoftwareContract contract,
            SubscriptionEntitlement entitlement,
            SubscriptionHistory history,
            boolean replayed) {
        return new UpgradeApplicationResult(
                order.getPublicId(),
                order.getStatus(),
                transaction.getPublicId(),
                contract.getPublicId(),
                entitlement.getTargetHotel().getId(),
                entitlement.getEffectiveFrom(),
                entitlement.getEffectiveUntil(),
                entitlement.isLifetime(),
                history.getActionType(),
                POLICY_VERSION,
                replayed);
    }

    private boolean sameOrder(SubscriptionOrder left, SubscriptionOrder right) {
        return sameEntity(left == null ? null : left.getId(), right == null ? null : right.getId(), left, right);
    }

    private boolean sameTransaction(PlatformFinancialTransaction left, PlatformFinancialTransaction right) {
        return sameEntity(left == null ? null : left.getId(), right == null ? null : right.getId(), left, right);
    }

    private boolean sameContract(SoftwareContract left, SoftwareContract right) {
        return sameEntity(left == null ? null : left.getId(), right == null ? null : right.getId(), left, right);
    }

    private boolean sameHotel(com.hotel.entities.Hotel left, com.hotel.entities.Hotel right) {
        return sameEntity(left == null ? null : left.getId(), right == null ? null : right.getId(), left, right);
    }

    private boolean sameEntity(Long leftId, Long rightId, Object left, Object right) {
        if (left == null || right == null) {
            return false;
        }
        if (leftId != null && rightId != null) {
            return Objects.equals(leftId, rightId);
        }
        return left == right;
    }

    private String normalizeCode(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value.trim();
    }

    public record UpgradeOrderCommand(Long targetHotelId, Long targetPlanId, String idempotencyKey) {
    }

    public record UpgradeApplicationResult(
            String orderPublicId,
            SubscriptionOrderState orderStatus,
            String transactionPublicId,
            String contractPublicId,
            Long targetHotelId,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveUntil,
            boolean lifetime,
            SubscriptionHistory.ActionType historyAction,
            String policyVersion,
            boolean replayed) {
    }

    private record UpgradePeriod(LocalDateTime effectiveFrom, LocalDateTime effectiveUntil, boolean lifetime) {
    }

    private record PlanSnapshot(
            Long planId,
            String planVersion,
            String planCode,
            String planName,
            java.math.BigDecimal price,
            String currency,
            String billingPeriod,
            int durationValue,
            SubscriptionOrder.DurationUnit durationUnit,
            String upgradePolicyVersion) {
    }

    private record EntitlementState(
            Long targetHotelId,
            String contractPublicId,
            Long planId,
            String featureSnapshotJson,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveUntil,
            boolean lifetime,
            SubscriptionEntitlement.Status status) {
    }
}
