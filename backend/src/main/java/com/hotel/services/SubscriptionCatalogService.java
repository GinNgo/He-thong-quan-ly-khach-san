package com.hotel.services;

import com.hotel.dtos.AccountSubscriptionDTO;
import com.hotel.dtos.SubscriptionEntitlementDTO;
import com.hotel.dtos.SubscriptionFeatureDTO;
import com.hotel.dtos.SubscriptionPlanDTO;
import com.hotel.dtos.SubscriptionUsageDTO;
import com.hotel.entities.AccountSubscription;
import com.hotel.entities.PlanFeature;
import com.hotel.entities.SubscriptionFeature;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.repositories.AccountSubscriptionRepository;
import com.hotel.repositories.PropertyImageRepository;
import com.hotel.repositories.RoomImageRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.RoomTypeImageRepository;
import com.hotel.repositories.RoomTypeRepository;
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
    private final AccountSubscriptionRepository accountSubscriptionRepository;
    private final SubscriptionFeatureRepository featureRepository;
    private final SubscriptionFeatureService featureService;
    private final UserPropertyRepository userPropertyRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final RoomTypeImageRepository roomTypeImageRepository;
    private final RoomImageRepository roomImageRepository;

    @Transactional(readOnly = true)
    public List<SubscriptionPlanDTO> getActivePlans() {
        return safeList(planRepository.findByStatusOrderByPriceAsc("ACTIVE"))
                .stream()
                .map(this::toPlanDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AccountSubscriptionDTO> getSubscriptions(Long userId) {
        return safeList(accountSubscriptionRepository.findByUserId(userId))
                .stream()
                .sorted(Comparator.comparing(AccountSubscription::getStartAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toSubscriptionDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionUsageDTO getUsage(Long userId) {
        List<AccountSubscription> effective = safeList(
                accountSubscriptionRepository.findEffectiveSubscriptionsByUserId(userId));
        AccountSubscription current = effective.isEmpty() ? null : effective.get(0);
        if (current == null) {
            current = accountSubscriptionRepository.findFirstByUserIdOrderByStartAtDesc(userId).orElse(null);
        }

        Map<String, Integer> limits = new LinkedHashMap<>(featureService.getActiveFeaturesForUser(userId));
        Map<String, Long> usage = calculateUsage(userId);
        Map<String, SubscriptionFeature> definitions = featureDefinitions(limits.keySet());
        List<SubscriptionEntitlementDTO> entitlements = limits.keySet().stream()
                .sorted()
                .map(code -> {
                    SubscriptionFeature definition = definitions.get(code);
                    int limit = limits.getOrDefault(code, 0);
                    long currentUsage = usage.getOrDefault(code, 0L);
                    return new SubscriptionEntitlementDTO(
                            code,
                            definition == null ? code : definition.getNameVi(),
                            definition == null ? code : definition.getNameEn(),
                            limit,
                            currentUsage,
                            limit == -1 || (limit > 0 && currentUsage < limit));
                })
                .toList();

        return new SubscriptionUsageDTO(
                current == null || current.getPlan() == null ? "NO_PLAN" : current.getPlan().getCode(),
                current == null ? "NONE" : current.getStatus(),
                current == null ? null : current.getStartAt(),
                current == null ? null : current.getEndAt(),
                current != null && Boolean.TRUE.equals(current.getIsLifetime()),
                limits,
                usage,
                entitlements);
    }

    private SubscriptionPlanDTO toPlanDto(SubscriptionPlan plan) {
        List<PlanFeature> planFeatures = plan.getFeatures() == null ? List.of() : plan.getFeatures().stream()
                .sorted(Comparator.comparing(PlanFeature::getFeatureCode, Comparator.nullsLast(String::compareTo)))
                .toList();
        Map<String, SubscriptionFeature> definitions = featureDefinitions(planFeatures.stream()
                .map(PlanFeature::getFeatureCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet()));
        List<SubscriptionFeatureDTO> features = planFeatures.stream()
                .filter(feature -> feature.getFeatureCode() != null && !feature.getFeatureCode().isBlank())
                .map(feature -> {
                    SubscriptionFeature definition = definitions.get(feature.getFeatureCode());
                    return new SubscriptionFeatureDTO(
                            feature.getFeatureCode(),
                            definition == null ? feature.getFeatureCode() : definition.getNameVi(),
                            definition == null ? feature.getFeatureCode() : definition.getNameEn(),
                            definition == null ? "NUMERIC" : definition.getValueType(),
                            feature.getLimitValue() == null ? 0 : feature.getLimitValue());
                })
                .toList();
        return new SubscriptionPlanDTO(
                plan.getId(), plan.getCode(), plan.getNameVi(), plan.getNameEn(), plan.getBillingType(),
                plan.getPrice(), "VND", plan.getIsLifetime(), plan.getStatus(), features);
    }

    private AccountSubscriptionDTO toSubscriptionDto(AccountSubscription subscription) {
        return new AccountSubscriptionDTO(
                subscription.getId(),
                subscription.getPlan() == null ? null : toPlanDto(subscription.getPlan()),
                subscription.getStartAt(), subscription.getEndAt(), subscription.getIsLifetime(), subscription.getStatus());
    }

    private Map<String, SubscriptionFeature> featureDefinitions(Set<String> codes) {
        if (codes == null || codes.isEmpty()) return Map.of();
        return safeList(featureRepository.findByCodeIn(codes)).stream()
                .collect(Collectors.toMap(SubscriptionFeature::getCode, Function.identity(), (left, right) -> left));
    }

    private Map<String, Long> calculateUsage(Long userId) {
        Set<Long> hotelIds = safeList(userPropertyRepository.findByUserId(userId)).stream()
                .filter(mapping -> "ACTIVE".equals(mapping.getStatus()) && mapping.getHotel() != null)
                .map(mapping -> mapping.getHotel().getId())
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<String, Long> usage = new LinkedHashMap<>();
        usage.put("MAX_PROPERTIES", userPropertyRepository.countActiveOwnedPropertiesByUserId(userId));
        usage.put("MAX_ROOM_TYPES", hotelIds.isEmpty() ? 0L : roomTypeRepository.countByHotelIdIn(hotelIds));
        usage.put("MAX_ROOMS", hotelIds.isEmpty() ? 0L : roomRepository.countByHotelIdIn(hotelIds));
        usage.put("MAX_IMAGES", hotelIds.stream().mapToLong(id -> propertyImageRepository.countByHotelId(id)
                + roomTypeImageRepository.countByRoomTypeHotelId(id)
                + roomImageRepository.countByRoomHotelId(id)).sum());
        usage.put("MAX_STAFF", hotelIds.isEmpty() ? 0L : userPropertyRepository.countActiveStaffByHotelIds(hotelIds));
        return usage;
    }

    private <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }
}
