package com.hotel.dtos;

import lombok.Data;

@Data
public class BulkRoomRequest {
    private Long hotelId;
    private Long roomTypeId;
    private Integer fromNumber;
    private Integer toNumber;
    private Integer floor;
    private String prefix;
    private String status = "AVAILABLE";
}
