package com.hotel.platformbilling.subscription;

import com.hotel.entities.Hotel;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.entities.User;
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

/**
 * Explicit compatibility projection for legacy user subscriptions.
 * Platform entitlements always take precedence over this read model.
 */
@Getter
@Entity
@Table(name = "legacy_subscription_entitlement_projections", uniqueConstraints = @UniqueConstraint(
        name = "UQ_legacy_entitlement_projection_hotel",
        columnNames = "target_hotel_id"))
public class LegacySubscriptionEntitlementProjection {

    public enum Status {
        ACTIVE,
        EXPIRED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_hotel_id", nullable = false, updatable = false)
    private Hotel targetHotel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false, updatable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Column(name = "feature_snapshot_json", nullable = false, columnDefinition = "nvarchar(max)")
    private String featureSnapshotJson;

    @Column(name = "source_subscription_ids", nullable = false, length = 1000)
    private String sourceSubscriptionIds;

    @Column(name = "source_fingerprint", nullable = false, length = 128)
    private String sourceFingerprint;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_until")
    private LocalDateTime effectiveUntil;

    @Column(name = "is_lifetime", nullable = false)
    private boolean lifetime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "projected_at", nullable = false)
    private LocalDateTime projectedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected LegacySubscriptionEntitlementProjection() {
    }

    public static LegacySubscriptionEntitlementProjection create(
            Hotel targetHotel,
            User owner,
            SubscriptionPlan plan,
            String featureSnapshotJson,
            String sourceSubscriptionIds,
            String sourceFingerprint,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveUntil,
            boolean lifetime,
            LocalDateTime projectedAt) {
        LegacySubscriptionEntitlementProjection projection = new LegacySubscriptionEntitlementProjection();
        projection.targetHotel = Objects.requireNonNull(targetHotel, "targetHotel must not be null");
        projection.owner = Objects.requireNonNull(owner, "owner must not be null");
        projection.plan = Objects.requireNonNull(plan, "plan must not be null");
        projection.featureSnapshotJson = requireText(featureSnapshotJson, "featureSnapshotJson");
        projection.sourceSubscriptionIds = requireText(sourceSubscriptionIds, "sourceSubscriptionIds");
        projection.sourceFingerprint = requireText(sourceFingerprint, "sourceFingerprint");
        projection.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
        projection.effectiveUntil = effectiveUntil;
        projection.lifetime = lifetime;
        projection.status = Status.ACTIVE;
        projection.projectedAt = Objects.requireNonNull(projectedAt, "projectedAt must not be null");
        projection.updatedAt = projectedAt;
        projection.validate();
        return projection;
    }

    public boolean refresh(
            SubscriptionPlan nextPlan,
            String nextFeatureSnapshotJson,
            String nextSourceSubscriptionIds,
            String nextSourceFingerprint,
            LocalDateTime nextEffectiveFrom,
            LocalDateTime nextEffectiveUntil,
            boolean nextLifetime,
            LocalDateTime now) {
        if (Objects.equals(sourceFingerprint, nextSourceFingerprint)
                && status == Status.ACTIVE) {
            return false;
        }
        plan = Objects.requireNonNull(nextPlan, "nextPlan must not be null");
        featureSnapshotJson = requireText(nextFeatureSnapshotJson, "featureSnapshotJson");
        sourceSubscriptionIds = requireText(nextSourceSubscriptionIds, "sourceSubscriptionIds");
        sourceFingerprint = requireText(nextSourceFingerprint, "sourceFingerprint");
        effectiveFrom = Objects.requireNonNull(nextEffectiveFrom, "effectiveFrom must not be null");
        effectiveUntil = nextEffectiveUntil;
        lifetime = nextLifetime;
        status = Status.ACTIVE;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
        validate();
        return true;
    }

    public void expire(LocalDateTime now) {
        status = Status.EXPIRED;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    @PrePersist
    @PreUpdate
    void updated() {
        validate();
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    private void validate() {
        if (targetHotel == null || owner == null || plan == null) {
            throw new IllegalStateException("Legacy entitlement projection requires owner, property and plan.");
        }
        if (!lifetime && (effectiveUntil == null || !effectiveUntil.isAfter(effectiveFrom))) {
            throw new IllegalArgumentException("Non-lifetime legacy projection requires an end after its start.");
        }
        if (lifetime && effectiveUntil != null) {
            throw new IllegalArgumentException("Lifetime legacy projection cannot have an effective end.");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        return value.trim();
    }
}
