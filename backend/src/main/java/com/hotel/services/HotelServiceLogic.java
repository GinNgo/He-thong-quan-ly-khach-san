package com.hotel.services;

import com.hotel.dtos.HotelServiceDTO;
import java.util.List;

public interface HotelServiceLogic {
    List<HotelServiceDTO> getAllServices(Long hotelId);
    HotelServiceDTO getServiceById(Long id);
    HotelServiceDTO createService(Long hotelId, HotelServiceDTO serviceDTO);
    HotelServiceDTO updateService(Long id, HotelServiceDTO serviceDTO);
    void deleteService(Long id);
}
