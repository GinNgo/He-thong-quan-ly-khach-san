package com.hotel.services;

import com.hotel.dtos.PropertyClosureRequest;
import com.hotel.dtos.PropertyCreateRequest;
import com.hotel.dtos.PropertyProfileDTO;
import com.hotel.dtos.PropertyUpdateRequest;
import com.hotel.entities.Hotel;
import java.util.List;
import java.util.Optional;

public interface HotelManagementService {
    List<Hotel> getAllHotels();
    List<Hotel> searchHotels(String city, String status);
    Optional<Hotel> getHotelById(Long id);
    PropertyProfileDTO createHotel(PropertyCreateRequest request);
    PropertyProfileDTO updateHotel(Long id, PropertyUpdateRequest request);
    PropertyProfileDTO updateOwnedHotel(Long id, PropertyUpdateRequest request);
    PropertyProfileDTO submitHotel(Long id);
    PropertyProfileDTO closeHotel(Long id, PropertyClosureRequest request);
    List<Hotel> getHotelsByOwnerId(Long ownerId);
}
