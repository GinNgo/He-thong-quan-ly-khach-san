package com.hotel.services;

import com.hotel.dtos.PropertyProfileDTO;
import com.hotel.entities.Hotel;
import com.hotel.entities.Location;
import com.hotel.repositories.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PropertyProfileMapper {

    private static final Set<String> PROPERTY_TYPES =
            Set.of("HOTEL", "MOTEL", "HOMESTAY", "APARTMENT", "VILLA", "RESORT");

    private final LocationRepository locationRepository;

    public void apply(Hotel hotel, PropertyProfileDTO profile) {
        if (profile == null) {
            throw new IllegalArgumentException("Property profile is required.");
        }
        if (!profile.isCoordinatePairValid()) {
            throw new IllegalArgumentException("Latitude and longitude must be provided together.");
        }
        if (!profile.isPriceRangeValid()) {
            throw new IllegalArgumentException("Minimum price must not exceed maximum price.");
        }

        LocationPair location = validateLocation(profile.getProvinceId(), profile.getWardId());
        String nameVi = requireText(profile.getNameVi(), "Property name is required.");
        String descriptionVi = trimToNull(profile.getDescriptionVi());

        hotel.setName(nameVi);
        hotel.setNameVi(nameVi);
        hotel.setNameEn(trimToNull(profile.getNameEn()));
        hotel.setPropertyType(requirePropertyType(profile.getPropertyType()));
        hotel.setAddressLine(requireText(profile.getAddressLine(), "Property address is required."));
        hotel.setProvinceId(location.province().getId());
        hotel.setWardId(location.ward().getId());
        hotel.setCity(location.province().getNameVi());
        hotel.setCountry("Vietnam");
        hotel.setLatitude(profile.getLatitude());
        hotel.setLongitude(profile.getLongitude());
        hotel.setDescription(descriptionVi);
        hotel.setDescriptionVi(descriptionVi);
        hotel.setDescriptionEn(trimToNull(profile.getDescriptionEn()));
        hotel.setStarRating(profile.getStarRating());
        hotel.setPhone(trimToNull(profile.getPhone()));
        hotel.setEmail(trimToNull(profile.getEmail()));
        hotel.setWebsite(validateWebsite(profile.getWebsite()));
        hotel.setCheckinTime(trimToNull(profile.getCheckinTime()));
        hotel.setCheckoutTime(trimToNull(profile.getCheckoutTime()));
        hotel.setMinPrice(profile.getMinPrice());
        hotel.setMaxPrice(profile.getMaxPrice());
    }

    private LocationPair validateLocation(Long provinceId, Long wardId) {
        if (provinceId == null || wardId == null) {
            throw new IllegalArgumentException("Province and ward are required.");
        }
        Location province = locationRepository.findById(provinceId)
                .filter(item -> "PROVINCE".equals(normalize(item.getLocationType())))
                .orElseThrow(() -> new IllegalArgumentException("Province is invalid."));
        Location ward = locationRepository.findById(wardId)
                .filter(item -> "WARD".equals(normalize(item.getLocationType())))
                .orElseThrow(() -> new IllegalArgumentException("Ward is invalid."));
        if (ward.getParent() == null || !province.getId().equals(ward.getParent().getId())) {
            throw new IllegalArgumentException("Ward does not belong to the selected province.");
        }
        return new LocationPair(province, ward);
    }

    private String requirePropertyType(String value) {
        String normalized = normalize(value);
        if (!PROPERTY_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Property type is invalid.");
        }
        return normalized;
    }

    private String validateWebsite(String value) {
        String website = trimToNull(value);
        if (website == null) return null;
        try {
            URI uri = URI.create(website);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("Property website must be an HTTP(S) URL.");
            }
            return website;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Property website must be an HTTP(S) URL.");
        }
    }

    private String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) throw new IllegalArgumentException(message);
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record LocationPair(Location province, Location ward) {
    }
}
