package com.hotel.services;

import com.hotel.entities.OwnerInvitation;
import com.hotel.entities.Hotel;
import com.hotel.entities.User;
import com.hotel.repositories.OwnerInvitationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OwnerInvitationTransitionServiceTest {
    @Mock OwnerInvitationRepository repository;
    @Mock EmailService emailService;
    @Mock OperationalAuditService auditService;
    @InjectMocks OwnerInvitationTransitionService service;

    @Test
    void failedAfterCommitDeliveryCancelsPendingInvitationForSafeRetry() {
        OwnerInvitation invite = invite();
        when(emailService.sendOwnerInvitationEmail("owner@example.test", "Hotel", "token", 7)).thenReturn(false);
        when(repository.findByIdForUpdate(9L)).thenReturn(Optional.of(invite));
        service.deliverOrCancel(9L, "owner@example.test", "Hotel", "token", "corr-9", "203.0.113.9", "T241-Agent");
        assertThat(invite.getStatus()).isEqualTo("CANCELLED");
        assertThat(invite.getCancelledAt()).isNotNull();
        verify(repository).saveAndFlush(invite);
        verify(auditService).append(argThat(command -> "OWNER_INVITATION_CANCELLED".equals(command.eventType())
                && "MAIL_DELIVERY_FAILED".equals(command.reason()) && "corr-9".equals(command.correlationId())
                && command.afterState().toString().contains("203.0.113.9") && command.afterState().toString().contains("T241-Agent")));
    }

    @Test
    void successfulDeliveryKeepsInvitationPending() {
        when(emailService.sendOwnerInvitationEmail("owner@example.test", "Hotel", "token", 7)).thenReturn(true);
        OwnerInvitation invite = invite(); when(repository.findByIdForUpdate(9L)).thenReturn(Optional.of(invite));
        service.deliverOrCancel(9L, "owner@example.test", "Hotel", "token", "corr-9", "203.0.113.9", "T241-Agent");
        verify(auditService).append(argThat(command -> "OWNER_INVITATION_SENT".equals(command.eventType())
                && "corr-9".equals(command.correlationId()) && command.afterState().toString().contains("203.0.113.9")));
        verify(repository, never()).saveAndFlush(any());
    }

    private OwnerInvitation invite() { Hotel hotel = new Hotel(); hotel.setId(10L); User inviter = new User(); inviter.setId(1L); OwnerInvitation invite = new OwnerInvitation(); invite.setStatus("PENDING"); invite.setHotel(hotel); invite.setInvitedBy(inviter); return invite; }
}
