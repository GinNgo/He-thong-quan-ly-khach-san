package com.hotel.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.dtos.home.HomeSpotlightDTO;
import com.hotel.entities.Hotel;
import com.hotel.entities.SponsoredPlacement;
import com.hotel.repositories.SponsoredPlacementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeSpotlightServiceTest {

    @Mock
    private SponsoredPlacementRepository placementRepository;
    @Mock
    private PropertyAccessService propertyAccessService;

    private HomeSpotlightService service;

    @BeforeEach
    void setUp() {
        service = new HomeSpotlightService(placementRepository, new ObjectMapper(), propertyAccessService);
    }

    @Test
    void sponsoredProjectionAlwaysDisclosesAndUsesCanonicalPropertyRoute() {
        Hotel hotel = hotel(501L);
        SponsoredPlacement placement = activePlacement(1L, "SPONSORED", hotel);
        when(placementRepository.findEligiblePublicPlacements(
                eq("HOME_PARTNER_SPOTLIGHT"), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(placement));
        when(propertyAccessService.isOperational(hotel)).thenReturn(true);

        List<HomeSpotlightDTO> result = service.publicSpotlights(6, "vi");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().disclosure()).isEqualTo("Được tài trợ");
        assertThat(result.getFirst().target().route()).isEqualTo("/hotel/501");
        assertThat(result.getFirst().kind()).isEqualTo("SPONSORED");
    }

    @Test
    void editorialSearchTargetUsesOnlyAllowlistedParametersAndEnglishDisclosure() {
        SponsoredPlacement placement = activePlacement(2L, "EDITORIAL", null);
        placement.setTargetType("SEARCH_COLLECTION");
        placement.setTargetHotel(null);
        placement.setTargetQueryJson("{\"provinceId\":\"10146\",\"sortBy\":\"NEAREST\"}");
        when(placementRepository.findEligiblePublicPlacements(
                eq("HOME_PARTNER_SPOTLIGHT"), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(placement));

        List<HomeSpotlightDTO> result = service.publicSpotlights(6, "en");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().disclosure()).isEqualTo("Editorial");
        assertThat(result.getFirst().target().route())
                .isEqualTo("/search?provinceId=10146&sortBy=NEAREST");
    }

    @Test
    void invalidTargetKeyIsOmittedInsteadOfLeakingAnArbitraryRoute() {
        SponsoredPlacement placement = activePlacement(3L, "EDITORIAL", null);
        placement.setTargetType("SEARCH_COLLECTION");
        placement.setTargetHotel(null);
        placement.setTargetQueryJson("{\"redirectUrl\":\"https://evil.example\"}");
        when(placementRepository.findEligiblePublicPlacements(
                eq("HOME_PARTNER_SPOTLIGHT"), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(placement));

        assertThat(service.publicSpotlights(6, "vi")).isEmpty();
    }

    @Test
    void expiredOrExhaustedPlacementIsOmittedEvenIfRepositoryReturnsIt() {
        SponsoredPlacement expired = activePlacement(4L, "EDITORIAL", null);
        expired.setEndsAt(Instant.now().minusSeconds(1));
        expired.setTargetType("SEARCH_COLLECTION");
        expired.setTargetHotel(null);
        expired.setTargetQueryJson("{\"provinceId\":\"10146\"}");
        SponsoredPlacement exhausted = activePlacement(5L, "EDITORIAL", null);
        exhausted.setTargetType("SEARCH_COLLECTION");
        exhausted.setTargetHotel(null);
        exhausted.setTargetQueryJson("{\"provinceId\":\"10146\"}");
        exhausted.setImpressionLimit(10L);
        exhausted.setImpressionCount(10L);
        when(placementRepository.findEligiblePublicPlacements(
                eq("HOME_PARTNER_SPOTLIGHT"), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(expired, exhausted));

        assertThat(service.publicSpotlights(6, "vi")).isEmpty();
    }

    private SponsoredPlacement activePlacement(Long id, String kind, Hotel hotel) {
        SponsoredPlacement placement = new SponsoredPlacement();
        placement.setId(id);
        placement.setHotel("SPONSORED".equals(kind) ? hotel : null);
        placement.setPlacementSurface("HOME_PARTNER_SPOTLIGHT");
        placement.setPlacementKind(kind);
        placement.setStatus("ACTIVE");
        placement.setTitleVi("Khám phá kỳ nghỉ");
        placement.setTitleEn("Discover your stay");
        placement.setDescriptionVi("Nội dung được quản trị");
        placement.setDescriptionEn("Governed content");
        placement.setImageUrl("/media/placements/spotlight.webp");
        placement.setImageAltVi("Khu nghỉ dưỡng");
        placement.setImageAltEn("Resort");
        placement.setTargetType("PROPERTY");
        placement.setTargetHotel(hotel);
        placement.setStartsAt(Instant.now().minusSeconds(60));
        placement.setEndsAt(Instant.now().plusSeconds(3600));
        placement.setApprovedAt(Instant.now().minusSeconds(30));
        placement.setSortPriority(10);
        placement.setBudget(BigDecimal.valueOf(1_000_000));
        placement.setSpentAmount(BigDecimal.ZERO);
        placement.setImpressionLimit(100L);
        placement.setImpressionCount(0L);
        placement.setClickLimit(20L);
        placement.setClickCount(0L);
        return placement;
    }

    private Hotel hotel(Long id) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        return hotel;
    }
}

