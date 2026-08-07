package com.hotel.dtos;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class PaymentSessionResponse {
    private String sessionId;
    private Long reservationId;
    private String provider;
    private String method;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String mode;
    private LocalDateTime expiresAt;
    private String url;
    private boolean reconciliationRequired;
}
