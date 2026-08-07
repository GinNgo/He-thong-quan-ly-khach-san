package com.hotel.services;

import com.hotel.dto.PropertySearchResponseDTO;
import com.hotel.dtos.PromotionQuoteDTO;
import com.hotel.dtos.home.HomeRecommendationItemDTO;
import com.hotel.dtos.home.HomeRecommendationResponseDTO;
import com.hotel.entities.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeRecommendationServiceTest {

    @Mock
    private ProvinceCompatibilityService provinceCompatibilityService;

    @Mock
    private PropertySearchService propertySearchService;

    private HomeRecommendationService service;
    private Location currentProvince;

    @BeforeEach
    void setUp() {
        service = new HomeRecommendationService(provinceCompatibilityService, propertySearchService);
        currentProvince = province(101L, "VN34-48", "Đà Nẵng", "Da Nang");
        lenient().when(provinceCompatibilityService.currentProvinceForId(any())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return id != null && (id == 101L || id == 48L) ? currentProvince : null;
        });
    }

    @Test
    void defaultDestinationPrefersRequestedProvinceAndExcludesEmptySupply() {
        Location secondary = province(202L, "VN34-79", "Hồ Chí Minh", "Ho Chi Minh City");
        when(provinceCompatibilityService.currentProvinces()).thenReturn(List.of(currentProvince, secondary));
        when(propertySearchService.searchProperties(any())).thenReturn(page(2, property(1L, 3)));

        var destinations = service.recommendationDestinations(101L, 5, "vi");

        assertEquals(2, destinations.size());
        assertEquals(101L, destinations.get(0).id());
        assertTrue(destinations.get(0).selectedByDefault());
    }

    @Test
    void legacyProvinceIsNormalizedAndSearchContextIsPreserved() {
        when(propertySearchService.searchProperties(any())).thenReturn(page(1, property(501L, 2)));

        HomeRecommendationResponseDTO response = service.recommendations(
                new HomeRecommendationService.RecommendationQuery(
                        48L, "2026-08-05", "2026-08-07", "OVERNIGHT", 2, 1, 1, 8, "en"));

        assertEquals(101L, response.destination().id());
        assertEquals(HomeRecommendationItemDTO.RecommendationReason.SEARCH_CONTEXT,
                response.items().get(0).recommendationReason());
        ArgumentCaptor<com.hotel.dto.PropertySearchRequestDTO> captor =
                ArgumentCaptor.forClass(com.hotel.dto.PropertySearchRequestDTO.class);
        verify(propertySearchService).searchProperties(captor.capture());
        var request = captor.getValue();
        assertEquals(101L, request.getProvinceId());
        assertEquals("2026-08-05", request.getCheckInDate());
        assertEquals("2026-08-07", request.getCheckOutDate());
        assertEquals("RATING", request.getSortBy());
    }

    @Test
    void unavailablePropertiesAreNotProjected() {
        when(propertySearchService.searchProperties(any())).thenReturn(new PageImpl<>(List.of(
                property(501L, 0), property(502L, 2))));

        HomeRecommendationResponseDTO response = service.recommendations(
                new HomeRecommendationService.RecommendationQuery(101L, null, null,
                        null, null, null, null, 8, "vi"));

        assertEquals(List.of(502L), response.items().stream().map(item -> item.propertyId()).toList());
    }

    @Test
    void canonicalQuoteAndDiscountedNightlyPriceAreProjectedWithoutRecalculation() {
        PropertySearchResponseDTO item = property(501L, 2);
        item.getPricing().setDiscountedNightlyPrice(BigDecimal.valueOf(450000));
        item.setQuote(new PromotionQuoteDTO(
                "quote-501",
                Instant.parse("2026-08-05T00:15:00Z"),
                501L,
                901L,
                BigDecimal.valueOf(500000),
                1,
                1,
                BigDecimal.valueOf(500000),
                BigDecimal.valueOf(60000),
                BigDecimal.valueOf(15000),
                BigDecimal.valueOf(75000),
                List.of(new PromotionQuoteDTO.AppliedPromotion(
                        71L, "MEMBER10", "AUTOMATIC", "Gi\u00e1 th\u00e0nh vi\u00ean", "Member price",
                        BigDecimal.valueOf(50000))),
                new PromotionQuoteDTO.MemberBenefit(true, "GOLD", "V\u00e0ng", "Gold", "Active tier"),
                BigDecimal.valueOf(50000),
                BigDecimal.valueOf(525000),
                "VND"));
        when(propertySearchService.searchProperties(any())).thenReturn(page(1, item));

        HomeRecommendationResponseDTO response = service.recommendations(
                new HomeRecommendationService.RecommendationQuery(
                        101L, "2026-08-04", "2026-08-05", "OVERNIGHT", 2, 0, 1, 8, "vi"));

        HomeRecommendationItemDTO projected = response.items().get(0);
        assertEquals("quote-501", projected.quote().quoteId());
        assertEquals(BigDecimal.valueOf(450000), projected.pricing().finalNightlyPrice());
        assertEquals(BigDecimal.valueOf(50000), projected.pricing().totalDiscount());
        assertEquals(BigDecimal.valueOf(525000), projected.quote().finalTotal());
    }

    @Test
    void invalidDatesAndLimitsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.recommendations(
                new HomeRecommendationService.RecommendationQuery(101L, "2026-08-08", "2026-08-08",
                        null, null, null, null, 8, "vi")));
        assertThrows(IllegalArgumentException.class, () -> service.recommendations(
                new HomeRecommendationService.RecommendationQuery(101L, null, null,
                        null, null, null, null, 13, "vi")));
        assertThrows(IllegalArgumentException.class, () -> service.recommendations(
                new HomeRecommendationService.RecommendationQuery(999L, null, null,
                        null, null, null, null, 8, "vi")));
    }

    @Test
    void emptyDestinationSupplyReturnsEmptyList() {
        when(provinceCompatibilityService.currentProvinces()).thenReturn(List.of(currentProvince));
        when(propertySearchService.searchProperties(any())).thenReturn(page(0));

        assertTrue(service.recommendationDestinations(null, 5, "vi").isEmpty());
    }

    private PageImpl<PropertySearchResponseDTO> page(long total, PropertySearchResponseDTO... items) {
        return new PageImpl<>(List.of(items), org.springframework.data.domain.PageRequest.of(0, 8), total);
    }

    private PropertySearchResponseDTO property(Long id, int availableRooms) {
        PropertySearchResponseDTO item = new PropertySearchResponseDTO();
        item.setId(id);
        item.setName("Property " + id);
        item.setPropertyType("HOTEL");
        item.setAvailableRoomCount(availableRooms);
        item.setReviewScore(9.0);
        item.setReviewCount(20);
        item.setThumbnailUrl("/images/" + id + ".webp");
        item.setPricing(new PropertySearchResponseDTO.PricingSummary(
                BigDecimal.valueOf(500000), BigDecimal.valueOf(500000), BigDecimal.valueOf(500000),
                1, 1, BigDecimal.valueOf(500000), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(500000), "VND"));
        return item;
    }

    private Location province(Long id, String sourceCode, String nameVi, String nameEn) {
        Location location = new Location();
        location.setId(id);
        location.setCode("P-" + id);
        location.setSourceCode(sourceCode);
        location.setNameVi(nameVi);
        location.setNameEn(nameEn);
        location.setLocationType("PROVINCE");
        location.setStatus("ACTIVE");
        return location;
    }
}
