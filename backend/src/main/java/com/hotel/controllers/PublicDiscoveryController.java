package com.hotel.controllers;

import com.hotel.dtos.LocationSuggestionDTO;
import com.hotel.dtos.RoomTypeDTO;
import com.hotel.dtos.SearchSuggestionGroupsDTO;
import com.hotel.services.PublicSearchSuggestionService;
import com.hotel.services.RoomTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicDiscoveryController {

    private final PublicSearchSuggestionService suggestionService;
    private final RoomTypeService roomTypeService;

    @GetMapping("/search/suggestions")
    public ResponseEntity<SearchSuggestionGroupsDTO> suggestions(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Long provinceId,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude) {
        return ResponseEntity.ok(suggestionService.search(keyword, limit, provinceId, latitude, longitude));
    }

    @GetMapping("/popular-destinations")
    public ResponseEntity<List<LocationSuggestionDTO>> popularDestinations(
            @RequestParam(defaultValue = "8") int limit) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic().mustRevalidate())
                .header("X-LuxeStay-Freshness-Seconds", "60")
                .body(suggestionService.popular(limit));
    }

    @GetMapping("/properties/{hotelId}/room-types")
    @PreAuthorize("permitAll()")
    public ResponseEntity<List<RoomTypeDTO>> roomTypes(
            @PathVariable Long hotelId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false) Integer guests) {
        return ResponseEntity.ok(roomTypeService.getRoomTypesByHotelId(hotelId, checkIn, checkOut, guests));
    }
}
