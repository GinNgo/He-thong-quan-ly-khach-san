package com.hotel.services;

import com.hotel.dtos.SubscriptionPlanDTO;
import com.hotel.dtos.SubscriptionUsageDTO;
import com.hotel.entities.AccountSubscription;
import com.hotel.entities.Hotel;
import com.hotel.entities.PlanFeature;
import com.hotel.entities.SubscriptionFeature;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.entities.UserProperty;
import com.hotel.repositories.AccountSubscriptionRepository;
import com.hotel.repositories.PropertyImageRepository;
import com.hotel.repositories.RoomImageRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.RoomTypeImageRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.repositories.SubscriptionFeatureRepository;
import com.hotel.repositories.SubscriptionPlanRepository;
import com.hotel.repositories.UserPropertyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionCatalogServiceTest {
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private AccountSubscriptionRepository accountSubscriptionRepository;
    @Mock private SubscriptionFeatureRepository featureRepository;
    @Mock private SubscriptionFeatureService featureService;
    @Mock private UserPropertyRepository userPropertyRepository;
    @Mock private RoomTypeRepository roomTypeRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private PropertyImageRepository propertyImageRepository;
    @Mock private RoomTypeImageRepository roomTypeImageRepository;
    @Mock private RoomImageRepository roomImageRepository;

    @InjectMocks
    private SubscriptionCatalogService catalogService;

    @Test
    void getActivePlans_UsesCanonicalFeatureCatalog() {
        SubscriptionPlan plan = plan("STANDARD", 3);
        when(planRepository.findByStatusOrderByPriceAsc("ACTIVE")).thenReturn(List.of(plan));
        when(featureRepository.findByCodeIn(anyCollection())).thenReturn(List.of(featureDefinition()));

        List<SubscriptionPlanDTO> result = catalogService.getActivePlans();

        assertEquals(1, result.size());
        assertEquals("VND", result.get(0).getCurrency());
        assertEquals("Properties", result.get(0).getFeatures().get(0).getNameEn());
        assertEquals(3, result.get(0).getFeatures().get(0).getLimit());
    }

    @Test
    void getUsage_MergesLimitsWithCurrentPropertyUsage() {
        SubscriptionPlan plan = plan("STANDARD", 3);
        AccountSubscription subscription = new AccountSubscription();
        subscription.setPlan(plan);
        subscription.setStatus("ACTIVE");
        subscription.setStartAt(LocalDateTime.now().minusDays(1));
        subscription.setEndAt(LocalDateTime.now().plusDays(30));

        Hotel hotel = new Hotel();
        hotel.setId(9L);
        UserProperty mapping = new UserProperty();
        mapping.setHotel(hotel);
        mapping.setStatus("ACTIVE");

        when(accountSubscriptionRepository.findEffectiveSubscriptionsByUserId(4L)).thenReturn(List.of(subscription));
        when(featureService.getActiveFeaturesForUser(4L)).thenReturn(Map.of("MAX_PROPERTIES", 3, "MAX_ROOMS", -1));
        when(featureRepository.findByCodeIn(anyCollection())).thenReturn(List.of(featureDefinition()));
        when(userPropertyRepository.findByUserId(4L)).thenReturn(List.of(mapping));
        when(userPropertyRepository.countActiveOwnedPropertiesByUserId(4L)).thenReturn(1L);
        when(roomTypeRepository.countByHotelIdIn(Set.of(9L))).thenReturn(2L);
        when(roomRepository.countByHotelIdIn(Set.of(9L))).thenReturn(4L);
        when(propertyImageRepository.countByHotelId(9L)).thenReturn(3L);
        when(roomTypeImageRepository.countByRoomTypeHotelId(9L)).thenReturn(5L);
        when(roomImageRepository.countByRoomHotelId(9L)).thenReturn(2L);
        when(userPropertyRepository.countActiveStaffByHotelIds(Set.of(9L))).thenReturn(1L);

        SubscriptionUsageDTO result = catalogService.getUsage(4L);

        assertEquals("STANDARD", result.getPlanCode());
        assertEquals(1L, result.getUsage().get("MAX_PROPERTIES"));
        assertEquals(4L, result.getUsage().get("MAX_ROOMS"));
        assertEquals(10L, result.getUsage().get("MAX_IMAGES"));
        assertTrue(result.getFeatures().stream().allMatch(feature -> feature.isAllowed()));
    }

    private SubscriptionPlan plan(String code, int propertyLimit) {
        PlanFeature planFeature = new PlanFeature();
        planFeature.setFeatureCode("MAX_PROPERTIES");
        planFeature.setLimitValue(propertyLimit);
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(1L);
        plan.setCode(code);
        plan.setNameVi("Standard");
        plan.setNameEn("Standard");
        plan.setBillingType("YEARLY");
        plan.setPrice(new BigDecimal("6000000"));
        plan.setIsLifetime(false);
        plan.setStatus("ACTIVE");
        plan.setFeatures(Set.of(planFeature));
        return plan;
    }

    private SubscriptionFeature featureDefinition() {
        SubscriptionFeature feature = new SubscriptionFeature();
        feature.setCode("MAX_PROPERTIES");
        feature.setNameVi("So co so");
        feature.setNameEn("Properties");
        feature.setValueType("NUMERIC");
        return feature;
    }
}
