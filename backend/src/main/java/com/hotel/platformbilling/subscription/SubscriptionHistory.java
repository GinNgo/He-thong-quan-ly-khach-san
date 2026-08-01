package com.hotel.platformbilling.subscription;

import com.hotel.entities.Hotel;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.platformbilling.payment.PlatformFinancialTransaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;

@Getter
@Immutable
@Entity(name = "PlatformSubscriptionHistory")
@Table(name = "platform_subscription_histories")
public class SubscriptionHistory {

    public enum ActionType {
        PURCHASED,
        RENEWED,
        UPGRADED,
        DOWNGRADE_BLOCKED,
        REVOKED,
        EXPIRED,
        REFUNDED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_hotel_id", nullable = false, updatable = false)
    private Hotel targetHotel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private SubscriptionOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", updatable = false)
    private SoftwareContract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", updatable = false)
    private PlatformFinancialTransaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30, updatable = false)
    private ActionType actionType;

    @Column(name = "previous_state_json", columnDefinition = "nvarchar(max)", updatable = false)
    private String previousStateJson;

    @Column(name = "new_state_json", nullable = false, columnDefinition = "nvarchar(max)", updatable = false)
    private String newStateJson;

    @Column(name = "actor_type", nullable = false, length = 30, updatable = false)
    private String actorType;

    @Column(name = "actor_id", updatable = false)
    private Long actorId;

    @Column(length = 1000, columnDefinition = "nvarchar(1000)", updatable = false)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    protected SubscriptionHistory() {
    }

    public static SubscriptionHistory record(
            SubscriptionOrder order,
            SoftwareContract contract,
            PlatformFinancialTransaction transaction,
            ActionType actionType,
            String previousStateJson,
            String newStateJson,
            String actorType,
            Long actorId,
            String reason,
            LocalDateTime occurredAt) {
        SubscriptionHistory history = new SubscriptionHistory();
        history.order = Objects.requireNonNull(order, "order must not be null");
        history.targetHotel = Objects.requireNonNull(order.getTargetHotel(), "order target hotel must not be null");
        history.contract = contract;
        history.transaction = transaction;
        history.actionType = Objects.requireNonNull(actionType, "actionType must not be null");
        history.previousStateJson = normalizeOptional(previousStateJson, Integer.MAX_VALUE);
        history.newStateJson = requireText(newStateJson, "newStateJson", Integer.MAX_VALUE);
        history.actorType = requireText(actorType, "actorType", 30).toUpperCase(Locale.ROOT);
        history.actorId = actorId;
        history.reason = normalizeOptional(reason, 1000);
        history.occurredAt = occurredAt;
        history.validate();
        return history;
    }

    @PrePersist
    void created() {
        validate();
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    @PreUpdate
    void rejectUpdate() {
        throw new IllegalStateException("Subscription history is append-only.");
    }

    @PreRemove
    void rejectDelete() {
        throw new IllegalStateException("Subscription history cannot be deleted.");
    }

    private void validate() {
        if (contract != null && !sameHotel(contract.getTargetHotel(), targetHotel)) {
            throw new IllegalArgumentException("History contract must target the order property.");
        }
        if (transaction != null && !sameOrder(transaction.getOrder(), order)) {
            throw new IllegalArgumentException("History transaction must belong to the order.");
        }
    }

    private static boolean sameHotel(Hotel left, Hotel right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.getId() != null && right.getId() != null) {
            return left.getId().equals(right.getId());
        }
        return left == right;
    }

    private static boolean sameOrder(SubscriptionOrder left, SubscriptionOrder right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.getId() != null && right.getId() != null) {
            return left.getId().equals(right.getId());
        }
        return left == right;
    }

    private static String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("value is too long.");
        }
        return normalized;
    }

    private static String requireText(String value, String field, int maxLength) {
        String normalized = normalizeOptional(value, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        return normalized;
    }
}
