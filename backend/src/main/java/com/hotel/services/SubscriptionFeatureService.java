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
}
