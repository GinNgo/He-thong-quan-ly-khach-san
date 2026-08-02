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

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionFeatureServiceTest {

    @Mock
    private AccountSubscriptionRepository accountSubscriptionRepository;

    @Mock
    private PropertySubscriptionEntitlementService propertyEntitlementService;

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
        when(accountSubscriptionRepository.findEffectiveSubscriptionsByUserId(1L)).thenReturn(List.of());

        Map<String, Integer> features = subscriptionFeatureService.getActiveFeaturesForUser(1L);

        assertTrue(features.isEmpty());
    }

    @Test
    void getActiveFeaturesForUser_WithBasicSubscription_ShouldReturnBasicLimits() {
        when(accountSubscriptionRepository.findEffectiveSubscriptionsByUserId(1L))
                .thenReturn(List.of(basicSubscription));

        Map<String, Integer> features = subscriptionFeatureService.getActiveFeaturesForUser(1L);

        assertEquals(2, features.size());
        assertEquals(1, features.get("MAX_PROPERTIES"));
        assertEquals(0, features.get("ADVANCED_REPORTS"));
    }

    @Test
    void getActiveFeaturesForUser_WithMultipleSubscriptions_ShouldReturnMaxLimits() {
        when(accountSubscriptionRepository.findEffectiveSubscriptionsByUserId(1L))
                .thenReturn(List.of(basicSubscription, premiumSubscription));

        Map<String, Integer> features = subscriptionFeatureService.getActiveFeaturesForUser(1L);

        assertEquals(2, features.size());
        assertEquals(-1, features.get("MAX_PROPERTIES")); // Unlimited (-1) > 1
        assertEquals(1, features.get("ADVANCED_REPORTS")); // 1 > 0
    }

    @Test
    void checkFeatureLimit_WithNoEffectiveSubscription_ShouldRequireUpgrade() {
        when(accountSubscriptionRepository.findEffectiveSubscriptionsByUserId(1L)).thenReturn(List.of());

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> subscriptionFeatureService.checkFeatureLimit(1L, "MAX_PROPERTIES", 0));

        assertTrue(error.getMessage().contains("nâng cấp"));
    }

    @Test
    void checkFeatureLimit_WhenUsageReachedLimit_ShouldRejectMutation() {
        when(accountSubscriptionRepository.findEffectiveSubscriptionsByUserId(1L))
                .thenReturn(List.of(basicSubscription));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> subscriptionFeatureService.checkFeatureLimit(1L, "MAX_PROPERTIES", 1));

        assertTrue(error.getMessage().contains("đạt giới hạn"));
    }

    @Test
    void checkFeatureLimit_WithUnlimitedPlan_ShouldAllowMutation() {
        when(accountSubscriptionRepository.findEffectiveSubscriptionsByUserId(1L))
                .thenReturn(List.of(premiumSubscription));

        assertDoesNotThrow(() -> subscriptionFeatureService.checkFeatureLimit(1L, "MAX_PROPERTIES", 10_000));
    }

    @Test
    void checkFeatureLimit_WhenBulkAdditionExceedsRemainingCapacity_ShouldRejectMutation() {
        when(accountSubscriptionRepository.findEffectiveSubscriptionsByUserId(1L))
                .thenReturn(List.of(basicSubscription));

        assertThrows(RuntimeException.class,
                () -> subscriptionFeatureService.checkFeatureLimit(1L, "MAX_PROPERTIES", 0L, 2L));
    }

    @Test
    void requireFeature_WithEffectiveEntitlement_ShouldAllowNonGrowingMutation() {
        when(accountSubscriptionRepository.findEffectiveSubscriptionsByUserId(1L))
                .thenReturn(List.of(basicSubscription));

        assertDoesNotThrow(() -> subscriptionFeatureService.requireFeature(1L, "MAX_PROPERTIES"));
    }

    @Test
    void propertyLimit_DoesNotAllowPremiumPropertyToUnlockBasicProperty() {
        when(propertyEntitlementService.getCurrentForUpdate(10L)).thenReturn(
                entitlement(10L, "BASIC", Map.of("MAX_ROOMS", 5)));
        when(propertyEntitlementService.getCurrentForUpdate(20L)).thenReturn(
                entitlement(20L, "PRO", Map.of("MAX_ROOMS", 50)));

        assertThrows(IllegalStateException.class,
                () -> subscriptionFeatureService.checkFeatureLimitForProperty(10L, "MAX_ROOMS", 5, 1));
        assertDoesNotThrow(
                () -> subscriptionFeatureService.checkFeatureLimitForProperty(20L, "MAX_ROOMS", 5, 1));
    }

    @Test
    void propertyLimit_UsesTheLockedPropertyReadModelForEveryMutation() {
        when(propertyEntitlementService.getCurrentForUpdate(10L)).thenReturn(
                entitlement(10L, "PRO", Map.of("MAX_IMAGES", 10)));

        assertDoesNotThrow(
                () -> subscriptionFeatureService.checkFeatureLimitForProperty(10L, "MAX_IMAGES", 8, 2));

        org.mockito.Mockito.verify(propertyEntitlementService).getCurrentForUpdate(10L);
        org.mockito.Mockito.verifyNoInteractions(accountSubscriptionRepository);
    }

    @Test
    void concurrentPropertyChecksKeepPremiumAndBasicLimitsIsolated() throws Exception {
        when(propertyEntitlementService.getCurrentForUpdate(10L)).thenReturn(
                entitlement(10L, "BASIC", Map.of("MAX_ROOMS", 5)));
        when(propertyEntitlementService.getCurrentForUpdate(20L)).thenReturn(
                entitlement(20L, "PRO", Map.of("MAX_ROOMS", 50)));
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<?> basic = executor.submit(
                    () -> subscriptionFeatureService.checkFeatureLimitForProperty(10L, "MAX_ROOMS", 5, 1));
            java.util.concurrent.Future<?> premium = executor.submit(
                    () -> subscriptionFeatureService.checkFeatureLimitForProperty(20L, "MAX_ROOMS", 5, 1));

            assertThrows(java.util.concurrent.ExecutionException.class, basic::get);
            assertDoesNotThrow(() -> { premium.get(); });
        } finally {
            executor.shutdownNow();
        }
    }

    private PropertySubscriptionEntitlementService.EntitlementView entitlement(
            Long hotelId,
            String planCode,
            Map<String, Integer> limits) {
        return new PropertySubscriptionEntitlementService.EntitlementView(
                hotelId, "PLATFORM", true, 1L, planCode, planCode, "ACTIVE",
                java.time.LocalDateTime.now().minusDays(1), null, true, limits, "contract-1", null);
    }
}
