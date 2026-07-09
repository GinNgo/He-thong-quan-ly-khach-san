package com.hotel.services;

import com.hotel.dtos.RoomTypeDTO;
import java.time.LocalDate;
import java.util.List;

public interface RoomTypeService {
    List<RoomTypeDTO> getAllRoomTypes();
    RoomTypeDTO getRoomTypeById(Long id);
    RoomTypeDTO createRoomType(RoomTypeDTO roomTypeDTO);
    RoomTypeDTO updateRoomType(Long id, RoomTypeDTO roomTypeDTO);
    void deleteRoomType(Long id);
    List<RoomTypeDTO> getRoomTypesByHotelId(Long hotelId);
    List<RoomTypeDTO> getRoomTypesByHotelId(Long hotelId, LocalDate checkIn, LocalDate checkOut, Integer guests);
}
