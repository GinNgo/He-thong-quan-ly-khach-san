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
        List<AccountSubscription> activeSubscriptions = accountSubscriptionRepository.findByUserIdAndStatus(userId, "ACTIVE");
        
        Map<String, Integer> featureLimits = new HashMap<>();
        
        for (AccountSubscription subscription : activeSubscriptions) {
            Set<PlanFeature> features = subscription.getPlan().getFeatures();
            for (PlanFeature feature : features) {
                String code = feature.getFeatureCode();
                Integer limit = feature.getLimitValue();
                
                // If the user has multiple active subscriptions (rare, but possible), we take the max limit
                if (featureLimits.containsKey(code)) {
                    Integer currentLimit = featureLimits.get(code);
                    if (currentLimit != -1) {
                        if (limit == -1 || (limit != null && limit > currentLimit)) {
                            featureLimits.put(code, limit);
                        }
                    }
                } else {
                    featureLimits.put(code, limit);
                }
            }
        }
        
        return featureLimits;
    }

    @Transactional(readOnly = true)
    public boolean hasFeature(Long userId, String featureCode) {
        Map<String, Integer> limits = getActiveFeaturesForUser(userId);
        return limits.containsKey(featureCode) && (limits.get(featureCode) == -1 || limits.get(featureCode) > 0);
    }

    @Transactional(readOnly = true)
    public void checkFeatureLimit(Long userId, String featureCode, int currentUsage) {
        Map<String, Integer> limits = getActiveFeaturesForUser(userId);
        if (!limits.containsKey(featureCode)) {
            throw new RuntimeException("Bạn cần nâng cấp gói dịch vụ để sử dụng tính năng này.");
        }
        Integer limit = limits.get(featureCode);
        if (limit != -1 && currentUsage >= limit) {
            throw new RuntimeException("Bạn đã đạt giới hạn của gói dịch vụ. Vui lòng nâng cấp để tiếp tục.");
        }
    }
}
