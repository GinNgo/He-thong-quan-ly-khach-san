package com.hotel.dtos;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class HotelServiceDTO {
    private Long id;
    private String code;
    private String nameVi;
    private String nameEn;
    private BigDecimal price;
    private String descriptionVi;
    private String descriptionEn;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
