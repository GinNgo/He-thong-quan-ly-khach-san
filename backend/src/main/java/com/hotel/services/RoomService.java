package com.hotel.services;

import com.hotel.dtos.RoomDTO;
import java.util.List;

public interface RoomService {
    List<RoomDTO> getAllRooms();
    RoomDTO getRoomById(Long id);
    RoomDTO createRoom(RoomDTO roomDTO);
    RoomDTO updateRoom(Long id, RoomDTO roomDTO);
    void deleteRoom(Long id);
}
