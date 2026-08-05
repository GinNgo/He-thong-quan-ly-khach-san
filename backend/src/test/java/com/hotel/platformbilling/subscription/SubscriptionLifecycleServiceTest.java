package com.hotel.platformbilling.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Hotel;
import com.hotel.entities.User;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.services.AuthSessionRevocationService;
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionLifecycleServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");
    @Mock HotelRepository hotelRepository;
    @Mock SubscriptionEntitlementRepository entitlementRepository;
    @Mock PlatformSoftwareContractRepository contractRepository;
    @Mock PlatformSubscriptionHistoryRepository historyRepository;
    @Mock PropertyAccessService accessService;
    @Mock UserPropertyRepository userPropertyRepository;
    @Mock AuthSessionRevocationService sessionRevocationService;
    @Mock FinancialAuditService auditService;
    SubscriptionLifecycleService service;

    @BeforeEach void setUp() {
        service = new SubscriptionLifecycleService(hotelRepository, entitlementRepository, contractRepository,
                historyRepository, accessService, userPropertyRepository, sessionRevocationService, auditService,
                new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test void expiresExactlyAtEffectiveUntilAndInvalidatesAssignedSessions() {
        Fixture fixture = fixture(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), false);
        User assigned = new User(); assigned.setId(8L);
        when(userPropertyRepository.findActiveAssignedUsersByHotelId(9L)).thenReturn(List.of(assigned));
        var result = service.expireIfDue(9L);
        assertTrue(result.transitioned());
        assertEquals(SoftwareContract.Status.EXPIRED, result.contractStatus());
        verify(historyRepository).saveAndFlush(any(SubscriptionHistory.class));
        verify(sessionRevocationService).revokeUserSession(8L, "SUBSCRIPTION_EXPIRED");
        verify(fixture.contract()).transitionTo(SoftwareContract.Status.EXPIRED);
    }

    @Test void beforeBoundaryAndLifetimeNeverExpire() {
        fixture(LocalDateTime.ofInstant(NOW.plusSeconds(1), ZoneOffset.UTC), false);
        assertFalse(service.expireIfDue(9L).transitioned());
        verify(historyRepository, never()).saveAndFlush(any());
    }

    @Test void lifetimeEntitlementNeverExpires() {
        fixture(null, true);
        assertFalse(service.expireIfDue(9L).transitioned());
        verify(historyRepository, never()).saveAndFlush(any());
    }

    @Test void revokeIsAuditedWithActorRequestContextAndReplayIsSafe() {
        Fixture fixture = fixture(LocalDateTime.ofInstant(NOW.plusSeconds(30), ZoneOffset.UTC), false);
        User actor = new User(); actor.setId(7L); when(accessService.currentUser()).thenReturn(actor);
        var result = service.revoke(9L, "Confirmed administrative revocation", "127.0.0.1", "browser", "corr-1");
        assertTrue(result.transitioned());
        ArgumentCaptor<FinancialAuditService.AuditCommand> audit = ArgumentCaptor.forClass(FinancialAuditService.AuditCommand.class);
        verify(auditService).append(audit.capture());
        assertEquals("corr-1", audit.getValue().correlationId());
        assertEquals("127.0.0.1", audit.getValue().metadata().get("ip"));
        assertEquals(7L, audit.getValue().actorId());
        assertFalse(service.revoke(9L, "Confirmed administrative revocation", "127.0.0.1", "browser", "corr-1").transitioned());
        verify(historyRepository, times(1)).saveAndFlush(any(SubscriptionHistory.class));
        assertEquals(SoftwareContract.Status.REVOKED, fixture.contractState().get());
    }

    @Test void expiryWinsWhenRevokeArrivesAtExactBoundary() {
        Fixture fixture = fixture(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), false);
        User actor = new User(); actor.setId(7L); when(accessService.currentUser()).thenReturn(actor);
        var result = service.revoke(9L, "Confirmed administrative revocation", "127.0.0.1", "browser", "corr-1");
        assertEquals(SoftwareContract.Status.EXPIRED, result.contractStatus());
        assertEquals(SubscriptionEntitlement.Status.EXPIRED, result.entitlementStatus());
        verify(fixture.contract(), never()).transitionTo(SoftwareContract.Status.REVOKED);
        verify(historyRepository).existsByOrderIdAndActionType(5L, SubscriptionHistory.ActionType.EXPIRED);
    }

    @Test void differentTerminalStateConflictsWithoutMutation() {
        Fixture fixture = fixture(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC), false);
        fixture.contractState().set(SoftwareContract.Status.REFUNDED);
        fixture.entitlementState().set(SubscriptionEntitlement.Status.REFUNDED);
        assertFalse(service.expireIfDue(9L).transitioned());
        verify(historyRepository, never()).saveAndFlush(any());
    }

    private Fixture fixture(LocalDateTime effectiveUntil, boolean lifetime) {
        Hotel hotel = mock(Hotel.class); when(hotel.getId()).thenReturn(9L);
        SubscriptionOrder order = mock(SubscriptionOrder.class); when(order.getId()).thenReturn(5L);
        when(order.getTargetHotel()).thenReturn(hotel);
        SoftwareContract contract = mock(SoftwareContract.class);
        SubscriptionEntitlement entitlement = mock(SubscriptionEntitlement.class);
        AtomicReference<SoftwareContract.Status> contractState = new AtomicReference<>(SoftwareContract.Status.ACTIVE);
        AtomicReference<SubscriptionEntitlement.Status> entitlementState = new AtomicReference<>(SubscriptionEntitlement.Status.ACTIVE);
        when(contract.getPublicId()).thenReturn("contract-1"); when(contract.getOrder()).thenReturn(order);
        when(contract.getTargetHotel()).thenReturn(hotel); when(contract.getStatus()).thenAnswer(i -> contractState.get());
        doAnswer(i -> { contractState.set(i.getArgument(0)); return null; }).when(contract).transitionTo(any());
        when(entitlement.getContract()).thenReturn(contract); when(entitlement.getStatus()).thenAnswer(i -> entitlementState.get());
        when(entitlement.getEffectiveUntil()).thenReturn(effectiveUntil); when(entitlement.isLifetime()).thenReturn(lifetime);
        doAnswer(i -> { entitlementState.set(i.getArgument(0)); return null; }).when(entitlement).transitionTo(any());
        when(hotelRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(hotel));
        when(entitlementRepository.findByTargetHotelIdForUpdate(9L)).thenReturn(Optional.of(entitlement));
        when(contractRepository.findByPublicIdForUpdate("contract-1")).thenReturn(Optional.of(contract));
        when(historyRepository.existsByOrderIdAndActionType(anyLong(), any())).thenReturn(false);
        return new Fixture(contract, entitlement, contractState, entitlementState);
    }

    private record Fixture(SoftwareContract contract, SubscriptionEntitlement entitlement,
            AtomicReference<SoftwareContract.Status> contractState,
            AtomicReference<SubscriptionEntitlement.Status> entitlementState) {}
}
