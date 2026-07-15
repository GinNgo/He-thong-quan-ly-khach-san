package com.hotel.controllers;

import com.hotel.dtos.LocationSuggestionDTO;
import com.hotel.dtos.SearchSuggestionGroupsDTO;
import com.hotel.services.PublicSearchSuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicDiscoveryController {

    private final PublicSearchSuggestionService suggestionService;

    @GetMapping("/search/suggestions")
    public ResponseEntity<SearchSuggestionGroupsDTO> suggestions(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude) {
        return ResponseEntity.ok(suggestionService.search(keyword, limit, null, latitude, longitude));
    }

    @GetMapping("/popular-destinations")
    public ResponseEntity<List<LocationSuggestionDTO>> popularDestinations(
            @RequestParam(defaultValue = "8") int limit) {
        return ResponseEntity.ok(suggestionService.popular(limit));
    }
}
