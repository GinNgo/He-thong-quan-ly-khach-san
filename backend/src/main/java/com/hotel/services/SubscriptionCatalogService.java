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
import java.math.BigDecimal;
import java.util.Locale;
import java.util.HashSet;
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
    public List<SubscriptionPlanDTO> getAllPlansForAdministration() {
        return safeList(planRepository.findAll()).stream()
                .sorted(Comparator.comparing(SubscriptionPlan::getPrice)
                        .thenComparing(SubscriptionPlan::getCode))
                .map(this::toPlanDto)
                .toList();
    }

    @Transactional
    public SubscriptionPlanDTO createPlan(PlanCommand command) {
        validatePlan(command, null);
        SubscriptionPlan plan = new SubscriptionPlan();
        applyPlan(plan, command);
        plan.setStatus("ACTIVE");
        return toPlanDto(planRepository.save(plan));
    }

    @Transactional
    public SubscriptionPlanDTO updatePlan(Long id, PlanCommand command) {
        SubscriptionPlan plan = requirePlan(id);
        validatePlan(command, id);
        applyPlan(plan, command);
        return toPlanDto(planRepository.save(plan));
    }

    @Transactional
    public SubscriptionPlanDTO setPlanStatus(Long id, String status) {
        SubscriptionPlan plan = requirePlan(id);
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "INACTIVE").contains(normalized)) {
            throw new IllegalArgumentException("Trạng thái gói không hợp lệ.");
        }
        plan.setStatus(normalized);
        return toPlanDto(planRepository.save(plan));
    }

    private SubscriptionPlan requirePlan(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy gói dịch vụ."));
    }

    private void validatePlan(PlanCommand command, Long currentId) {
        if (command == null) throw new IllegalArgumentException("Thiếu thông tin gói dịch vụ.");
        if (command.code() == null || !command.code().trim().matches("[A-Za-z0-9_-]{2,40}")) {
            throw new IllegalArgumentException("Mã gói phải có 2-40 ký tự chữ, số, gạch ngang hoặc gạch dưới.");
        }
        if (command.nameVi() == null || command.nameVi().isBlank()) {
            throw new IllegalArgumentException("Tên gói tiếng Việt là bắt buộc.");
        }
        String billingType = normalizeBillingType(command.billingType());
        if (command.price() == null || command.price().signum() < 0) {
            throw new IllegalArgumentException("Giá gói không được âm.");
        }
        boolean duplicate = currentId == null
                ? planRepository.existsByCodeIgnoreCase(command.code().trim())
                : planRepository.existsByCodeIgnoreCaseAndIdNot(command.code().trim(), currentId);
        if (duplicate) throw new IllegalArgumentException("Mã gói đã tồn tại.");
        if ("ONCE".equals(billingType) && !Boolean.TRUE.equals(command.isLifetime())) {
            throw new IllegalArgumentException("Gói thanh toán một lần phải là gói vĩnh viễn.");
        }
        Set<String> requestedFeatureCodes = command.features() == null ? Set.of() : command.features().stream()
                .map(FeatureLimitCommand::code)
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (requestedFeatureCodes.size() != (command.features() == null ? 0 : command.features().size())) {
            throw new IllegalArgumentException("Danh sách quyền lợi có mã trống hoặc bị trùng.");
        }
        if (featureRepository.findByCodeIn(requestedFeatureCodes).size() != requestedFeatureCodes.size()) {
            throw new IllegalArgumentException("Danh sách quyền lợi chứa mã không tồn tại.");
        }
        if (command.features() != null && command.features().stream()
                .anyMatch(feature -> feature.limit() == null || feature.limit() < -1)) {
            throw new IllegalArgumentException("Hạn mức quyền lợi phải từ 0 trở lên hoặc -1 là không giới hạn.");
        }
    }

    private void applyPlan(SubscriptionPlan plan, PlanCommand command) {
        String billingType = normalizeBillingType(command.billingType());
        plan.setCode(command.code().trim().toUpperCase(Locale.ROOT));
        plan.setNameVi(command.nameVi().trim());
        plan.setNameEn(command.nameEn() == null ? "" : command.nameEn().trim());
        plan.setBillingType(billingType);
        plan.setPrice(command.price());
        plan.setIsLifetime("ONCE".equals(billingType) || Boolean.TRUE.equals(command.isLifetime()));
        Set<PlanFeature> features = plan.getFeatures();
        if (features == null) {
            features = new HashSet<>();
            plan.setFeatures(features);
        } else {
            features.clear();
        }
        if (command.features() != null) {
            for (FeatureLimitCommand requested : command.features()) {
                PlanFeature feature = new PlanFeature();
                feature.setPlan(plan);
                feature.setFeatureCode(requested.code().trim().toUpperCase(Locale.ROOT));
                feature.setLimitValue(requested.limit());
                features.add(feature);
            }
        }
    }

    private String normalizeBillingType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("MONTHLY", "YEARLY", "ONCE").contains(normalized)) {
            throw new IllegalArgumentException("Chu kỳ gói không hợp lệ.");
        }
        return normalized;
    }

    public record PlanCommand(
            String code,
            String nameVi,
            String nameEn,
            String billingType,
            BigDecimal price,
            Boolean isLifetime,
            List<FeatureLimitCommand> features) {
    }

    public record FeatureLimitCommand(String code, Integer limit) {
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
