package com.hotel.platformbilling.subscription;

import com.hotel.entities.Hotel;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.entities.User;
import com.hotel.paymentprovider.domain.VndMoney;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Getter
@Entity(name = "PlatformSoftwareContract")
@Table(name = "platform_software_contracts", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_platform_contract_public", columnNames = "public_id"),
        @UniqueConstraint(name = "UQ_platform_contract_order", columnNames = "order_id")
})
public class SoftwareContract {

    public enum Status {
        ACTIVE,
        SUPERSEDED,
        EXPIRED,
        REVOKED,
        REFUNDED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 64, updatable = false)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_hotel_id", nullable = false, updatable = false)
    private Hotel targetHotel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false, updatable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private SubscriptionOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "originating_transaction_id", nullable = false, updatable = false)
    private PlatformFinancialTransaction originatingTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_contract_id", updatable = false)
    private SoftwareContract supersedesContract;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private SubscriptionPlan plan;

    @Column(name = "plan_snapshot_json", nullable = false, columnDefinition = "nvarchar(max)", updatable = false)
    private String planSnapshotJson;

    @Column(name = "feature_snapshot_json", nullable = false, columnDefinition = "nvarchar(max)", updatable = false)
    private String featureSnapshotJson;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_until", updatable = false)
    private LocalDateTime effectiveUntil;

    @Column(name = "is_lifetime", nullable = false, updatable = false)
    private boolean lifetime;

    @Column(name = "contract_value", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal contractValue;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SoftwareContract() {
    }

    public static SoftwareContract activate(
            String publicId,
            SubscriptionOrder order,
            PlatformFinancialTransaction originatingTransaction,
            SoftwareContract supersedesContract,
            String planSnapshotJson,
            String featureSnapshotJson,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveUntil,
            boolean lifetime,
            VndMoney contractValue) {
        SoftwareContract contract = new SoftwareContract();
        contract.publicId = requireText(publicId, "publicId", 64);
        contract.order = Objects.requireNonNull(order, "order must not be null");
        contract.targetHotel = Objects.requireNonNull(order.getTargetHotel(), "order target hotel must not be null");
        contract.owner = Objects.requireNonNull(order.getOwner(), "order owner must not be null");
        contract.originatingTransaction = Objects.requireNonNull(
                originatingTransaction, "originatingTransaction must not be null");
        contract.supersedesContract = supersedesContract;
        contract.plan = Objects.requireNonNull(order.getPlan(), "order plan must not be null");
        contract.planSnapshotJson = requireText(planSnapshotJson, "planSnapshotJson", Integer.MAX_VALUE);
        contract.featureSnapshotJson = requireText(featureSnapshotJson, "featureSnapshotJson", Integer.MAX_VALUE);
        contract.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
        contract.effectiveUntil = effectiveUntil;
        contract.lifetime = lifetime;
        contract.contractValue = Objects.requireNonNull(contractValue, "contractValue must not be null").amount();
        contract.status = Status.ACTIVE;
        contract.validate();
        return contract;
    }

    public VndMoney contractMoney() {
        return VndMoney.of(contractValue);
    }

    public void transitionTo(Status next) {
        Objects.requireNonNull(next, "next must not be null");
        if (status != Status.ACTIVE || next == Status.ACTIVE) {
            throw new IllegalStateException("Only an active software contract can enter a terminal lifecycle state.");
        }
        status = next;
    }

    @PrePersist
    void created() {
        validate();
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    @PreUpdate
    void updated() {
        validate();
    }

    private void validate() {
        VndMoney.of(contractValue);
        if (!"VND".equals(currency)) {
            throw new IllegalStateException("Platform software contracts support VND only.");
        }
        if (!sameOrder(originatingTransaction.getOrder(), order)) {
            throw new IllegalArgumentException("Contract transaction must belong to the originating order.");
        }
        if (!lifetime && (effectiveUntil == null || !effectiveUntil.isAfter(effectiveFrom))) {
            throw new IllegalArgumentException("Non-lifetime contract requires an end after its start.");
        }
        if (lifetime && effectiveUntil != null) {
            throw new IllegalArgumentException("Lifetime contract cannot have an effective end.");
        }
        if (supersedesContract != null && !sameHotel(supersedesContract.getTargetHotel(), targetHotel)) {
            throw new IllegalArgumentException("Superseded contract must target the same property.");
        }
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

    private static boolean sameHotel(Hotel left, Hotel right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.getId() != null && right.getId() != null) {
            return left.getId().equals(right.getId());
        }
        return left == right;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long.");
        }
        return normalized;
    }
}
