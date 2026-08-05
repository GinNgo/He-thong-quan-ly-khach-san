package com.hotel.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Location;
import com.hotel.repositories.LocationRepository;
import com.hotel.services.LocationImportService;
import com.hotel.services.ProvinceCompatibilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "app.location-import.enabled=false",
        "app.e2e-fixtures.enabled=false",
        "payment.property.encryption-key=test-property-payment-encryption-key"
})
@ActiveProfiles("test")
class PackagedLocationImportIntegrationTest {

    @Autowired
    private LocationImportService locationImportService;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private ProvinceCompatibilityService provinceCompatibilityService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void packagedCatalogIsCompleteAndIdempotent() throws Exception {
        LocationImportService.ImportReport first = locationImportService.importData(false);
        long firstCount = locationRepository.count();
        LocationImportService.ImportReport second = locationImportService.importData(false);
        List<Location> locations = locationRepository.findAll();

        assertEquals(0, first.errors());
        assertEquals(0, second.errors());
        assertTrue(first.added() > 0);
        assertEquals(0, second.added());
        assertEquals(firstCount, locations.size());
        assertEquals(63, count(locations, "PROVINCE", source -> source.matches("\\d+")));
        assertEquals(34, count(locations, "PROVINCE", source -> source.startsWith("VN34-")));
        assertEquals(10_051, count(locations, "WARD", source -> true));
        assertEquals(122, count(locations, "LANDMARK", source -> true));
        assertEquals(34, provinceCompatibilityService.validateCatalog().currentProvinceCount());
        assertEquals(63, provinceCompatibilityService.validateCatalog().legacyProvinceCount());

        Set<String> legacyCodes = locations.stream()
                .filter(location -> "PROVINCE".equals(location.getLocationType()))
                .map(Location::getSourceCode)
                .filter(source -> source != null && source.matches("\\d+"))
                .collect(Collectors.toSet());
        assertEquals(63, legacyCodes.size());
        Map<String, Location> provincesBySourceCode = locations.stream()
                .filter(location -> "PROVINCE".equals(location.getLocationType()))
                .collect(Collectors.toMap(Location::getSourceCode, location -> location));
        for (String legacyCode : legacyCodes) {
            String currentCode = provinceCompatibilityService.currentSourceCodeFor(legacyCode).orElseThrow();
            Location legacy = provincesBySourceCode.get(legacyCode);
            Location current = provincesBySourceCode.get(currentCode);
            assertEquals(currentCode, provinceCompatibilityService.currentProvinceFor(legacy).getSourceCode());
            assertTrue(provinceCompatibilityService.provinceScopeIds(current.getId()).contains(legacy.getId()));
            assertTrue(provinceCompatibilityService.provinceScopeIds(legacy.getId()).contains(current.getId()));
        }

        List<Location> activeLandmarks = locations.stream()
                .filter(location -> "LANDMARK".equals(location.getLocationType()))
                .filter(location -> "ACTIVE".equals(location.getStatus()))
                .toList();
        assertEquals(121, activeLandmarks.size());
        assertTrue(activeLandmarks.stream().allMatch(this::validCoordinates));
        assertTrue(activeLandmarks.stream().allMatch(location -> location.getDefaultRadiusKm() != null
                && location.getDefaultRadiusKm() > 0 && location.getDefaultRadiusKm() <= 50));
        assertTrue(activeLandmarks.stream().allMatch(location -> location.getParent() != null
                && location.getParent().getSourceCode().startsWith("VN34-")));
        assertEquals(2, locations.stream()
                .filter(location -> "LANDMARK".equals(location.getLocationType()))
                .filter(location -> "Hồ Xuân Hương".equals(location.getNameVi()))
                .count());

        byte[] invalidFixture = objectMapper.writeValueAsBytes(List.of(
                Map.of(
                        "code", "LM-VALID-BEFORE-INVALID",
                        "nameVi", "Valid before invalid",
                        "provinceCode", "VN34-01",
                        "latitude", 21.0285,
                        "longitude", 105.8542,
                        "defaultRadiusKm", 5,
                        "status", "ACTIVE"),
                Map.of(
                        "code", "LM-INVALID-COORD",
                        "nameVi", "Invalid coordinate",
                        "provinceCode", "VN34-01",
                        "latitude", 999,
                        "longitude", 105,
                        "defaultRadiusKm", 5,
                        "status", "ACTIVE")));
        ReflectionTestUtils.setField(locationImportService, "landmarkResource",
                new ByteArrayResource(invalidFixture, "invalid-landmarks.json"));

        assertThrows(IllegalStateException.class, () -> locationImportService.importData(false));
        assertFalse(locationRepository.findByCode("LM-VALID-BEFORE-INVALID").isPresent());
        assertFalse(locationRepository.findByCode("LM-INVALID-COORD").isPresent());
    }

    private long count(List<Location> locations, String type,
                       java.util.function.Predicate<String> sourcePredicate) {
        return locations.stream()
                .filter(location -> type.equals(location.getLocationType()))
                .map(Location::getSourceCode)
                .filter(source -> source != null && sourcePredicate.test(source))
                .count();
    }

    private boolean validCoordinates(Location location) {
        return location.getLatitude() != null && location.getLongitude() != null
                && location.getLatitude() >= -90 && location.getLatitude() <= 90
                && location.getLongitude() >= -180 && location.getLongitude() <= 180;
    }
}
