package com.hotel.services;

import com.hotel.dtos.PropertyApprovalSubmissionResponse;
import com.hotel.entities.Hotel;
import com.hotel.entities.OperationalAuditEvent;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.UserPropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PropertyApprovalWorkflowService {

    private final HotelRepository hotelRepository;
    private final UserPropertyRepository userPropertyRepository;
    private final OperationalAuditService operationalAuditService;

    @Transactional
    public PropertyApprovalSubmissionResponse submitDraft(Long authenticatedUserId, Long propertyId) {
        if (authenticatedUserId == null) {
            throw new AccessDeniedException("Authenticated account id is required.");
        }
        if (propertyId == null) {
            throw new IllegalArgumentException("Property id is required.");
        }

        Hotel property = hotelRepository.findByIdForUpdate(propertyId)
                .orElseThrow(this::notFound);
        userPropertyRepository.findPendingOwnerMappingForUpdate(authenticatedUserId, propertyId)
                .orElseThrow(this::notFound);
        requireDraft(property);

        Map<String, Object> before = snapshot(property);
        property.setStatus("PENDING_APPROVAL");
        property.setApprovalStatus("PENDING_APPROVAL");
        property.setOperationStatus("INACTIVE");
        Hotel saved = hotelRepository.saveAndFlush(property);

        OperationalAuditEvent event = operationalAuditService.append(new OperationalAuditService.AuditCommand(
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
                snapshot(saved),
                null));

        return new PropertyApprovalSubmissionResponse(
                saved.getId(),
                saved.getStatus(),
                saved.getApprovalStatus(),
                saved.getOperationStatus(),
                authenticatedUserId,
                event.getOccurredAt());
    }

    private void requireDraft(Hotel property) {
        if (!"DRAFT".equals(normalize(property.getStatus()))
                || !"DRAFT".equals(normalize(property.getApprovalStatus()))
                || !"INACTIVE".equals(normalize(property.getOperationStatus()))) {
            throw new IllegalStateException("Only a draft property can be submitted for approval.");
        }
    }

    private Map<String, Object> snapshot(Hotel property) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("propertyId", property.getId());
        state.put("propertyName", property.getName());
        state.put("status", property.getStatus());
        state.put("approvalStatus", property.getApprovalStatus());
        state.put("operationStatus", property.getOperationStatus());
        return state;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Property was not found.");
    }
}
