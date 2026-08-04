package com.hotel.platformbilling.subscription;

import com.hotel.entities.*;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.repositories.*;
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionPlanAdministrationServiceTest {
    @Mock SubscriptionPlanRepository plans; @Mock SubscriptionFeatureRepository features;
    @Mock PropertyAccessService access; @Mock FinancialAuditService audit;
    @Mock SubscriptionPlanAdminOperationRepository operations;
    SubscriptionPlanAdministrationService service;

    @BeforeEach void setUp(){ service=new SubscriptionPlanAdministrationService(plans,features,access,audit,operations,
            Clock.fixed(Instant.parse("2026-08-04T12:00:00Z"), ZoneOffset.UTC)); }

    @Test void createsNewInactiveVersionWithoutMutatingPublishedVersionAndReplaysStable(){
        when(access.isSystemAdministrator()).thenReturn(true); User actor=new User(); actor.setId(7L); when(access.currentUser()).thenReturn(actor);
        SubscriptionFeature definition=new SubscriptionFeature(); definition.setCode("MAX_ROOMS");
        when(features.findByCodeIn(Set.of("MAX_ROOMS"))).thenReturn(List.of(definition));
        SubscriptionPlan published=plan(1L,"PRO",1,"ACTIVE");
        when(plans.findFamilyForUpdate("PRO")).thenReturn(List.of(published));
        when(plans.saveAndFlush(any())).thenAnswer(inv->{SubscriptionPlan p=inv.getArgument(0); ReflectionTestUtils.setField(p,"id",2L); return p;});
        var command=command(new BigDecimal("1000000"));
        var created=service.createVersion(command,"key-1","corr");
        assertEquals(2,created.versionNumber()); assertEquals("INACTIVE",created.status());
        assertEquals("ACTIVE",published.getStatus());
        var captor=org.mockito.ArgumentCaptor.forClass(SubscriptionPlan.class); verify(plans).saveAndFlush(captor.capture());
        SubscriptionPlan saved=captor.getValue();
        when(plans.findByCreationKeyHash(saved.getCreationKeyHash())).thenReturn(Optional.of(saved));
        assertEquals(created.id(),service.createVersion(command,"key-1","corr").id());
        var changed=new SubscriptionPlanAdministrationService.CreateVersionCommand("PRO","Changed","Pro","YEARLY",new BigDecimal("2000000"),1,"YEAR",List.of(new SubscriptionPlanAdministrationService.FeatureLimit("MAX_ROOMS",10)));
        assertThrows(FinancialException.class,()->service.createVersion(changed,"key-1","corr"));
        verify(plans,times(1)).saveAndFlush(any());
    }

    @Test void rejectsInvalidPriceUnknownFeatureAndNonAdmin(){
        assertThrows(AccessDeniedException.class,()->service.createVersion(command(BigDecimal.ONE),"key","c"));
        when(access.isSystemAdministrator()).thenReturn(true);
        assertThrows(IllegalArgumentException.class,()->service.createVersion(command(BigDecimal.ZERO),"key","c"));
        when(features.findByCodeIn(any())).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class,()->service.createVersion(command(BigDecimal.ONE),"key","c"));
    }

    @Test void activationAtomicallyRetiresPriorVersionAuditsBothAndCannotReactivateRetired(){
        when(access.isSystemAdministrator()).thenReturn(true); User actor=new User(); actor.setId(7L); when(access.currentUser()).thenReturn(actor);
        SubscriptionPlan old=plan(1L,"PRO",1,"ACTIVE"); SubscriptionPlan draft=plan(2L,"PRO",2,"INACTIVE");
        when(plans.findFamilyCodeById(2L)).thenReturn(Optional.of("PRO")); when(plans.findFamilyForUpdate("PRO")).thenReturn(List.of(draft,old));
        service.activate(2L,"activate-key","corr");
        assertEquals("INACTIVE",old.getStatus()); assertEquals("ACTIVE",draft.getStatus()); verify(audit,times(2)).append(any());
        var order= inOrder(plans); order.verify(plans).saveAndFlush(old); order.verify(plans).saveAndFlush(draft);
        draft.setStatus("INACTIVE"); draft.setDeactivatedAt(LocalDateTime.now());
        assertThrows(FinancialException.class,()->service.activate(2L,"other-key","corr"));
    }

    @Test void actionIdempotencyKeyCannotTargetDifferentPlan(){
        when(access.isSystemAdministrator()).thenReturn(true);
        SubscriptionPlanAdminOperation operation=SubscriptionPlanAdminOperation.record("hash","ACTIVE",2L,"ACTIVE","different",LocalDateTime.now());
        when(operations.findByKeyHash(any())).thenReturn(Optional.of(operation));
        assertThrows(FinancialException.class,()->service.activate(3L,"same-key","corr"));
        verify(plans,never()).findFamilyCodeById(anyLong());
    }

    @Test void sameStateReplayBindsOperationBeforeReturning(){
        when(access.isSystemAdministrator()).thenReturn(true);
        SubscriptionPlan active=plan(2L,"PRO",2,"ACTIVE");
        when(plans.findFamilyCodeById(2L)).thenReturn(Optional.of("PRO"));
        when(plans.findFamilyForUpdate("PRO")).thenReturn(List.of(active));
        service.activate(2L,"same-state-key","corr");
        verify(operations).saveAndFlush(any(SubscriptionPlanAdminOperation.class));
        verify(plans,never()).saveAndFlush(active);
    }

    @Test void legacyFreeCompatibilityVersionCannotBeActivated(){
        when(access.isSystemAdministrator()).thenReturn(true);
        SubscriptionPlan free=plan(4L,"FREE",1,"INACTIVE"); free.setPrice(BigDecimal.ZERO);
        assertEquals("INACTIVE",free.getStatus()); assertEquals(0,free.getPrice().signum());
        when(operations.findByKeyHash(anyString())).thenReturn(Optional.empty());
        when(plans.findFamilyCodeById(4L)).thenReturn(Optional.of("FREE"));
        when(plans.findFamilyForUpdate("FREE")).thenReturn(List.of(free));
        assertThrows(FinancialException.class,()->service.activate(4L,"free-key","corr"));
        verify(plans,never()).saveAndFlush(free);
    }

    @Test void existingOrderAndContractSnapshotsRemainUnchangedAcrossCatalogVersioning(){
        when(access.isSystemAdministrator()).thenReturn(true); User actor=new User(); actor.setId(7L); when(access.currentUser()).thenReturn(actor);
        SubscriptionPlan old=plan(1L,"PRO",1,"ACTIVE"), draft=plan(2L,"PRO",2,"INACTIVE");
        when(plans.findFamilyCodeById(2L)).thenReturn(Optional.of("PRO"));
        when(plans.findFamilyForUpdate("PRO")).thenReturn(List.of(draft,old));
        var order=mock(com.hotel.platformbilling.order.SubscriptionOrder.class);
        var contract=mock(SoftwareContract.class);
        when(order.getPlanVersion()).thenReturn("PLAN-1-original");
        when(order.getFeatureSnapshotJson()).thenReturn("{\"MAX_ROOMS\":5}");
        when(contract.getPlanSnapshotJson()).thenReturn("{\"code\":\"PRO\"}");
        when(contract.getFeatureSnapshotJson()).thenReturn("{\"MAX_ROOMS\":5}");
        String orderVersion=order.getPlanVersion(), orderFeatures=order.getFeatureSnapshotJson();
        String contractPlan=contract.getPlanSnapshotJson(), contractFeatures=contract.getFeatureSnapshotJson();
        service.activate(2L,"snapshot-activation","corr");
        assertEquals("PLAN-1-original",orderVersion); assertEquals("{\"MAX_ROOMS\":5}",orderFeatures);
        assertEquals("{\"code\":\"PRO\"}",contractPlan); assertEquals("{\"MAX_ROOMS\":5}",contractFeatures);
        assertEquals("PLAN-1-original",order.getPlanVersion()); assertEquals(contractPlan,contract.getPlanSnapshotJson());
        verify(order,times(2)).getPlanVersion(); verify(order).getFeatureSnapshotJson();
        verify(contract,times(2)).getPlanSnapshotJson(); verify(contract).getFeatureSnapshotJson();
    }

    private SubscriptionPlan plan(Long id,String family,int version,String status){ SubscriptionPlan p=new SubscriptionPlan(); ReflectionTestUtils.setField(p,"id",id); p.setFamilyCode(family); p.setVersionNumber(version); p.setCode(family+"_V"+version); p.setNameVi("Goi Pro"); p.setNameEn("Pro"); p.setBillingType("YEARLY"); p.setPrice(BigDecimal.TEN); p.setDurationUnit("YEAR"); p.setDurationValue(1); p.setStatus(status); p.setFeatures(Set.of()); return p; }
    private SubscriptionPlanAdministrationService.CreateVersionCommand command(BigDecimal price){ return new SubscriptionPlanAdministrationService.CreateVersionCommand("PRO","Goi Pro","Pro","YEARLY",price,1,"YEAR",List.of(new SubscriptionPlanAdministrationService.FeatureLimit("MAX_ROOMS",10))); }
}
