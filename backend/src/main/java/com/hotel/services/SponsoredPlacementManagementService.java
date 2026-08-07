package com.hotel.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.dtos.SponsoredPlacementDTO;
import com.hotel.dtos.SponsoredPlacementRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.SponsoredPlacement;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.SponsoredPlacementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SponsoredPlacementManagementService {

    static final String SUBSCRIPTION_FEATURE = "SPONSORED_PLACEMENTS";
    private static final Set<String> COUNTED_STATUSES = Set.of("DRAFT", "SCHEDULED", "ACTIVE", "PAUSED");
    private static final Set<String> TARGET_QUERY_KEYS = Set.of(
            "provinceId", "landmarkId", "radiusKm", "sortBy", "stayType", "checkInDate", "checkOutDate",
            "adultCount", "childCount", "roomCount");
    private static final String HOME_SURFACE = "HOME_PARTNER_SPOTLIGHT";

    private final SponsoredPlacementRepository placementRepository;
    private final HotelRepository hotelRepository;
    private final PropertyAccessService propertyAccessService;
    private final SubscriptionFeatureService subscriptionFeatureService;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();

    @Transactional(readOnly = true)
    public List<SponsoredPlacementDTO> list(Long hotelId) {
        if (propertyAccessService.isSystemAdministrator()) {
            List<SponsoredPlacement> placements = hotelId == null
                    ? placementRepository.findAll()
                    : placementsForHotel(hotelId);
            return placements.stream().sorted(order()).map(this::toDto).toList();
        }
        if (hotelId == null) {
            throw new IllegalArgumentException("hotelId is required for tenant placement management.");
        }
        propertyAccessService.requireAssignedHotel(hotelId);
        return placementsForHotel(hotelId).stream().sorted(order()).map(this::toDto).toList();
    }

    @Transactional
    public SponsoredPlacementDTO create(SponsoredPlacementRequest request) {
        validateRequest(request);
        SponsoredPlacement placement = new SponsoredPlacement();
        applyOwner(placement, request);
        if (request.placementKind() == SponsoredPlacementRequest.PlacementKind.SPONSORED) {
            long current = placementRepository.countByHotelIdAndStatusIn(placement.getHotel().getId(), COUNTED_STATUSES);
            subscriptionFeatureService.checkFeatureLimitForProperty(
                    placement.getHotel().getId(), SUBSCRIPTION_FEATURE, current, 1);
        }
        applyMutableFields(placement, request);
        placement.setStatus(SponsoredPlacementDTO.PlacementStatus.DRAFT.name());
        return toDto(placementRepository.save(placement));
    }

    @Transactional
    public SponsoredPlacementDTO update(Long id, SponsoredPlacementRequest request) {
        validateRequest(request);
        SponsoredPlacement placement = requirePlacement(id);
        requireCanManage(placement);
        requireSameOwner(placement, request);
        if ("ACTIVE".equals(placement.getStatus())) {
            throw new IllegalStateException("Pause the placement before editing it.");
        }
        if (placement.getHotel() != null && "SPONSORED".equals(placement.getPlacementKind())) {
            subscriptionFeatureService.requireFeatureForProperty(placement.getHotel().getId(), SUBSCRIPTION_FEATURE);
        }
        applyMutableFields(placement, request);
        return toDto(placementRepository.save(placement));
    }

    @Transactional
    public SponsoredPlacementDTO approve(Long id) {
        requireSystemAdministrator();
        SponsoredPlacement placement = requirePlacementForUpdate(id);
        validatePublication(placement);
        Instant now = clock.instant();
        placement.setApprovedAt(now);
        placement.setApprovedBy(propertyAccessService.currentUser());
        placement.setStatus(placement.getStartsAt().isAfter(now)
                ? SponsoredPlacementDTO.PlacementStatus.SCHEDULED.name()
                : SponsoredPlacementDTO.PlacementStatus.ACTIVE.name());
        placement.setRejectedReason(null);
        return toDto(placementRepository.save(placement));
    }

    @Transactional
    public SponsoredPlacementDTO pause(Long id) {
        SponsoredPlacement placement = requirePlacementForUpdate(id);
        requireCanManage(placement);
        if (!Set.of("ACTIVE", "SCHEDULED").contains(placement.getStatus())) {
            throw new IllegalStateException("Only active or scheduled placements can be paused.");
        }
        placement.setStatus(SponsoredPlacementDTO.PlacementStatus.PAUSED.name());
        return toDto(placementRepository.save(placement));
    }

    @Transactional
    public SponsoredPlacementDTO reject(Long id, String reason) {
        requireSystemAdministrator();
        SponsoredPlacement placement = requirePlacementForUpdate(id);
        placement.setStatus(SponsoredPlacementDTO.PlacementStatus.REJECTED.name());
        placement.setRejectedReason(reason == null || reason.isBlank() ? "Rejected by platform administrator." : reason.trim());
        return toDto(placementRepository.save(placement));
    }

    private List<SponsoredPlacement> placementsForHotel(Long hotelId) {
        List<SponsoredPlacement> placements = new ArrayList<>(placementRepository.findByHotelIsNullOrderByStartsAtDescIdDesc());
        placements.addAll(placementRepository.findByHotelIdOrderByStartsAtDescIdDesc(hotelId));
        return placements;
    }

    private SponsoredPlacement requirePlacement(Long id) {
        return placementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sponsored placement not found."));
    }

    private SponsoredPlacement requirePlacementForUpdate(Long id) {
        return placementRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sponsored placement not found."));
    }

    private void applyOwner(SponsoredPlacement placement, SponsoredPlacementRequest request) {
        if (request.placementKind() == SponsoredPlacementRequest.PlacementKind.EDITORIAL) {
            requireSystemAdministrator();
            if (request.hotelId() != null) {
                throw new IllegalArgumentException("Editorial placements cannot carry hotelId.");
            }
            placement.setHotel(null);
            return;
        }
        if (request.hotelId() == null) {
            throw new IllegalArgumentException("Sponsored placements require hotelId.");
        }
        placement.setHotel(propertyAccessService.requireManagedHotel(request.hotelId()));
    }

    private void requireSameOwner(SponsoredPlacement placement, SponsoredPlacementRequest request) {
        Long currentHotelId = placement.getHotel() == null ? null : placement.getHotel().getId();
        if (!java.util.Objects.equals(currentHotelId, request.hotelId())
                || !placement.getPlacementKind().equals(request.placementKind().name())) {
            throw new IllegalArgumentException("Placement ownership and kind cannot be changed.");
        }
    }

    private void requireCanManage(SponsoredPlacement placement) {
        if (placement.getHotel() == null) {
            requireSystemAdministrator();
            return;
        }
        propertyAccessService.requireAssignedHotel(placement.getHotel().getId());
    }

    private void applyMutableFields(SponsoredPlacement placement, SponsoredPlacementRequest request) {
        placement.setPlacementSurface(request.placementSurface().name());
        placement.setPlacementKind(request.placementKind().name());
        placement.setTitleVi(request.titleVi().trim());
        placement.setTitleEn(request.titleEn().trim());
        placement.setDescriptionVi(trimToNull(request.descriptionVi()));
        placement.setDescriptionEn(trimToNull(request.descriptionEn()));
        placement.setImageUrl(normalizeAssetUrl(request.imageUrl()));
        placement.setImageAltVi(request.imageAltVi().trim());
        placement.setImageAltEn(request.imageAltEn().trim());
        placement.setTargetType(request.targetType().name());
        placement.setTargetHotel(resolveTargetHotel(request));
        placement.setTargetQueryJson(writeStringJson(request.targetQuery()));
        placement.setTargetProvinceId(request.targetProvinceId());
        placement.setTargetLandmarkId(request.targetLandmarkId());
        placement.setStartsAt(request.startsAt());
        placement.setEndsAt(request.endsAt());
        placement.setSortPriority(request.sortPriority() == null ? 0 : request.sortPriority());
        placement.setBudget(request.budget());
        placement.setImpressionLimit(request.impressionLimit());
        placement.setClickLimit(request.clickLimit());
        if (placement.getSpentAmount() == null) placement.setSpentAmount(BigDecimal.ZERO);
        if (placement.getImpressionCount() == null) placement.setImpressionCount(0L);
        if (placement.getClickCount() == null) placement.setClickCount(0L);
    }

    private Hotel resolveTargetHotel(SponsoredPlacementRequest request) {
        if (request.targetType() != SponsoredPlacementRequest.TargetType.PROPERTY) return null;
        if (request.targetHotelId() == null) {
            throw new IllegalArgumentException("Property targets require targetHotelId.");
        }
        Hotel target = hotelRepository.findById(request.targetHotelId())
                .orElseThrow(() -> new ResourceNotFoundException("Target property not found."));
        if (!propertyAccessService.isOperational(target)) {
            throw new IllegalStateException("Target property is not approved and operational.");
        }
        if (request.placementKind() == SponsoredPlacementRequest.PlacementKind.SPONSORED
                && !request.hotelId().equals(target.getId())) {
            throw new SecurityException("A tenant may sponsor only its own property.");
        }
        return target;
    }

    private void validateRequest(SponsoredPlacementRequest request) {
        if (request.placementSurface() != SponsoredPlacementRequest.PlacementSurface.HOME_PARTNER_SPOTLIGHT
                && request.placementSurface() != SponsoredPlacementRequest.PlacementSurface.SEARCH_RESULTS) {
            throw new IllegalArgumentException("Unsupported placement surface.");
        }
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new IllegalArgumentException("Placement endsAt must be after startsAt.");
        }
        normalizeAssetUrl(request.imageUrl());
        if (request.targetType() == SponsoredPlacementRequest.TargetType.PROPERTY) {
            if (request.targetHotelId() == null || request.targetQuery() != null && !request.targetQuery().isEmpty()) {
                throw new IllegalArgumentException("Property target requires only targetHotelId.");
            }
        } else if (request.targetQuery() == null || request.targetQuery().isEmpty()) {
            throw new IllegalArgumentException("Search collection target requires an allowlisted query.");
        } else if (!TARGET_QUERY_KEYS.containsAll(request.targetQuery().keySet())) {
            throw new IllegalArgumentException("Target query contains a key outside the public allowlist.");
        }
        if (request.placementKind() == SponsoredPlacementRequest.PlacementKind.SPONSORED
                && request.hotelId() == null) {
            throw new IllegalArgumentException("Sponsored placements require a tenant hotelId.");
        }
    }

    private void validatePublication(SponsoredPlacement placement) {
        if (!HOME_SURFACE.equals(placement.getPlacementSurface()) && !"SEARCH_RESULTS".equals(placement.getPlacementSurface())) {
            throw new IllegalStateException("Placement surface is not publishable.");
        }
        if (!placement.getEndsAt().isAfter(clock.instant())) {
            throw new IllegalStateException("Expired placements cannot be published.");
        }
        normalizeAssetUrl(placement.getImageUrl());
        if (placement.getTargetHotel() == null && placement.getTargetQueryJson() == null) {
            throw new IllegalStateException("Placement target is missing.");
        }
        if (placement.getTargetHotel() != null && !propertyAccessService.isOperational(placement.getTargetHotel())) {
            throw new IllegalStateException("Target property is not approved and operational.");
        }
        if (placement.getPlacementKind().equals("SPONSORED")
                && (placement.getHotel() == null || !propertyAccessService.isOperational(placement.getHotel()))) {
            throw new IllegalStateException("Sponsored placement owner is not approved and operational.");
        }
    }

    private SponsoredPlacementDTO toDto(SponsoredPlacement placement) {
        return new SponsoredPlacementDTO(
                placement.getId(), placement.getHotel() == null ? null : placement.getHotel().getId(),
                SponsoredPlacementRequest.PlacementSurface.valueOf(placement.getPlacementSurface()),
                SponsoredPlacementRequest.PlacementKind.valueOf(placement.getPlacementKind()),
                SponsoredPlacementDTO.PlacementStatus.valueOf(placement.getStatus()),
                placement.getTitleVi(), placement.getTitleEn(), placement.getDescriptionVi(), placement.getDescriptionEn(),
                placement.getImageUrl(), placement.getImageAltVi(), placement.getImageAltEn(),
                SponsoredPlacementRequest.TargetType.valueOf(placement.getTargetType()),
                placement.getTargetHotel() == null ? null : placement.getTargetHotel().getId(),
                readStringJson(placement.getTargetQueryJson()), placement.getTargetProvinceId(), placement.getTargetLandmarkId(),
                placement.getStartsAt(), placement.getEndsAt(), placement.getSortPriority(), placement.getBudget(),
                placement.getSpentAmount(), placement.getImpressionLimit(), placement.getImpressionCount(),
                placement.getClickLimit(), placement.getClickCount(), placement.getApprovedBy() == null ? null : placement.getApprovedBy().getId(),
                placement.getApprovedAt(), placement.getRejectedReason(), placement.getCreatedAt(), placement.getUpdatedAt());
    }

    private Comparator<SponsoredPlacement> order() {
        return Comparator.comparing(SponsoredPlacement::getSortPriority, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SponsoredPlacement::getId, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private String normalizeAssetUrl(String value) {
        String url = value == null ? "" : value.trim();
        if (!(url.startsWith("/assets/") || url.startsWith("/media/"))) {
            throw new IllegalArgumentException("Only server-managed /assets/ or /media/ assets are allowed.");
        }
        if (url.contains("..") || url.contains("\\") || url.contains("javascript:") || url.contains("data:")) {
            throw new IllegalArgumentException("Asset URL is not safe.");
        }
        return url;
    }

    private String writeStringJson(Map<String, String> value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Target query is not valid JSON data.", exception);
        }
    }

    private Map<String, String> readStringJson(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored target query is invalid.", exception);
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void requireSystemAdministrator() {
        if (!propertyAccessService.isSystemAdministrator()) {
            throw new SecurityException("Only a platform administrator can manage editorial placement approval.");
        }
    }
}
