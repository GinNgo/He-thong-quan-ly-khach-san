package com.hotel.services;

import com.hotel.entities.*;
import com.hotel.exceptions.OwnershipLifecycleException;
import com.hotel.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PropertyOwnershipGovernanceServiceTest {
    @Mock HotelRepository hotelRepository;
    @Mock UserRepository userRepository;
    @Mock UserPropertyRepository membershipRepository;
    @Mock OwnerInvitationRepository invitationRepository;
    @Mock OwnershipTransferRepository transferRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock OperationalAuditService auditService;
    @Mock NotificationService notificationService;
    @Mock AuthSessionRevocationService sessionRevocationService;
    @Mock OwnerInvitationTransitionService invitationTransitionService;
    @Mock OwnershipTransferFinancialReadinessGateway financialReadinessGateway;
    @Mock PropertyOwnershipLifecycleService ownershipLifecycleService;
    @InjectMocks PropertyOwnershipGovernanceService service;

    @BeforeEach
    void configure() { ReflectionTestUtils.setField(service, "maxActiveOwners", 10); }

    @Test
    void invitationStoresOnlyHashAndDoesNotExposeRawToken() {
        Hotel hotel = hotel(10L); User primaryUser = user(1L, "primary@example.test");
        when(hotelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(hotel));
        when(membershipRepository.findOwnerMappingForUpdate(1L, 10L)).thenReturn(Optional.of(owner(primaryUser, hotel, true, LocalDateTime.now().minusDays(30))));
        when(invitationRepository.saveAndFlush(any())).thenAnswer(invocation -> { OwnerInvitation value = invocation.getArgument(0); value.setId(90L); return value; });
        TransactionSynchronizationManager.initSynchronization();
        try {
            var result = service.invite(10L, 1L, " invited@example.test ");
            assertThat(result).extracting("invitationId", "email", "status").containsExactly(90L, "invited@example.test", "DISPATCH_PENDING");
            verify(invitationRepository).saveAndFlush(argThat(invite -> invite.getTokenHash().matches("[0-9a-f]{64}") && !invite.getTokenHash().contains("=")));
            verifyNoInteractions(invitationTransitionService);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test
    void acceptedInvitationTokenCannotBeReused() {
        Hotel hotel = hotel(10L); User actor = user(2L, "invited@example.test"); OwnerInvitation invitation = new OwnerInvitation();
        invitation.setHotel(hotel); invitation.setInvitedEmail(actor.getEmail()); invitation.setStatus("ACCEPTED"); invitation.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(actor));
        when(invitationRepository.findHotelIdByTokenHash(anyString())).thenReturn(Optional.of(10L));
        when(hotelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(hotel));
        when(invitationRepository.findByTokenHash(anyString())).thenReturn(Optional.of(invitation));
        when(invitationRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(invitation));
        assertThatThrownBy(() -> service.acceptInvitation(2L, "one-time-token", true))
                .isInstanceOfSatisfying(OwnershipLifecycleException.class, error -> assertThat(error.code()).isEqualTo("OWNER_INVITATION_EXPIRED"));
        verify(membershipRepository, never()).saveAndFlush(any());
    }

    @Test
    void expiredInvitationPersistsExpiryOutsideRejectedAcceptance() {
        Hotel hotel = hotel(10L); OwnerInvitation invitation = new OwnerInvitation(); invitation.setId(91L);
        invitation.setHotel(hotel); invitation.setStatus("PENDING"); invitation.setExpiresAt(LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(1));
        when(invitationRepository.findHotelIdByTokenHash(anyString())).thenReturn(Optional.of(10L));
        when(hotelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(hotel));
        when(invitationRepository.findByTokenHash(anyString())).thenReturn(Optional.of(invitation));
        assertThatThrownBy(() -> service.acceptInvitation(2L, "expired-token", true))
                .isInstanceOfSatisfying(OwnershipLifecycleException.class, error -> assertThat(error.code()).isEqualTo("OWNER_INVITATION_EXPIRED"));
        verify(invitationTransitionService).expire(91L);
        verify(invitationRepository, never()).findByTokenHashForUpdate(anyString());
    }

    @Test
    void transferInitiationRequiresServerVerifiedPassword() {
        Hotel hotel = hotel(10L); User primaryUser = user(1L, "primary@example.test");
        when(hotelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(hotel));
        when(membershipRepository.findOwnerMappingForUpdate(1L, 10L)).thenReturn(Optional.of(owner(primaryUser, hotel, true, LocalDateTime.now().minusDays(30))));
        when(passwordEncoder.matches("wrong", primaryUser.getPasswordHash())).thenReturn(false);
        assertThatThrownBy(() -> service.initiateTransfer(10L, 1L, 2L, "wrong"))
                .isInstanceOfSatisfying(OwnershipLifecycleException.class, error -> assertThat(error.code()).isEqualTo("OWNERSHIP_REAUTH_REQUIRED"));
        verify(transferRepository, never()).saveAndFlush(any());
    }

    @Test
    void coolingPeriodBlocksPrimaryTransfer() {
        Hotel hotel = hotel(10L); User primaryUser = user(1L, "primary@example.test"); User targetUser = user(2L, "target@example.test");
        when(hotelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(hotel));
        when(membershipRepository.findOwnerMappingForUpdate(1L, 10L)).thenReturn(Optional.of(owner(primaryUser, hotel, true, LocalDateTime.now().minusDays(30))));
        when(membershipRepository.findOwnerMappingForUpdate(2L, 10L)).thenReturn(Optional.of(owner(targetUser, hotel, false, LocalDateTime.now().minusDays(2))));
        when(passwordEncoder.matches("secret", primaryUser.getPasswordHash())).thenReturn(true);
        assertThatThrownBy(() -> service.initiateTransfer(10L, 1L, 2L, "secret"))
                .isInstanceOfSatisfying(OwnershipLifecycleException.class, error -> assertThat(error.code()).isEqualTo("OWNER_COOLING_PERIOD"));
    }

    @Test
    void financialReadinessUnavailableFailsClosed() {
        Hotel hotel = hotel(10L); User primaryUser = user(1L, "primary@example.test"); User targetUser = user(2L, "target@example.test");
        when(hotelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(hotel));
        when(membershipRepository.findOwnerMappingForUpdate(1L, 10L)).thenReturn(Optional.of(owner(primaryUser, hotel, true, LocalDateTime.now().minusDays(30))));
        when(membershipRepository.findOwnerMappingForUpdate(2L, 10L)).thenReturn(Optional.of(owner(targetUser, hotel, false, LocalDateTime.now().minusDays(10))));
        when(passwordEncoder.matches("secret", primaryUser.getPasswordHash())).thenReturn(true);
        when(financialReadinessGateway.assess(10L)).thenReturn(new OwnershipTransferFinancialReadinessGateway.Readiness(
                OwnershipTransferFinancialReadinessGateway.State.UNAVAILABLE, null, "missing source"));
        assertThatThrownBy(() -> service.initiateTransfer(10L, 1L, 2L, "secret"))
                .isInstanceOfSatisfying(OwnershipLifecycleException.class, error -> assertThat(error.code()).isEqualTo("OWNERSHIP_FINANCIAL_READINESS_UNAVAILABLE"));
    }

    @Test
    void readyTransferIsCreatedWithoutChangingSubscription() {
        Hotel hotel = hotel(10L); User primaryUser = user(1L, "primary@example.test"); User targetUser = user(2L, "target@example.test");
        when(hotelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(hotel));
        when(membershipRepository.findOwnerMappingForUpdate(1L, 10L)).thenReturn(Optional.of(owner(primaryUser, hotel, true, LocalDateTime.now().minusDays(30))));
        when(membershipRepository.findOwnerMappingForUpdate(2L, 10L)).thenReturn(Optional.of(owner(targetUser, hotel, false, LocalDateTime.now().minusDays(10))));
        when(passwordEncoder.matches("secret", primaryUser.getPasswordHash())).thenReturn(true);
        when(financialReadinessGateway.assess(10L)).thenReturn(new OwnershipTransferFinancialReadinessGateway.Readiness(
                OwnershipTransferFinancialReadinessGateway.State.READY,
                new OwnershipTransferFinancialReadinessGateway.Disclosure("PRO", LocalDateTime.now().plusDays(20), 0, 0, 0, 0), "ready"));
        when(transferRepository.saveAndFlush(any())).thenAnswer(invocation -> { OwnershipTransfer transfer = invocation.getArgument(0); transfer.setId(88L); return transfer; });
        var result = service.initiateTransfer(10L, 1L, 2L, "secret");
        assertThat(result.transferId()).isEqualTo(88L);
        assertThat(result.responsibility().subscriptionPlan()).isEqualTo("PRO");
        verifyNoInteractions(sessionRevocationService);
    }

    @Test
    void acceptingReadyTransferAtomicallySwapsPrimaryAndRevokesBothSessions() {
        Hotel hotel = hotel(10L); User fromUser = user(1L, "primary@example.test"); User toUser = user(2L, "target@example.test");
        UserProperty from = owner(fromUser, hotel, true, LocalDateTime.now().minusDays(30));
        UserProperty to = owner(toUser, hotel, false, LocalDateTime.now().minusDays(10));
        OwnershipTransfer transfer = new OwnershipTransfer(); transfer.setId(88L); transfer.setHotel(hotel);
        transfer.setFromUser(fromUser); transfer.setToUser(toUser); transfer.setStatus("PENDING"); transfer.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(transferRepository.findHotelIdById(88L)).thenReturn(Optional.of(10L));
        when(hotelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(hotel));
        when(transferRepository.findByIdForUpdate(88L)).thenReturn(Optional.of(transfer));
        when(membershipRepository.findActiveOwnerMappingsForUpdate(10L)).thenReturn(List.of(from, to));
        when(membershipRepository.findOwnerMappingsByHotelId(10L)).thenReturn(List.of(from, to));
        when(financialReadinessGateway.assess(10L)).thenReturn(new OwnershipTransferFinancialReadinessGateway.Readiness(
                OwnershipTransferFinancialReadinessGateway.State.READY,
                new OwnershipTransferFinancialReadinessGateway.Disclosure("PRO", null, 0, 0, 0, 0), "ready"));
        var result = service.acceptTransfer(88L, 2L, true);
        assertThat(from.getIsPrimaryOwner()).isFalse();
        assertThat(to.getIsPrimaryOwner()).isTrue();
        assertThat(transfer.getStatus()).isEqualTo("ACCEPTED");
        assertThat(result.owners()).filteredOn(PropertyOwnershipGovernanceService.OwnerView::canManageOwners).hasSize(1);
        verify(sessionRevocationService).revokeUserSession(1L, "OWNERSHIP_TRANSFER");
        verify(sessionRevocationService).revokeUserSession(2L, "OWNERSHIP_TRANSFER");
        verify(membershipRepository).saveAndFlush(to);
    }

    @Test
    void primaryCannotLeaveAndCoOwnerExitRevokesOnlyUnusedOwnerRole() {
        Hotel hotel = hotel(10L); User primaryUser = user(1L, "primary@example.test");
        when(hotelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(hotel));
        when(membershipRepository.findOwnerMappingForUpdate(1L, 10L)).thenReturn(Optional.of(owner(primaryUser, hotel, true, LocalDateTime.now().minusDays(30))));
        assertThatThrownBy(() -> service.leave(10L, 1L, "leaving"))
                .isInstanceOfSatisfying(OwnershipLifecycleException.class, error -> assertThat(error.code()).isEqualTo("PRIMARY_OWNER_REQUIRED"));
        verify(sessionRevocationService, never()).revokeUserSession(anyLong(), anyString());
    }

    @Test
    void initiatorMayCancelOnlyPendingTransfer() {
        Hotel hotel = hotel(10L); OwnershipTransfer transfer = new OwnershipTransfer();
        transfer.setId(80L); transfer.setHotel(hotel); transfer.setFromUser(user(1L, "primary@example.test"));
        transfer.setToUser(user(2L, "target@example.test")); transfer.setStatus("PENDING"); transfer.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(transferRepository.findHotelIdById(80L)).thenReturn(Optional.of(10L));
        when(hotelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(hotel));
        when(transferRepository.findByIdForUpdate(80L)).thenReturn(Optional.of(transfer));
        service.cancelTransfer(80L, 1L);
        assertThat(transfer.getStatus()).isEqualTo("CANCELLED");
        verify(transferRepository).saveAndFlush(transfer);
    }

    @Test
    void crossPropertyInvitationCancelIsHiddenBeforeAnyAggregateLock() {
        when(invitationRepository.findHotelIdById(90L)).thenReturn(Optional.of(11L));
        assertThatThrownBy(() -> service.cancelInvitation(10L, 90L, 1L))
                .isInstanceOfSatisfying(OwnershipLifecycleException.class, error -> assertThat(error.code()).isEqualTo("OWNERSHIP_NOT_FOUND"));
        verifyNoInteractions(hotelRepository);
    }

    @Test
    void inactiveMembershipCannotReplayLeave() {
        Hotel hotel = hotel(10L); User coOwner = user(2L, "coowner@example.test"); UserProperty membership = owner(coOwner, hotel, false, LocalDateTime.now().minusDays(10)); membership.setStatus("LEFT");
        when(hotelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(hotel));
        when(membershipRepository.findOwnerMappingForUpdate(2L, 10L)).thenReturn(Optional.of(membership));
        assertThatThrownBy(() -> service.leave(10L, 2L, "again"))
                .isInstanceOfSatisfying(OwnershipLifecycleException.class, error -> assertThat(error.code()).isEqualTo("OWNER_MEMBERSHIP_NOT_ACTIVE"));
        verify(membershipRepository, never()).saveAndFlush(any());
    }

    @Test
    void leaveAuditPreservesReasonAndRequestMetadata() {
        Hotel hotel = hotel(10L); User coOwner = user(2L, "coowner@example.test"); UserProperty membership = owner(coOwner, hotel, false, LocalDateTime.now().minusDays(10));
        when(hotelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(hotel));
        when(membershipRepository.findOwnerMappingForUpdate(2L, 10L)).thenReturn(Optional.of(membership));
        when(membershipRepository.findOwnerMappingsByHotelId(10L)).thenReturn(List.of());
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setRemoteAddr("203.0.113.10"); request.addHeader("User-Agent", "T241-Test"); request.addHeader("X-Correlation-ID", "corr-t241");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try { service.leave(10L, 2L, "Owner requested departure"); }
        finally { RequestContextHolder.resetRequestAttributes(); }
        var command = org.mockito.ArgumentCaptor.forClass(OperationalAuditService.AuditCommand.class);
        verify(auditService).append(command.capture());
        assertThat(command.getValue().reason()).isEqualTo("Owner requested departure");
        assertThat(command.getValue().correlationId()).isEqualTo("corr-t241");
        assertThat(command.getValue().afterState().toString()).contains("203.0.113.10", "T241-Test", "Owner requested departure");
    }

    private Hotel hotel(Long id) { Hotel hotel = new Hotel(); hotel.setId(id); hotel.setName("Test Hotel"); return hotel; }
    private User user(Long id, String email) { User user = new User(); user.setId(id); user.setEmail(email); user.setFullName("Owner " + id); user.setStatus("ACTIVE"); user.setEmailVerifiedAt(Instant.now()); user.setPasswordHash("hash"); return user; }
    private UserProperty owner(User user, Hotel hotel, boolean primary, LocalDateTime acceptedAt) { UserProperty membership = new UserProperty(); membership.setId(user.getId() * 10); membership.setUser(user); membership.setHotel(hotel); membership.setRelationshipType("OWNER"); membership.setStatus("ACTIVE"); membership.setIsPrimaryOwner(primary); membership.setAcceptedAt(acceptedAt); return membership; }
}
