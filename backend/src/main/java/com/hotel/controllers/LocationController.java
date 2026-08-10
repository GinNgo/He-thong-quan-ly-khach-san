package com.hotel.controllers;

import com.hotel.dtos.LocationSuggestionDTO;
import com.hotel.entities.Location;
import com.hotel.services.ProvinceCompatibilityService;
import com.hotel.services.PublicSearchSuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public/locations")
@RequiredArgsConstructor
public class LocationController {

    private final ProvinceCompatibilityService provinceCompatibilityService;
    private final PublicSearchSuggestionService suggestionService;

    @GetMapping("/provinces")
    public ResponseEntity<List<Location>> getProvinces() {
        return ResponseEntity.ok(provinceCompatibilityService.currentProvinces());
    }

    @GetMapping("/provinces/{provinceId}/wards")
    public ResponseEntity<List<Map<String, Object>>> getWards(@PathVariable Long provinceId) {
        return ResponseEntity.ok(provinceCompatibilityService.wardsFor(provinceId).stream()
                .map(this::locationResponse).toList());
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

    private Map<String, Object> locationResponse(Location location) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", location.getId());
        response.put("code", location.getCode());
        response.put("sourceCode", location.getSourceCode());
        response.put("nameVi", location.getNameVi());
        response.put("nameEn", location.getNameEn());
        response.put("normalizedName", location.getNormalizedName());
        response.put("locationType", location.getLocationType());
        response.put("fullPath", location.getFullPath());
        response.put("legacyParentName", location.getLegacyParentName());
        response.put("sourceProvider", location.getSourceProvider());
        response.put("sourceObjectId", location.getSourceObjectId());
        response.put("parent", location.getParent() == null ? null : Map.of("id", location.getParent().getId()));
        return response;
    }
}
