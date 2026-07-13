package com.hotel.dtos;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ReservationServiceItemDTO {
    private Long id;
    private Long reservationId;
    private Long serviceId;
    private String serviceNameVi;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal totalAmount;
}
