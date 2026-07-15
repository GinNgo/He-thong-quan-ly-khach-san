package com.hotel.dtos;

import java.util.List;

public class AssignRoomsRequest {
    private List<Long> roomIds;

    public List<Long> getRoomIds() {
        return roomIds;
    }

    public void setRoomIds(List<Long> roomIds) {
        this.roomIds = roomIds;
    }
}
