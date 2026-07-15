package com.hotel.dtos;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CheckoutRequest {
    private String paymentMethod;
    private BigDecimal paymentAmount;
    private String transactionId;
}
