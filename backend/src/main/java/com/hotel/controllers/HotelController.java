package com.hotel.controllers;

import com.hotel.entities.Hotel;
import com.hotel.dtos.PublicHotelDetailDTO;
import com.hotel.services.HotelManagementService;
import com.hotel.services.RoomTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/hotels")
public class HotelController {

    @Autowired
    private HotelManagementService hotelService;

    @Autowired
    private RoomTypeService roomTypeService;

    @GetMapping("/public/search")
    public ResponseEntity<List<Hotel>> searchHotels(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Long provinceId,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Long wardId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false) Integer guests) {
        
        List<Hotel> hotels = hotelService.searchHotels(city, "ACTIVE");
        
        if (provinceId != null) {
            hotels = hotels.stream().filter(h -> provinceId.equals(h.getProvinceId())).toList();
        }
        if (districtId != null) {
            // District is mapped implicitly or no longer used directly in Hotel entity
        }
        if (wardId != null) {
            hotels = hotels.stream().filter(h -> wardId.equals(h.getWardId())).toList();
        }

        if (checkIn != null || checkOut != null || guests != null) {
            hotels = hotels.stream()
                    .filter(hotel -> !roomTypeService.getRoomTypesByHotelId(hotel.getId(), checkIn, checkOut, guests).isEmpty())
                    .toList();
        }

        return ResponseEntity.ok(hotels);
    }
    @GetMapping("/public/{id}")
    public ResponseEntity<PublicHotelDetailDTO> getHotelById(@PathVariable Long id) {
        return hotelService.getHotelById(id)
                .map(PublicHotelDetailDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @com.hotel.security.RequireFeature("HOTEL")
    @GetMapping("/my-hotels")
    public ResponseEntity<List<Hotel>> getMyHotels(@org.springframework.security.core.annotation.AuthenticationPrincipal com.hotel.security.CustomUserDetails userDetails) {
        return ResponseEntity.ok(hotelService.getHotelsByOwnerId(userDetails.getUserId()));
    }

    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @GetMapping
    public ResponseEntity<List<PublicHotelDetailDTO>> getAllHotels() {
        return ResponseEntity.ok(hotelService.getAllHotels().stream().map(PublicHotelDetailDTO::from).toList());
    }

    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @PostMapping
    public ResponseEntity<Hotel> createHotel(@RequestBody Hotel hotel) {
        return ResponseEntity.ok(hotelService.createHotel(hotel));
    }



    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<Hotel> updateHotel(@PathVariable Long id, @RequestBody Hotel hotel) {
        return ResponseEntity.ok(hotelService.updateHotel(id, hotel));
    }

    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHotel(@PathVariable Long id) {
        hotelService.deleteHotel(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<Hotel> submitHotel(@PathVariable Long id) {
        return hotelService.getHotelById(id).map(hotel -> {
            hotel.setStatus("PENDING");
            return ResponseEntity.ok(hotelService.updateHotel(id, hotel));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @PostMapping("/{id}/approve")
    public ResponseEntity<Hotel> approveHotel(@PathVariable Long id) {
        return hotelService.getHotelById(id).map(hotel -> {
            hotel.setStatus("ACTIVE");
            return ResponseEntity.ok(hotelService.updateHotel(id, hotel));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @PostMapping("/{id}/reject")
    public ResponseEntity<Hotel> rejectHotel(@PathVariable Long id) {
        return hotelService.getHotelById(id).map(hotel -> {
            hotel.setStatus("REJECTED");
            return ResponseEntity.ok(hotelService.updateHotel(id, hotel));
        }).orElse(ResponseEntity.notFound().build());
    }
}
