package com.hotel.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class BulkRoomResultDTO {
    private List<RoomDTO> created;
    private List<String> failedRoomNumbers;
}
