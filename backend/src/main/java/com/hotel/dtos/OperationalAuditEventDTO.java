package com.hotel.dtos;

import java.time.LocalDateTime;

public record OperationalAuditEventDTO(
        Long id,
        String scope,
        Long hotelId,
        String domain,
        String eventType,
        String aggregateType,
        String aggregateId,
        String actorType,
        Long actorId,
        String reason,
        String beforeState,
        String afterState,
        String correlationId,
        LocalDateTime occurredAt) {
}
