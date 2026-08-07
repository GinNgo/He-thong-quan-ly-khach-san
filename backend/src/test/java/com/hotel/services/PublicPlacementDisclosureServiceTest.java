package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.SponsoredPlacement;
import com.hotel.repositories.SponsoredPlacementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicPlacementDisclosureServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-03T08:00:00Z");

    @Mock
    private SponsoredPlacementRepository placementRepository;
    @Mock
    private PropertyAccessService propertyAccessService;

    private PublicPlacementDisclosureService service;

    @BeforeEach
    void setUp() {
        service = new PublicPlacementDisclosureService(
                placementRepository,
                propertyAccessService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void exposesOnlyApprovedActiveInQuotaSponsoredPlacementsForOperationalProperties() {
        SponsoredPlacement eligible = placement(1L, hotel(11L), "SPONSORED", "ACTIVE",
                NOW.minusSeconds(60), NOW.plusSeconds(60));
        eligible.setApprovedAt(NOW.minusSeconds(120));
        SponsoredPlacement expired = placement(2L, hotel(12L), "SPONSORED", "ACTIVE",
                NOW.minusSeconds(120), NOW);
        expired.setApprovedAt(NOW.minusSeconds(180));
        SponsoredPlacement unapproved = placement(3L, hotel(13L), "SPONSORED", "ACTIVE",
                NOW.minusSeconds(60), NOW.plusSeconds(60));
        SponsoredPlacement exhausted = placement(4L, hotel(14L), "SPONSORED", "ACTIVE",
                NOW.minusSeconds(60), NOW.plusSeconds(60));
        exhausted.setApprovedAt(NOW.minusSeconds(120));
        exhausted.setImpressionLimit(10L);
        exhausted.setImpressionCount(10L);
        SponsoredPlacement editorial = placement(5L, hotel(15L), "EDITORIAL", "ACTIVE",
                NOW.minusSeconds(60), NOW.plusSeconds(60));
        editorial.setApprovedAt(NOW.minusSeconds(120));
        SponsoredPlacement inactiveProperty = placement(6L, hotel(16L), "SPONSORED", "ACTIVE",
                NOW.minusSeconds(60), NOW.plusSeconds(60));
        inactiveProperty.setApprovedAt(NOW.minusSeconds(120));

        when(placementRepository.findEligiblePublicSearchPlacements(
                eq("SEARCH_RESULTS"), anyCollection(), eq(NOW), any()))
                .thenReturn(List.of(eligible, expired, unapproved, exhausted, editorial, inactiveProperty));
        when(propertyAccessService.isOperational(any(Hotel.class)))
                .thenAnswer(invocation -> ((Hotel) invocation.getArgument(0)).getId().equals(11L));

        var result = service.searchDisclosures(List.of(11L, 12L, 13L, 14L, 15L, 16L));

        assertThat(result).containsOnlyKeys(11L);
        assertThat(result.get(11L).placementKind()).isEqualTo("SPONSORED");
        assertThat(result.get(11L).disclosureVi()).isEqualTo("\u0110\u01b0\u1ee3c t\u00e0i tr\u1ee3");
        assertThat(result.get(11L).disclosureEn()).isEqualTo("Sponsored");
    }

    @Test
    void invalidSingleHotelLookupReturnsNoDisclosureWithoutQueryingARepository() {
        assertThat(service.searchDisclosure(null)).isEmpty();
        assertThat(service.searchDisclosure(0L)).isEmpty();
    }

    private SponsoredPlacement placement(
            Long id,
            Hotel target,
            String kind,
            String status,
            Instant startsAt,
            Instant endsAt) {
        SponsoredPlacement placement = new SponsoredPlacement();
        placement.setId(id);
        placement.setPlacementSurface("SEARCH_RESULTS");
        placement.setPlacementKind(kind);
        placement.setStatus(status);
        placement.setTargetHotel(target);
        placement.setStartsAt(startsAt);
        placement.setEndsAt(endsAt);
        placement.setBudget(BigDecimal.valueOf(1_000_000));
        placement.setSpentAmount(BigDecimal.ZERO);
        placement.setImpressionCount(0L);
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
