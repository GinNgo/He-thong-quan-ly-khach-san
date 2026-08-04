package com.hotel.dtos;

import java.time.LocalDateTime;

public record FinancialAuditEventDTO(Long id, String context, Long hotelId, String aggregateType,
                                     String aggregateId, String actorType, Long actorId, String source,
                                     String previousState, String newState, String reason,
                                     String idempotencyReference, String providerReference,
                                     String correlationId, String metadataJson, LocalDateTime occurredAt) { }
