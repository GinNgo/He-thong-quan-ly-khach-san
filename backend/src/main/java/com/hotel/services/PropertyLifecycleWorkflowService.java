package com.hotel.services;

import com.hotel.dtos.PropertyLifecycleDecisionResponse;
import com.hotel.dtos.PropertyLifecycleSummary;
import com.hotel.entities.Hotel;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.UserPropertyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class PropertyLifecycleWorkflowService {

    private static final int MIN_REASON_LENGTH = 10;
    private static final int MAX_REASON_LENGTH = 500;

    private final HotelRepository hotelRepository;
    private final UserPropertyRepository userPropertyRepository;
    private final OperationalAuditService operationalAuditService;
    private final NotificationService notificationService;
    private final Clock clock;

    @Autowired
    public PropertyLifecycleWorkflowService(
            HotelRepository hotelRepository,
            UserPropertyRepository userPropertyRepository,
            OperationalAuditService operationalAuditService,
            NotificationService notificationService) {
        this(hotelRepository, userPropertyRepository, operationalAuditService,
                notificationService, Clock.systemUTC());
    }

    PropertyLifecycleWorkflowService(
            HotelRepository hotelRepository,
            UserPropertyRepository userPropertyRepository,
            OperationalAuditService operationalAuditService,
            NotificationService notificationService,
            Clock clock) {
        this.hotelRepository = hotelRepository;
        this.userPropertyRepository = userPropertyRepository;
        this.operationalAuditService = operationalAuditService;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PropertyLifecycleSummary> properties() {
        return hotelRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(this::summary)
                .toList();
    }

    @Transactional
    public PropertyLifecycleDecisionResponse suspend(Long actorUserId, Long propertyId, String reason) {
        return transition(actorUserId, propertyId, reason, Transition.SUSPEND);
    }

    @Transactional
    public PropertyLifecycleDecisionResponse reactivate(Long actorUserId, Long propertyId, String reason) {
        return transition(actorUserId, propertyId, reason, Transition.REACTIVATE);
    }

    @Transactional
    public PropertyLifecycleDecisionResponse close(Long actorUserId, Long propertyId, String reason) {
        return transition(actorUserId, propertyId, reason, Transition.CLOSE);
    }

    private PropertyLifecycleDecisionResponse transition(
            Long actorUserId,
            Long propertyId,
            String reason,
            Transition transition) {
        requireActor(actorUserId);
        requirePropertyId(propertyId);
        String validatedReason = validateReason(reason);

        Hotel property = hotelRepository.findByIdForUpdate(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property was not found."));

        if (isExactReplay(property, transition, actorUserId, validatedReason)) {
            return decision(property, transition, false);
        }
        transition.requireSource(property);

        Map<String, Object> before = snapshot(property);
        LocalDateTime changedAt = LocalDateTime.now(clock);
        property.setStatus(transition.targetStatus);
        property.setApprovalStatus("APPROVED");
        property.setOperationStatus(transition.targetOperationStatus);
        property.setLifecycleAction(transition.name());
        property.setLifecycleReason(validatedReason);
        property.setLifecycleChangedByUserId(actorUserId);
        property.setLifecycleChangedAt(changedAt);
        Hotel saved = hotelRepository.saveAndFlush(property);

        operationalAuditService.append(new OperationalAuditService.AuditCommand(
                "TENANT",
                saved.getId(),
                "PROPERTY",
                transition.eventType,
                "HOTEL",
                String.valueOf(saved.getId()),
                "USER",
                actorUserId,
                validatedReason,
                before,
                snapshot(saved),
                null));

        notifyAssignedUsers(saved, transition, validatedReason, changedAt);
        return decision(saved, transition, true);
    }

    private void notifyAssignedUsers(
            Hotel property,
            Transition transition,
            String reason,
            LocalDateTime changedAt) {
        String propertyName = displayName(property);
        String title = switch (transition) {
            case SUSPEND -> "Property suspended";
            case REACTIVATE -> "Property reactivated";
            case CLOSE -> "Property closed";
        };
        String message = switch (transition) {
            case SUSPEND -> propertyName + " has been suspended. Reason: " + reason;
            case REACTIVATE -> propertyName + " has been reactivated. Reason: " + reason;
            case CLOSE -> propertyName + " has been closed. Historical records remain available. Reason: " + reason;
        };
        for (Long userId : userPropertyRepository.findActiveAssignedUserIdsByHotelId(property.getId())) {
            notificationService.sendUserNotification(
                    userId, "PROPERTY_LIFECYCLE", title, message, changedAt);
        }
    }

    private PropertyLifecycleSummary summary(Hotel property) {
        return new PropertyLifecycleSummary(
                property.getId(),
                property.getCode(),
                displayName(property),
                property.getAddressLine(),
                property.getPropertyType(),
                property.getStatus(),
                property.getApprovalStatus(),
                property.getOperationStatus(),
                property.getLifecycleAction(),
                property.getLifecycleReason(),
                property.getLifecycleChangedByUserId(),
                property.getLifecycleChangedAt(),
                allowedTransitions(property));
    }

    private List<String> allowedTransitions(Hotel property) {
        if (Transition.SUSPEND.sourceMatches(property)) {
            return List.of(Transition.SUSPEND.name(), Transition.CLOSE.name());
        }
        if (Transition.REACTIVATE.sourceMatches(property)) {
            return List.of(Transition.REACTIVATE.name(), Transition.CLOSE.name());
        }
        return List.of();
    }

    private PropertyLifecycleDecisionResponse decision(
            Hotel property,
            Transition transition,
            boolean changed) {
        return new PropertyLifecycleDecisionResponse(
                property.getId(),
                property.getStatus(),
                property.getApprovalStatus(),
                property.getOperationStatus(),
                transition.name(),
                changed,
                property.getLifecycleChangedByUserId(),
                property.getLifecycleChangedAt(),
                property.getLifecycleReason());
    }

    private boolean isExactReplay(
            Hotel property,
            Transition transition,
            Long actorUserId,
            String reason) {
        return transition.targetMatches(property)
                && transition.name().equals(normalize(property.getLifecycleAction()))
                && Objects.equals(actorUserId, property.getLifecycleChangedByUserId())
                && reason.equals(property.getLifecycleReason());
    }

    private Map<String, Object> snapshot(Hotel property) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("propertyId", property.getId());
        state.put("propertyName", displayName(property));
        state.put("status", property.getStatus());
        state.put("approvalStatus", property.getApprovalStatus());
        state.put("operationStatus", property.getOperationStatus());
        state.put("lifecycleAction", property.getLifecycleAction());
        state.put("lifecycleReason", property.getLifecycleReason());
        state.put("lifecycleChangedByUserId", property.getLifecycleChangedByUserId());
        state.put("lifecycleChangedAt", property.getLifecycleChangedAt());
        return state;
    }

    private String validateReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < MIN_REASON_LENGTH || normalized.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException(
                    "Lifecycle reason must contain between 10 and 500 characters.");
        }
        return normalized;
    }

    private void requireActor(Long actorUserId) {
        if (actorUserId == null) {
            throw new AccessDeniedException("Authenticated account id is required.");
        }
    }

    private void requirePropertyId(Long propertyId) {
        if (propertyId == null) {
            throw new IllegalArgumentException("Property id is required.");
        }
    }

    private String displayName(Hotel property) {
        if (property.getNameVi() != null && !property.getNameVi().isBlank()) {
            return property.getNameVi();
        }
        return property.getName();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private enum Transition {
        SUSPEND("ACTIVE", "ACTIVE", "SUSPENDED", "SUSPENDED", "PROPERTY_SUSPENDED"),
        REACTIVATE("SUSPENDED", "SUSPENDED", "ACTIVE", "ACTIVE", "PROPERTY_REACTIVATED"),
        CLOSE(null, null, "CLOSED", "CLOSED", "PROPERTY_CLOSED");

        private final String sourceStatus;
        private final String sourceOperationStatus;
        private final String targetStatus;
        private final String targetOperationStatus;
        private final String eventType;

        Transition(
                String sourceStatus,
                String sourceOperationStatus,
                String targetStatus,
                String targetOperationStatus,
                String eventType) {
            this.sourceStatus = sourceStatus;
            this.sourceOperationStatus = sourceOperationStatus;
            this.targetStatus = targetStatus;
            this.targetOperationStatus = targetOperationStatus;
            this.eventType = eventType;
        }

        private void requireSource(Hotel property) {
            if (!sourceMatches(property)) {
                throw new IllegalStateException("Property lifecycle transition is not allowed from the current state.");
            }
        }

        private boolean sourceMatches(Hotel property) {
            if (!"APPROVED".equals(normalize(property.getApprovalStatus()))) {
                return false;
            }
            if (this == CLOSE) {
                return ("ACTIVE".equals(normalize(property.getStatus()))
                        && "ACTIVE".equals(normalize(property.getOperationStatus())))
                        || ("SUSPENDED".equals(normalize(property.getStatus()))
                        && "SUSPENDED".equals(normalize(property.getOperationStatus())));
            }
            return sourceStatus.equals(normalize(property.getStatus()))
                    && sourceOperationStatus.equals(normalize(property.getOperationStatus()));
        }

        private boolean targetMatches(Hotel property) {
            return "APPROVED".equals(normalize(property.getApprovalStatus()))
                    && targetStatus.equals(normalize(property.getStatus()))
                    && targetOperationStatus.equals(normalize(property.getOperationStatus()));
        }
    }
}
