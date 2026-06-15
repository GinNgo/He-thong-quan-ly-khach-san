package com.hotel.dtos;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RoomDTO {
    private Long id;
    private String roomNumber;
    private Long roomTypeId;
    private String roomTypeCode;
    private String roomTypeNameVi;
    private Integer floor;
    private String status;
    private String descriptionVi;
    private String descriptionEn;
    private List<RoomImageDTO> images;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
