package com.hotel.controllers;

import com.hotel.dto.PropertySearchRequestDTO;
import com.hotel.dto.PropertySearchResponseDTO;
import com.hotel.services.PropertySearchService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.WebDataBinder;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

@RestController
@RequestMapping("/api/public/properties")
@RequiredArgsConstructor
public class PropertySearchController {

    private static final Set<String> RECOGNIZED_QUERY_PARAMETERS = Set.of(
            "keyword", "provinceId", "wardId", "landmarkId", "checkInDate", "checkOutDate",
            "adultCount", "childCount", "roomCount", "latitude", "longitude", "radiusKm",
            "sortBy", "pageNumber", "pageSize", "propertyTypes", "stayType", "minPrice", "maxPrice",
            "starRatings", "minReviewScore", "amenityIds", "freeCancellation", "payAtProperty",
            "breakfastIncluded");

    private final PropertySearchService propertySearchService;

    @InitBinder
    void disallowInternalSearchFields(WebDataBinder binder) {
        binder.setDisallowedFields("legacyAddressKeyword");
    }

    @GetMapping("/search")
    public ResponseEntity<Page<PropertySearchResponseDTO>> searchProperties(
            @ModelAttribute PropertySearchRequestDTO request,
            HttpServletRequest servletRequest) {
        rejectUnknownQueryParameters(servletRequest);
        Page<PropertySearchResponseDTO> result = propertySearchService.searchProperties(request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-LuxeStay-Freshness", "LIVE_SEARCH")
                .body(result);
    }

    private void rejectUnknownQueryParameters(HttpServletRequest request) {
        Set<String> unknown = new TreeSet<>(Collections.list(request.getParameterNames()));
        unknown.removeAll(RECOGNIZED_QUERY_PARAMETERS);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported property-search query parameter(s): "
                    + String.join(", ", unknown));
        }
    }
}
