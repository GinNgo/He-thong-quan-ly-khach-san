package com.hotel.controllers;

import com.hotel.dto.PropertySearchRequestDTO;
import com.hotel.dto.PropertySearchResponseDTO;
import com.hotel.services.PropertySearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/properties")
@RequiredArgsConstructor
public class PropertySearchController {

    private final PropertySearchService propertySearchService;

    @GetMapping("/search")
    public ResponseEntity<Page<PropertySearchResponseDTO>> searchProperties(
            @ModelAttribute PropertySearchRequestDTO request) {
        System.out.println("PropertySearchController HIT!");
        Page<PropertySearchResponseDTO> result = propertySearchService.searchProperties(request);
        return ResponseEntity.ok(result);
    }
}
