package com.hotel.services;

import com.hotel.dtos.PublicPlacementDisclosureDTO;
import com.hotel.entities.SponsoredPlacement;
import com.hotel.repositories.SponsoredPlacementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Resolves disclosure only from an approved, scheduled, server-managed placement. */
@Service
@RequiredArgsConstructor
public class PublicPlacementDisclosureService {

    private static final String SEARCH_SURFACE = "SEARCH_RESULTS";
    private final SponsoredPlacementRepository placementRepository;
    private final PropertyAccessService propertyAccessService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Map<Long, PublicPlacementDisclosureDTO> searchDisclosures(Collection<Long> hotelIds) {
        if (hotelIds == null || hotelIds.isEmpty()) return Map.of();
        Instant at = clock.instant();
        Map<Long, PublicPlacementDisclosureDTO> result = new HashMap<>();
        placementRepository.findEligiblePublicSearchPlacements(
                        SEARCH_SURFACE, hotelIds, at, PageRequest.of(0, Math.min(hotelIds.size(), 100)))
                .stream()
                .filter(placement -> isEligible(placement, at))
                .forEach(placement -> result.putIfAbsent(
                        placement.getTargetHotel().getId(), toDto(placement)));
        return Map.copyOf(result);
    }

    @Transactional(readOnly = true)
    public Optional<PublicPlacementDisclosureDTO> searchDisclosure(Long hotelId) {
        if (hotelId == null || hotelId <= 0) return Optional.empty();
        return searchDisclosures(List.of(hotelId)).values().stream().findFirst();
    }

    private boolean isEligible(SponsoredPlacement placement, Instant at) {
        if (!SEARCH_SURFACE.equals(placement.getPlacementSurface())
                || !"ACTIVE".equals(placement.getStatus())
                || placement.getApprovedAt() == null
                || placement.getStartsAt() == null
                || placement.getEndsAt() == null
                || placement.getStartsAt().isAfter(at)
                || !placement.getEndsAt().isAfter(at)
                || placement.getTargetHotel() == null
                || !propertyAccessService.isOperational(placement.getTargetHotel())) return false;
        if (placement.getImpressionLimit() != null
                && placement.getImpressionCount() >= placement.getImpressionLimit()) return false;
        if (placement.getClickLimit() != null
                && placement.getClickCount() >= placement.getClickLimit()) return false;
        if (placement.getBudget() != null && placement.getSpentAmount() != null
                && placement.getSpentAmount().compareTo(placement.getBudget()) >= 0) return false;
        return "SPONSORED".equals(placement.getPlacementKind());
    }

    private PublicPlacementDisclosureDTO toDto(SponsoredPlacement placement) {
        return new PublicPlacementDisclosureDTO(
                placement.getId(), placement.getPlacementKind(), "Được tài trợ", "Sponsored", placement.getEndsAt());
    }
}
