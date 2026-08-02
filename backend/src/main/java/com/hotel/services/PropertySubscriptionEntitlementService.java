package com.hotel.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.AccountSubscription;
import com.hotel.entities.Hotel;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.platformbilling.subscription.LegacySubscriptionEntitlementProjection;
import com.hotel.platformbilling.subscription.LegacySubscriptionEntitlementProjectionRepository;
import com.hotel.platformbilling.subscription.SubscriptionEntitlement;
import com.hotel.platformbilling.subscription.SubscriptionEntitlementRepository;
import com.hotel.repositories.AccountSubscriptionRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Property-scoped entitlement read model. Platform rows are authoritative;
 * legacy subscriptions are projected only when their property scope is unambiguous.
 */
@Service
@RequiredArgsConstructor
public class PropertySubscriptionEntitlementService {

    private final SubscriptionEntitlementRepository platformRepository;
    private final LegacySubscriptionEntitlementProjectionRepository legacyRepository;
    private final UserPropertyRepository userPropertyRepository;
    private final AccountSubscriptionRepository accountSubscriptionRepository;
    private final UserRepository userRepository;
    private final FinancialAuditService auditService;
    private final ObjectMapper objectMapper;

    private final Clock clock = Clock.systemUTC();

    @Transactional
    public EntitlementView getCurrent(Long targetHotelId) {
        requireHotelId(targetHotelId);
        Optional<SubscriptionEntitlement> platform = platformRepository.findByTargetHotelId(targetHotelId);
        if (platform.isPresent()) {
            return platformView(platform.get(), now());
        }
        return projectLegacyForUpdate(targetHotelId);
    }

    @Transactional
    public EntitlementView getCurrentForUpdate(Long targetHotelId) {
        requireHotelId(targetHotelId);
        Optional<SubscriptionEntitlement> platform = platformRepository.findByTargetHotelIdForUpdate(targetHotelId);
        if (platform.isPresent()) {
            return platformView(platform.get(), now());
        }
        return projectLegacyForUpdate(targetHotelId);
    }

    private EntitlementView projectLegacyForUpdate(Long targetHotelId) {
        LocalDateTime now = now();
        Optional<SubscriptionEntitlement> platform = platformRepository.findByTargetHotelIdForUpdate(targetHotelId);
        if (platform.isPresent()) {
            return platformView(platform.get(), now);
        }

        LegacyOwner owner = resolveLegacyOwner(targetHotelId);
        if (owner == null) {
            return EntitlementView.none(targetHotelId, "LEGACY_SCOPE_AMBIGUOUS_OR_MISSING");
        }
        userRepository.findByIdForUpdate(owner.user().getId());
        Optional<LegacySubscriptionEntitlementProjection> existing = legacyRepository
                .findByTargetHotelIdForUpdate(targetHotelId);
        if (existing.isPresent()) {
            LegacySubscriptionEntitlementProjection projection = existing.get();
            if (matchesActiveSource(projection, owner.subscriptions(), now)) {
                return projectionView(projection, now);
            }
            String previousFingerprint = projection.getSourceFingerprint();
            ProjectionData data = projectionData(targetHotelId, owner, now);
            boolean changed = projection.refresh(
                    data.plan(), data.featureSnapshotJson(), data.sourceSubscriptionIds(), data.fingerprint(),
                    data.effectiveFrom(), data.effectiveUntil(), data.lifetime(), now);
            if (changed) {
                legacyRepository.saveAndFlush(projection);
                auditProjection(targetHotelId, owner.user(), previousFingerprint, data.fingerprint(), false);
            }
            return projectionView(projection, now);
        }

        ProjectionData data = projectionData(targetHotelId, owner, now);
        LegacySubscriptionEntitlementProjection projection = LegacySubscriptionEntitlementProjection.create(
                owner.property(), owner.user(), data.plan(), data.featureSnapshotJson(),
                data.sourceSubscriptionIds(), data.fingerprint(), data.effectiveFrom(), data.effectiveUntil(),
                data.lifetime(), now);
        legacyRepository.saveAndFlush(projection);
        auditProjection(targetHotelId, owner.user(), null, data.fingerprint(), true);
        return projectionView(projection, now);
    }

    private LegacyOwner resolveLegacyOwner(Long targetHotelId) {
        List<UserProperty> mappings = userPropertyRepository
                .findByHotelIdAndRelationshipTypeAndStatus(targetHotelId, "OWNER", "ACTIVE");
        List<UserProperty> primary = mappings.stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsPrimaryOwner()))
                .toList();
        if (primary.size() == 1) {
            mappings = primary;
        }
        if (mappings.size() != 1 || mappings.get(0).getUser() == null || mappings.get(0).getHotel() == null) {
            return null;
        }
        User user = mappings.get(0).getUser();
        long ownedProperties = userPropertyRepository.countActiveOwnedPropertiesByUserId(user.getId());
        if (ownedProperties != 1) {
            return null;
        }
        List<AccountSubscription> subscriptions = accountSubscriptionRepository
                .findEffectiveSubscriptionsByUserId(user.getId());
        if (subscriptions.isEmpty()) {
            return null;
        }
        return new LegacyOwner(mappings.get(0).getHotel(), user, subscriptions);
    }

    private ProjectionData projectionData(Long targetHotelId, LegacyOwner owner, LocalDateTime now) {
        List<AccountSubscription> subscriptions = owner.subscriptions();
        AccountSubscription current = subscriptions.get(0);
        Map<String, Integer> limits = new LinkedHashMap<>();
        subscriptions.stream()
                .map(AccountSubscription::getPlan)
                .filter(Objects::nonNull)
                .flatMap(plan -> plan.getFeatures() == null ? java.util.stream.Stream.empty() : plan.getFeatures().stream())
                .filter(Objects::nonNull)
                .forEach(feature -> {
                    String code = normalizeCode(feature.getFeatureCode());
                    if (code == null) return;
                    int limit = feature.getLimitValue() == null ? 1 : feature.getLimitValue();
                    limits.merge(code, limit, this::higherLimit);
                });
        String snapshot = writeSnapshot(current.getPlan(), limits);
        String sourceIds = subscriptions.stream()
                .map(AccountSubscription::getId)
                .filter(Objects::nonNull)
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        String material = subscriptions.stream()
                .map(item -> item.getId() + "|" + item.getPlan().getId() + "|" + item.getStartAt()
                        + "|" + item.getEndAt() + "|" + item.getIsLifetime() + "|" + item.getStatus())
                .collect(Collectors.joining(";"));
        LocalDateTime effectiveFrom = subscriptions.stream()
                .map(AccountSubscription::getStartAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(now);
        boolean lifetime = subscriptions.stream().anyMatch(item -> Boolean.TRUE.equals(item.getIsLifetime()));
        LocalDateTime effectiveUntil = lifetime ? null : subscriptions.stream()
                .map(AccountSubscription::getEndAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (effectiveUntil == null) {
            return new ProjectionData(current.getPlan(), snapshot, sourceIds, fingerprint(material), effectiveFrom,
                    null, true);
        }
        return new ProjectionData(current.getPlan(), snapshot, sourceIds, fingerprint(material), effectiveFrom,
                effectiveUntil, lifetime);
    }

    private boolean matchesActiveSource(
            LegacySubscriptionEntitlementProjection projection,
            List<AccountSubscription> subscriptions,
            LocalDateTime now) {
        String material = subscriptions.stream()
                .map(item -> item.getId() + "|" + item.getPlan().getId() + "|" + item.getStartAt()
                        + "|" + item.getEndAt() + "|" + item.getIsLifetime() + "|" + item.getStatus())
                .collect(Collectors.joining(";"));
        return projection.getStatus() == LegacySubscriptionEntitlementProjection.Status.ACTIVE
                && projection.getSourceFingerprint().equals(fingerprint(material))
                && (projection.isLifetime()
                || projection.getEffectiveUntil() != null && projection.getEffectiveUntil().isAfter(now));
    }

    private EntitlementView platformView(SubscriptionEntitlement entitlement, LocalDateTime now) {
        boolean active = entitlement.getStatus() == SubscriptionEntitlement.Status.ACTIVE
                && !entitlement.getEffectiveFrom().isAfter(now)
                && (entitlement.isLifetime()
                || entitlement.getEffectiveUntil() != null && entitlement.getEffectiveUntil().isAfter(now));
        Map<String, Integer> limits = active ? parseSnapshot(entitlement.getFeatureSnapshotJson()) : Map.of();
        return new EntitlementView(
                entitlement.getTargetHotel().getId(), "PLATFORM", true,
                entitlement.getPlan() == null ? null : entitlement.getPlan().getId(),
                entitlement.getPlan() == null ? null : entitlement.getPlan().getCode(),
                entitlement.getPlan() == null ? null : entitlement.getPlan().getNameVi(),
                entitlement.getStatus().name(), entitlement.getEffectiveFrom(), entitlement.getEffectiveUntil(),
                entitlement.isLifetime(), limits, entitlement.getContract() == null ? null : entitlement.getContract().getPublicId(),
                active ? null : "PLATFORM_ENTITLEMENT_NOT_ACTIVE");
    }

    private EntitlementView projectionView(LegacySubscriptionEntitlementProjection projection, LocalDateTime now) {
        boolean active = projection.getStatus() == LegacySubscriptionEntitlementProjection.Status.ACTIVE
                && !projection.getEffectiveFrom().isAfter(now)
                && (projection.isLifetime()
                || projection.getEffectiveUntil() != null && projection.getEffectiveUntil().isAfter(now));
        Map<String, Integer> limits = active ? parseSnapshot(projection.getFeatureSnapshotJson()) : Map.of();
        return new EntitlementView(
                projection.getTargetHotel().getId(), "LEGACY_PROJECTION", false,
                projection.getPlan().getId(), projection.getPlan().getCode(), projection.getPlan().getNameVi(),
                projection.getStatus().name(), projection.getEffectiveFrom(), projection.getEffectiveUntil(),
                projection.isLifetime(), limits, projection.getSourceSubscriptionIds(),
                active ? null : "LEGACY_SUBSCRIPTION_NOT_ACTIVE");
    }

    private void auditProjection(Long hotelId, User owner, String previous, String next, boolean created) {
        auditService.append(new FinancialAuditService.AuditCommand(
                "PLATFORM_BILLING", hotelId, "LEGACY_SUBSCRIPTION_PROJECTION", String.valueOf(hotelId),
                "SYSTEM", owner.getId(), "LEGACY_BACKFILL", previous, next,
                created ? "Created unambiguous legacy subscription entitlement projection"
                        : "Refreshed legacy subscription entitlement projection",
                "LEGACY-PROJECTION:" + hotelId + ":" + next, null, null,
                Map.of("ownerUserId", owner.getId(), "projectionVersion", "V1")));
    }

    private Map<String, Integer> parseSnapshot(String snapshot) {
        try {
            JsonNode features = objectMapper.readTree(snapshot).path("features");
            if (!features.isArray()) {
                throw new IllegalStateException("Stored entitlement feature snapshot is invalid.");
            }
            Map<String, Integer> limits = new LinkedHashMap<>();
            for (JsonNode feature : features) {
                String code = normalizeCode(feature.path("code").asText(null));
                int limit = feature.path("limit").asInt(0);
                if (code == null || limit < -1) {
                    throw new IllegalStateException("Stored entitlement feature snapshot is invalid.");
                }
                limits.merge(code, limit, this::higherLimit);
            }
            return limits;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored entitlement feature snapshot is invalid.", exception);
        }
    }

    private String writeSnapshot(SubscriptionPlan plan, Map<String, Integer> limits) {
        try {
            List<FeatureSnapshot> features = new ArrayList<>();
            limits.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> features.add(new FeatureSnapshot(entry.getKey(), entry.getValue())));
            return objectMapper.writeValueAsString(new CatalogSnapshot(
                    plan.getCode(), plan.getNameVi(), plan.getNameEn(), Boolean.TRUE.equals(plan.getIsLifetime()), features));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize legacy entitlement projection.", exception);
        }
    }

    private int higherLimit(int left, int right) {
        return left == -1 || right == -1 ? -1 : Math.max(left, right);
    }

    private String fingerprint(String material) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) return null;
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private void requireHotelId(Long hotelId) {
        if (hotelId == null || hotelId < 1) {
            throw new IllegalArgumentException("targetHotelId is required.");
        }
    }

    private record LegacyOwner(Hotel property, User user, List<AccountSubscription> subscriptions) {
    }

    private record ProjectionData(
            SubscriptionPlan plan,
            String featureSnapshotJson,
            String sourceSubscriptionIds,
            String fingerprint,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveUntil,
            boolean lifetime) {
    }

    private record FeatureSnapshot(String code, int limit) {
    }

    private record CatalogSnapshot(
            String planCode,
            String nameVi,
            String nameEn,
            boolean lifetime,
            List<FeatureSnapshot> features) {
    }

    public record EntitlementView(
            Long targetHotelId,
            String source,
            boolean platformAuthoritative,
            Long planId,
            String planCode,
            String planName,
            String status,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveUntil,
            boolean lifetime,
            Map<String, Integer> limits,
            String sourceReference,
            String migrationBlocker) {

        public static EntitlementView none(Long targetHotelId, String blocker) {
            return new EntitlementView(targetHotelId, "NONE", false, null, "NO_PLAN", null,
                    "NONE", null, null, false, Map.of(), null, blocker);
        }
    }
}
