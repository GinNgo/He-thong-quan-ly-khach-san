package com.hotel.services;

import com.hotel.dtos.PropertyClosureRequest;
import com.hotel.dtos.PropertyProfileDTO;
import com.hotel.dtos.PropertyProfileUpdateRequest;
import com.hotel.entities.Hotel;
import java.util.List;
import java.util.Optional;

public interface HotelManagementService {
    List<Hotel> getAllHotels();
    List<Hotel> searchHotels(String city, String status);
    Optional<Hotel> getHotelById(Long id);
    PropertyProfileDTO createHotel(PropertyProfileDTO request);
    PropertyProfileDTO updateHotel(Long id, PropertyProfileUpdateRequest request);
    PropertyProfileDTO updateOwnedHotel(Long id, PropertyProfileUpdateRequest request);
    PropertyProfileDTO getProfile(Long id);
    PropertyProfileDTO getOwnedProfile(Long id);
    PropertyProfileDTO submitHotel(Long id);
    PropertyProfileDTO closeHotel(Long id, PropertyClosureRequest request);
    List<Hotel> getHotelsByOwnerId(Long ownerId);
}
