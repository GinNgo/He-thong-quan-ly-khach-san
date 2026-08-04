package com.hotel.services;

import com.hotel.repositories.OwnerInvitationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class OwnerInvitationTransitionService {
    private final OwnerInvitationRepository repository;
    private final EmailService emailService;
    private final OperationalAuditService auditService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliverOrCancel(Long invitationId, String email, String propertyName, String token,
                                String correlationId, String ip, String userAgent) {
        repository.findByIdForUpdate(invitationId).filter(invite -> "PENDING".equals(invite.getStatus())).ifPresent(invite -> {
            boolean delivered = emailService.sendOwnerInvitationEmail(email, propertyName, token, 7);
            if (delivered) {
                auditService.append(new OperationalAuditService.AuditCommand("TENANT", invite.getHotel().getId(), "OWNERSHIP",
                        "OWNER_INVITATION_SENT", "OWNER_INVITATION", String.valueOf(invitationId), "USER",
                        invite.getInvitedBy().getId(), "Owner invitation email delivered", java.util.Map.of("status", "PENDING"),
                        context("PENDING", "SENT", null, ip, userAgent), correlationId));
                return;
            }
            invite.setStatus("CANCELLED"); invite.setCancelledAt(LocalDateTime.now(ZoneOffset.UTC)); repository.saveAndFlush(invite);
            auditService.append(new OperationalAuditService.AuditCommand("TENANT", invite.getHotel().getId(), "OWNERSHIP",
                    "OWNER_INVITATION_CANCELLED", "OWNER_INVITATION", String.valueOf(invitationId), "SYSTEM", null,
                    "MAIL_DELIVERY_FAILED", java.util.Map.of("status", "PENDING"),
                    context("CANCELLED", "FAILED", "MAIL_DELIVERY_FAILED", ip, userAgent), correlationId));
        });
    }

    private java.util.Map<String, Object> context(String status, String delivery, String reason, String ip, String userAgent) {
        java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
        state.put("status", status); state.put("delivery", delivery);
        if (reason != null) state.put("reason", reason);
        if (ip != null) state.put("ip", ip);
        if (userAgent != null) state.put("userAgent", userAgent);
        return state;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expire(Long invitationId) {
        repository.findByIdForUpdate(invitationId).filter(invite -> "PENDING".equals(invite.getStatus())).ifPresent(invite -> {
            invite.setStatus("EXPIRED"); repository.saveAndFlush(invite);
        });
    }
}
