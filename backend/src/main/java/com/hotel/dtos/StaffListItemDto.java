package com.hotel.dtos;

import java.time.LocalDateTime;
import java.util.List;

public record StaffListItemDto(
        Long id,
        Long version,
        String username,
        String email,
        String fullName,
        String phone,
        String status,
        List<RoleSummary> roles,
        PropertySummary hotel,
        List<AssignmentSummary> staffAssignments) {

    public record RoleSummary(Long id, String code, String name) {}

    public record PropertySummary(Long id, String name) {}

    public record AssignmentSummary(
            Long id,
            Long hotelId,
            String hotelName,
            String status,
            String statusReason,
            LocalDateTime startDate,
            LocalDateTime endDate) {}
}
