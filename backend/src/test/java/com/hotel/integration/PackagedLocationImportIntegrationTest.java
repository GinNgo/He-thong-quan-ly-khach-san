package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.entities.Location;
import com.hotel.repositories.LocationRepository;
import com.hotel.services.LocationImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = BackendApplication.class, properties = {
        "app.location-import.enabled=false",
        "app.e2e-fixtures.enabled=false",
        "payment.property.encryption-key=test-property-payment-encryption-key"
})
@ActiveProfiles("test")
@Transactional
class PackagedLocationImportIntegrationTest {

    @Autowired
    private LocationImportService locationImportService;

    @Autowired
    private LocationRepository locationRepository;

    @Test
    void packagedCatalogImportsTwiceWithoutAddingRows() {
        LocationImportService.ImportReport first = locationImportService.importData(false);
        long firstCount = locationRepository.count();

        LocationImportService.ImportReport second = locationImportService.importData(false);
        List<Location> locations = locationRepository.findAll();

        assertEquals(0, first.errors());
        assertEquals(0, second.errors());
        assertTrue(first.added() > 0);
        assertEquals(0, second.added());
        assertEquals(firstCount, locations.size());
        assertEquals(34, locations.stream()
                .filter(location -> "PROVINCE".equals(location.getLocationType()))
                .filter(location -> location.getSourceCode() != null
                        && location.getSourceCode().startsWith("VN34-"))
                .count());
        assertEquals(63, locations.stream()
                .filter(location -> "PROVINCE".equals(location.getLocationType()))
                .filter(location -> location.getSourceCode() != null
                        && location.getSourceCode().matches("\\d+"))
                .count());
        assertEquals(122, locations.stream()
                .filter(location -> "LANDMARK".equals(location.getLocationType()))
                .count());
    }
}
