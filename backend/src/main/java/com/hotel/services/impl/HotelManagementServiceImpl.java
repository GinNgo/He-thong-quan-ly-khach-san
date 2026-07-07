package com.hotel.services.impl;

import com.hotel.entities.Hotel;
import com.hotel.repositories.HotelRepository;
import com.hotel.services.HotelManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class HotelManagementServiceImpl implements HotelManagementService {

    @Autowired
    private HotelRepository hotelRepository;

    @Override
    public List<Hotel> getAllHotels() {
        return hotelRepository.findAll();
    }

    @Override
    public List<Hotel> searchHotels(String city, String status) {
        if (city == null || city.trim().isEmpty()) {
            return hotelRepository.findByStatus(status);
        }
        return hotelRepository.findByCityContainingIgnoreCaseAndStatus(city, status);
    }

    @Override
    public Optional<Hotel> getHotelById(Long id) {
        return hotelRepository.findById(id);
    }

    @Override
    @Transactional
    public Hotel createHotel(Hotel hotel) {
        return hotelRepository.save(hotel);
    }

    @Override
    @Transactional
    public Hotel updateHotel(Long id, Hotel hotel) {
        Hotel existingHotel = hotelRepository.findById(id).orElseThrow(() -> new RuntimeException("Hotel not found"));
        existingHotel.setName(hotel.getName());
        existingHotel.setDescription(hotel.getDescription());
        existingHotel.setAddress(hotel.getAddress());
        existingHotel.setCity(hotel.getCity());
        existingHotel.setCountry(hotel.getCountry());
        existingHotel.setStarRating(hotel.getStarRating());
        existingHotel.setMainImage(hotel.getMainImage());
        existingHotel.setStatus(hotel.getStatus());
        return hotelRepository.save(existingHotel);
    }

    @Override
    @Transactional
    public void deleteHotel(Long id) {
        hotelRepository.deleteById(id);
    }
}
