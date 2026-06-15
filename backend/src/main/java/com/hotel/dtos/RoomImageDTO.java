package com.hotel.dtos;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RoomImageDTO {
    private Long id;
    private String imageUrl;
    private Boolean isPrimary;
    private LocalDateTime createdAt;
}
