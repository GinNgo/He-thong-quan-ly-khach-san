package com.hotel.controllers;

import com.hotel.dtos.LocationSuggestionDTO;
import com.hotel.entities.Location;
import com.hotel.repositories.LocationRepository;
import com.hotel.services.PublicSearchSuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationRepository locationRepository;
    private final PublicSearchSuggestionService suggestionService;

    @GetMapping("/provinces")
    public ResponseEntity<List<Location>> getProvinces() {
        return ResponseEntity.ok(locationRepository.findByLocationTypeAndStatusOrderBySortOrderAscNameViAsc("PROVINCE", "ACTIVE"));
    }

    @GetMapping("/provinces/{provinceId}/wards")
    public ResponseEntity<List<Location>> getWards(@PathVariable Long provinceId) {
        return ResponseEntity.ok(locationRepository.findByParentIdAndLocationTypeAndStatusOrderByNameViAsc(provinceId, "WARD", "ACTIVE"));
    }

    @GetMapping("/search")
    public ResponseEntity<List<LocationSuggestionDTO>> search(
            @RequestParam String keyword,
            @RequestParam(required = false) Long provinceId,
            @RequestParam(defaultValue = "12") int size) {
        int safeSize = Math.min(Math.max(size, 1), 30);
        return ResponseEntity.ok(suggestionService.searchFlat(keyword, safeSize, provinceId));
    }

    @GetMapping("/provinces/popular")
    public ResponseEntity<List<LocationSuggestionDTO>> getPopularProvinces(
            @RequestParam(defaultValue = "6") int size) {
        return ResponseEntity.ok(suggestionService.popular(size));
    }
}
