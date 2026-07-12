package com.hotel.services.impl;

import com.hotel.services.GeoCoordinate;
import com.hotel.services.GeocodingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class NominatimGeocodingProvider implements GeocodingProvider {

    private static final Logger log = LoggerFactory.getLogger(NominatimGeocodingProvider.class);
    private static final String NOMINATIM_API_URL = "https://nominatim.openstreetmap.org/search?q=%s&format=json&limit=1";
    
    private final RestTemplate restTemplate;

    public NominatimGeocodingProvider() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public Optional<GeoCoordinate> geocode(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }

        try {
            String encodedAddress = URLEncoder.encode(address, StandardCharsets.UTF_8.toString());
            String url = String.format(NOMINATIM_API_URL, encodedAddress);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "HotelManagementSystem/1.0");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );

            List<Map<String, Object>> body = response.getBody();
            if (body != null && !body.isEmpty()) {
                Map<String, Object> firstResult = body.get(0);
                double lat = Double.parseDouble((String) firstResult.get("lat"));
                double lon = Double.parseDouble((String) firstResult.get("lon"));
                return Optional.of(new GeoCoordinate(lat, lon));
            }
        } catch (Exception e) {
            log.error("Geocoding failed for address: {}", address, e);
        }
        
        return Optional.empty();
    }
}
