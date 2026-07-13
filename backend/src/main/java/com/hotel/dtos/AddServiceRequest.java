package com.hotel.dtos;

import lombok.Data;

@Data
public class AddServiceRequest {
    private Long serviceId;
    private Integer quantity;
}
