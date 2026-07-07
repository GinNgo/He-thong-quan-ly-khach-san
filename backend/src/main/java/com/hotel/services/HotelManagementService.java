package com.hotel.services;

import com.hotel.entities.Hotel;
import java.util.List;
import java.util.Optional;

public interface HotelManagementService {
    List<Hotel> getAllHotels();
    List<Hotel> searchHotels(String city, String status);
    Optional<Hotel> getHotelById(Long id);
    Hotel createHotel(Hotel hotel);
    Hotel updateHotel(Long id, Hotel hotel);
    void deleteHotel(Long id);
}
