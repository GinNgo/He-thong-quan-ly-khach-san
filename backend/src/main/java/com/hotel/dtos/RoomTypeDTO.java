package com.hotel.dtos;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RoomTypeDTO {
    private Long id;
    private String code;
    private String nameVi;
    private String nameEn;
    private Integer maxGuest;
    private BigDecimal basePrice;
    private String descriptionVi;
    private String descriptionEn;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
