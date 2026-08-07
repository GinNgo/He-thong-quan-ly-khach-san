package com.hotel.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Location;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.LocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationImportServiceTest {

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private HotelRepository hotelRepository;

    private LocationImportService service;

    @BeforeEach
    void setUp() {
        service = new LocationImportService(locationRepository, hotelRepository, new ObjectMapper());
        ReflectionTestUtils.setField(service, "currentProvinceResource",
                new ByteArrayResource("[]".getBytes(StandardCharsets.UTF_8), "empty-current-provinces.json"));
    }

    @Test
    void packagedLocationResourceIsReadable() {
        ReflectionTestUtils.setField(service, "locationResource", new ClassPathResource("data/locations.json"));

        assertTrue(service.resolveSourceResource().isReadable());
    }

    @Test
    void packagedLandmarkResourceIsReadable() {
        ReflectionTestUtils.setField(service, "landmarkResource", new ClassPathResource("data/landmarks.json"));

        assertTrue(service.resolveLandmarkResource().isReadable());
    }

    @Test
    void packagedCurrentProvinceResourceIsReadableAndCoversThirtyFourUnits() throws Exception {
        ClassPathResource resource = new ClassPathResource("data/provinces-current-34.json");
        ReflectionTestUtils.setField(service, "currentProvinceResource", resource);

        assertTrue(service.resolveCurrentProvinceResource().isReadable());
        List<?> provinces = new ObjectMapper().readValue(resource.getInputStream(), List.class);
        assertEquals(34, provinces.size());
    }

    @Test
    void importsFromConfiguredResourceWithoutFilesystemFallbacks() {
        String json = """
                [{"name":"Tinh Test","code":"1","districts":[{"name":"Huyen Test","wards":[{"name":"Xa Test","code":"101"}]}]}]
                """;
        ReflectionTestUtils.setField(service, "locationResource",
                new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8), "test-locations.json"));
        when(locationRepository.findAll()).thenReturn(List.of());
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocationImportService.ImportReport report = service.importData(false);

        assertEquals(2, report.added());
        assertEquals(1, report.provinces());
        assertEquals(1, report.wards());
        assertEquals(0, report.landmarks());
        verify(locationRepository).flush();
    }

    @Test
    void importsValidLandmarkAndRejectsActiveLandmarkWithoutCoordinates() {
        String locations = """
                [{"name":"Tinh Test","code":"1","districts":[]}]
                """;
        String landmarks = """
                [
                  {"code":"LM-1","nameVi":"Cau Rong","nameEn":"Dragon Bridge","provinceCode":"1","category":"CULTURE","latitude":16.0611,"longitude":108.2277,"defaultRadiusKm":5,"popularityScore":20,"status":"ACTIVE","sourceProvider":"CURATED_VN_TRAVEL","sourceObjectType":"DATASET_ROW","sourceObjectId":"VNTRAVEL-001","sourceUpdatedAt":"2026-07-29T00:00:00","dataQualityStatus":"VERIFIED","manualOverride":false},
                  {"code":"LM-2","nameVi":"Landmark Missing Coordinates","provinceCode":"1","category":"NATURE","status":"ACTIVE"}
                ]
                """;
        ReflectionTestUtils.setField(service, "locationResource",
                new ByteArrayResource(locations.getBytes(StandardCharsets.UTF_8), "test-locations.json"));
        ReflectionTestUtils.setField(service, "landmarkResource",
                new ByteArrayResource(landmarks.getBytes(StandardCharsets.UTF_8), "test-landmarks.json"));
        when(locationRepository.findAll()).thenReturn(List.of());
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocationImportService.ImportReport report = service.importData(false);

        assertEquals(1, report.landmarks());
        assertEquals(1, report.errors());
        ArgumentCaptor<Location> captor = ArgumentCaptor.forClass(Location.class);
        verify(locationRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Location landmark = captor.getAllValues().stream()
                .filter(location -> "LANDMARK".equals(location.getLocationType()))
                .findFirst()
                .orElseThrow();
        assertEquals("CURATED_VN_TRAVEL", landmark.getSourceProvider());
        assertEquals("DATASET_ROW", landmark.getSourceObjectType());
        assertEquals("VNTRAVEL-001", landmark.getSourceObjectId());
        assertEquals("VERIFIED", landmark.getDataQualityStatus());
        assertEquals(false, landmark.getManualOverride());
        verify(locationRepository).flush();
    }

    @Test
    void importsCurrentProvinceWithoutRepurposingLegacyNumericCodes() {
        String locations = """
                [
                  {"name":"Tỉnh Bình Định","code":"52","districts":[]},
                  {"name":"Tỉnh Gia Lai","code":"64","districts":[]}
                ]
                """;
        String current = """
                [{"sourceCode":"VN34-52","officialCode":"52","name":"Tỉnh Gia Lai","legacyProvinceCodes":["52","64"]}]
                """;
        ReflectionTestUtils.setField(service, "locationResource",
                new ByteArrayResource(locations.getBytes(StandardCharsets.UTF_8), "legacy-provinces.json"));
        ReflectionTestUtils.setField(service, "currentProvinceResource",
                new ByteArrayResource(current.getBytes(StandardCharsets.UTF_8), "current-provinces.json"));
        when(locationRepository.findAll()).thenReturn(List.of());
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocationImportService.ImportReport report = service.importData(false);

        assertEquals(3, report.provinces());
        ArgumentCaptor<Location> captor = ArgumentCaptor.forClass(Location.class);
        verify(locationRepository, org.mockito.Mockito.atLeast(3)).save(captor.capture());
        List<Location> provinces = captor.getAllValues().stream()
                .filter(location -> "PROVINCE".equals(location.getLocationType())).toList();
        assertTrue(provinces.stream().anyMatch(location -> "52".equals(location.getSourceCode())
                && "Tỉnh Bình Định".equals(location.getNameVi())));
        assertTrue(provinces.stream().anyMatch(location -> "64".equals(location.getSourceCode())
                && "Tỉnh Gia Lai".equals(location.getNameVi())));
        assertTrue(provinces.stream().anyMatch(location -> "VN34-52".equals(location.getSourceCode())
                && "Tỉnh Gia Lai".equals(location.getNameVi())));
    }

    @Test
    void repeatedImportIsIdempotentAcrossLegacyCurrentAndLandmarkRows() {
        String locations = """
                [
                  {"name":"Tỉnh Cũ A","code":"1","districts":[]},
                  {"name":"Tỉnh Cũ B","code":"2","districts":[]}
                ]
                """;
        String current = """
                [{"sourceCode":"VN34-01","officialCode":"01","name":"Tỉnh Mới","legacyProvinceCodes":["1","2"]}]
                """;
        String landmarks = """
                [
                  {"code":"LM-A","nameVi":"Điểm A","provinceCode":"VN34-01","latitude":21.0,"longitude":105.0,"status":"ACTIVE","sourceProvider":"SOURCE","sourceObjectType":"ROW","sourceObjectId":"A","sourceUpdatedAt":"2026-07-29T00:00:00","dataQualityStatus":"VERIFIED"},
                  {"code":"LM-B","nameVi":"Điểm B","provinceCode":"VN34-01","latitude":21.1,"longitude":105.1,"status":"ACTIVE","sourceProvider":"SOURCE","sourceObjectType":"ROW","sourceObjectId":"B","sourceUpdatedAt":"2026-07-29T00:00:00","dataQualityStatus":"VERIFIED"}
                ]
                """;
        ReflectionTestUtils.setField(service, "locationResource",
                new ByteArrayResource(locations.getBytes(StandardCharsets.UTF_8), "idempotent-locations.json"));
        ReflectionTestUtils.setField(service, "currentProvinceResource",
                new ByteArrayResource(current.getBytes(StandardCharsets.UTF_8), "idempotent-current.json"));
        ReflectionTestUtils.setField(service, "landmarkResource",
                new ByteArrayResource(landmarks.getBytes(StandardCharsets.UTF_8), "idempotent-landmarks.json"));

        List<Location> stored = new ArrayList<>();
        AtomicLong ids = new AtomicLong(1);
        when(locationRepository.findAll()).thenAnswer(invocation -> List.copyOf(stored));
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> {
            Location location = invocation.getArgument(0);
            if (location.getId() == null) {
                location.setId(ids.getAndIncrement());
                stored.add(location);
            }
            return location;
        });

        LocationImportService.ImportReport first = service.importData(false);
        int firstSize = stored.size();
        LocationImportService.ImportReport second = service.importData(false);

        assertEquals(5, first.added());
        assertEquals(0, second.added());
        assertEquals(firstSize, stored.size());
        assertEquals(2, stored.stream().filter(location -> "LANDMARK".equals(location.getLocationType())).count());
        assertEquals(1, stored.stream().filter(location -> "VN34-01".equals(location.getSourceCode())).count());
    }

    @Test
    void missingResourceFailsWithActionableConfigurationMessage() {
        ReflectionTestUtils.setField(service, "locationResource",
                new ClassPathResource("data/missing-locations.json"));

        IllegalStateException error = assertThrows(IllegalStateException.class, service::resolveSourceResource);

        assertTrue(error.getMessage().contains("LOCATION_IMPORT_RESOURCE"));
    }

    @Test
    void cleanupMarksMissingLandmarkWithoutDeletingIt() {
        String locations = """
                [{"name":"Tinh Test","code":"1","districts":[]}]
                """;
        Location province = new Location();
        province.setLocationType("PROVINCE");
        province.setSourceCode("1");
        province.setCode("P-1");
        province.setNameVi("Tinh Test");
        province.setStatus("ACTIVE");
        Location staleLandmark = new Location();
        staleLandmark.setLocationType("LANDMARK");
        staleLandmark.setSourceCode("LM-OLD");
        staleLandmark.setCode("LM-OLD");
        staleLandmark.setNameVi("Old Landmark");
        staleLandmark.setStatus("ACTIVE");
        staleLandmark.setManualOverride(false);

        ReflectionTestUtils.setField(service, "locationResource",
                new ByteArrayResource(locations.getBytes(StandardCharsets.UTF_8), "test-locations.json"));
        when(locationRepository.findAll()).thenReturn(List.of(province, staleLandmark));
        when(hotelRepository.findAll()).thenReturn(List.of());
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocationImportService.ImportReport report = service.importData(true);

        assertEquals(0, report.removed());
        assertEquals("ACTIVE", staleLandmark.getStatus());
        assertEquals("MISSING_SOURCE", staleLandmark.getDataQualityStatus());
        verify(locationRepository).save(staleLandmark);
    }

    @Test
    void stableSourceIdentityUpdatesLandmarkWhenCatalogCodeChanges() {
        String locations = """
                [{"name":"Tinh Test","code":"1","districts":[]}]
                """;
        String landmarks = """
                [{"code":"LM-NEW","nameVi":"Stable Landmark","provinceCode":"1","latitude":10.5,"longitude":106.5,"status":"ACTIVE","sourceProvider":"SOURCE","sourceObjectType":"ROW","sourceObjectId":"42","sourceUpdatedAt":"2026-07-29T00:00:00","dataQualityStatus":"VERIFIED"}]
                """;
        Location province = new Location();
        province.setLocationType("PROVINCE");
        province.setSourceCode("1");
        province.setCode("P-1");
        province.setNameVi("Tinh Test");
        province.setStatus("ACTIVE");
        Location existing = new Location();
        existing.setLocationType("LANDMARK");
        existing.setSourceCode("LM-OLD");
        existing.setCode("LM-OLD");
        existing.setNameVi("Stable Landmark");
        existing.setParent(province);
        existing.setLatitude(10.5);
        existing.setLongitude(106.5);
        existing.setStatus("ACTIVE");
        existing.setSourceProvider("SOURCE");
        existing.setSourceObjectType("ROW");
        existing.setSourceObjectId("42");
        existing.setSourceUpdatedAt(LocalDateTime.of(2026, 7, 29, 0, 0));
        existing.setDataQualityStatus("VERIFIED");
        existing.setManualOverride(false);

        ReflectionTestUtils.setField(service, "locationResource",
                new ByteArrayResource(locations.getBytes(StandardCharsets.UTF_8), "test-locations.json"));
        ReflectionTestUtils.setField(service, "landmarkResource",
                new ByteArrayResource(landmarks.getBytes(StandardCharsets.UTF_8), "test-landmarks.json"));
        when(locationRepository.findAll()).thenReturn(List.of(province, existing));
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocationImportService.ImportReport report = service.importData(false);

        assertEquals(0, report.added());
        assertEquals(1, report.landmarks());
        assertEquals("LM-NEW", existing.getCode());
        assertEquals("LM-NEW", existing.getSourceCode());
    }

    @Test
    void sourceRefreshPreservesManualLandmarkFields() {
        String locations = """
                [{"name":"Tinh Test","code":"1","districts":[]}]
                """;
        String landmarks = """
                [{"code":"LM-1","nameVi":"Source Name","provinceCode":"1","latitude":11.0,"longitude":107.0,"status":"ACTIVE","sourceProvider":"SOURCE","sourceObjectType":"ROW","sourceObjectId":"1","sourceUpdatedAt":"2026-07-29T00:00:00","dataQualityStatus":"VERIFIED","manualOverride":false}]
                """;
        Location province = new Location();
        province.setLocationType("PROVINCE");
        province.setSourceCode("1");
        province.setCode("P-1");
        province.setNameVi("Tinh Test");
        province.setStatus("ACTIVE");
        Location existing = new Location();
        existing.setLocationType("LANDMARK");
        existing.setSourceCode("LM-1");
        existing.setCode("LM-1");
        existing.setNameVi("Manual Name");
        existing.setParent(province);
        existing.setLatitude(10.5);
        existing.setLongitude(106.5);
        existing.setStatus("ACTIVE");
        existing.setSourceProvider("SOURCE");
        existing.setSourceObjectType("ROW");
        existing.setSourceObjectId("1");
        existing.setSourceUpdatedAt(LocalDateTime.of(2026, 7, 29, 0, 0));
        existing.setDataQualityStatus("VERIFIED");
        existing.setManualOverride(true);

        ReflectionTestUtils.setField(service, "locationResource",
                new ByteArrayResource(locations.getBytes(StandardCharsets.UTF_8), "test-locations.json"));
        ReflectionTestUtils.setField(service, "landmarkResource",
                new ByteArrayResource(landmarks.getBytes(StandardCharsets.UTF_8), "test-landmarks.json"));
        when(locationRepository.findAll()).thenReturn(List.of(province, existing));
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocationImportService.ImportReport report = service.importData(false);

        assertEquals(1, report.landmarks());
        assertEquals("Manual Name", existing.getNameVi());
        assertEquals(10.5, existing.getLatitude());
        assertEquals(106.5, existing.getLongitude());
        assertEquals(true, existing.getManualOverride());
    }
}
