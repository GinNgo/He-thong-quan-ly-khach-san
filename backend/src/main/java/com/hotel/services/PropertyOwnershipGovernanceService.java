package com.hotel.services;

import com.hotel.entities.*;
import com.hotel.exceptions.OwnershipLifecycleException;
import com.hotel.exceptions.CorrelationIdSupport;
import com.hotel.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PropertyOwnershipGovernanceService {
    private static final int COOLING_DAYS = 7;
    @Value("${app.ownership.max-active-owners:10}")
    private int maxActiveOwners;
    private final HotelRepository hotelRepository;
    private final UserRepository userRepository;
    private final UserPropertyRepository membershipRepository;
    private final OwnerInvitationRepository invitationRepository;
    private final OwnershipTransferRepository transferRepository;
    private final PasswordEncoder passwordEncoder;
    private final OperationalAuditService auditService;
    private final NotificationService notificationService;
    private final AuthSessionRevocationService sessionRevocationService;
    private final OwnerInvitationTransitionService invitationTransitionService;
    private final OwnershipTransferFinancialReadinessGateway financialReadinessGateway;
    private final PropertyOwnershipLifecycleService ownershipLifecycleService;

    @Transactional(readOnly = true)
    public OwnershipView owners(Long propertyId, Long actorId) {
        List<UserProperty> memberships = membershipRepository.findOwnerMappingsByHotelId(propertyId);
        UserProperty actor = memberships.stream().filter(m -> same(m.getUser().getId(), actorId) && "ACTIVE".equals(m.getStatus())).findFirst().orElse(null);
        if (actor == null) throw notFound();
        OwnershipTransfer pending = transferRepository.findFirstByHotelIdAndStatusOrderByCreatedAtDesc(propertyId, "PENDING")
                .filter(t -> same(t.getFromUser().getId(), actorId) || same(t.getToUser().getId(), actorId)).orElse(null);
        return view(propertyId, actorId, memberships, pending);
    }

    @Transactional
    public InvitationResult invite(Long propertyId, Long actorId, String email) {
        Hotel hotel = lockHotel(propertyId);
        UserProperty primary = requirePrimary(propertyId, actorId);
        String normalized = normalizeEmail(email);
        if (membershipRepository.countByHotelIdAndRelationshipTypeAndStatus(propertyId, "OWNER", "ACTIVE") >= maxActiveOwners)
            throw conflict("OWNER_LIMIT_REACHED", "The property owner limit has been reached.");
        if (invitationRepository.existsByHotelIdAndInvitedEmailAndStatus(propertyId, normalized, "PENDING"))
            throw conflict("OWNER_INVITATION_CONFLICT", "A pending invitation already exists for this email.");
        userRepository.findByEmailIgnoreCase(normalized).flatMap(user -> membershipRepository
                .findByUserIdAndHotelIdAndRelationshipType(user.getId(), propertyId, "OWNER"))
                .filter(m -> "ACTIVE".equals(m.getStatus())).ifPresent(m -> { throw conflict("OWNER_INVITATION_CONFLICT", "This account is already an active owner."); });
        String token = rawToken();
        OwnerInvitation invitation = new OwnerInvitation();
        invitation.setHotel(hotel); invitation.setInvitedBy(primary.getUser()); invitation.setInvitedEmail(normalized);
        invitation.setTokenHash(hash(token)); invitation.setStatus("PENDING"); invitation.setExpiresAt(now().plusDays(7));
        invitation = invitationRepository.saveAndFlush(invitation);
        sendInvitationAfterCommit(invitation.getId(), normalized, hotel.getName(), token);
        audit(propertyId, actorId, "OWNER_INVITATION_DISPATCH_REQUESTED", "OWNER_INVITATION", invitation.getId(), Map.of(), Map.of("email", normalized, "status", "PENDING"));
        return new InvitationResult(invitation.getId(), normalized, "DISPATCH_PENDING", invitation.getExpiresAt());
    }

    @Transactional
    public OwnerView acceptInvitation(Long actorId, String rawToken, boolean termsAccepted) {
        if (!termsAccepted) throw conflict("OWNER_TERMS_REQUIRED", "Owner terms must be accepted.");
        String tokenHash = hash(rawToken);
        Long hotelId = invitationRepository.findHotelIdByTokenHash(tokenHash).orElseThrow(() -> conflict("OWNER_INVITATION_INVALID", "The invitation is invalid or unavailable."));
        Hotel hotel = lockHotel(hotelId);
        OwnerInvitation snapshot = invitationRepository.findByTokenHash(tokenHash).orElseThrow(() -> conflict("OWNER_INVITATION_INVALID", "The invitation is invalid or unavailable."));
        if ("PENDING".equals(snapshot.getStatus()) && !snapshot.getExpiresAt().isAfter(now())) {
            invitationTransitionService.expire(snapshot.getId());
            throw conflict("OWNER_INVITATION_EXPIRED", "The invitation has expired or was already used.");
        }
        OwnerInvitation invitation = invitationRepository.findByTokenHashForUpdate(tokenHash).orElseThrow(() -> conflict("OWNER_INVITATION_INVALID", "The invitation is invalid or unavailable."));
        User actor = userRepository.findByIdForUpdate(actorId).orElseThrow(this::notFound);
        if (actor.getEmailVerifiedAt() == null) throw conflict("EMAIL_VERIFICATION_REQUIRED", "The invited email must be verified.");
        LocalDateTime now = now();
        if (!"PENDING".equals(invitation.getStatus()) || !invitation.getExpiresAt().isAfter(now)) {
            throw conflict("OWNER_INVITATION_EXPIRED", "The invitation has expired or was already used.");
        }
        if (!invitation.getInvitedEmail().equalsIgnoreCase(actor.getEmail())) throw conflict("OWNER_INVITATION_EMAIL_MISMATCH", "Sign in with the verified invited email.");
        if (membershipRepository.countByHotelIdAndRelationshipTypeAndStatus(hotel.getId(), "OWNER", "ACTIVE") >= maxActiveOwners)
            throw conflict("OWNER_LIMIT_REACHED", "The property owner limit has been reached.");
        UserProperty membership = membershipRepository.findOwnerMappingForUpdate(actorId, hotel.getId()).orElseGet(UserProperty::new);
        membership.setUser(actor); membership.setHotel(hotel); membership.setRelationshipType("OWNER"); membership.setStatus("ACTIVE");
        membership.setIsPrimaryOwner(false); membership.setAcceptedAt(now); membership.setStartDate(now); membership.setEndDate(null);
        membership.setLeftAt(null); membership.setRemovedAt(null); membership.setRemovedBy(null); membership.setOwnerExitReason(null);
        membership = membershipRepository.saveAndFlush(membership);
        ownershipLifecycleService.ensureOwnerRole(actor);
        invitation.setStatus("ACCEPTED"); invitation.setAcceptedAt(now); invitation.setAcceptedBy(actor); invitation.setOwnerTermsAcceptedAt(now);
        invitationRepository.saveAndFlush(invitation);
        audit(hotel.getId(), actorId, "OWNER_INVITATION_ACCEPTED", "OWNER_MEMBERSHIP", membership.getId(), Map.of(), Map.of("role", "CO_OWNER", "status", "ACTIVE"));
        notifyOwners(hotel.getId(), "Co-owner invitation accepted", actor.getFullName() + " joined as a co-owner.");
        return owner(membership);
    }

    @Transactional
    public void cancelInvitation(Long propertyId, Long invitationId, Long actorId) {
        Long invitationHotelId = invitationRepository.findHotelIdById(invitationId).orElseThrow(this::notFound);
        if (!same(propertyId, invitationHotelId)) throw notFound();
        lockHotel(propertyId);
        OwnerInvitation invitation = invitationRepository.findByIdForUpdate(invitationId).orElseThrow(this::notFound);
        requirePrimary(propertyId, actorId);
        if (!"PENDING".equals(invitation.getStatus())) throw conflict("OWNER_INVITATION_CONFLICT", "Only a pending invitation can be cancelled.");
        invitation.setStatus("CANCELLED"); invitation.setCancelledAt(now()); invitationRepository.saveAndFlush(invitation);
        audit(propertyId, actorId, "OWNER_INVITATION_CANCELLED", "OWNER_INVITATION", invitationId, Map.of("status", "PENDING"), Map.of("status", "CANCELLED"));
    }

    @Transactional
    public TransferView initiateTransfer(Long propertyId, Long actorId, Long targetUserId, String currentPassword) {
        Hotel hotel = lockHotel(propertyId);
        transferRepository.findFirstByHotelIdAndStatusOrderByCreatedAtDesc(propertyId, "PENDING").ifPresent(existing -> {
            if (existing.getExpiresAt().isAfter(now())) throw conflict("OWNERSHIP_TRANSFER_CONFLICT", "A transfer is already pending.");
            existing.setStatus("EXPIRED"); transferRepository.save(existing);
        });
        UserProperty primary = requirePrimary(propertyId, actorId);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, primary.getUser().getPasswordHash()))
            throw conflict("OWNERSHIP_REAUTH_REQUIRED", "Current password verification failed.");
        UserProperty target = membershipRepository.findOwnerMappingForUpdate(targetUserId, propertyId).orElseThrow(this::notFound);
        requireTransferEligible(target);
        Responsibility responsibility = responsibility(propertyId);
        if (responsibility.pendingRefundCount() > 0 || responsibility.pendingContractChangeCount() > 0)
            throw conflict("OWNERSHIP_TRANSFER_BLOCKED", "Pending subscription financial operations block ownership transfer.");
        OwnershipTransfer transfer = new OwnershipTransfer();
        transfer.setHotel(hotel); transfer.setFromUser(primary.getUser()); transfer.setToUser(target.getUser()); transfer.setStatus("PENDING"); transfer.setExpiresAt(now().plusDays(7));
        transfer = transferRepository.saveAndFlush(transfer);
        audit(propertyId, actorId, "OWNERSHIP_TRANSFER_INITIATED", "OWNERSHIP_TRANSFER", transfer.getId(), Map.of(), Map.of("targetUserId", targetUserId, "status", "PENDING"));
        notificationService.sendUserNotification(targetUserId, "OWNERSHIP_TRANSFER", "Primary ownership transfer", "Review and accept the primary ownership responsibilities.", now());
        return transfer(transfer, responsibility);
    }

    @Transactional
    public OwnershipView acceptTransfer(Long transferId, Long actorId, boolean responsibilityAccepted) {
        if (!responsibilityAccepted) throw conflict("OWNER_RESPONSIBILITY_REQUIRED", "Primary owner responsibilities must be accepted.");
        Long hotelId = transferRepository.findHotelIdById(transferId).orElseThrow(this::notFound);
        Hotel hotel = lockHotel(hotelId);
        OwnershipTransfer transfer = transferRepository.findByIdForUpdate(transferId).orElseThrow(this::notFound);
        if (!same(transfer.getToUser().getId(), actorId)) throw notFound();
        if (!"PENDING".equals(transfer.getStatus()) || !transfer.getExpiresAt().isAfter(now())) throw conflict("OWNERSHIP_TRANSFER_EXPIRED", "The ownership transfer is no longer pending.");
        List<UserProperty> owners = membershipRepository.findActiveOwnerMappingsForUpdate(hotel.getId());
        UserProperty from = owners.stream().filter(m -> same(m.getUser().getId(), transfer.getFromUser().getId()) && Boolean.TRUE.equals(m.getIsPrimaryOwner())).findFirst().orElseThrow(() -> conflict("PRIMARY_OWNER_REQUIRED", "The current primary owner is unavailable."));
        UserProperty to = owners.stream().filter(m -> same(m.getUser().getId(), actorId)).findFirst().orElseThrow(this::notFound);
        requireTransferEligible(to);
        Responsibility responsibility = responsibility(hotel.getId());
        if (responsibility.pendingRefundCount() > 0 || responsibility.pendingContractChangeCount() > 0) throw conflict("OWNERSHIP_TRANSFER_BLOCKED", "Pending subscription financial operations block ownership transfer.");
        from.setIsPrimaryOwner(false); to.setIsPrimaryOwner(true);
        membershipRepository.save(from); membershipRepository.saveAndFlush(to);
        transfer.setStatus("ACCEPTED"); transfer.setAcceptedAt(now()); transfer.setResponsibilityAcceptedAt(now()); transferRepository.saveAndFlush(transfer);
        audit(hotel.getId(), actorId, "OWNERSHIP_TRANSFER_ACCEPTED", "OWNERSHIP_TRANSFER", transfer.getId(), Map.of("primaryUserId", from.getUser().getId()), Map.of("primaryUserId", to.getUser().getId()));
        sessionRevocationService.revokeUserSession(from.getUser().getId(), "OWNERSHIP_TRANSFER");
        sessionRevocationService.revokeUserSession(to.getUser().getId(), "OWNERSHIP_TRANSFER");
        notifyOwners(hotel.getId(), "Primary ownership transferred", to.getUser().getFullName() + " is now the primary owner.");
        return view(hotel.getId(), actorId, membershipRepository.findOwnerMappingsByHotelId(hotel.getId()), null);
    }

    @Transactional
    public void cancelTransfer(Long transferId, Long actorId) {
        Long hotelId = transferRepository.findHotelIdById(transferId).orElseThrow(this::notFound);
        lockHotel(hotelId);
        OwnershipTransfer transfer = transferRepository.findByIdForUpdate(transferId).orElseThrow(this::notFound);
        if (!same(transfer.getFromUser().getId(), actorId)) throw notFound();
        if (!"PENDING".equals(transfer.getStatus())) throw conflict("OWNERSHIP_TRANSFER_CONFLICT", "Only a pending transfer can be cancelled.");
        transfer.setStatus(transfer.getExpiresAt().isAfter(now()) ? "CANCELLED" : "EXPIRED");
        transferRepository.saveAndFlush(transfer);
        audit(hotelId, actorId, "OWNERSHIP_TRANSFER_" + transfer.getStatus(), "OWNERSHIP_TRANSFER", transferId, Map.of("status", "PENDING"), Map.of("status", transfer.getStatus()));
    }

    @Transactional
    public void leave(Long propertyId, Long actorId, String reason) {
        lockHotel(propertyId);
        UserProperty membership = membershipRepository.findOwnerMappingForUpdate(actorId, propertyId).orElseThrow(this::notFound);
        if (!"ACTIVE".equals(membership.getStatus())) throw conflict("OWNER_MEMBERSHIP_NOT_ACTIVE", "Only an active co-owner can leave.");
        if (Boolean.TRUE.equals(membership.getIsPrimaryOwner())) throw conflict("PRIMARY_OWNER_REQUIRED", "Transfer primary ownership before leaving.");
        if (transferRepository.existsByHotelIdAndToUserIdAndStatus(propertyId, actorId, "PENDING")) throw conflict("OWNERSHIP_TRANSFER_CONFLICT", "Resolve the pending ownership transfer before leaving.");
        exit(membership, actorId, "LEFT", reason, null);
    }

    @Transactional
    public void remove(Long propertyId, Long actorId, Long targetUserId, String reason) {
        lockHotel(propertyId); requirePrimary(propertyId, actorId);
        UserProperty target = membershipRepository.findOwnerMappingForUpdate(targetUserId, propertyId).orElseThrow(this::notFound);
        if (!"ACTIVE".equals(target.getStatus())) throw conflict("OWNER_MEMBERSHIP_NOT_ACTIVE", "Only an active co-owner can be removed.");
        if (Boolean.TRUE.equals(target.getIsPrimaryOwner())) throw conflict("PRIMARY_OWNER_REQUIRED", "The primary owner cannot be removed.");
        if (transferRepository.existsByHotelIdAndToUserIdAndStatus(propertyId, targetUserId, "PENDING")) throw conflict("OWNERSHIP_TRANSFER_CONFLICT", "Resolve the pending ownership transfer before removing this owner.");
        exit(target, actorId, "REMOVED", reason, userRepository.findById(actorId).orElseThrow(this::notFound));
    }

    private void exit(UserProperty membership, Long actorId, String status, String reason, User removedBy) {
        String normalizedReason = requireReason(reason); LocalDateTime now = now();
        membership.setStatus(status); membership.setEndDate(now); membership.setOwnerExitReason(normalizedReason); membership.setIsPrimaryOwner(false); membership.setBillingAdmin(false);
        if ("LEFT".equals(status)) membership.setLeftAt(now); else { membership.setRemovedAt(now); membership.setRemovedBy(removedBy); }
        membershipRepository.saveAndFlush(membership);
        ownershipLifecycleService.removeOwnerRoleWhenUnused(membership.getUser());
        audit(membership.getHotel().getId(), actorId, "OWNER_" + status, "OWNER_MEMBERSHIP", membership.getId(), normalizedReason, Map.of("status", "ACTIVE"), Map.of("status", status));
        sessionRevocationService.revokeUserSession(membership.getUser().getId(), "OWNER_" + status);
        notifyOwners(membership.getHotel().getId(), "Property owner changed", membership.getUser().getFullName() + " is no longer an active co-owner.");
    }

    private Responsibility responsibility(Long propertyId) {
        var readiness = financialReadinessGateway.assess(propertyId);
        if (readiness == null || readiness.state() == OwnershipTransferFinancialReadinessGateway.State.UNAVAILABLE)
            throw conflict("OWNERSHIP_FINANCIAL_READINESS_UNAVAILABLE", readiness == null ? "Subscription financial readiness could not be verified." : readiness.reason());
        var disclosure = readiness.disclosure();
        Responsibility result = new Responsibility(disclosure.subscriptionPlan(), disclosure.renewalAt(), disclosure.overdueInvoiceCount(), disclosure.openDisputeCount(), disclosure.pendingRefundCount(), disclosure.pendingContractChangeCount());
        if (readiness.state() == OwnershipTransferFinancialReadinessGateway.State.BLOCKED)
            throw conflict("OWNERSHIP_TRANSFER_BLOCKED", readiness.reason());
        return result;
    }
    private void requireTransferEligible(UserProperty target) { if (!"ACTIVE".equals(target.getStatus()) || Boolean.TRUE.equals(target.getIsPrimaryOwner()) || target.getUser().getEmailVerifiedAt() == null || !"ACTIVE".equals(target.getUser().getStatus())) throw conflict("OWNERSHIP_TRANSFER_INELIGIBLE", "The selected co-owner is not eligible."); requireCoolingComplete(target); }
    private void requireCoolingComplete(UserProperty membership) { if (membership.getAcceptedAt() == null || membership.getAcceptedAt().plusDays(COOLING_DAYS).isAfter(now())) throw conflict("OWNER_COOLING_PERIOD", "The owner seven-day cooling period has not ended."); }
    private UserProperty requirePrimary(Long propertyId, Long actorId) { return membershipRepository.findOwnerMappingForUpdate(actorId, propertyId).filter(m -> "ACTIVE".equals(m.getStatus()) && Boolean.TRUE.equals(m.getIsPrimaryOwner())).orElseThrow(this::notFound); }
    private Hotel lockHotel(Long id) { return hotelRepository.findByIdForUpdate(id).orElseThrow(this::notFound); }
    private OwnershipView view(Long propertyId, Long actorId, List<UserProperty> memberships, OwnershipTransfer pending) { UserProperty actor = memberships.stream().filter(m -> same(m.getUser().getId(), actorId) && "ACTIVE".equals(m.getStatus())).findFirst().orElseThrow(this::notFound); String role = Boolean.TRUE.equals(actor.getIsPrimaryOwner()) ? "PRIMARY_OWNER" : "CO_OWNER"; ActorView actorView = new ActorView(actorId, role, "PRIMARY_OWNER".equals(role), "CO_OWNER".equals(role), "PRIMARY_OWNER".equals(role)); return new OwnershipView(propertyId, actorView, memberships.stream().map(this::owner).toList(), pending == null ? null : transfer(pending, responsibility(propertyId))); }
    private OwnerView owner(UserProperty m) { LocalDateTime cooling = m.getAcceptedAt() == null ? null : m.getAcceptedAt().plusDays(COOLING_DAYS); boolean eligible = "ACTIVE".equals(m.getStatus()) && !Boolean.TRUE.equals(m.getIsPrimaryOwner()) && cooling != null && !cooling.isAfter(now()) && m.getUser().getEmailVerifiedAt() != null && "ACTIVE".equals(m.getUser().getStatus()); return new OwnerView(m.getId(), m.getUser().getId(), m.getUser().getFullName(), m.getUser().getEmail(), Boolean.TRUE.equals(m.getIsPrimaryOwner()) ? "PRIMARY_OWNER" : "CO_OWNER", m.getStatus(), m.getAcceptedAt(), cooling, Boolean.TRUE.equals(m.getBillingAdmin()), Boolean.TRUE.equals(m.getIsPrimaryOwner()), eligible); }
    private TransferView transfer(OwnershipTransfer t, Responsibility r) { return new TransferView(t.getId(), t.getStatus(), t.getExpiresAt(), t.getToUser().getId(), r); }
    private void notifyOwners(Long propertyId, String title, String message) { membershipRepository.findOwnerMappingsByHotelId(propertyId).stream().filter(m -> "ACTIVE".equals(m.getStatus())).forEach(m -> notificationService.sendUserNotification(m.getUser().getId(), "OWNERSHIP", title, message, now())); }
    private void sendInvitationAfterCommit(Long invitationId, String email, String propertyName, String token) { var attributes = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet ? servlet : null; String correlationId = attributes == null ? null : attributes.getRequest().getHeader(CorrelationIdSupport.HEADER); String ip = attributes == null ? null : attributes.getRequest().getRemoteAddr(); String userAgent = attributes == null ? null : attributes.getRequest().getHeader("User-Agent"); TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { invitationTransitionService.deliverOrCancel(invitationId, email, propertyName, token, correlationId, ip, userAgent); } }); }
    private void audit(Long propertyId, Long actorId, String event, String type, Long id, Object before, Object after) { audit(propertyId, actorId, event, type, id, event.replace('_', ' '), before, after); }
    private void audit(Long propertyId, Long actorId, String event, String type, Long id, String reason, Object before, Object after) { var attributes = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet ? servlet : null; Map<String,Object> contextual = new LinkedHashMap<>(); if (after instanceof Map<?,?> values) values.forEach((key,value) -> contextual.put(String.valueOf(key), value)); if (attributes != null) { contextual.put("ip", attributes.getRequest().getRemoteAddr()); contextual.put("userAgent", attributes.getRequest().getHeader("User-Agent")); } if (reason != null) contextual.put("reason", reason); auditService.append(new OperationalAuditService.AuditCommand("TENANT", propertyId, "OWNERSHIP", event, type, String.valueOf(id), "USER", actorId, reason == null || reason.isBlank() ? event.replace('_', ' ') : reason, before, contextual, attributes == null ? null : attributes.getRequest().getHeader(CorrelationIdSupport.HEADER))); }
    private String rawToken() { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String hash(String value) { if (value == null || value.isBlank()) throw conflict("OWNER_INVITATION_INVALID", "The invitation token is required."); try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String normalizeEmail(String email) { if (email == null || email.isBlank()) throw new IllegalArgumentException("Invitation email is required."); return email.trim().toLowerCase(Locale.ROOT); }
    private String requireReason(String reason) { if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Ownership change reason is required."); String normalized = reason.trim(); if (normalized.length() > 500) throw new IllegalArgumentException("Ownership change reason must contain at most 500 characters."); return normalized; }
    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    private boolean same(Long a, Long b) { return Objects.equals(a, b); }
    private OwnershipLifecycleException notFound() { return new OwnershipLifecycleException("OWNERSHIP_NOT_FOUND", "Ownership resource was not found."); }
    private OwnershipLifecycleException conflict(String code, String message) { return new OwnershipLifecycleException(code, message); }

    public record OwnerView(Long membershipId, Long userId, String fullName, String email, String role, String status, LocalDateTime acceptedAt, LocalDateTime coolingEndsAt, boolean billingAdmin, boolean canManageOwners, boolean canReceivePrimary) {}
    public record ActorView(Long userId, String role, boolean canInvite, boolean canLeave, boolean canTransferPrimary) {}
    public record Responsibility(String subscriptionPlan, LocalDateTime renewalAt, int overdueInvoiceCount, int openDisputeCount, int pendingRefundCount, int pendingContractChangeCount) {}
    public record TransferView(Long transferId, String status, LocalDateTime expiresAt, Long targetUserId, Responsibility responsibility) {}
    public record OwnershipView(Long propertyId, ActorView actor, List<OwnerView> owners, TransferView pendingTransfer) {}
    public record InvitationResult(Long invitationId, String email, String status, LocalDateTime expiresAt) {}
}
