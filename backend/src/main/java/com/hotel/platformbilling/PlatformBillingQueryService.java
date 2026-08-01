package com.hotel.platformbilling;

import com.hotel.entities.User;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.domain.FinancialStates.SubscriptionOrderState;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.platformbilling.order.PlatformSubscriptionOrderRepository;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.platformbilling.payment.PlatformPaymentAttempt;
import com.hotel.platformbilling.payment.PlatformPaymentAttemptRepository;
import com.hotel.platformbilling.subscription.PlatformSubscriptionHistoryRepository;
import com.hotel.platformbilling.subscription.SubscriptionHistory;
import com.hotel.services.PropertyAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
public class PlatformBillingQueryService {

    private final PlatformSubscriptionOrderRepository orderRepository;
    private final PlatformPaymentAttemptRepository attemptRepository;
    private final PlatformSubscriptionHistoryRepository historyRepository;
    private final PropertyAccessService propertyAccessService;
    private final FinancialAuditService auditService;
    private final Clock clock;

    @Autowired
    public PlatformBillingQueryService(
            PlatformSubscriptionOrderRepository orderRepository,
            PlatformPaymentAttemptRepository attemptRepository,
            PlatformSubscriptionHistoryRepository historyRepository,
            PropertyAccessService propertyAccessService,
            FinancialAuditService auditService) {
        this(orderRepository, attemptRepository, historyRepository, propertyAccessService, auditService,
                Clock.systemUTC());
    }

    PlatformBillingQueryService(
            PlatformSubscriptionOrderRepository orderRepository,
            PlatformPaymentAttemptRepository attemptRepository,
            PlatformSubscriptionHistoryRepository historyRepository,
            PropertyAccessService propertyAccessService,
            FinancialAuditService auditService,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.attemptRepository = attemptRepository;
        this.historyRepository = historyRepository;
        this.propertyAccessService = propertyAccessService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OrderDetails getOrder(String orderPublicId) {
        SubscriptionOrder order = orderRepository.findByPublicId(requireText(orderPublicId, "orderId"))
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        authorize(order);
        return details(order);
    }

    @Transactional
    public OrderDetails cancelOrder(String orderPublicId, String correlationId) {
        SubscriptionOrder order = orderRepository.findByPublicIdForUpdate(requireText(orderPublicId, "orderId"))
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        authorize(order);
        if (order.getStatus() != SubscriptionOrderState.CREATED
                && order.getStatus() != SubscriptionOrderState.PENDING_PAYMENT) {
            throw new FinancialException(
                    FinancialErrorCode.INVALID_STATE_TRANSITION,
                    "Only an unpaid subscription order can be cancelled.",
                    null,
                    order.getStatus().name(),
                    null);
        }
        List<PlatformPaymentAttempt> attempts = attemptRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());
        if (attempts.stream().anyMatch(attempt -> attempt.getStatus() == PlatformPaymentAttempt.Status.PROCESSING)) {
            throw new FinancialException(FinancialErrorCode.INVALID_STATE_TRANSITION,
                    "A processing platform payment attempt must finish before cancellation.");
        }
        String previousState = order.getStatus().name();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<PlatformPaymentAttempt> cancelledAttempts = attempts.stream()
                .filter(attempt -> attempt.getStatus() == PlatformPaymentAttempt.Status.CREATED
                        || attempt.getStatus() == PlatformPaymentAttempt.Status.PENDING)
                .toList();
        cancelledAttempts.forEach(attempt -> attempt.cancel(now));
        if (!cancelledAttempts.isEmpty()) {
            attemptRepository.saveAll(cancelledAttempts);
        }
        order.transitionTo(SubscriptionOrderState.CANCELLED, now);
        SubscriptionOrder saved = orderRepository.saveAndFlush(order);
        User actor = propertyAccessService.currentUser();
        auditService.append(new FinancialAuditService.AuditCommand(
                "PLATFORM_BILLING", null, "PLATFORM_SUBSCRIPTION_ORDER", saved.getPublicId(),
                "USER", actor == null ? null : actor.getId(), "PLATFORM",
                previousState, SubscriptionOrderState.CANCELLED.name(),
                "Cancelled unpaid platform subscription order", "PLAT-CANCEL:" + saved.getPublicId(),
                null, correlationId, Map.of("cancelledAttempts", cancelledAttempts.size())));
        return details(saved);
    }

    @Transactional(readOnly = true)
    public List<HistoryItem> history(Long targetHotelId) {
        propertyAccessService.requireManagedHotel(targetHotelId);
        return historyRepository.findByTargetHotelIdOrderByOccurredAtDesc(targetHotelId).stream()
                .map(this::historyItem)
                .toList();
    }

    private void authorize(SubscriptionOrder order) {
        if (propertyAccessService.isSystemAdministrator()) {
            return;
        }
        User actor = propertyAccessService.currentUser();
        Long actorId = actor == null ? null : actor.getId();
        Long ownerId = order.getOwner() == null ? null : order.getOwner().getId();
        if (actorId == null || !actorId.equals(ownerId)) {
            throw new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private OrderDetails details(SubscriptionOrder order) {
        List<AttemptItem> attempts = attemptRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
                .map(this::attemptItem)
                .toList();
        return new OrderDetails(
                order.getPublicId(), order.getOrderCode(), order.getOwner().getId(),
                order.getTargetHotel().getId(), order.getOperation(), order.getPlan().getId(),
                order.getPlanVersion(), order.getPlanCode(), order.getPlanName(), order.getPrice(),
                order.getCurrency(), order.getBillingPeriod(), order.getDurationValue(), order.getDurationUnit(),
                order.getFeatureSnapshotJson(), order.getStatus(), order.getExpiresAt(), order.getAppliedAt(), attempts);
    }

    private AttemptItem attemptItem(PlatformPaymentAttempt attempt) {
        return new AttemptItem(
                attempt.getPublicId(), attempt.getStatus(), attempt.getProvider(), attempt.getMethod(),
                attempt.getEnvironment(), attempt.getExpectedAmount(), attempt.getCurrency(),
                attempt.getProviderOrderReference(), attempt.getExpiresAt(), attempt.getCompletedAt(),
                attempt.getConfiguration().getMerchantReferenceMasked());
    }

    private HistoryItem historyItem(SubscriptionHistory history) {
        return new HistoryItem(
                history.getId(), history.getOrder().getPublicId(),
                history.getContract() == null ? null : history.getContract().getPublicId(),
                history.getTransaction() == null ? null : history.getTransaction().getPublicId(),
                history.getActionType(), history.getPreviousStateJson(), history.getNewStateJson(),
                history.getActorType(), history.getActorId(), history.getReason(), history.getOccurredAt());
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value.trim();
    }

    public record OrderDetails(
            String publicId, String orderCode, Long ownerUserId, Long targetHotelId,
            SubscriptionOrder.Operation operation, Long planId, String planVersion, String planCode,
            String planName, BigDecimal price, String currency, String billingPeriod, int durationValue,
            SubscriptionOrder.DurationUnit durationUnit, String featureSnapshotJson,
            SubscriptionOrderState status, LocalDateTime expiresAt, LocalDateTime appliedAt,
            List<AttemptItem> attempts) {
    }

    public record AttemptItem(
            String publicId, PlatformPaymentAttempt.Status status, String provider, String method,
            com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment environment,
            BigDecimal expectedAmount, String currency, String providerOrderReference,
            LocalDateTime expiresAt, LocalDateTime completedAt, String merchantReferenceMasked) {
    }

    public record HistoryItem(
            Long id, String orderPublicId, String contractPublicId, String transactionPublicId,
            SubscriptionHistory.ActionType actionType, String previousStateJson, String newStateJson,
            String actorType, Long actorId, String reason, LocalDateTime occurredAt) {
    }
}
