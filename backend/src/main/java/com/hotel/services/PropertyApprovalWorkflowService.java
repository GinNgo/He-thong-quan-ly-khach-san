package com.hotel.services;

import com.hotel.dtos.PropertyApprovalDecisionResponse;
import com.hotel.dtos.PropertyApprovalQueueItem;
import com.hotel.dtos.PropertyApprovalSubmissionResponse;
import com.hotel.entities.Hotel;
import com.hotel.entities.OperationalAuditEvent;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.propertyreview.PropertyReviewEmailOutboxService;
import com.hotel.propertyreview.PropertyReviewInAppNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PropertyApprovalWorkflowService {

    private static final int MIN_REJECTION_REASON_LENGTH = 10;
    private static final int MAX_REJECTION_REASON_LENGTH = 500;

    private final HotelRepository hotelRepository;
    private final UserPropertyRepository userPropertyRepository;
    private final PropertyOwnershipLifecycleService ownershipLifecycleService;
    private final OperationalAuditService operationalAuditService;
    private final NotificationService legacyNotificationService;
    private final PropertyReviewInAppNotificationService inAppNotificationService;
    private final PropertyReviewEmailOutboxService emailOutboxService;
    private final Clock clock;

    @Autowired
    public PropertyApprovalWorkflowService(
            HotelRepository hotelRepository,
            UserPropertyRepository userPropertyRepository,
            PropertyOwnershipLifecycleService ownershipLifecycleService,
            OperationalAuditService operationalAuditService,
            PropertyReviewInAppNotificationService inAppNotificationService,
            PropertyReviewEmailOutboxService emailOutboxService) {
        this(hotelRepository, userPropertyRepository, ownershipLifecycleService,
                operationalAuditService, null, inAppNotificationService,
                emailOutboxService, Clock.systemUTC());
    }

    PropertyApprovalWorkflowService(
            HotelRepository hotelRepository,
            UserPropertyRepository userPropertyRepository,
            PropertyOwnershipLifecycleService ownershipLifecycleService,
            OperationalAuditService operationalAuditService,
            NotificationService notificationService,
            Clock clock) {
        this(hotelRepository, userPropertyRepository, ownershipLifecycleService,
                operationalAuditService, notificationService, null, null, clock);
    }

    PropertyApprovalWorkflowService(
            HotelRepository hotelRepository,
            UserPropertyRepository userPropertyRepository,
            PropertyOwnershipLifecycleService ownershipLifecycleService,
            OperationalAuditService operationalAuditService,
            NotificationService legacyNotificationService,
            PropertyReviewInAppNotificationService inAppNotificationService,
            PropertyReviewEmailOutboxService emailOutboxService,
            Clock clock) {
        this.hotelRepository = hotelRepository;
        this.userPropertyRepository = userPropertyRepository;
        this.ownershipLifecycleService = ownershipLifecycleService;
        this.operationalAuditService = operationalAuditService;
        this.legacyNotificationService = legacyNotificationService;
        this.inAppNotificationService = inAppNotificationService;
        this.emailOutboxService = emailOutboxService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PropertyApprovalQueueItem> pendingApprovals() {
        return userPropertyRepository.findPropertyApprovalQueue().stream()
                .map(this::toQueueItem)
                .toList();
    }

    @Transactional
    public PropertyApprovalSubmissionResponse submitDraft(Long authenticatedUserId, Long propertyId) {
        requireAuthenticatedUser(authenticatedUserId);
        requirePropertyId(propertyId);

        Hotel property = hotelRepository.findByIdForUpdate(propertyId)
                .orElseThrow(this::notFound);
        UserProperty pendingOwner = userPropertyRepository
                .findPendingOwnerMappingForUpdate(authenticatedUserId, propertyId)
                .orElseThrow(this::notFound);
        requireDraft(property);

        Map<String, Object> before = snapshot(property, pendingOwner.getStatus());
        LocalDateTime submittedAt = now();
        property.setStatus("PENDING_APPROVAL");
        property.setApprovalStatus("PENDING_APPROVAL");
        property.setOperationStatus("INACTIVE");
        property.setSubmittedByUserId(authenticatedUserId);
        property.setSubmittedAt(submittedAt);
        property.setReviewedByUserId(null);
        property.setReviewedAt(null);
        property.setReviewReason(null);
        Hotel saved = hotelRepository.saveAndFlush(property);

        OperationalAuditEvent auditEvent = operationalAuditService.append(new OperationalAuditService.AuditCommand(
                "TENANT",
                saved.getId(),
                "PROPERTY",
                "PROPERTY_SUBMITTED_FOR_APPROVAL",
                "HOTEL",
                String.valueOf(saved.getId()),
                "USER",
                authenticatedUserId,
                "Owner submitted property for approval",
                before,
                snapshot(saved, pendingOwner.getStatus()),
                null));
        notifyOwner(
                auditEvent,
                pendingOwner.getUser(),
                saved,
                submittedAt,
                "Property submitted for review",
                "Your property " + displayName(saved) + " was submitted for review.");

        return new PropertyApprovalSubmissionResponse(
                saved.getId(),
                saved.getStatus(),
                saved.getApprovalStatus(),
                saved.getOperationStatus(),
                saved.getSubmittedByUserId(),
                saved.getSubmittedAt());
    }

    @Transactional
    public PropertyApprovalDecisionResponse approve(Long reviewerUserId, Long propertyId) {
        return approve(reviewerUserId, propertyId, null);
    }

    @Transactional
    public PropertyApprovalDecisionResponse approve(Long reviewerUserId, Long propertyId, String reviewerNote) {
        requireAuthenticatedUser(reviewerUserId);
        requirePropertyId(propertyId);
        String validatedNote = validateOptionalReviewerNote(reviewerNote);

        Hotel property = lockPendingProperty(propertyId);
        UserProperty pendingOwner = lockSinglePendingOwner(propertyId);
        Map<String, Object> before = snapshot(property, pendingOwner.getStatus());
        LocalDateTime reviewedAt = now();

        UserProperty activeOwner = ownershipLifecycleService.activateOwner(
                propertyId, pendingOwner.getUser().getId());
        property.setStatus("ACTIVE");
        property.setApprovalStatus("APPROVED");
        property.setOperationStatus("ACTIVE");
        property.setReviewedByUserId(reviewerUserId);
        property.setReviewedAt(reviewedAt);
        property.setReviewReason(validatedNote);
        Hotel saved = hotelRepository.saveAndFlush(property);

        OperationalAuditEvent auditEvent = appendDecisionAudit(
                "PROPERTY_APPROVED",
                reviewerUserId,
                validatedNote == null ? "Property approval completed" : validatedNote,
                before,
                snapshot(saved, activeOwner.getStatus()),
                saved);
        notifyOwner(
                auditEvent,
                pendingOwner.getUser(),
                saved,
                reviewedAt,
                "Property approved",
                approvedMessage(saved, validatedNote));

        return decision(saved, activeOwner.getStatus());
    }

    @Transactional
    public PropertyApprovalDecisionResponse reject(Long reviewerUserId, Long propertyId, String reason) {
        requireAuthenticatedUser(reviewerUserId);
        requirePropertyId(propertyId);
        String validatedReason = validateRejectionReason(reason);

        Hotel property = lockPendingProperty(propertyId);
        UserProperty pendingOwner = lockSinglePendingOwner(propertyId);
        Map<String, Object> before = snapshot(property, pendingOwner.getStatus());
        LocalDateTime reviewedAt = now();

        if (!ownershipLifecycleService.deactivatePendingOwner(propertyId, pendingOwner.getUser().getId())) {
            throw new IllegalStateException("Pending property owner mapping is no longer actionable.");
        }
        property.setStatus("REJECTED");
        property.setApprovalStatus("REJECTED");
        property.setOperationStatus("INACTIVE");
        property.setReviewedByUserId(reviewerUserId);
        property.setReviewedAt(reviewedAt);
        property.setReviewReason(validatedReason);
        Hotel saved = hotelRepository.saveAndFlush(property);

        OperationalAuditEvent auditEvent = appendDecisionAudit(
                "PROPERTY_REJECTED",
                reviewerUserId,
                validatedReason,
                before,
                snapshot(saved, "INACTIVE"),
                saved);
        notifyOwner(
                auditEvent,
                pendingOwner.getUser(),
                saved,
                reviewedAt,
                "Property review rejected",
                "Your property " + displayName(saved) + " was rejected. Reason: " + validatedReason);

        return decision(saved, "INACTIVE");
    }

    private Hotel lockPendingProperty(Long propertyId) {
        Hotel property = hotelRepository.findByIdForUpdate(propertyId)
                .orElseThrow(this::notFound);
        if (!"PENDING_APPROVAL".equals(normalize(property.getStatus()))
                || !"PENDING_APPROVAL".equals(normalize(property.getApprovalStatus()))
                || !"INACTIVE".equals(normalize(property.getOperationStatus()))) {
            throw new IllegalStateException("Only a pending property can be reviewed.");
        }
        return property;
    }

    private UserProperty lockSinglePendingOwner(Long propertyId) {
        List<UserProperty> pendingOwners = userPropertyRepository.findPendingOwnerMappingsForUpdate(propertyId);
        if (pendingOwners.isEmpty()) {
            throw new IllegalStateException("Pending property owner mapping is required.");
        }
        if (pendingOwners.size() > 1) {
            throw new IllegalStateException("Property has multiple pending owner mappings and cannot be reviewed safely.");
        }
        return pendingOwners.getFirst();
    }

    private void requireDraft(Hotel property) {
        if (!"DRAFT".equals(normalize(property.getStatus()))
                || !"DRAFT".equals(normalize(property.getApprovalStatus()))
                || !"INACTIVE".equals(normalize(property.getOperationStatus()))) {
            throw new IllegalStateException("Only a draft property can be submitted for approval.");
        }
    }

    private OperationalAuditEvent appendDecisionAudit(
            String eventType,
            Long reviewerUserId,
            String reason,
            Map<String, Object> before,
            Map<String, Object> after,
            Hotel property) {
        return operationalAuditService.append(new OperationalAuditService.AuditCommand(
                "TENANT",
                property.getId(),
                "PROPERTY",
                eventType,
                "HOTEL",
                String.valueOf(property.getId()),
                "USER",
                reviewerUserId,
                reason,
                before,
                after,
                null));
    }

    private void notifyOwner(
            OperationalAuditEvent auditEvent,
            User owner,
            Hotel property,
            LocalDateTime createdAt,
            String title,
            String message) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalStateException("Pending property owner account is unavailable.");
        }
        if (inAppNotificationService != null) {
            inAppNotificationService.send(
                    owner.getId(), "PROPERTY_APPROVAL", title, message, createdAt);
        } else {
            legacyNotificationService.sendUserNotification(
                    owner.getId(), "PROPERTY_APPROVAL", title, message, createdAt);
        }
        if (emailOutboxService != null) {
            if (auditEvent == null || auditEvent.getId() == null) {
                throw new IllegalStateException("Property transition audit evidence is unavailable.");
            }
            emailOutboxService.enqueue(
                    auditEvent.getId(),
                    property.getId(),
                    owner.getId(),
                    owner.getEmail(),
                    title,
                    message,
                    createdAt);
        }
    }

    private PropertyApprovalQueueItem toQueueItem(UserProperty mapping) {
        Hotel property = mapping.getHotel();
        User owner = mapping.getUser();
        return new PropertyApprovalQueueItem(
                property.getId(),
                property.getCode(),
                displayName(property),
                property.getAddressLine(),
                property.getPropertyType(),
                property.getStatus(),
                property.getApprovalStatus(),
                property.getOperationStatus(),
                mapping.getStatus(),
                owner == null ? null : owner.getId(),
                owner == null ? null : owner.getFullName(),
                owner == null ? null : owner.getEmail(),
                property.getSubmittedByUserId(),
                property.getSubmittedAt(),
                property.getReviewedByUserId(),
                property.getReviewedAt(),
                property.getReviewReason());
    }

    private PropertyApprovalDecisionResponse decision(Hotel property, String ownershipStatus) {
        return new PropertyApprovalDecisionResponse(
                property.getId(),
                property.getStatus(),
                property.getApprovalStatus(),
                property.getOperationStatus(),
                ownershipStatus,
                property.getReviewedByUserId(),
                property.getReviewedAt(),
                property.getReviewReason());
    }

    private Map<String, Object> snapshot(Hotel property, String ownershipStatus) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("propertyId", property.getId());
        state.put("propertyName", displayName(property));
        state.put("status", property.getStatus());
        state.put("approvalStatus", property.getApprovalStatus());
        state.put("operationStatus", property.getOperationStatus());
        state.put("ownershipStatus", ownershipStatus);
        state.put("submittedByUserId", property.getSubmittedByUserId());
        state.put("submittedAt", property.getSubmittedAt());
        state.put("reviewedByUserId", property.getReviewedByUserId());
        state.put("reviewedAt", property.getReviewedAt());
        state.put("reviewReason", property.getReviewReason());
        return state;
    }

    private String validateRejectionReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < MIN_REJECTION_REASON_LENGTH
                || normalized.length() > MAX_REJECTION_REASON_LENGTH) {
            throw new IllegalArgumentException(
                    "Rejection reason must contain between 10 and 500 characters.");
        }
        return normalized;
    }

    private String validateOptionalReviewerNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String normalized = note.trim();
        if (normalized.length() > MAX_REJECTION_REASON_LENGTH) {
            throw new IllegalArgumentException("Reviewer note must contain at most 500 characters.");
        }
        return normalized;
    }

    private String approvedMessage(Hotel property, String reviewerNote) {
        String message = "Your property " + displayName(property) + " has been approved and is now active.";
        return reviewerNote == null ? message : message + " Note: " + reviewerNote;
    }

    private void requireAuthenticatedUser(Long userId) {
        if (userId == null) {
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

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Property was not found.");
    }
}
