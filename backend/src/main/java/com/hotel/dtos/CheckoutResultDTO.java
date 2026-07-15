package com.hotel.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class CheckoutResultDTO {
    private Long reservationId;
    private String reservationStatus;
    private Long invoiceId;
    private String invoiceCode;
    private String invoiceStatus;
    private BigDecimal totalAmount;
    private List<Long> dirtyRoomIds;
}
