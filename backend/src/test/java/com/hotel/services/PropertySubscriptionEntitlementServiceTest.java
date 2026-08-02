package com.hotel.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.AccountSubscription;
import com.hotel.entities.Hotel;
import com.hotel.entities.PlanFeature;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.platformbilling.subscription.LegacySubscriptionEntitlementProjection;
import com.hotel.platformbilling.subscription.LegacySubscriptionEntitlementProjectionRepository;
import com.hotel.platformbilling.subscription.SubscriptionEntitlement;
import com.hotel.platformbilling.subscription.SubscriptionEntitlementRepository;
import com.hotel.repositories.AccountSubscriptionRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PropertySubscriptionEntitlementServiceTest {

    @Mock private SubscriptionEntitlementRepository platformRepository;
    @Mock private LegacySubscriptionEntitlementProjectionRepository legacyRepository;
    @Mock private UserPropertyRepository userPropertyRepository;
    @Mock private AccountSubscriptionRepository accountSubscriptionRepository;
    @Mock private UserRepository userRepository;
    @Mock private FinancialAuditService auditService;

    private PropertySubscriptionEntitlementService service;

    @BeforeEach
    void setUp() {
        service = new PropertySubscriptionEntitlementService(
                platformRepository, legacyRepository, userPropertyRepository,
                accountSubscriptionRepository, userRepository, auditService, new ObjectMapper());
    }

    @Test
    void platformEntitlementIsTheOnlyReadSourceWhenPresent() {
        Hotel hotel = hotel(42L);
        SubscriptionPlan plan = plan(7L, "PRO");
        SubscriptionEntitlement entitlement = mock(SubscriptionEntitlement.class);
        when(entitlement.getTargetHotel()).thenReturn(hotel);
        when(entitlement.getPlan()).thenReturn(plan);
        when(entitlement.getContract()).thenReturn(null);
        when(entitlement.getFeatureSnapshotJson()).thenReturn(
                "{\"planCode\":\"PRO\",\"features\":[{\"code\":\"MAX_ROOMS\",\"limit\":50}]}" );
        when(entitlement.getStatus()).thenReturn(SubscriptionEntitlement.Status.ACTIVE);
        when(entitlement.getEffectiveFrom()).thenReturn(LocalDateTime.now().minusDays(1));
        when(entitlement.isLifetime()).thenReturn(true);
        when(platformRepository.findByTargetHotelId(42L)).thenReturn(Optional.of(entitlement));

        var view = service.getCurrent(42L);

        assertEquals("PLATFORM", view.source());
        assertTrue(view.platformAuthoritative());
        assertEquals(50, view.limits().get("MAX_ROOMS"));
        verify(accountSubscriptionRepository, never()).findEffectiveSubscriptionsByUserId(any());
        verify(legacyRepository, never()).saveAndFlush(any());
    }

    @Test
    void terminalPlatformEntitlementDoesNotFallBackToLegacyFeatures() {
        Hotel hotel = hotel(42L);
        SubscriptionPlan plan = plan(7L, "PRO");
        SubscriptionEntitlement entitlement = mock(SubscriptionEntitlement.class);
        when(entitlement.getTargetHotel()).thenReturn(hotel);
        when(entitlement.getPlan()).thenReturn(plan);
        when(entitlement.getStatus()).thenReturn(SubscriptionEntitlement.Status.REFUNDED);
        when(entitlement.getEffectiveFrom()).thenReturn(LocalDateTime.now().minusDays(2));
        when(entitlement.isLifetime()).thenReturn(true);
        when(platformRepository.findByTargetHotelId(42L)).thenReturn(Optional.of(entitlement));

        var view = service.getCurrent(42L);

        assertEquals("PLATFORM", view.source());
        assertEquals("REFUNDED", view.status());
        assertTrue(view.limits().isEmpty());
        verify(accountSubscriptionRepository, never()).findEffectiveSubscriptionsByUserId(any());
    }

    @Test
    void unambiguousLegacySubscriptionIsProjectedOnceAndAudited() {
        Hotel hotel = hotel(42L);
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(9L);
        UserProperty mapping = mock(UserProperty.class);
        when(mapping.getUser()).thenReturn(owner);
        when(mapping.getHotel()).thenReturn(hotel);
        when(mapping.getIsPrimaryOwner()).thenReturn(true);
        when(userPropertyRepository.findByHotelIdAndRelationshipTypeAndStatus(42L, "OWNER", "ACTIVE"))
                .thenReturn(List.of(mapping));
        when(userPropertyRepository.countActiveOwnedPropertiesByUserId(9L)).thenReturn(1L);
        when(userRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(owner));

        SubscriptionPlan plan = plan(7L, "PRO");
        PlanFeature feature = mock(PlanFeature.class);
        when(feature.getFeatureCode()).thenReturn("MAX_ROOMS");
        when(feature.getLimitValue()).thenReturn(50);
        when(plan.getFeatures()).thenReturn(Set.of(feature));
        AccountSubscription subscription = mock(AccountSubscription.class);
        when(subscription.getId()).thenReturn(101L);
        when(subscription.getPlan()).thenReturn(plan);
        when(subscription.getStartAt()).thenReturn(LocalDateTime.now().minusDays(2));
        when(subscription.getEndAt()).thenReturn(LocalDateTime.now().plusDays(20));
        when(subscription.getIsLifetime()).thenReturn(false);
        when(subscription.getStatus()).thenReturn("ACTIVE");
        when(accountSubscriptionRepository.findEffectiveSubscriptionsByUserId(9L))
                .thenReturn(List.of(subscription));
        when(legacyRepository.findByTargetHotelIdForUpdate(42L)).thenReturn(Optional.empty());
        when(legacyRepository.saveAndFlush(any(LegacySubscriptionEntitlementProjection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var first = service.getCurrent(42L);
        LegacySubscriptionEntitlementProjection saved = captureProjection();
        when(legacyRepository.findByTargetHotelIdForUpdate(42L)).thenReturn(Optional.of(saved));

        var second = service.getCurrent(42L);

        assertEquals("LEGACY_PROJECTION", first.source());
        assertEquals(50, first.limits().get("MAX_ROOMS"));
        assertEquals(first.limits(), second.limits());
        verify(legacyRepository).saveAndFlush(any(LegacySubscriptionEntitlementProjection.class));
        verify(auditService).append(any(FinancialAuditService.AuditCommand.class));
    }

    @Test
    void ambiguousLegacyScopeFailsClosedWithoutProjection() {
        when(platformRepository.findByTargetHotelId(42L)).thenReturn(Optional.empty());
        when(userPropertyRepository.findByHotelIdAndRelationshipTypeAndStatus(42L, "OWNER", "ACTIVE"))
                .thenReturn(List.of());

        var view = service.getCurrent(42L);

        assertEquals("NONE", view.source());
        assertTrue(view.limits().isEmpty());
        assertEquals("LEGACY_SCOPE_AMBIGUOUS_OR_MISSING", view.migrationBlocker());
        verify(legacyRepository, never()).saveAndFlush(any());
    }

    private LegacySubscriptionEntitlementProjection captureProjection() {
        ArgumentCaptor<LegacySubscriptionEntitlementProjection> captor =
                ArgumentCaptor.forClass(LegacySubscriptionEntitlementProjection.class);
        verify(legacyRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private Hotel hotel(Long id) {
        Hotel hotel = mock(Hotel.class);
        when(hotel.getId()).thenReturn(id);
        return hotel;
    }

    private SubscriptionPlan plan(Long id, String code) {
        SubscriptionPlan plan = mock(SubscriptionPlan.class);
        when(plan.getId()).thenReturn(id);
        when(plan.getCode()).thenReturn(code);
        when(plan.getNameVi()).thenReturn(code);
        lenient().when(plan.getNameEn()).thenReturn(code);
        lenient().when(plan.getIsLifetime()).thenReturn(false);
        return plan;
    }
}
