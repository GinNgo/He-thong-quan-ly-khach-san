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
import com.hotel.platformbilling.payment.PlatformFinancialTransaction;
import com.hotel.platformbilling.payment.PlatformFinancialTransactionRepository;
import com.hotel.platformbilling.payment.PlatformPaymentAttempt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class SubscriptionApplicationService {

    private final PlatformSubscriptionOrderRepository orderRepository;
    private final PlatformFinancialTransactionRepository transactionRepository;
    private final PlatformSoftwareContractRepository contractRepository;
    private final SubscriptionEntitlementRepository entitlementRepository;
    private final PlatformSubscriptionHistoryRepository historyRepository;
    private final FinancialAuditService auditService;
    private final ObjectMapper objectMapper;

    public SubscriptionApplicationService(
            PlatformSubscriptionOrderRepository orderRepository,
            PlatformFinancialTransactionRepository transactionRepository,
            PlatformSoftwareContractRepository contractRepository,
            SubscriptionEntitlementRepository entitlementRepository,
            PlatformSubscriptionHistoryRepository historyRepository,
            FinancialAuditService auditService,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.transactionRepository = transactionRepository;
        this.contractRepository = contractRepository;
        this.entitlementRepository = entitlementRepository;
        this.historyRepository = historyRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ApplicationResult applyPaidOrder(
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
                    "Only a paid subscription order can be applied.",
                    null,
                    order.getStatus().name(),
                    null);
        }
        if (order.getOperation() != SubscriptionOrder.Operation.PURCHASE) {
            throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "Renewal and upgrade application require their approved lifecycle services.");
        }
        if (existingContract != null) {
            return repairFromExistingContract(order, transaction, existingContract, correlationId);
        }

        SoftwareContract activeContract = contractRepository
                .findByTargetHotelIdAndStatusForUpdate(
                        order.getTargetHotel().getId(), SoftwareContract.Status.ACTIVE)
                .orElse(null);
        if (activeContract != null) {
            throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "An active contract already exists; use renewal or upgrade policy.");
        }

        LocalDateTime effectiveFrom = transaction.getOccurredAt();
        LocalDateTime effectiveUntil = effectiveUntil(order, effectiveFrom);
        SoftwareContract contract = SoftwareContract.activate(
                UUID.randomUUID().toString(),
                order,
                transaction,
                null,
                planSnapshot(order),
                order.getFeatureSnapshotJson(),
                effectiveFrom,
                effectiveUntil,
                order.getDurationUnit() == SubscriptionOrder.DurationUnit.LIFETIME,
                VndMoney.of(order.getPrice()));
        SoftwareContract savedContract = contractRepository.saveAndFlush(contract);

        SubscriptionEntitlement entitlement = entitlementRepository
                .findByTargetHotelIdForUpdate(order.getTargetHotel().getId())
                .orElseGet(() -> SubscriptionEntitlement.activate(savedContract));
        if (!sameContract(entitlement.getContract(), savedContract)) {
            entitlement.applyContract(savedContract);
        }
        entitlement = entitlementRepository.saveAndFlush(entitlement);

        SubscriptionHistory history = ensureHistory(order, transaction, savedContract, entitlement);
        order.transitionTo(SubscriptionOrderState.APPLIED, effectiveFrom);
        orderRepository.saveAndFlush(order);
        audit(order, transaction, savedContract, correlationId, false);
        return response(order, transaction, savedContract, entitlement, history, false);
    }

    private ApplicationResult repairFromExistingContract(
            SubscriptionOrder order,
            PlatformFinancialTransaction transaction,
            SoftwareContract contract,
            String correlationId) {
        validateContractEvidence(order, transaction, contract);
        SubscriptionEntitlement entitlement = entitlementRepository
                .findByTargetHotelIdForUpdate(order.getTargetHotel().getId())
                .orElseGet(() -> SubscriptionEntitlement.activate(contract));
        if (!sameContract(entitlement.getContract(), contract)) {
            entitlement.applyContract(contract);
        }
        entitlement = entitlementRepository.saveAndFlush(entitlement);
        SubscriptionHistory history = ensureHistory(order, transaction, contract, entitlement);
        order.transitionTo(SubscriptionOrderState.APPLIED, transaction.getOccurredAt());
        orderRepository.saveAndFlush(order);
        audit(order, transaction, contract, correlationId, true);
        return response(order, transaction, contract, entitlement, history, true);
    }

    private ApplicationResult replay(
            SubscriptionOrder order,
            PlatformFinancialTransaction transaction,
            SoftwareContract contract) {
        if (contract == null) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "Applied subscription order is missing its contract evidence.");
        }
        validateContractEvidence(order, transaction, contract);
        SubscriptionEntitlement entitlement = entitlementRepository
                .findByTargetHotelIdForUpdate(order.getTargetHotel().getId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION));
        if (!sameContract(entitlement.getContract(), contract)
                || !historyRepository.existsByOrderIdAndActionType(
                        order.getId(), SubscriptionHistory.ActionType.PURCHASED)) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "Applied subscription evidence is incomplete.");
        }
        SubscriptionHistory history = historyRepository.findByOrderIdOrderByOccurredAtAsc(order.getId()).stream()
                .filter(item -> item.getActionType() == SubscriptionHistory.ActionType.PURCHASED)
                .findFirst()
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION));
        return response(order, transaction, contract, entitlement, history, true);
    }

    private SubscriptionHistory ensureHistory(
            SubscriptionOrder order,
            PlatformFinancialTransaction transaction,
            SoftwareContract contract,
            SubscriptionEntitlement entitlement) {
        if (historyRepository.existsByOrderIdAndActionType(
                order.getId(), SubscriptionHistory.ActionType.PURCHASED)) {
            return historyRepository.findByOrderIdOrderByOccurredAtAsc(order.getId()).stream()
                    .filter(item -> item.getActionType() == SubscriptionHistory.ActionType.PURCHASED)
                    .findFirst()
                    .orElseThrow(() -> new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION));
        }
        SubscriptionHistory history = SubscriptionHistory.record(
                order,
                contract,
                transaction,
                SubscriptionHistory.ActionType.PURCHASED,
                null,
                entitlementState(entitlement),
                "PROVIDER",
                null,
                "Authoritative platform purchase applied",
                transaction.getOccurredAt());
        return historyRepository.saveAndFlush(history);
    }

    private void validatePaymentEvidence(
            SubscriptionOrder order,
            PlatformFinancialTransaction transaction) {
        if (!sameOrder(transaction.getOrder(), order)
                || transaction.getDirection() != PlatformFinancialTransaction.Direction.DEBIT
                || transaction.getTransactionType()
                != PlatformFinancialTransaction.TransactionType.SUBSCRIPTION_PURCHASE
                || transaction.getAmount().compareTo(order.getPrice()) != 0
                || !"VND".equals(transaction.getCurrency())) {
            throw new FinancialException(FinancialErrorCode.CALLBACK_REFERENCE_MISMATCH,
                    "Platform payment evidence does not match the subscription order.");
        }
        PlatformPaymentAttempt attempt = transaction.getAttempt();
        if (attempt == null || attempt.getStatus() != PlatformPaymentAttempt.Status.SUCCESS
                || !sameOrder(attempt.getOrder(), order)) {
            throw new FinancialException(FinancialErrorCode.INVALID_STATE_TRANSITION,
                    "Subscription application requires a successful platform payment attempt.");
        }
    }

    private void validateContractEvidence(
            SubscriptionOrder order,
            PlatformFinancialTransaction transaction,
            SoftwareContract contract) {
        if (!sameOrder(contract.getOrder(), order)
                || !sameTransaction(contract.getOriginatingTransaction(), transaction)
                || !sameHotel(contract.getTargetHotel(), order.getTargetHotel())) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "Stored platform contract evidence does not match the paid order.");
        }
    }

    private LocalDateTime effectiveUntil(SubscriptionOrder order, LocalDateTime effectiveFrom) {
        try {
            return switch (order.getDurationUnit()) {
                case DAY -> effectiveFrom.plusDays(order.getDurationValue());
                case MONTH -> effectiveFrom.plusMonths(order.getDurationValue());
                case YEAR -> effectiveFrom.plusYears(order.getDurationValue());
                case LIFETIME -> null;
            };
        } catch (DateTimeException | ArithmeticException exception) {
            throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "The snapshotted subscription duration cannot be applied.");
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize platform subscription evidence.", exception);
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
                "PLATFORM_SUBSCRIPTION_APPLICATION",
                order.getPublicId(),
                "PROVIDER",
                null,
                transaction.getProvider() == null ? "PLATFORM" : transaction.getProvider(),
                SubscriptionOrderState.PAID.name(),
                SubscriptionOrderState.APPLIED.name(),
                repaired ? "Repaired idempotent platform subscription application"
                        : "Applied authoritative platform subscription purchase",
                "PLAT-APPLY:" + order.getPublicId(),
                transaction.getProviderTransactionReference(),
                correlationId,
                Map.of(
                        "contractPublicId", contract.getPublicId(),
                        "operation", order.getOperation().name(),
                        "repaired", repaired)));
    }

    private ApplicationResult response(
            SubscriptionOrder order,
            PlatformFinancialTransaction transaction,
            SoftwareContract contract,
            SubscriptionEntitlement entitlement,
            SubscriptionHistory history,
            boolean replayed) {
        return new ApplicationResult(
                order.getPublicId(),
                order.getStatus(),
                transaction.getPublicId(),
                contract.getPublicId(),
                entitlement.getTargetHotel().getId(),
                entitlement.getStatus(),
                entitlement.getEffectiveFrom(),
                entitlement.getEffectiveUntil(),
                entitlement.isLifetime(),
                history.getActionType(),
                replayed);
    }

    private boolean sameOrder(SubscriptionOrder left, SubscriptionOrder right) {
        return samePersistentEntity(left == null ? null : left.getId(), right == null ? null : right.getId(), left, right);
    }

    private boolean sameTransaction(PlatformFinancialTransaction left, PlatformFinancialTransaction right) {
        return samePersistentEntity(left == null ? null : left.getId(), right == null ? null : right.getId(), left, right);
    }

    private boolean sameContract(SoftwareContract left, SoftwareContract right) {
        return samePersistentEntity(left == null ? null : left.getId(), right == null ? null : right.getId(), left, right);
    }

    private boolean sameHotel(com.hotel.entities.Hotel left, com.hotel.entities.Hotel right) {
        return samePersistentEntity(left == null ? null : left.getId(), right == null ? null : right.getId(), left, right);
    }

    private boolean samePersistentEntity(Long leftId, Long rightId, Object left, Object right) {
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

    public record ApplicationResult(
            String orderPublicId,
            SubscriptionOrderState orderStatus,
            String transactionPublicId,
            String contractPublicId,
            Long targetHotelId,
            SubscriptionEntitlement.Status entitlementStatus,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveUntil,
            boolean lifetime,
            SubscriptionHistory.ActionType historyAction,
            boolean replayed) {
    }
}
