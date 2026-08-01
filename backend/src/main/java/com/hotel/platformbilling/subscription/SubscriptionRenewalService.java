package com.hotel.platformbilling.subscription;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.domain.FinancialStates.SubscriptionOrderState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.platformbilling.order.PlatformSubscriptionOrderRepository;
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
import java.util.Map;
import java.util.UUID;

@Service
public class SubscriptionRenewalService {

    private final PlatformSubscriptionOrderRepository orderRepository;
    private final PlatformFinancialTransactionRepository transactionRepository;
    private final PlatformSoftwareContractRepository contractRepository;
    private final SubscriptionEntitlementRepository entitlementRepository;
    private final PlatformSubscriptionHistoryRepository historyRepository;
    private final SubscriptionOrderService orderService;
    private final PropertyAccessService propertyAccessService;
    private final FinancialAuditService auditService;
    private final ObjectMapper objectMapper;

    public SubscriptionRenewalService(
            PlatformSubscriptionOrderRepository orderRepository,
            PlatformFinancialTransactionRepository transactionRepository,
            PlatformSoftwareContractRepository contractRepository,
            SubscriptionEntitlementRepository entitlementRepository,
            PlatformSubscriptionHistoryRepository historyRepository,
            SubscriptionOrderService orderService,
            PropertyAccessService propertyAccessService,
            FinancialAuditService auditService,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.transactionRepository = transactionRepository;
        this.contractRepository = contractRepository;
        this.entitlementRepository = entitlementRepository;
        this.historyRepository = historyRepository;
        this.orderService = orderService;
        this.propertyAccessService = propertyAccessService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SubscriptionOrderService.OrderResponse createRenewalOrder(RenewalOrderCommand command) {
        if (command == null || command.targetHotelId() == null) {
            throw new IllegalArgumentException("Target property is required for subscription renewal.");
        }
        propertyAccessService.requireManagedHotel(command.targetHotelId());
        SubscriptionEntitlement entitlement = entitlementRepository
                .findByTargetHotelIdForUpdate(command.targetHotelId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        SoftwareContract currentContract = entitlement.getContract();
        validateRenewableEntitlement(entitlement, currentContract);
        return orderService.createLifecycleOrder(new SubscriptionOrderService.CreateLifecycleOrderCommand(
                command.targetHotelId(),
                currentContract.getPlan().getId(),
                SubscriptionOrder.Operation.RENEW,
                requireText(command.idempotencyKey(), "idempotencyKey")));
    }

    @Transactional
    public RenewalApplicationResult applyPaidRenewal(
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
                    "Only a paid subscription renewal order can be applied.",
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
        validateRenewableEntitlement(entitlement, currentContract);
        if (!samePlan(currentContract, order)) {
            throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "Renewal must retain the current subscription plan; use the upgrade workflow instead.");
        }

        String previousState = entitlementState(entitlement);
        RenewalPeriod period = renewalPeriod(currentContract, order, transaction.getOccurredAt());
        SoftwareContract renewedContract = SoftwareContract.activate(
                UUID.randomUUID().toString(),
                order,
                transaction,
                currentContract,
                planSnapshot(order),
                order.getFeatureSnapshotJson(),
                period.effectiveFrom(),
                period.effectiveUntil(),
                false,
                VndMoney.of(order.getPrice()));
        SoftwareContract savedContract = contractRepository.saveAndFlush(renewedContract);
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

    private RenewalApplicationResult repair(
            SubscriptionOrder order,
            PlatformFinancialTransaction transaction,
            SoftwareContract renewedContract,
            String correlationId) {
        validateContractEvidence(order, transaction, renewedContract);
        SoftwareContract previousContract = renewedContract.getSupersedesContract();
        if (previousContract == null) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "Renewal contract is missing its previous contract evidence.");
        }
        SubscriptionEntitlement entitlement = entitlementRepository
                .findByTargetHotelIdForUpdate(order.getTargetHotel().getId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION));
        String previousState = activeContractState(previousContract);
        if (previousContract.getStatus() == SoftwareContract.Status.ACTIVE) {
            previousContract.transitionTo(SoftwareContract.Status.SUPERSEDED);
            contractRepository.saveAndFlush(previousContract);
        } else if (previousContract.getStatus() != SoftwareContract.Status.SUPERSEDED) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "Renewal previous contract has an incompatible stored state.");
        }
        SubscriptionEntitlement savedEntitlement = entitlement;
        if (!sameContract(entitlement.getContract(), renewedContract)) {
            entitlement.applyContract(renewedContract);
            savedEntitlement = entitlementRepository.saveAndFlush(entitlement);
        }
        SubscriptionHistory history = ensureHistory(
                order, transaction, renewedContract, savedEntitlement, previousState);
        order.transitionTo(SubscriptionOrderState.APPLIED, transaction.getOccurredAt());
        orderRepository.saveAndFlush(order);
        audit(order, transaction, renewedContract, correlationId, true);
        return response(order, transaction, renewedContract, savedEntitlement, history, true);
    }

    private RenewalApplicationResult replay(
            SubscriptionOrder order,
            PlatformFinancialTransaction transaction,
            SoftwareContract contract) {
        if (contract == null) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "Applied renewal order is missing its contract evidence.");
        }
        validateContractEvidence(order, transaction, contract);
        SubscriptionEntitlement entitlement = entitlementRepository
                .findByTargetHotelIdForUpdate(order.getTargetHotel().getId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION));
        if (!sameContract(entitlement.getContract(), contract)
                || !historyRepository.existsByOrderIdAndActionType(
                        order.getId(), SubscriptionHistory.ActionType.RENEWED)) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "Applied renewal evidence is incomplete.");
        }
        SubscriptionHistory history = historyRepository.findByOrderIdOrderByOccurredAtAsc(order.getId()).stream()
                .filter(item -> item.getActionType() == SubscriptionHistory.ActionType.RENEWED)
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
        if (historyRepository.existsByOrderIdAndActionType(order.getId(), SubscriptionHistory.ActionType.RENEWED)) {
            return historyRepository.findByOrderIdOrderByOccurredAtAsc(order.getId()).stream()
                    .filter(item -> item.getActionType() == SubscriptionHistory.ActionType.RENEWED)
                    .findFirst()
                    .orElseThrow(() -> new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION));
        }
        return historyRepository.saveAndFlush(SubscriptionHistory.record(
                order,
                contract,
                transaction,
                SubscriptionHistory.ActionType.RENEWED,
                previousState,
                entitlementState(entitlement),
                "PROVIDER",
                null,
                "Authoritative platform renewal applied",
                transaction.getOccurredAt()));
    }

    private void validateRenewableEntitlement(
            SubscriptionEntitlement entitlement,
            SoftwareContract currentContract) {
        if (entitlement.getStatus() != SubscriptionEntitlement.Status.ACTIVE
                || currentContract == null
                || currentContract.getStatus() != SoftwareContract.Status.ACTIVE) {
            throw new FinancialException(FinancialErrorCode.INVALID_STATE_TRANSITION,
                    "Only an active subscription contract can be renewed.");
        }
        if (entitlement.isLifetime() || currentContract.isLifetime()) {
            throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "Lifetime subscriptions do not have an approved renewal policy.");
        }
        if (currentContract.getEffectiveUntil() == null) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "Renewable contract is missing its effective end.");
        }
    }

    private void validatePaymentEvidence(
            SubscriptionOrder order,
            PlatformFinancialTransaction transaction) {
        if (order.getOperation() != SubscriptionOrder.Operation.RENEW
                || !sameOrder(transaction.getOrder(), order)
                || transaction.getDirection() != PlatformFinancialTransaction.Direction.DEBIT
                || transaction.getTransactionType()
                != PlatformFinancialTransaction.TransactionType.SUBSCRIPTION_RENEWAL
                || transaction.getAmount().compareTo(order.getPrice()) != 0
                || !"VND".equals(transaction.getCurrency())) {
            throw new FinancialException(FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH,
                    "Platform renewal payment evidence does not match the subscription order.");
        }
        PlatformPaymentAttempt attempt = transaction.getAttempt();
        if (attempt == null || attempt.getStatus() != PlatformPaymentAttempt.Status.SUCCESS
                || !sameOrder(attempt.getOrder(), order)) {
            throw new FinancialException(FinancialErrorCode.INVALID_STATE_TRANSITION,
                    "Subscription renewal requires a successful platform payment attempt.");
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
                    "Stored renewal contract evidence does not match the paid order.");
        }
    }

    private RenewalPeriod renewalPeriod(
            SoftwareContract currentContract,
            SubscriptionOrder order,
            LocalDateTime paidAt) {
        if (order.getDurationUnit() == SubscriptionOrder.DurationUnit.LIFETIME) {
            throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "A renewal order cannot convert a subscription to lifetime access.");
        }
        LocalDateTime currentEnd = currentContract.getEffectiveUntil();
        boolean continuous = currentEnd.isAfter(paidAt);
        LocalDateTime base = continuous ? currentEnd : paidAt;
        LocalDateTime effectiveFrom = continuous ? currentContract.getEffectiveFrom() : paidAt;
        try {
            LocalDateTime effectiveUntil = switch (order.getDurationUnit()) {
                case DAY -> base.plusDays(order.getDurationValue());
                case MONTH -> base.plusMonths(order.getDurationValue());
                case YEAR -> base.plusYears(order.getDurationValue());
                case LIFETIME -> throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED);
            };
            return new RenewalPeriod(effectiveFrom, effectiveUntil);
        } catch (DateTimeException | ArithmeticException exception) {
            throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "The snapshotted renewal duration cannot be applied.");
        }
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
                order.getDurationUnit()));
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
            throw new IllegalStateException("Unable to serialize platform renewal evidence.", exception);
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
                "PLATFORM_SUBSCRIPTION_RENEWAL",
                order.getPublicId(),
                "PROVIDER",
                null,
                transaction.getProvider() == null ? "PLATFORM" : transaction.getProvider(),
                SubscriptionOrderState.PAID.name(),
                SubscriptionOrderState.APPLIED.name(),
                repaired ? "Repaired idempotent platform subscription renewal"
                        : "Applied authoritative platform subscription renewal",
                "PLAT-RENEW:" + order.getPublicId(),
                transaction.getProviderTransactionReference(),
                correlationId,
                Map.of(
                        "contractPublicId", contract.getPublicId(),
                        "durationValue", order.getDurationValue(),
                        "durationUnit", order.getDurationUnit().name(),
                        "repaired", repaired)));
    }

    private RenewalApplicationResult response(
            SubscriptionOrder order,
            PlatformFinancialTransaction transaction,
            SoftwareContract contract,
            SubscriptionEntitlement entitlement,
            SubscriptionHistory history,
            boolean replayed) {
        return new RenewalApplicationResult(
                order.getPublicId(),
                order.getStatus(),
                transaction.getPublicId(),
                contract.getPublicId(),
                entitlement.getTargetHotel().getId(),
                entitlement.getEffectiveFrom(),
                entitlement.getEffectiveUntil(),
                history.getActionType(),
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

    private boolean samePlan(SoftwareContract contract, SubscriptionOrder order) {
        return sameEntity(
                contract == null || contract.getPlan() == null ? null : contract.getPlan().getId(),
                order == null || order.getPlan() == null ? null : order.getPlan().getId(),
                contract == null ? null : contract.getPlan(),
                order == null ? null : order.getPlan());
    }

    private boolean sameEntity(Long leftId, Long rightId, Object left, Object right) {
        if (left == null || right == null) {
            return false;
        }
        if (leftId != null && rightId != null) {
            return leftId.equals(rightId);
        }
        return left == right;
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value.trim();
    }

    public record RenewalOrderCommand(Long targetHotelId, String idempotencyKey) {
    }

    public record RenewalApplicationResult(
            String orderPublicId,
            SubscriptionOrderState orderStatus,
            String transactionPublicId,
            String contractPublicId,
            Long targetHotelId,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveUntil,
            SubscriptionHistory.ActionType historyAction,
            boolean replayed) {
    }

    private record RenewalPeriod(LocalDateTime effectiveFrom, LocalDateTime effectiveUntil) {
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
            SubscriptionOrder.DurationUnit durationUnit) {
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
