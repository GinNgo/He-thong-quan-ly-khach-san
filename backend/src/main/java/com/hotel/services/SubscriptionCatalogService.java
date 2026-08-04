package com.hotel.services;

import com.hotel.dtos.AccountSubscriptionDTO;
import com.hotel.dtos.SubscriptionEntitlementDTO;
import com.hotel.dtos.SubscriptionFeatureDTO;
import com.hotel.dtos.SubscriptionPlanDTO;
import com.hotel.dtos.SubscriptionUsageDTO;
import com.hotel.entities.PlanFeature;
import com.hotel.entities.SubscriptionFeature;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.entities.UserProperty;
import com.hotel.platformbilling.subscription.PlatformSubscriptionUsageRepository;
import com.hotel.repositories.SubscriptionFeatureRepository;
import com.hotel.repositories.SubscriptionPlanRepository;
import com.hotel.repositories.UserPropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionCatalogService {

    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionFeatureRepository featureRepository;
    private final PropertySubscriptionEntitlementService entitlementService;
    private final PropertyAccessService propertyAccessService;
    private final PlatformSubscriptionUsageRepository usageRepository;
    private final UserPropertyRepository userPropertyRepository;

    @Transactional(readOnly = true)
    public List<SubscriptionPlanDTO> getActivePlans() {
        List<SubscriptionPlan> plans = planRepository.findByStatusOrderByPriceAsc("ACTIVE");
        Set<String> codes = plans.stream()
                .flatMap(plan -> safeFeatures(plan).stream())
                .map(PlanFeature::getFeatureCode)
                .collect(Collectors.toSet());
        Map<String, SubscriptionFeature> definitions = definitions(codes);
        return plans.stream().map(plan -> toPlan(plan, definitions)).toList();
    }

    @Transactional(readOnly = true)
    public AccountSubscriptionDTO getCurrent(Long targetHotelId) {
        PropertySubscriptionEntitlementService.EntitlementView view = authorizedView(targetHotelId);
        return new AccountSubscriptionDTO(view.targetHotelId(), view.source(), view.platformAuthoritative(),
                view.planId(), view.planCode(), view.planName(), view.status(), view.effectiveFrom(),
                view.effectiveUntil(), view.lifetime(), publicReference(view), view.migrationBlocker());
    }

    @Transactional(readOnly = true)
    public Map<String, Integer> getFeatures(Long targetHotelId) {
        return Map.copyOf(authorizedView(targetHotelId).limits());
    }

    @Transactional(readOnly = true)
    public SubscriptionUsageDTO getUsage(Long targetHotelId) {
        PropertySubscriptionEntitlementService.EntitlementView view = authorizedView(targetHotelId);
        Map<String, Long> usage = usageRepository.snapshot(primaryOwnerId(targetHotelId), targetHotelId);
        Map<String, SubscriptionFeature> definitions = definitions(view.limits().keySet());
        List<SubscriptionEntitlementDTO> features = view.limits().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entitlement(entry, usage.getOrDefault(entry.getKey(), 0L), definitions.get(entry.getKey())))
                .toList();
        return new SubscriptionUsageDTO(view.targetHotelId(), view.source(), view.platformAuthoritative(),
                view.planCode(), view.status(), view.effectiveFrom(), view.effectiveUntil(), view.lifetime(),
                Map.copyOf(view.limits()), Map.copyOf(usage), features, view.migrationBlocker());
    }

    private PropertySubscriptionEntitlementService.EntitlementView authorizedView(Long targetHotelId) {
        // Billing reads remain available to assigned tenants even while a property is suspended.
        propertyAccessService.requireAssignedHotel(targetHotelId);
        return entitlementService.getCurrent(targetHotelId);
    }

    private String publicReference(PropertySubscriptionEntitlementService.EntitlementView view) {
        return view.platformAuthoritative() ? view.sourceReference() : null;
    }

    private Map<String, SubscriptionFeature> definitions(Set<String> codes) {
        if (codes.isEmpty()) return Map.of();
        return featureRepository.findByCodeIn(codes).stream()
                .collect(Collectors.toMap(SubscriptionFeature::getCode, Function.identity()));
    }

    private Long primaryOwnerId(Long targetHotelId) {
        return userPropertyRepository.findOwnerMappingsByHotelId(targetHotelId).stream()
                .filter(mapping -> "ACTIVE".equals(mapping.getStatus()))
                .filter(mapping -> Boolean.TRUE.equals(mapping.getIsPrimaryOwner()))
                .map(UserProperty::getUser)
                .filter(java.util.Objects::nonNull)
                .map(user -> user.getId())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Assigned property has no active primary owner."));
    }

    private SubscriptionPlanDTO toPlan(SubscriptionPlan plan, Map<String, SubscriptionFeature> definitions) {
        List<SubscriptionFeatureDTO> features = safeFeatures(plan).stream()
                .sorted(Comparator.comparing(PlanFeature::getFeatureCode))
                .map(feature -> {
                    SubscriptionFeature definition = definitions.get(feature.getFeatureCode());
                    return new SubscriptionFeatureDTO(feature.getFeatureCode(),
                            definition == null ? null : definition.getNameVi(),
                            definition == null ? null : definition.getNameEn(),
                            definition == null ? null : definition.getValueType(), feature.getLimitValue());
                }).toList();
        return new SubscriptionPlanDTO(plan.getId(), plan.getCode(), plan.getNameVi(), plan.getNameEn(),
                plan.getBillingType(), plan.getPrice(), "VND", Boolean.TRUE.equals(plan.getIsLifetime()),
                plan.getStatus(), features);
    }

    private Set<PlanFeature> safeFeatures(SubscriptionPlan plan) {
        return plan.getFeatures() == null ? Set.of() : plan.getFeatures();
    }

    private SubscriptionEntitlementDTO entitlement(Map.Entry<String, Integer> entry, long usage,
                                                    SubscriptionFeature definition) {
        int limit = entry.getValue();
        boolean allowed = limit == -1 || usage < limit;
        return new SubscriptionEntitlementDTO(entry.getKey(),
                definition == null ? null : definition.getNameVi(),
                definition == null ? null : definition.getNameEn(), limit, usage, allowed);
    }
}
