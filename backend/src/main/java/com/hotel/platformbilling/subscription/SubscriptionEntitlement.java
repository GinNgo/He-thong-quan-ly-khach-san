package com.hotel.platformbilling.subscription;

import com.hotel.entities.Hotel;
import com.hotel.entities.SubscriptionPlan;
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
import jakarta.persistence.Version;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Getter
@Entity
@Table(name = "platform_subscription_entitlements", uniqueConstraints = @UniqueConstraint(
        name = "UQ_platform_entitlement_hotel",
        columnNames = "target_hotel_id"))
public class SubscriptionEntitlement {

    public enum Status {
        ACTIVE,
        EXPIRED,
        REVOKED,
        REFUNDED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_hotel_id", nullable = false, updatable = false)
    private Hotel targetHotel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private SoftwareContract contract;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Column(name = "feature_snapshot_json", nullable = false, columnDefinition = "nvarchar(max)")
    private String featureSnapshotJson;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_until")
    private LocalDateTime effectiveUntil;

    @Column(name = "is_lifetime", nullable = false)
    private boolean lifetime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SubscriptionEntitlement() {
    }

    public static SubscriptionEntitlement activate(SoftwareContract contract) {
        SubscriptionEntitlement entitlement = new SubscriptionEntitlement();
        entitlement.targetHotel = Objects.requireNonNull(contract, "contract must not be null").getTargetHotel();
        entitlement.applyContract(contract);
        return entitlement;
    }

    public void applyContract(SoftwareContract contract) {
        Objects.requireNonNull(contract, "contract must not be null");
        if (targetHotel != null && !sameHotel(contract.getTargetHotel(), targetHotel)) {
            throw new IllegalArgumentException("Entitlement contract must target the same property.");
        }
        if (contract.getStatus() != SoftwareContract.Status.ACTIVE) {
            throw new IllegalArgumentException("Only an active contract can become the current entitlement.");
        }
        targetHotel = contract.getTargetHotel();
        this.contract = contract;
        plan = contract.getPlan();
        featureSnapshotJson = contract.getFeatureSnapshotJson();
        effectiveFrom = contract.getEffectiveFrom();
        effectiveUntil = contract.getEffectiveUntil();
        lifetime = contract.isLifetime();
        status = Status.ACTIVE;
        validate();
    }

    public void transitionTo(Status next) {
        Objects.requireNonNull(next, "next must not be null");
        if (status != Status.ACTIVE || next == Status.ACTIVE) {
            throw new IllegalStateException("Only an active entitlement can enter a terminal state.");
        }
        status = next;
    }

    @PrePersist
    @PreUpdate
    void updated() {
        validate();
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    private void validate() {
        if (contract == null || plan == null || targetHotel == null) {
            throw new IllegalStateException("Subscription entitlement requires contract, plan and target property.");
        }
        if (!sameHotel(contract.getTargetHotel(), targetHotel) || !samePlan(contract.getPlan(), plan)) {
            throw new IllegalStateException("Subscription entitlement must mirror its contract owner and plan.");
        }
        if (!lifetime && (effectiveUntil == null || !effectiveUntil.isAfter(effectiveFrom))) {
            throw new IllegalArgumentException("Non-lifetime entitlement requires an end after its start.");
        }
        if (lifetime && effectiveUntil != null) {
            throw new IllegalArgumentException("Lifetime entitlement cannot have an effective end.");
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

    private static boolean samePlan(SubscriptionPlan left, SubscriptionPlan right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.getId() != null && right.getId() != null) {
            return left.getId().equals(right.getId());
        }
        return left == right;
    }
}
