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
        Map<String, Integer> limits = getActiveFeaturesForUser(userId);
        if (!limits.containsKey(featureCode)) {
            throw new RuntimeException("Bạn cần nâng cấp gói dịch vụ để sử dụng tính năng này.");
        }
        Integer limit = limits.get(featureCode);
        if (limit == null || limit == 0 || limit < -1) {
            throw new RuntimeException("Bạn cần nâng cấp gói dịch vụ để sử dụng tính năng này.");
        }
        if (limit != -1 && currentUsage >= limit) {
            throw new RuntimeException("Bạn đã đạt giới hạn của gói dịch vụ. Vui lòng nâng cấp để tiếp tục.");
        }
    }

    private int higherLimit(int current, int candidate) {
        if (current == -1 || candidate == -1) {
            return -1;
        }
        return Math.max(current, candidate);
    }
}
