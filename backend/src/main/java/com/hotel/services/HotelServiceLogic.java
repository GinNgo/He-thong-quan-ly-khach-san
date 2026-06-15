package com.hotel.services;

import com.hotel.dtos.HotelServiceDTO;
import java.util.List;

public interface HotelServiceLogic {
    List<HotelServiceDTO> getAllServices();
    HotelServiceDTO getServiceById(Long id);
    HotelServiceDTO createService(HotelServiceDTO serviceDTO);
    HotelServiceDTO updateService(Long id, HotelServiceDTO serviceDTO);
    void deleteService(Long id);
}
