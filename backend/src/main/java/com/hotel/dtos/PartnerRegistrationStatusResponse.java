package com.hotel.dtos;

import java.util.List;

public record PartnerRegistrationStatusResponse(
        String overallStatus,
        int propertyCount,
        List<PropertyStatus> properties) {

    public PartnerRegistrationStatusResponse {
        properties = properties == null ? List.of() : List.copyOf(properties);
    }

    public record PropertyStatus(
            Long propertyId,
            String propertyName,
            String status,
            String approvalStatus,
            String operationStatus,
            String ownershipStatus,
            String rejectionReason) {
    }
}
