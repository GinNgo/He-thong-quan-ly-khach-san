package com.hotel.services;

import com.hotel.entities.AccountSubscription;
import com.hotel.entities.PlanFeature;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.repositories.AccountSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionFeatureServiceTest {

    @Mock
    private AccountSubscriptionRepository accountSubscriptionRepository;

    @InjectMocks
    private SubscriptionFeatureService subscriptionFeatureService;

    private AccountSubscription basicSubscription;
    private AccountSubscription premiumSubscription;

    @BeforeEach
    void setUp() {
        SubscriptionPlan basicPlan = new SubscriptionPlan();
        PlanFeature basicFeature1 = new PlanFeature();
        basicFeature1.setFeatureCode("MAX_PROPERTIES");
        basicFeature1.setLimitValue(1);
        
        PlanFeature basicFeature2 = new PlanFeature();
        basicFeature2.setFeatureCode("ADVANCED_REPORTS");
        basicFeature2.setLimitValue(0);

        basicPlan.setFeatures(Set.of(basicFeature1, basicFeature2));
        
        basicSubscription = new AccountSubscription();
        basicSubscription.setPlan(basicPlan);

        SubscriptionPlan premiumPlan = new SubscriptionPlan();
        PlanFeature premiumFeature1 = new PlanFeature();
        premiumFeature1.setFeatureCode("MAX_PROPERTIES");
        premiumFeature1.setLimitValue(-1); // Unlimited
        
        PlanFeature premiumFeature2 = new PlanFeature();
        premiumFeature2.setFeatureCode("ADVANCED_REPORTS");
        premiumFeature2.setLimitValue(1);

        premiumPlan.setFeatures(Set.of(premiumFeature1, premiumFeature2));
        
        premiumSubscription = new AccountSubscription();
        premiumSubscription.setPlan(premiumPlan);
    }

    @Test
    void getActiveFeaturesForUser_WithNoActiveSubscriptions_ShouldReturnEmptyMap() {
        when(accountSubscriptionRepository.findByUserIdAndStatus(1L, "ACTIVE")).thenReturn(List.of());

        Map<String, Integer> features = subscriptionFeatureService.getActiveFeaturesForUser(1L);

        assertTrue(features.isEmpty());
    }

    @Test
    void getActiveFeaturesForUser_WithBasicSubscription_ShouldReturnBasicLimits() {
        when(accountSubscriptionRepository.findByUserIdAndStatus(1L, "ACTIVE")).thenReturn(List.of(basicSubscription));

        Map<String, Integer> features = subscriptionFeatureService.getActiveFeaturesForUser(1L);

        assertEquals(2, features.size());
        assertEquals(1, features.get("MAX_PROPERTIES"));
        assertEquals(0, features.get("ADVANCED_REPORTS"));
    }

    @Test
    void getActiveFeaturesForUser_WithMultipleSubscriptions_ShouldReturnMaxLimits() {
        when(accountSubscriptionRepository.findByUserIdAndStatus(1L, "ACTIVE"))
                .thenReturn(Arrays.asList(basicSubscription, premiumSubscription));

        Map<String, Integer> features = subscriptionFeatureService.getActiveFeaturesForUser(1L);

        assertEquals(2, features.size());
        assertEquals(-1, features.get("MAX_PROPERTIES")); // Unlimited (-1) > 1
        assertEquals(1, features.get("ADVANCED_REPORTS")); // 1 > 0
    }
}
