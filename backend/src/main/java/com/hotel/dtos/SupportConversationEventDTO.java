package com.hotel.dtos;

import java.time.Instant;

public record SupportConversationEventDTO(Long id, Long conversationId, Long hotelId, Long actorUserId,
                                          String eventType, String details, Instant occurredAt) { }
