package com.hotel.emailoutbox;

public enum EmailOutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED,
    BOUNCED,
    DEAD_LETTER
}
