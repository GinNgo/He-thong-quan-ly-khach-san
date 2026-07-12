package com.hotel.services.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.dto.provider.AccommodationProviderSearchRequest;
import com.hotel.dto.provider.ProviderPhoto;
import com.hotel.dto.provider.ProviderPropertyDetail;
import com.hotel.dto.provider.ProviderSearchResult;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class NominatimAccommodationProvider implements AccommodationDataProvider {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getProviderName() {
        return "NOMINATIM";
    }

    @Override
    public List<ProviderSearchResult> search(AccommodationProviderSearchRequest request) {
        List<ProviderSearchResult> results = new ArrayList<>();
        // Note: Nominatim allows searching by query.
        // It does not have radius easily via simple endpoint, but we can do a bounded box or structured search.
        // To respect rate limits (1 req/s) and simple implementation, we mock the real API call here if no exact location.
        // In real use, we'd use: https://nominatim.openstreetmap.org/search?q=[query]&format=json&addressdetails=1&extratags=1
        
        try {
            String q = (request.getKeyword() != null ? request.getKeyword() : "") + "+accommodation";
            String url = "https://nominatim.openstreetmap.org/search?q=" + q + "&format=json&addressdetails=1&extratags=1&limit=" + (request.getMaxResults() != null ? request.getMaxResults() : 50);

            // Respect rate limit intentionally for Nominatim
            Thread.sleep(1000);
            
            String responseStr = restTemplate.getForObject(url, String.class);
            if (responseStr != null) {
                JsonNode arrayNode = objectMapper.readTree(responseStr);
                for (JsonNode node : arrayNode) {
                    ProviderSearchResult item = new ProviderSearchResult();
                    item.setExternalId(node.path("osm_type").asText() + "/" + node.path("osm_id").asText());
                    item.setName(node.path("name").asText());
                    item.setAddress(node.path("display_name").asText());
                    item.setLatitude(node.path("lat").asDouble());
                    item.setLongitude(node.path("lon").asDouble());
                    
                    JsonNode extratags = node.path("extratags");
                    if (!extratags.isMissingNode()) {
                        item.setPhone(extratags.path("phone").asText(null));
                        item.setWebsite(extratags.path("website").asText(null));
                    }
                    item.setPropertyType("HOTEL"); // Defaulting
                    item.setRawPayloadJson(node.toString());
                    results.add(item);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return results;
    }

    @Override
    public ProviderPropertyDetail getDetail(String externalId) {
        // Mocked or minimal implementation
        return null;
    }

    @Override
    public List<ProviderPhoto> getPhotos(String externalId) {
        // Nominatim does not provide photos
        return new ArrayList<>();
    }
}
