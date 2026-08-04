package com.hotel.dtos;

import java.time.Instant;

public record SupportAttachmentDTO(
        Long id,
        Long conversationId,
        String filename,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        Long uploadedByUserId,
        Instant uploadedAt) {
}
