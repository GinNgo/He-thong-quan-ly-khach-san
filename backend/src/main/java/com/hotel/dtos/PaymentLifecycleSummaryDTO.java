package com.hotel.dtos;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class PaymentLifecycleSummaryDTO {
    private String provider;
    private BigDecimal amount;
    private String currency;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;
    private boolean reconciliationRequired;
    private String failureCode;
}
