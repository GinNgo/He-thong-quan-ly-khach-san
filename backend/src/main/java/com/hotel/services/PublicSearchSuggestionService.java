package com.hotel.services;

import com.hotel.dtos.LocationSuggestionDTO;
import com.hotel.dtos.SearchSuggestionGroupsDTO;
import com.hotel.entities.Hotel;
import com.hotel.entities.Location;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.LocationRepository;
import com.hotel.repositories.PropertyImageRepository;
import com.hotel.util.VietnameseTextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicSearchSuggestionService {

    private final LocationRepository locationRepository;
    private final HotelRepository hotelRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final Environment environment;
    private final ProvinceCompatibilityService provinceCompatibilityService;

    @Value("${app.demo-data.allow-public-demo:false}")
    private boolean allowPublicDemo;

    public SearchSuggestionGroupsDTO search(String keyword, int propertyLimit, Long provinceId,
                                            Double latitude, Double longitude) {
        String rawKeyword = keyword == null ? "" : keyword.trim();
        String normalizedKeyword = VietnameseTextNormalizer.normalize(rawKeyword);
        if (normalizedKeyword == null || normalizedKeyword.length() < 2) {
            return emptyGroups();
        }

        int safeLimit = Math.min(Math.max(propertyLimit, 1), 10);
        int candidateLimit = Math.min(safeLimit * 3, 30);
        Set<Long> provinceScope = provinceCompatibilityService.provinceScopeIds(provinceId);
        List<Location> provinceCandidates = new ArrayList<>(locationRepository
                .searchCurrentProvinces(normalizedKeyword, rawKeyword,
                        provinceCompatibilityService.currentSourceCodes(), PageRequest.of(0, candidateLimit))
                .getContent());
        locationRepository.searchLocations(
                        normalizedKeyword, rawKeyword, "PROVINCE", PageRequest.of(0, candidateLimit))
                .stream()
                .map(provinceCompatibilityService::currentProvinceFor)
                .filter(java.util.Objects::nonNull)
                .filter(provinceCompatibilityService::isCurrentProvince)
                .forEach(provinceCandidates::add);
        List<LocationSuggestionDTO> provinces = provinceCandidates.stream()
                .filter(location -> provinceId == null || provinceScope.contains(location.getId()))
                .map(this::toLocationSuggestion)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toList(), items -> distinctAndLimit(items, safeLimit)));
        var wardPage = provinceId == null
                ? locationRepository.searchLocations(
                        normalizedKeyword, rawKeyword, "WARD", PageRequest.of(0, candidateLimit))
                : locationRepository.searchWardsInProvinceScope(
                        normalizedKeyword, rawKeyword, provinceScope, PageRequest.of(0, candidateLimit));
        List<LocationSuggestionDTO> wards = wardPage.stream()
                .map(this::toLocationSuggestion)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toList(), items -> distinctAndLimit(items, safeLimit)));
        List<Hotel> propertyCandidates = provinceId == null
                ? hotelRepository.searchAutocomplete(normalizedKeyword, rawKeyword, PageRequest.of(0, candidateLimit))
                : hotelRepository.searchAutocompleteInProvinceScope(
                        normalizedKeyword, rawKeyword, provinceScope, PageRequest.of(0, candidateLimit));
        List<LocationSuggestionDTO> properties = propertyCandidates.stream()
                .filter(hotel -> includeDemo() || !Boolean.TRUE.equals(hotel.getIsDemo()))
                .map(hotel -> toPropertySuggestion(hotel, latitude, longitude))
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toList(), items -> distinctAndLimit(items, safeLimit)));
        var landmarkPage = provinceId == null
                ? locationRepository.searchActiveLandmarks(
                        normalizedKeyword, rawKeyword, null, PageRequest.of(0, candidateLimit))
                : locationRepository.searchActiveLandmarksInProvinceScope(
                        normalizedKeyword, rawKeyword, provinceScope, PageRequest.of(0, candidateLimit));
        List<LocationSuggestionDTO> landmarks = landmarkPage.stream()
                .map(this::toLandmarkSuggestion)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toList(), items -> distinctAndLimit(items, safeLimit)));

        return SearchSuggestionGroupsDTO.builder()
                .provinces(provinces)
                .wards(wards)
                .properties(properties)
                .landmarks(landmarks)
                .build();
    }

    public List<LocationSuggestionDTO> searchFlat(String keyword, int size, Long provinceId) {
        SearchSuggestionGroupsDTO groups = search(keyword, Math.min(size, 10), provinceId, null, null);
        List<LocationSuggestionDTO> result = new ArrayList<>();
        result.addAll(groups.getProvinces());
        result.addAll(groups.getWards());
        result.addAll(groups.getProperties());
        result.addAll(groups.getLandmarks());
        return result.stream().limit(Math.min(Math.max(size, 1), 30)).toList();
    }

    public List<LocationSuggestionDTO> popular(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 8);
        List<LocationSuggestionDTO> results = provinceCompatibilityService.currentProvinces()
                .stream()
                .map(this::toPopularDestination)
                .filter(item -> item.getPropertyCount() != null && item.getPropertyCount() > 0)
                .sorted(Comparator.comparing(LocationSuggestionDTO::getPropertyCount).reversed()
                        .thenComparing(LocationSuggestionDTO::getDisplayName)
                        .thenComparing(LocationSuggestionDTO::getId))
                .limit(safeLimit)
                .toList();
        return results;
    }

    private LocationSuggestionDTO toPopularDestination(Location location) {
        LocationSuggestionDTO destination = toLocationSuggestion(location);
        String stableAssetKey = location.getSourceCode() == null ? location.getCode() : location.getSourceCode();
        int assetNumber = Math.floorMod(stableAssetKey.hashCode(), 8) + 1;
        String assetName = "destination-" + String.format("%02d", assetNumber) + ".webp";
        destination.setImageUrl("/assets/destinations/" + assetName);
        destination.setImageAltText("Kh\u00E1m ph\u00E1 " + location.getNameVi());
        destination.setImageProvenance("BUNDLED_DESTINATION:" + assetName);
        return destination;
    }

    private SearchSuggestionGroupsDTO emptyGroups() {
        return SearchSuggestionGroupsDTO.builder()
                .provinces(List.of()).wards(List.of()).properties(List.of()).landmarks(List.of()).build();
    }

    private List<LocationSuggestionDTO> distinctAndLimit(List<LocationSuggestionDTO> items, int limit) {
        LinkedHashMap<String, LocationSuggestionDTO> distinct = new LinkedHashMap<>();
        for (LocationSuggestionDTO item : items) {
            distinct.putIfAbsent(item.getType() + ":" + item.getId(), item);
        }
        return distinct.values().stream().limit(limit).toList();
    }

    private LocationSuggestionDTO toLocationSuggestion(Location location) {
        Location province = provinceCompatibilityService.currentProvinceFor(location);
        long propertyCount = countProperties(location);
        String displayName = "WARD".equals(location.getLocationType()) && province != null
                ? location.getNameVi() + ", " + province.getNameVi()
                : location.getNameVi();
        return LocationSuggestionDTO.builder()
                .type(location.getLocationType())
                .id(location.getId())
                .parentId(location.getParent() == null ? null : location.getParent().getId())
                .name(location.getNameVi())
                .displayName(displayName)
                .secondaryText("PROVINCE".equals(location.getLocationType())
                        ? "Tỉnh/Thành phố" : province == null ? null : province.getNameVi())
                .provinceId(province == null ? null : province.getId())
                .provinceName(province == null ? null : province.getNameVi())
                .wardId("WARD".equals(location.getLocationType()) ? location.getId() : null)
                .wardName("WARD".equals(location.getLocationType()) ? location.getNameVi() : null)
                .propertyCount(propertyCount)
                .imageUrl("/assets/destinations/destination-" + String.format("%02d", Math.floorMod(location.getId(), 8) + 1) + ".webp")
                .build();
    }

    private LocationSuggestionDTO toLandmarkSuggestion(Location landmark) {
        Location province = provinceCompatibilityService.currentProvinceFor(landmark);
        Location parent = landmark.getParent();
        Location ward = parent != null && "WARD".equals(parent.getLocationType()) ? parent : null;
        String provinceName = province == null ? null : province.getNameVi();
        String displayName = provinceName == null
                ? landmark.getNameVi()
                : landmark.getNameVi() + ", " + provinceName;
        String secondary = categoryLabel(landmark.getCategory());
        if (ward != null) secondary += " · " + ward.getNameVi();
        return LocationSuggestionDTO.builder()
                .type("LANDMARK")
                .id(landmark.getId())
                .parentId(parent == null ? null : parent.getId())
                .name(landmark.getNameVi())
                .displayName(displayName)
                .secondaryText(secondary)
                .provinceId(province == null ? null : province.getId())
                .provinceName(provinceName)
                .wardId(ward == null ? null : ward.getId())
                .wardName(ward == null ? null : ward.getNameVi())
                .latitude(landmark.getLatitude())
                .longitude(landmark.getLongitude())
                .defaultRadiusKm(defaultRadius(landmark.getDefaultRadiusKm()))
                .category(landmark.getCategory())
                .descriptionVi(landmark.getDescriptionVi())
                .descriptionEn(landmark.getDescriptionEn())
                .build();
    }

    private Double defaultRadius(Double radius) {
        if (radius == null || !Double.isFinite(radius) || radius <= 0) return 5d;
        return Math.min(radius, 50d);
    }

    private String categoryLabel(String category) {
        return switch (category == null ? "" : category.toUpperCase()) {
            case "CULTURE" -> "Văn hóa";
            case "BEACH" -> "Biển";
            case "NATURE" -> "Thiên nhiên";
            case "BUSINESS" -> "Trung tâm";
            default -> "Điểm tham quan";
        };
    }

    private long countProperties(Location location) {
        boolean province = "PROVINCE".equals(location.getLocationType());
        Set<Long> provinceIds = province
                ? provinceCompatibilityService.provinceScopeIds(location.getId())
                : Set.of();
        if (includeDemo()) {
            return province
                    ? hotelRepository.countByProvinceIdInAndApprovalStatusAndOperationStatus(
                            provinceIds, "APPROVED", "ACTIVE")
                    : hotelRepository.countByWardIdAndApprovalStatusAndOperationStatus(location.getId(), "APPROVED", "ACTIVE");
        }
        return province
                ? hotelRepository.countByProvinceIdInAndApprovalStatusAndOperationStatusAndIsDemoFalse(
                        provinceIds, "APPROVED", "ACTIVE")
                : hotelRepository.countByWardIdAndApprovalStatusAndOperationStatusAndIsDemoFalse(location.getId(), "APPROVED", "ACTIVE");
    }

    private LocationSuggestionDTO toPropertySuggestion(Hotel hotel, Double latitude, Double longitude) {
        Location province = provinceCompatibilityService.currentProvinceForId(hotel.getProvinceId());
        Location ward = hotel.getWardId() == null ? null : locationRepository.findById(hotel.getWardId()).orElse(null);
        String displayName = firstNotBlank(hotel.getNameVi(), hotel.getName(), hotel.getNameEn());
        String secondary = ward != null && province != null ? ward.getNameVi() + ", " + province.getNameVi()
                : province != null ? province.getNameVi() : hotel.getAddressLine();
        return LocationSuggestionDTO.builder()
                .type("PROPERTY").id(hotel.getId()).slug(hotel.getSlug())
                .name(displayName).displayName(displayName).secondaryText(secondary)
                .address(hotel.getAddressLine()).propertyType(hotel.getPropertyType())
                .thumbnailUrl(thumbnailFor(hotel))
                .reviewScore(hotel.getReviewCount() != null && hotel.getReviewCount() > 0 ? hotel.getAverageRating() : null)
                .distanceKm(distance(latitude, longitude, hotel.getLatitude(), hotel.getLongitude()))
                .provinceId(province == null ? hotel.getProvinceId() : province.getId())
                .provinceName(province == null ? null : province.getNameVi())
                .wardId(hotel.getWardId()).wardName(ward == null ? null : ward.getNameVi())
                .propertyCount(null).build();
    }

    private String thumbnailFor(Hotel hotel) {
        var images = propertyImageRepository.findByHotelIdOrderBySortOrderAscIdAsc(hotel.getId());
        return images.stream().filter(image -> Boolean.TRUE.equals(image.getIsPrimary())).findFirst()
                .or(() -> images.stream().findFirst())
                .map(image -> image.getImageUrl()).orElse(null);
    }

    private Double distance(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return null;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return Math.round((6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))) * 10d) / 10d;
    }

    private String firstNotBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "Cơ sở lưu trú";
    }

    private boolean includeDemo() {
        return allowPublicDemo || !environment.acceptsProfiles(Profiles.of("production"));
    }
}
