package com.hotel.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.dtos.home.HomeSpotlightDTO;
import com.hotel.entities.SponsoredPlacement;
import com.hotel.repositories.SponsoredPlacementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HomeSpotlightService {

    private static final String HOME_SURFACE = "HOME_PARTNER_SPOTLIGHT";
    private static final Set<String> ALLOWED_QUERY_KEYS = Set.of(
            "provinceId", "landmarkId", "radiusKm", "sortBy", "stayType", "checkInDate", "checkOutDate",
            "adultCount", "childCount", "roomCount");

    private final SponsoredPlacementRepository placementRepository;
    private final ObjectMapper objectMapper;
    private final PropertyAccessService propertyAccessService;
    private final Clock clock = Clock.systemUTC();

    @Transactional(readOnly = true)
    public List<HomeSpotlightDTO> publicSpotlights(int limit, String locale) {
        int boundedLimit = Math.max(1, Math.min(limit, 10));
        String normalizedLocale = normalizeLocale(locale);
        return placementRepository.findEligiblePublicPlacements(
                        HOME_SURFACE, clock.instant(), PageRequest.of(0, boundedLimit * 2)).stream()
                .filter(this::isPubliclyEligible)
                .map(placement -> toProjection(placement, normalizedLocale))
                .flatMap(java.util.Optional::stream)
                .limit(boundedLimit)
                .toList();
    }

    private boolean isPubliclyEligible(SponsoredPlacement placement) {
        if (!"HOME_PARTNER_SPOTLIGHT".equals(placement.getPlacementSurface())
                || !"ACTIVE".equals(placement.getStatus())
                || placement.getApprovedAt() == null
                || placement.getStartsAt() == null
                || placement.getEndsAt() == null
                || placement.getStartsAt().isAfter(clock.instant())
                || !placement.getEndsAt().isAfter(clock.instant())) {
            return false;
        }
        if (placement.getImpressionLimit() != null
                && placement.getImpressionCount() >= placement.getImpressionLimit()) return false;
        if (placement.getClickLimit() != null
                && placement.getClickCount() >= placement.getClickLimit()) return false;
        if (placement.getBudget() != null
                && placement.getSpentAmount() != null
                && placement.getSpentAmount().compareTo(placement.getBudget()) >= 0) return false;
        if ("SPONSORED".equals(placement.getPlacementKind())
                && (placement.getHotel() == null || !propertyAccessService.isOperational(placement.getHotel()))) return false;
        return placement.getTargetHotel() != null && propertyAccessService.isOperational(placement.getTargetHotel())
                || "SEARCH_COLLECTION".equals(placement.getTargetType()) && placement.getTargetQueryJson() != null;
    }

    private java.util.Optional<HomeSpotlightDTO> toProjection(SponsoredPlacement placement, String locale) {
        Map<String, String> query = readQuery(placement.getTargetQueryJson());
        if (query == null) return java.util.Optional.empty();
        HomeSpotlightDTO.Target target;
        if ("PROPERTY".equals(placement.getTargetType())) {
            if (placement.getTargetHotel() == null || !propertyAccessService.isOperational(placement.getTargetHotel())) {
                return java.util.Optional.empty();
            }
            target = new HomeSpotlightDTO.Target(
                    "PROPERTY", placement.getTargetHotel().getId(), "/hotel/" + placement.getTargetHotel().getId(), Map.of());
        } else if ("SEARCH_COLLECTION".equals(placement.getTargetType())) {
            String route = buildSearchRoute(query);
            if (route == null) return java.util.Optional.empty();
            target = new HomeSpotlightDTO.Target("SEARCH_COLLECTION", null, route, query);
        } else {
            return java.util.Optional.empty();
        }
        boolean vi = "vi".equals(locale);
        String sponsored = "SPONSORED".equals(placement.getPlacementKind()) ? (vi ? "Được tài trợ" : "Sponsored") : (vi ? "Biên tập" : "Editorial");
        return java.util.Optional.of(new HomeSpotlightDTO(
                placement.getId(), placement.getPlacementKind(),
                vi ? placement.getTitleVi() : placement.getTitleEn(),
                vi ? firstNonBlank(placement.getDescriptionVi(), placement.getDescriptionEn())
                        : firstNonBlank(placement.getDescriptionEn(), placement.getDescriptionVi()),
                placement.getImageUrl(), vi ? placement.getImageAltVi() : placement.getImageAltEn(), sponsored,
                target, placement.getStartsAt(), placement.getEndsAt()));
    }

    private Map<String, String> readQuery(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, String> raw = objectMapper.readValue(json, new TypeReference<>() { });
            if (raw == null || raw.isEmpty() || !ALLOWED_QUERY_KEYS.containsAll(raw.keySet())) return null;
            Map<String, String> normalized = new LinkedHashMap<>();
            raw.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                String key = entry.getKey();
                String value = entry.getValue();
                if (value == null || value.isBlank() || !value.matches("[\\p{L}\\p{N}_.:,%-]+")) {
                    throw new IllegalArgumentException("invalid");
                }
                normalized.put(key, value);
            });
            return normalized;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return null;
        }
    }

    private String buildSearchRoute(Map<String, String> query) {
        if (query == null || query.isEmpty()) return null;
        List<String> pairs = new ArrayList<>();
        query.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> pairs.add(
                UriUtils.encodeQueryParam(entry.getKey(), StandardCharsets.UTF_8)
                        + "=" + UriUtils.encodeQueryParam(entry.getValue(), StandardCharsets.UTF_8)));
        return "/search?" + String.join("&", pairs);
    }

    private String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) return "vi";
        String normalized = locale.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("vi", "en").contains(normalized)) {
            throw new IllegalArgumentException("locale must be vi or en");
        }
        return normalized;
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }
}

