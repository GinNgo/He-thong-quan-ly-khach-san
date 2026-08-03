package com.hotel.dtos;

import java.time.LocalDateTime;

public record ReservationEventDTO(
        Long id,
        String eventType,
        String reason,
        String beforeState,
        String afterState,
        String actorType,
        LocalDateTime occurredAt) {
}
