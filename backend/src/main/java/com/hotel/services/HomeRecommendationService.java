package com.hotel.services;

import com.hotel.dto.PropertySearchRequestDTO;
import com.hotel.dto.PropertySearchResponseDTO;
import com.hotel.dtos.home.HomeRecommendationDestinationDTO;
import com.hotel.dtos.home.HomeRecommendationItemDTO;
import com.hotel.dtos.home.HomeRecommendationResponseDTO;
import com.hotel.entities.Location;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Builds the organic Home discovery projection from the canonical property search. */
@Service
@Transactional(readOnly = true)
public class HomeRecommendationService {

    private static final int DESTINATION_MIN_LIMIT = 1;
    private static final int DESTINATION_MAX_LIMIT = 8;
    private static final int RECOMMENDATION_MIN_LIMIT = 1;
    private static final int RECOMMENDATION_MAX_LIMIT = 12;
    private static final int DEFAULT_ADULTS = 1;
    private static final int DEFAULT_CHILDREN = 0;
    private static final int DEFAULT_ROOMS = 1;
    private static final int MAX_ADULTS = 20;
    private static final int MAX_CHILDREN = 20;
    private static final int MAX_ROOMS = 10;

    private final ProvinceCompatibilityService provinceCompatibilityService;
    private final PropertySearchService propertySearchService;

    public HomeRecommendationService(ProvinceCompatibilityService provinceCompatibilityService,
                                     PropertySearchService propertySearchService) {
        this.provinceCompatibilityService = provinceCompatibilityService;
        this.propertySearchService = propertySearchService;
    }

    public List<HomeRecommendationDestinationDTO> recommendationDestinations(Long preferredProvinceId,
                                                                              int limit,
                                                                              String locale) {
        int safeLimit = boundedLimit(limit, DESTINATION_MIN_LIMIT, DESTINATION_MAX_LIMIT, "destination limit");
        String safeLocale = normalizeLocale(locale);
        Location preferred = preferredProvinceId == null ? null : currentProvince(preferredProvinceId);

        List<DestinationSupply> supplies = provinceCompatibilityService.currentProvinces().stream()
                .map(province -> new DestinationSupply(province, availablePropertyCount(province.getId())))
                .filter(supply -> supply.propertyCount() > 0)
                .sorted(Comparator.comparingLong(DestinationSupply::propertyCount).reversed()
                        .thenComparing(supply -> supply.province().getId(), Comparator.reverseOrder()))
                .toList();

        DestinationSupply preferredSupply = preferred == null ? null : supplies.stream()
                .filter(supply -> Objects.equals(supply.province().getId(), preferred.getId()))
                .findFirst().orElse(null);
        List<DestinationSupply> ordered = preferredSupply == null
                ? supplies
                : java.util.stream.Stream.concat(
                                java.util.stream.Stream.of(preferredSupply),
                                supplies.stream().filter(supply -> supply != preferredSupply))
                        .toList();
        Long selectedId = ordered.isEmpty() ? null : ordered.get(0).province().getId();

        return ordered.stream().limit(safeLimit).map(supply -> {
            Location province = supply.province();
            String name = shortName(province, safeLocale);
            return new HomeRecommendationDestinationDTO(
                    province.getId(),
                    name,
                    displayName(province, safeLocale),
                    supply.propertyCount(),
                    Objects.equals(province.getId(), selectedId));
        }).toList();
    }

    public HomeRecommendationResponseDTO recommendations(RecommendationQuery query) {
        if (query == null) throw new IllegalArgumentException("Recommendation query is required.");
        int safeLimit = boundedLimit(query.limit(), RECOMMENDATION_MIN_LIMIT, RECOMMENDATION_MAX_LIMIT,
                "recommendation limit");
        String safeLocale = normalizeLocale(query.locale());
        Location destination = currentProvince(query.provinceId());
        ValidatedQuery validated = validateQuery(query);

        PropertySearchRequestDTO searchRequest = new PropertySearchRequestDTO();
        searchRequest.setProvinceId(destination.getId());
        searchRequest.setCheckInDate(validated.checkIn() == null ? null : validated.checkIn().toString());
        searchRequest.setCheckOutDate(validated.checkOut() == null ? null : validated.checkOut().toString());
        searchRequest.setStayType(validated.stayType());
        searchRequest.setAdultCount(validated.adults());
        searchRequest.setChildCount(validated.children());
        searchRequest.setRoomCount(validated.rooms());
        searchRequest.setSortBy("RATING");
        searchRequest.setPageNumber(1);
        searchRequest.setPageSize(safeLimit);

        Page<PropertySearchResponseDTO> page = propertySearchService.searchProperties(searchRequest);
        HomeRecommendationItemDTO.RecommendationReason reason = validated.hasContext()
                ? HomeRecommendationItemDTO.RecommendationReason.SEARCH_CONTEXT
                : HomeRecommendationItemDTO.RecommendationReason.TOP_RATED;
        List<HomeRecommendationItemDTO> items = page.getContent().stream()
                .filter(item -> item.getAvailableRoomCount() != null && item.getAvailableRoomCount() > 0)
                .map(item -> toRecommendationItem(item, destination, reason))
                .toList();

        HomeRecommendationDestinationDTO destinationProjection = new HomeRecommendationDestinationDTO(
                destination.getId(),
                shortName(destination, safeLocale),
                displayName(destination, safeLocale),
                page.getTotalElements(),
                true);
        return new HomeRecommendationResponseDTO(destinationProjection, items, page.getTotalElements());
    }

    private HomeRecommendationItemDTO toRecommendationItem(PropertySearchResponseDTO source,
                                                            Location destination,
                                                            HomeRecommendationItemDTO.RecommendationReason reason) {
        PropertySearchResponseDTO.PricingSummary sourcePricing = source.getPricing();
        com.hotel.dtos.PromotionQuoteDTO quote = source.getQuote();
        BigDecimal finalNightlyPrice = sourcePricing == null ? null : sourcePricing.getDiscountedNightlyPrice();
        BigDecimal totalDiscount = quote == null ? BigDecimal.ZERO : quote.totalDiscount();
        HomeRecommendationItemDTO.PricingSummary pricing = sourcePricing == null
                ? null
                : new HomeRecommendationItemDTO.PricingSummary(
                        sourcePricing.getNightlyPrice(), finalNightlyPrice, totalDiscount, "VND");
        String imageUrl = firstNotBlank(source.getThumbnailUrl(), source.getMainImageUrl());
        String imageAlt = firstNotBlank(source.getImageAltText(), source.getName());
        return new HomeRecommendationItemDTO(
                source.getId(),
                source.getName(),
                source.getPropertyType(),
                destination.getId(),
                firstNotBlank(source.getProvinceName(), localizedName(destination, "vi")),
                source.getWardName(),
                imageUrl,
                imageAlt,
                source.getStarRating(),
                source.getReviewScore(),
                source.getReviewCount(),
                source.getAvailableRoomCount(),
                pricing,
                quote,
                reason,
                false);
    }

    private long availablePropertyCount(Long provinceId) {
        PropertySearchRequestDTO request = new PropertySearchRequestDTO();
        request.setProvinceId(provinceId);
        request.setAdultCount(DEFAULT_ADULTS);
        request.setChildCount(DEFAULT_CHILDREN);
        request.setRoomCount(DEFAULT_ROOMS);
        request.setSortBy("RATING");
        request.setPageNumber(1);
        request.setPageSize(1);
        return propertySearchService.searchProperties(request).getTotalElements();
    }

    private Location currentProvince(Long provinceId) {
        if (provinceId == null || provinceId <= 0) {
            throw new IllegalArgumentException("provinceId must be a positive current province id.");
        }
        Location current = provinceCompatibilityService.currentProvinceForId(provinceId);
        if (current == null || current.getId() == null || !"PROVINCE".equals(current.getLocationType())
                || !"ACTIVE".equals(current.getStatus())) {
            throw new IllegalArgumentException("provinceId does not identify an active province.");
        }
        return current;
    }

    private ValidatedQuery validateQuery(RecommendationQuery query) {
        LocalDate checkIn = parseDate(query.checkInDate(), "checkInDate");
        LocalDate checkOut = parseDate(query.checkOutDate(), "checkOutDate");
        if ((checkIn == null) != (checkOut == null)) {
            throw new IllegalArgumentException("checkInDate and checkOutDate must be provided together.");
        }
        if (checkIn != null && !checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("checkOutDate must be after checkInDate.");
        }
        String stayType = query.stayType() == null || query.stayType().isBlank()
                ? null : query.stayType().trim().toUpperCase(Locale.ROOT);
        if (stayType != null && !List.of("OVERNIGHT", "DAY_USE").contains(stayType)) {
            throw new IllegalArgumentException("stayType is not supported.");
        }
        int adults = positiveBounded(query.adultCount(), DEFAULT_ADULTS, MAX_ADULTS, "adultCount");
        int children = nonNegativeBounded(query.childCount(), DEFAULT_CHILDREN, MAX_CHILDREN, "childCount");
        int rooms = positiveBounded(query.roomCount(), DEFAULT_ROOMS, MAX_ROOMS, "roomCount");
        return new ValidatedQuery(checkIn, checkOut, stayType, adults, children, rooms,
                checkIn != null || checkOut != null || stayType != null
                        || query.adultCount() != null || query.childCount() != null || query.roomCount() != null);
    }

    private int boundedLimit(int value, int min, int max, String name) {
        if (value < min || value > max) throw new IllegalArgumentException(name + " must be between " + min + " and " + max + ".");
        return value;
    }

    private int positiveBounded(Integer value, int defaultValue, int max, String field) {
        int resolved = value == null ? defaultValue : value;
        if (resolved < 1 || resolved > max) throw new IllegalArgumentException(field + " must be between 1 and " + max + ".");
        return resolved;
    }

    private int nonNegativeBounded(Integer value, int defaultValue, int max, String field) {
        int resolved = value == null ? defaultValue : value;
        if (resolved < 0 || resolved > max) throw new IllegalArgumentException(field + " must be between 0 and " + max + ".");
        return resolved;
    }

    private LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must use yyyy-MM-dd format.");
        }
    }

    private String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) return "vi";
        String normalized = locale.trim().toLowerCase(Locale.ROOT);
        if (!List.of("vi", "en").contains(normalized)) throw new IllegalArgumentException("locale must be vi or en.");
        return normalized;
    }

    private String localizedName(Location location, String locale) {
        return "en".equals(locale) ? firstNotBlank(location.getNameEn(), location.getNameVi())
                : firstNotBlank(location.getNameVi(), location.getNameEn());
    }

    private String displayName(Location location, String locale) {
        String name = localizedName(location, locale);
        String normalized = name.toLowerCase(Locale.ROOT);
        if ("en".equals(locale)) {
            return normalized.startsWith("province") || normalized.startsWith("city")
                    ? name : "Province of " + name;
        }
        return normalized.startsWith("tỉnh ") || normalized.startsWith("thành phố ")
                ? name : "Tỉnh " + name;
    }

    private String shortName(Location location, String locale) {
        String name = localizedName(location, locale);
        if ("en".equals(locale)) {
            return name.replaceFirst("(?i)^(province of|province|city of|city)\\s+", "");
        }
        return name.replaceFirst("(?i)^(tỉnh|thành phố)\\s+", "");
    }

    private String firstNotBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    private record DestinationSupply(Location province, long propertyCount) { }

    private record ValidatedQuery(LocalDate checkIn, LocalDate checkOut, String stayType,
                                  int adults, int children, int rooms, boolean hasContext) { }

    public record RecommendationQuery(Long provinceId, String checkInDate, String checkOutDate,
                                      String stayType, Integer adultCount, Integer childCount,
                                      Integer roomCount, int limit, String locale) {
        public RecommendationQuery {
            limit = limit == 0 ? 8 : limit;
        }
    }
}
