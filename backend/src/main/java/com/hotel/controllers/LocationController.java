package com.hotel.controllers;

import com.hotel.entities.Location;
import com.hotel.repositories.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationRepository locationRepository;

    @GetMapping("/provinces")
    public ResponseEntity<List<Location>> getProvinces() {
        return ResponseEntity.ok(locationRepository.findByParentIsNull());
    }

    @GetMapping("/provinces/{provinceId}/wards")
    public ResponseEntity<List<Location>> getWards(@PathVariable Long provinceId) {
        return ResponseEntity.ok(locationRepository.findByParentId(provinceId));
    }

    @GetMapping("/search")
    public ResponseEntity<org.springframework.data.domain.Page<Location>> searchLocations(
            @org.springframework.web.bind.annotation.RequestParam("keyword") String keyword,
            @org.springframework.web.bind.annotation.RequestParam(value = "type", defaultValue = "WARD") String type,
            @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "20") int size) {
        
        String normalizedKeyword = keyword != null ? keyword.toLowerCase() : "";
        org.springframework.data.domain.Page<Location> results = locationRepository.searchLocations(
                normalizedKeyword, 
                type, 
                org.springframework.data.domain.PageRequest.of(page, size));
        return ResponseEntity.ok(results);
    }
}
