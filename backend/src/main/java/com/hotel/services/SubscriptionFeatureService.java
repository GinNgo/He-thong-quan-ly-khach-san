package com.hotel.services;

import com.hotel.entities.AccountSubscription;
import com.hotel.entities.PlanFeature;
import com.hotel.repositories.AccountSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SubscriptionFeatureService {

    private final AccountSubscriptionRepository accountSubscriptionRepository;
    private final PropertySubscriptionEntitlementService propertyEntitlementService;

    @Transactional
    public Map<String, Integer> getActiveFeaturesForProperty(Long hotelId) {
        return propertyEntitlementService.getCurrent(hotelId).limits();
    }

    @Transactional
    public boolean hasFeatureForProperty(Long hotelId, String featureCode) {
        Integer limit = getActiveFeaturesForProperty(hotelId).get(featureCode);
        return limit != null && (limit == -1 || limit > 0);
    }

    @Transactional
    public void checkFeatureLimitForProperty(
            Long hotelId,
            String featureCode,
            long currentUsage,
            long addition) {
        if (currentUsage < 0 || addition < 0) {
            throw new IllegalArgumentException("Usage and addition must be non-negative.");
        }
        Map<String, Integer> limits = propertyEntitlementService.getCurrentForUpdate(hotelId).limits();
        Integer limit = limits.get(featureCode);
        if (limit == null || limit == 0 || limit < -1) {
            throw new IllegalStateException("Upgrade this property's subscription to use this feature.");
        }
        if (limit != -1 && currentUsage > limit - addition) {
            throw new IllegalStateException("This property has reached its subscription limit.");
        }
    }

    @Transactional
    public void requireFeatureForProperty(Long hotelId, String featureCode) {
        checkFeatureLimitForProperty(hotelId, featureCode, 0, 0);
    }

    @Transactional(readOnly = true)
    public Map<String, Integer> getActiveFeaturesForUser(Long userId) {
        List<AccountSubscription> activeSubscriptions =
                accountSubscriptionRepository.findEffectiveSubscriptionsByUserId(userId);

        Map<String, Integer> featureLimits = new HashMap<>();

        for (AccountSubscription subscription : activeSubscriptions) {
            if (subscription.getPlan() == null) {
                continue;
            }
            Set<PlanFeature> features = subscription.getPlan().getFeatures();
            if (features == null) {
                continue;
            }
            for (PlanFeature feature : features) {
                String code = feature.getFeatureCode();
                if (code == null || code.isBlank()) {
                    continue;
                }
                int limit = feature.getLimitValue() == null ? 0 : feature.getLimitValue();
                featureLimits.merge(code, limit, this::higherLimit);
            }
        }

        return featureLimits;
    }

    @Transactional(readOnly = true)
    public boolean hasFeature(Long userId, String featureCode) {
        Integer limit = getActiveFeaturesForUser(userId).get(featureCode);
        return limit != null && (limit == -1 || limit > 0);
    }

    @Transactional(readOnly = true)
    public void checkFeatureLimit(Long userId, String featureCode, int currentUsage) {
        checkFeatureLimit(userId, featureCode, currentUsage, 1);
    }

    /**
     * Validates quota capacity before a mutation. Use an addition of zero for
     * a mutation that requires an active entitlement without increasing usage.
     */
    @Transactional(readOnly = true)
    public void checkFeatureLimit(Long userId, String featureCode, long currentUsage, long addition) {
        if (currentUsage < 0 || addition < 0) {
            throw new IllegalArgumentException("Usage and addition must be non-negative.");
        }
        Map<String, Integer> limits = getActiveFeaturesForUser(userId);
        if (!limits.containsKey(featureCode)) {
            throw new IllegalStateException("Bạn cần nâng cấp gói dịch vụ để sử dụng tính năng này.");
        }
        Integer limit = limits.get(featureCode);
        if (limit == null || limit == 0 || limit < -1) {
            throw new IllegalStateException("Bạn cần nâng cấp gói dịch vụ để sử dụng tính năng này.");
        }
        if (limit != -1 && currentUsage > limit - addition) {
            throw new IllegalStateException("Bạn đã đạt giới hạn của gói dịch vụ. Vui lòng nâng cấp để tiếp tục.");
        }
    }

    @Transactional(readOnly = true)
    public void requireFeature(Long userId, String featureCode) {
        checkFeatureLimit(userId, featureCode, 0, 0);
    }

    private int higherLimit(int current, int candidate) {
        if (current == -1 || candidate == -1) {
            return -1;
        }
        return Math.max(current, candidate);
    }
}
