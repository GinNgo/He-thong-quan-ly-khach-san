package com.hotel.services;

import com.hotel.dto.PropertySearchRequestDTO;
import com.hotel.dto.PropertySearchResponseDTO;
import org.springframework.data.domain.Page;

public interface PropertySearchService {
    Page<PropertySearchResponseDTO> searchProperties(PropertySearchRequestDTO request);
}
