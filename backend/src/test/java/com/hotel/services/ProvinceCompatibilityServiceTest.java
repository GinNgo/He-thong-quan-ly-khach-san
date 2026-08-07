package com.hotel.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Location;
import com.hotel.repositories.LocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProvinceCompatibilityServiceTest {

    @Mock
    private LocationRepository locationRepository;

    private ProvinceCompatibilityService service;
    private Location current;
    private Location hoChiMinh;
    private Location binhDuong;
    private Location baRiaVungTau;

    @BeforeEach
    void setUp() {
        service = new ProvinceCompatibilityService(locationRepository, new ObjectMapper());
        ReflectionTestUtils.setField(service, "currentProvinceResource",
                new ClassPathResource("data/provinces-current-34.json"));
<<<<<<< HEAD
        current = province(100L, "VN34-79", "Ho Chi Minh City");
        hoChiMinh = province(79L, "79", "Ho Chi Minh City legacy");
        binhDuong = province(74L, "74", "Binh Duong legacy");
        baRiaVungTau = province(77L, "77", "Ba Ria - Vung Tau legacy");
    }

    @Test
    void catalogContainsExactly34CurrentAnd63UniqueLegacyCodes() {
        ProvinceCompatibilityService.CatalogValidation validation = service.validateCatalog();

        assertEquals(34, validation.currentProvinceCount());
        assertEquals(63, validation.legacyProvinceCount());
    }

    @Test
    void currentAndLegacySelectionsResolveToTheSameProvinceScope() {
        when(locationRepository.findByIdAndLocationType(100L, "PROVINCE")).thenReturn(Optional.of(current));
        when(locationRepository.findByIdAndLocationType(74L, "PROVINCE")).thenReturn(Optional.of(binhDuong));
        when(locationRepository.findByLocationTypeAndSourceCodeIn(eq("PROVINCE"), anyCollection()))
                .thenReturn(List.of(current, hoChiMinh, binhDuong, baRiaVungTau));

        assertEquals(Set.of(100L, 79L, 74L, 77L), service.provinceScopeIds(100L));
        assertEquals(Set.of(100L, 79L, 74L, 77L), service.provinceScopeIds(74L));
        assertTrue(service.sameProvinceScope(100L, 74L));
    }

    @Test
    void legacyProvinceProjectsToCurrentDisplayProvinceAndWardUnion() {
        Location ward = province(501L, "WARD-501", "Legacy ward");
        ward.setLocationType("WARD");
        ward.setParent(binhDuong);
        when(locationRepository.findByLocationTypeAndSourceCode("PROVINCE", "VN34-79"))
                .thenReturn(Optional.of(current));
        when(locationRepository.findByIdAndLocationType(100L, "PROVINCE")).thenReturn(Optional.of(current));
        when(locationRepository.findByLocationTypeAndSourceCodeIn(eq("PROVINCE"), anyCollection()))
                .thenReturn(List.of(current, hoChiMinh, binhDuong, baRiaVungTau));
        when(locationRepository.findByParentIdInAndLocationTypeAndStatusOrderByNameViAsc(
                anyCollection(), eq("WARD"), eq("ACTIVE"))).thenReturn(List.of(ward));

        assertEquals(current, service.currentProvinceFor(binhDuong));
        assertEquals(List.of(ward), service.wardsFor(100L));
=======
        current = province(100L, "VN34-79", "Thành phố Hồ Chí Minh");
        hoChiMinh = province(79L, "79", "Thành phố Hồ Chí Minh");
        binhDuong = province(74L, "74", "Tỉnh Bình Dương");
        baRiaVungTau = province(77L, "77", "Tỉnh Bà Rịa - Vũng Tàu");
    }

    @Test
    void currentProvinceScopeIncludesEveryLegacyMember() {
        when(locationRepository.findByIdAndLocationType(100L, "PROVINCE")).thenReturn(Optional.of(current));
        when(locationRepository.findByLocationTypeAndSourceCodeIn(eq("PROVINCE"), anyCollection()))
                .thenReturn(List.of(current, hoChiMinh, binhDuong, baRiaVungTau));

        Set<Long> ids = service.provinceScopeIds(100L);

        assertEquals(Set.of(100L, 79L, 74L, 77L), ids);
    }

    @Test
    void legacyProvinceResolvesToCurrentDisplayProvince() {
        when(locationRepository.findByLocationTypeAndSourceCode("PROVINCE", "VN34-79"))
                .thenReturn(Optional.of(current));

        Location resolved = service.currentProvinceFor(binhDuong);

        assertEquals(current, resolved);
        assertTrue(service.currentProvinceFor(hoChiMinh).getSourceCode().startsWith("VN34-"));
>>>>>>> codex/ui-functional-audit-polish
    }

    private Location province(Long id, String sourceCode, String name) {
        Location location = new Location();
        location.setId(id);
        location.setCode("P-" + sourceCode);
        location.setSourceCode(sourceCode);
        location.setNameVi(name);
        location.setLocationType("PROVINCE");
        location.setStatus("ACTIVE");
        return location;
    }
}
