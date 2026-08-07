package com.hotel.dtos;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class RefundSummaryDTO {
    private String publicId;
    private BigDecimal amount;
    private String currency;
    private String provider;
    private String status;
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
    private String failureCode;
}
