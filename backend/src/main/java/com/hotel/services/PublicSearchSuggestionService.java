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
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicSearchSuggestionService {

    private final LocationRepository locationRepository;
    private final HotelRepository hotelRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final Environment environment;

    @Value("${app.demo-data.allow-public-demo:false}")
    private boolean allowPublicDemo;

    public SearchSuggestionGroupsDTO search(String keyword, int propertyLimit, Long provinceId,
                                            Double latitude, Double longitude) {
        String rawKeyword = keyword == null ? "" : keyword.trim();
        String normalizedKeyword = VietnameseTextNormalizer.normalize(rawKeyword);
        if (normalizedKeyword == null || normalizedKeyword.length() < 2) {
            return emptyGroups();
        }

        int safePropertyLimit = Math.min(Math.max(propertyLimit, 1), 10);
        List<LocationSuggestionDTO> provinces = locationRepository
                .searchLocations(normalizedKeyword, rawKeyword, "PROVINCE", PageRequest.of(0, 5))
                .stream()
                .filter(location -> provinceId == null || provinceId.equals(location.getId()))
                .map(this::toLocationSuggestion)
                .toList();
        List<LocationSuggestionDTO> wards = locationRepository
                .searchLocations(normalizedKeyword, rawKeyword, "WARD", PageRequest.of(0, 8))
                .stream()
                .filter(location -> provinceId == null || belongsToProvince(location, provinceId))
                .map(this::toLocationSuggestion)
                .toList();
        List<LocationSuggestionDTO> properties = hotelRepository
                .searchAutocomplete(normalizedKeyword, rawKeyword, PageRequest.of(0, safePropertyLimit))
                .stream()
                .filter(hotel -> includeDemo() || !Boolean.TRUE.equals(hotel.getIsDemo()))
                .filter(hotel -> provinceId == null || provinceId.equals(hotel.getProvinceId()))
                .map(hotel -> toPropertySuggestion(hotel, latitude, longitude))
                .toList();

        return SearchSuggestionGroupsDTO.builder()
                .provinces(provinces)
                .wards(wards)
                .properties(properties)
                .landmarks(List.of())
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
        List<LocationSuggestionDTO> results = locationRepository.findByLocationTypeAndStatusOrderBySortOrderAscNameViAsc("PROVINCE", "ACTIVE")
                .stream()
                .map(this::toLocationSuggestion)
                .filter(item -> item.getPropertyCount() != null && item.getPropertyCount() > 0)
                .sorted(Comparator.comparing(LocationSuggestionDTO::getPropertyCount).reversed()
                        .thenComparing(LocationSuggestionDTO::getDisplayName))
                .limit(safeLimit)
                .toList();
        for (int index = 0; index < results.size(); index++) {
            results.get(index).setImageUrl("/assets/destinations/destination-" + String.format("%02d", index + 1) + ".webp");
        }
        return results;
    }

    private SearchSuggestionGroupsDTO emptyGroups() {
        return SearchSuggestionGroupsDTO.builder()
                .provinces(List.of()).wards(List.of()).properties(List.of()).landmarks(List.of()).build();
    }

    private boolean belongsToProvince(Location location, Long provinceId) {
        return location.getParent() != null && provinceId.equals(location.getParent().getId());
    }

    private LocationSuggestionDTO toLocationSuggestion(Location location) {
        Location province = "PROVINCE".equals(location.getLocationType()) ? location : location.getParent();
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

    private long countProperties(Location location) {
        boolean province = "PROVINCE".equals(location.getLocationType());
        if (includeDemo()) {
            return province
                    ? hotelRepository.countByProvinceIdAndApprovalStatusAndOperationStatus(location.getId(), "APPROVED", "ACTIVE")
                    : hotelRepository.countByWardIdAndApprovalStatusAndOperationStatus(location.getId(), "APPROVED", "ACTIVE");
        }
        return province
                ? hotelRepository.countByProvinceIdAndApprovalStatusAndOperationStatusAndIsDemoFalse(location.getId(), "APPROVED", "ACTIVE")
                : hotelRepository.countByWardIdAndApprovalStatusAndOperationStatusAndIsDemoFalse(location.getId(), "APPROVED", "ACTIVE");
    }

    private LocationSuggestionDTO toPropertySuggestion(Hotel hotel, Double latitude, Double longitude) {
        Location province = hotel.getProvinceId() == null ? null : locationRepository.findById(hotel.getProvinceId()).orElse(null);
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
                .provinceId(hotel.getProvinceId()).provinceName(province == null ? null : province.getNameVi())
                .wardId(hotel.getWardId()).wardName(ward == null ? null : ward.getNameVi())
                .propertyCount(null).build();
    }

    private String thumbnailFor(Hotel hotel) {
        var images = propertyImageRepository.findByHotelIdOrderBySortOrderAsc(hotel.getId());
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
