package com.hotel.services;

import com.hotel.dtos.AccountSubscriptionDTO;
import com.hotel.dtos.SubscriptionUsageDTO;
import com.hotel.entities.PlanFeature;
import com.hotel.entities.Hotel;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.platformbilling.subscription.PlatformSubscriptionUsageRepository;
import com.hotel.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionCatalogServiceTest {
    @Mock SubscriptionPlanRepository planRepository;
    @Mock SubscriptionFeatureRepository featureRepository;
    @Mock PropertySubscriptionEntitlementService entitlementService;
    @Mock PropertyAccessService propertyAccessService;
    @Mock PlatformSubscriptionUsageRepository usageRepository;
    @Mock UserPropertyRepository userPropertyRepository;
    SubscriptionCatalogService service;

    @BeforeEach void setUp() {
        service = new SubscriptionCatalogService(planRepository, featureRepository, entitlementService,
                propertyAccessService, usageRepository, userPropertyRepository);
    }

    @Test void catalogReturnsDtoOnlyActivePlans() {
        PlanFeature feature = new PlanFeature(); feature.setFeatureCode("MAX_ROOMS"); feature.setLimitValue(10);
        SubscriptionPlan plan = new SubscriptionPlan(); plan.setId(1L); plan.setCode("STANDARD");
        plan.setNameVi("Tieu chuan"); plan.setNameEn("Standard"); plan.setBillingType("YEARLY");
        plan.setPrice(new BigDecimal("6000000")); plan.setStatus("ACTIVE"); plan.setFeatures(Set.of(feature));
        when(planRepository.findByStatusOrderByPriceAsc("ACTIVE")).thenReturn(List.of(plan));
        when(featureRepository.findByCodeIn(Set.of("MAX_ROOMS"))).thenReturn(List.of());
        var result = service.getActivePlans();
        assertEquals("STANDARD", result.get(0).code());
        assertEquals("VND", result.get(0).currency());
        assertEquals(10, result.get(0).features().get(0).limit());
    }

    @Test void currentUsesHotelScopedAuthoritativeViewAndHidesLegacyIds() {
        var view = view("LEGACY_PROJECTION", false, "41,42", null);
        when(entitlementService.getCurrent(9L)).thenReturn(view);
        AccountSubscriptionDTO result = service.getCurrent(9L);
        verify(propertyAccessService).requireAssignedHotel(9L);
        assertNull(result.sourceReference());
        assertEquals(9L, result.targetHotelId());
    }

    @Test void terminalPlatformStateIsReturnedWithoutLegacyFallback() {
        when(entitlementService.getCurrent(9L)).thenReturn(view("PLATFORM", true, "contract-public", null));
        AccountSubscriptionDTO result = service.getCurrent(9L);
        assertTrue(result.platformAuthoritative());
        assertEquals("EXPIRED", result.status());
        assertEquals("contract-public", result.sourceReference());
    }

    @Test void ambiguousLegacyBlockerRemainsTruthful() {
        when(entitlementService.getCurrent(9L)).thenReturn(view("NONE", false, null, "AMBIGUOUS_LEGACY_OWNER"));
        AccountSubscriptionDTO result = service.getCurrent(9L);
        assertEquals("AMBIGUOUS_LEGACY_OWNER", result.migrationBlocker());
        assertEquals("NONE", result.source());
    }

    @Test void deniedPropertyStopsBeforeEntitlementAndUsageReads() {
        doThrow(new ResourceNotFoundException("not found"))
                .when(propertyAccessService).requireAssignedHotel(8L);
        assertThrows(ResourceNotFoundException.class, () -> service.getUsage(8L));
        verifyNoInteractions(entitlementService, usageRepository, userPropertyRepository);
    }

    @Test void assignedSuspendedPropertyCanStillReadHistoricalBilling() {
        Hotel suspended = new Hotel(); suspended.setId(9L); suspended.setOperationStatus("SUSPENDED");
        when(propertyAccessService.requireAssignedHotel(9L)).thenReturn(suspended);
        when(entitlementService.getCurrent(9L)).thenReturn(view("PLATFORM", true, "contract-public", null));
        assertEquals("EXPIRED", service.getCurrent(9L).status());
        verify(entitlementService).getCurrent(9L);
    }

    @Test void usageIsSelectedHotelOnlyAndUsesAuthoritativeLimits() {
        when(entitlementService.getCurrent(9L)).thenReturn(new PropertySubscriptionEntitlementService.EntitlementView(
                9L, "PLATFORM", true, 3L, "STANDARD", "Standard", "ACTIVE", null, null, false,
                Map.of("MAX_PROPERTIES", 1, "MAX_ROOMS", 5, "MAX_IMAGES", 20, "MAX_STAFF", 3), "ref", null));
        User owner = new User(); owner.setId(4L);
        UserProperty primary = new UserProperty(); primary.setUser(owner); primary.setStatus("ACTIVE");
        primary.setIsPrimaryOwner(true);
        when(userPropertyRepository.findOwnerMappingsByHotelId(9L)).thenReturn(List.of(primary));
        when(usageRepository.snapshot(4L, 9L)).thenReturn(Map.of("MAX_PROPERTIES", 2L,
                "MAX_ROOM_TYPES", 2L, "MAX_ROOMS", 4L, "MAX_IMAGES", 10L, "MAX_STAFF", 2L));
        when(featureRepository.findByCodeIn(any())).thenReturn(List.of());
        SubscriptionUsageDTO result = service.getUsage(9L);
        assertEquals(2L, result.usage().get("MAX_PROPERTIES"));
        assertEquals(4L, result.usage().get("MAX_ROOMS"));
        assertEquals(10L, result.usage().get("MAX_IMAGES"));
        assertFalse(result.features().stream().filter(item -> "MAX_PROPERTIES".equals(item.code()))
                .findFirst().orElseThrow().allowed());
        assertTrue(result.features().stream().filter(item -> "MAX_ROOMS".equals(item.code()))
                .findFirst().orElseThrow().allowed());
        verify(usageRepository).snapshot(4L, 9L);
    }

    private PropertySubscriptionEntitlementService.EntitlementView view(String source, boolean authoritative,
                                                                         String reference, String blocker) {
        return new PropertySubscriptionEntitlementService.EntitlementView(9L, source, authoritative, 3L,
                "STANDARD", "Standard", "EXPIRED", LocalDateTime.now().minusYears(1), LocalDateTime.now(),
                false, Map.of("MAX_ROOMS", 5), reference, blocker);
    }
}
