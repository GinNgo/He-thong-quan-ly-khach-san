package com.hotel.platformbilling.subscription;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.User;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.services.AuthSessionRevocationService;
import com.hotel.services.PropertyAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Service
public class SubscriptionLifecycleService {
    private final HotelRepository hotelRepository;
    private final SubscriptionEntitlementRepository entitlementRepository;
    private final PlatformSoftwareContractRepository contractRepository;
    private final PlatformSubscriptionHistoryRepository historyRepository;
    private final PropertyAccessService propertyAccessService;
    private final UserPropertyRepository userPropertyRepository;
    private final AuthSessionRevocationService sessionRevocationService;
    private final FinancialAuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public SubscriptionLifecycleService(HotelRepository hotelRepository,
            SubscriptionEntitlementRepository entitlementRepository,
            PlatformSoftwareContractRepository contractRepository,
            PlatformSubscriptionHistoryRepository historyRepository,
            PropertyAccessService propertyAccessService,
            UserPropertyRepository userPropertyRepository,
            AuthSessionRevocationService sessionRevocationService,
            FinancialAuditService auditService, ObjectMapper objectMapper) {
        this(hotelRepository, entitlementRepository, contractRepository, historyRepository, propertyAccessService,
                userPropertyRepository, sessionRevocationService, auditService, objectMapper, Clock.systemUTC());
    }

    SubscriptionLifecycleService(HotelRepository hotelRepository,
            SubscriptionEntitlementRepository entitlementRepository,
            PlatformSoftwareContractRepository contractRepository,
            PlatformSubscriptionHistoryRepository historyRepository,
            PropertyAccessService propertyAccessService,
            UserPropertyRepository userPropertyRepository,
            AuthSessionRevocationService sessionRevocationService,
            FinancialAuditService auditService, ObjectMapper objectMapper, Clock clock) {
        this.hotelRepository = hotelRepository; this.entitlementRepository = entitlementRepository;
        this.contractRepository = contractRepository; this.historyRepository = historyRepository;
        this.propertyAccessService = propertyAccessService; this.userPropertyRepository = userPropertyRepository;
        this.sessionRevocationService = sessionRevocationService; this.auditService = auditService;
        this.objectMapper = objectMapper; this.clock = clock;
    }

    @Transactional
    public LifecycleResult revoke(Long hotelId, String reason, String ip, String userAgent, String correlationId) {
        propertyAccessService.requireAssignedHotel(hotelId);
        User actor = propertyAccessService.currentUser();
        String safeReason = requireReason(reason);
        LifecycleResult expiry = expireIfDue(hotelId);
        if (expiry.contractStatus() == SoftwareContract.Status.EXPIRED) return expiry;
        return transition(hotelId, SoftwareContract.Status.REVOKED, SubscriptionEntitlement.Status.REVOKED,
                SubscriptionHistory.ActionType.REVOKED, "USER", actor == null ? null : actor.getId(), safeReason,
                ip, userAgent, correlationId, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LifecycleResult expireIfDue(Long hotelId) {
        return transition(hotelId, SoftwareContract.Status.EXPIRED, SubscriptionEntitlement.Status.EXPIRED,
                SubscriptionHistory.ActionType.EXPIRED, "SYSTEM", null, "Contract effective period elapsed",
                null, null, "SUBSCRIPTION-EXPIRY:" + hotelId, true);
    }

    private LifecycleResult transition(Long hotelId, SoftwareContract.Status contractTarget,
            SubscriptionEntitlement.Status entitlementTarget, SubscriptionHistory.ActionType action,
            String actorType, Long actorId, String reason, String ip, String userAgent, String correlationId,
            boolean requireDue) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        hotelRepository.findByIdForUpdate(hotelId)
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        SubscriptionEntitlement entitlement = entitlementRepository.findByTargetHotelIdForUpdate(hotelId)
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        SoftwareContract contract = contractRepository.findByPublicIdForUpdate(entitlement.getContract().getPublicId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION));
        if (entitlement.getStatus() == entitlementTarget && contract.getStatus() == contractTarget) {
            return result(hotelId, contract, entitlement, false, now);
        }
        if (entitlement.getStatus() != SubscriptionEntitlement.Status.ACTIVE
                || contract.getStatus() != SoftwareContract.Status.ACTIVE) {
            if (requireDue) return result(hotelId, contract, entitlement, false, now);
            throw conflict(entitlement.getStatus() + "/" + contract.getStatus());
        }
        if (requireDue && (entitlement.isLifetime() || entitlement.getEffectiveUntil() == null
                || entitlement.getEffectiveUntil().isAfter(now))) {
            return result(hotelId, contract, entitlement, false, now);
        }
        String previous = state(contract, entitlement);
        contract.transitionTo(contractTarget);
        entitlement.transitionTo(entitlementTarget);
        contractRepository.saveAndFlush(contract);
        entitlementRepository.saveAndFlush(entitlement);
        if (!historyRepository.existsByOrderIdAndActionType(contract.getOrder().getId(), action)) {
            historyRepository.saveAndFlush(SubscriptionHistory.record(contract.getOrder(), contract, null, action,
                    previous, state(contract, entitlement), actorType, actorId, reason, now));
        }
        auditService.append(new FinancialAuditService.AuditCommand("PLATFORM_BILLING", hotelId,
                "PLATFORM_SUBSCRIPTION", contract.getPublicId(), actorType, actorId, "PLATFORM",
                previous, state(contract, entitlement), reason, action + ":" + contract.getPublicId(), null,
                correlationId, Map.of("ip", safe(ip), "userAgent", safe(userAgent), "action", action.name())));
        userPropertyRepository.findActiveAssignedUsersByHotelId(hotelId)
                .forEach(user -> sessionRevocationService.revokeUserSession(user.getId(), "SUBSCRIPTION_" + action));
        return result(hotelId, contract, entitlement, true, now);
    }

    private LifecycleResult result(Long hotelId, SoftwareContract contract, SubscriptionEntitlement entitlement,
                                   boolean transitioned, LocalDateTime occurredAt) {
        return new LifecycleResult(hotelId, contract.getPublicId(), contract.getStatus(), entitlement.getStatus(),
                transitioned, occurredAt);
    }

    private String state(SoftwareContract contract, SubscriptionEntitlement entitlement) {
        try { return objectMapper.writeValueAsString(Map.of("contract", contract.getStatus().name(),
                "entitlement", entitlement.getStatus().name())); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Unable to serialize lifecycle state.", ex); }
    }

    private FinancialException conflict(Object state) {
        return new FinancialException(FinancialErrorCode.INVALID_STATE_TRANSITION,
                "Subscription lifecycle is already terminal.", null, String.valueOf(state), null);
    }
    private String requireReason(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("reason is required.");
        String normalized = value.trim();
        if (normalized.length() < 10 || normalized.length() > 1000)
            throw new IllegalArgumentException("reason must contain 10 to 1000 characters.");
        return normalized;
    }
    private String safe(String value) { return value == null ? "" : value.substring(0, Math.min(value.length(), 500)); }

    public record LifecycleResult(Long targetHotelId, String contractPublicId,
            SoftwareContract.Status contractStatus, SubscriptionEntitlement.Status entitlementStatus,
            boolean transitioned, LocalDateTime occurredAt) {}
}
