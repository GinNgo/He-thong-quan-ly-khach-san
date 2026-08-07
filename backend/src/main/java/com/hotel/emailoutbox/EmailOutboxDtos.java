package com.hotel.emailoutbox;

import java.time.LocalDateTime;

public final class EmailOutboxDtos {
    private EmailOutboxDtos() {
    }

    public record EnqueueRequest(Long hotelId, String idempotencyKey, String templateKey,
                                 String templateVersion, String recipientEmail, String subject,
                                 String bodyHtml, String bodyText, String attachmentName,
                                 String attachmentContentType, byte[] attachmentBytes,
                                 Integer maxAttempts) {
    }

    public record EnqueueResult(Long id, String status, boolean replayed) {
    }

    public record Failure(Long id, Long hotelId, String idempotencyKey, String templateKey,
                          String templateVersion, String maskedRecipient, String subject,
                          String status, int attemptCount, int maxAttempts, int manualRetryCount,
                          String lastErrorCode, LocalDateTime failedAt, LocalDateTime nextAttemptAt,
                          LocalDateTime createdAt) {
    }

    public record DeliveryAttempt(Long id, int attemptNumber, String outcome, String errorCode,
                                  String providerMessageId, long durationMs, LocalDateTime attemptedAt) {
    }
}
