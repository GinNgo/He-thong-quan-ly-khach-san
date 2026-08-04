package com.hotel.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.dtos.PropertyReviewHistoryItem;
import com.hotel.entities.OperationalAuditEvent;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.OperationalAuditEventRepository;
import com.hotel.repositories.UserPropertyRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class PropertyReviewHistoryService {

    private static final Set<String> TRANSITION_EVENTS = Set.of(
            "PROPERTY_SUBMITTED_FOR_APPROVAL",
            "PROPERTY_APPROVED",
            "PROPERTY_REJECTED",
            "PROPERTY_SUSPENDED",
            "PROPERTY_REACTIVATED",
            "PROPERTY_CLOSED");

    private final OperationalAuditEventRepository auditRepository;
    private final UserPropertyRepository userPropertyRepository;
    private final HotelRepository hotelRepository;
    private final ObjectMapper objectMapper;

    public PropertyReviewHistoryService(
            OperationalAuditEventRepository auditRepository,
            UserPropertyRepository userPropertyRepository,
            HotelRepository hotelRepository,
            ObjectMapper objectMapper) {
        this.auditRepository = auditRepository;
        this.userPropertyRepository = userPropertyRepository;
        this.hotelRepository = hotelRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PropertyReviewHistoryItem> ownerHistory(
            Long ownerUserId,
            Long propertyId) {
        if (ownerUserId == null) {
            throw new AccessDeniedException("Authenticated account id is required.");
        }
        requirePropertyId(propertyId);
        if (!userPropertyRepository.existsByUserIdAndHotelIdAndRelationshipType(
                ownerUserId, propertyId, "OWNER")) {
            throw notFound();
        }
        return history(propertyId);
    }

    @Transactional(readOnly = true)
    public List<PropertyReviewHistoryItem> adminHistory(Long propertyId) {
        requirePropertyId(propertyId);
        if (!hotelRepository.existsById(propertyId)) {
            throw notFound();
        }
        return history(propertyId);
    }

    private List<PropertyReviewHistoryItem> history(Long propertyId) {
        return auditRepository.findPropertyTransitionHistory(
                        propertyId,
                        String.valueOf(propertyId),
                        TRANSITION_EVENTS,
                        PageRequest.of(0, 100, Sort.by(
                                Sort.Order.desc("occurredAt"),
                                Sort.Order.desc("id"))))
                .stream()
                .map(this::toItem)
                .toList();
    }

    private PropertyReviewHistoryItem toItem(OperationalAuditEvent event) {
        return new PropertyReviewHistoryItem(
                event.getId(),
                event.getHotelId(),
                event.getEventType(),
                actorKind(event.getEventType()),
                event.getReason(),
                triplet(event.getBeforeStateJson()),
                triplet(event.getAfterStateJson()),
                event.getOccurredAt());
    }

    private PropertyReviewHistoryItem.StatusTriplet triplet(String stateJson) {
        if (stateJson == null || stateJson.isBlank()) {
            return null;
        }
        try {
            JsonNode state = objectMapper.readTree(stateJson);
            return new PropertyReviewHistoryItem.StatusTriplet(
                    text(state, "status"),
                    text(state, "approvalStatus"),
                    text(state, "operationStatus"),
                    text(state, "ownershipStatus"));
        } catch (Exception invalidAuditState) {
            return null;
        }
    }

    private String text(JsonNode state, String field) {
        JsonNode value = state.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String actorKind(String eventType) {
        return "PROPERTY_SUBMITTED_FOR_APPROVAL".equals(eventType) ? "OWNER" : "ADMIN";
    }

    private void requirePropertyId(Long propertyId) {
        if (propertyId == null) {
            throw new IllegalArgumentException("Property id is required.");
        }
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Property history was not found.");
    }
}
