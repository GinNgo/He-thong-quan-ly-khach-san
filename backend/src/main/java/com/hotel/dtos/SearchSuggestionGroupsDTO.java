package com.hotel.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchSuggestionGroupsDTO {
    private List<LocationSuggestionDTO> provinces;
    private List<LocationSuggestionDTO> wards;
    private List<LocationSuggestionDTO> properties;
    private List<LocationSuggestionDTO> landmarks;
}
