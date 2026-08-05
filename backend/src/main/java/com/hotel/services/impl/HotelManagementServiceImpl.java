package com.hotel.services.impl;

import com.hotel.entities.Hotel;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.services.HotelManagementService;
import com.hotel.services.PropertyAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class HotelManagementServiceImpl implements HotelManagementService {

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private PropertyAccessService propertyAccessService;

    @Override
    public List<Hotel> getAllHotels() {
        return hotelRepository.findAll();
    }

    @Override
    public List<Hotel> searchHotels(String city, String status) {
        if (city == null || city.trim().isEmpty()) {
            return hotelRepository.findByStatus(status);
        }
        return hotelRepository.findByAddressLineContainingIgnoreCaseAndStatus(city, status);
    }

    @Override
    public Optional<Hotel> getHotelById(Long id) {
        return hotelRepository.findById(id);
    }

    @Override
    @Transactional
    public Hotel createHotel(Hotel hotel) {
        hotel.setStatus("DRAFT");
        hotel.setApprovalStatus("DRAFT");
        hotel.setOperationStatus("INACTIVE");
        hotel.setLifecycleAction(null);
        hotel.setLifecycleReason(null);
        hotel.setLifecycleChangedByUserId(null);
        hotel.setLifecycleChangedAt(null);
        return hotelRepository.save(hotel);
    }

    @Override
    @Transactional
    public Hotel updateHotel(Long id, Hotel hotel) {
        Hotel existingHotel = hotelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cơ sở."));
        propertyAccessService.requireAccessibleOrNotFound(id, "cơ sở");
        existingHotel.setName(hotel.getName());
        existingHotel.setDescription(hotel.getDescription());
        existingHotel.setAddressLine(hotel.getAddressLine());
        existingHotel.setProvinceId(hotel.getProvinceId());
        existingHotel.setWardId(hotel.getWardId());
        existingHotel.setStarRating(hotel.getStarRating());
        existingHotel.setMainImage(hotel.getMainImage());
        return hotelRepository.save(existingHotel);
    }

    @Override
    @Transactional
    public void deleteHotel(Long id) {
        if (!hotelRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy cơ sở.");
        }
        propertyAccessService.requireAccessibleOrNotFound(id, "cơ sở");
        throw new IllegalStateException(
                "Hard deletion is disabled. Use the governed property close transition.");
    }

    @Override
    public List<Hotel> getHotelsByOwnerId(Long ownerId) {
        return hotelRepository.findByOwnerId(ownerId);
    }
}
