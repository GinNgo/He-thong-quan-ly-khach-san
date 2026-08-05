package com.hotel.services.impl;

import com.hotel.dto.PropertySearchRequestDTO;
import com.hotel.dto.PropertySearchResponseDTO;
import com.hotel.entities.Location;
import com.hotel.entities.RoomType;
import com.hotel.entities.PropertyImage;
import com.hotel.repositories.LocationRepository;
import com.hotel.repositories.PropertyImageRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.services.AmenityService;
import com.hotel.services.PropertySearchService;
import com.hotel.services.PublicInventoryEligibilityPolicy;
import com.hotel.services.ProvinceCompatibilityService;
import com.hotel.services.RoomAvailabilityPolicy;
import com.hotel.services.RoomAvailabilityService;
import com.hotel.util.VietnameseTextNormalizer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PropertySearchServiceImpl implements PropertySearchService {

    private static final String RELEASED_STATUSES = "'CANCELLED','REJECTED','EXPIRED','NO_SHOW','CHECKED_OUT','COMPLETED'";
    private static final Set<String> PUBLIC_PROPERTY_TYPES = Set.of(
            "HOTEL", "RESORT", "APARTMENT", "VILLA", "HOMESTAY", "MOTEL", "GUEST_HOUSE", "HOSTEL");

    private final EntityManager entityManager;
    private final LocationRepository locationRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final RoomAvailabilityService roomAvailabilityService;
    private final AmenityService amenityService;
    private final RoomAvailabilityPolicy roomAvailabilityPolicy;
    private final ProvinceCompatibilityService provinceCompatibilityService;
    private final PublicInventoryEligibilityPolicy publicInventoryEligibilityPolicy;

    public PropertySearchServiceImpl(EntityManager entityManager, LocationRepository locationRepository,
                                     RoomTypeRepository roomTypeRepository,
                                     PropertyImageRepository propertyImageRepository,
                                     RoomAvailabilityService roomAvailabilityService,
                                     AmenityService amenityService,
                                     RoomAvailabilityPolicy roomAvailabilityPolicy,
                                     ProvinceCompatibilityService provinceCompatibilityService,
                                     PublicInventoryEligibilityPolicy publicInventoryEligibilityPolicy) {
        this.entityManager = entityManager;
        this.locationRepository = locationRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.propertyImageRepository = propertyImageRepository;
        this.roomAvailabilityService = roomAvailabilityService;
        this.amenityService = amenityService;
        this.roomAvailabilityPolicy = roomAvailabilityPolicy;
        this.provinceCompatibilityService = provinceCompatibilityService;
        this.publicInventoryEligibilityPolicy = publicInventoryEligibilityPolicy;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Page<PropertySearchResponseDTO> searchProperties(PropertySearchRequestDTO request) {
        validateSupportedRequestContract(request);
        String sortBy = request.getSortBy();
        LocalDate checkIn = parseDate(request.getCheckInDate(), "checkInDate");
        LocalDate checkOut = parseDate(request.getCheckOutDate(), "checkOutDate");
        if ((checkIn == null) != (checkOut == null)) {
            throw new IllegalArgumentException("Ngày nhận và trả phòng phải được cung cấp cùng nhau.");
        }
        if (checkIn != null && checkIn.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("checkInDate cannot be in the past.");
        }
        if (checkIn != null && !checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("Ngày trả phòng phải sau ngày nhận phòng.");
        }

        resolveLandmark(request);
        validateCoordinateRequest(request);

        int roomCount = Math.max(request.getRoomCount() == null ? 1 : request.getRoomCount(), 1);
        int adults = Math.max(request.getAdultCount() == null ? 0 : request.getAdultCount(), 0);
        int children = Math.max(request.getChildCount() == null ? 0 : request.getChildCount(), 0);
        Map<String, Object> params = new HashMap<>();
        params.put("roomCount", roomCount);
        params.put("adultCount", adults);
        params.put("childCount", children);
        params.put("guestCount", adults + children);
        if (request.getMinPrice() != null) params.put("minPrice", request.getMinPrice());
        if (request.getMaxPrice() != null) params.put("maxPrice", request.getMaxPrice());
        if (checkIn != null) {
            params.put("checkIn", checkIn);
            params.put("checkOut", checkOut);
        }

        String pricedRoomPredicate = eligibleRoomPredicate(
                "rt_price", request.getMinPrice() != null, request.getMaxPrice() != null, checkIn != null);
        String qualifyingRoomPredicate = eligibleRoomPredicate(
                "rt_offer", request.getMinPrice() != null, request.getMaxPrice() != null, checkIn != null);

        boolean hasCoordinates = request.getLatitude() != null && request.getLongitude() != null;
        String distance = hasCoordinates
                ? "(6371 * ACOS(COS(RADIANS(:userLat)) * COS(RADIANS(h.latitude)) * COS(RADIANS(h.longitude) - RADIANS(:userLng)) + SIN(RADIANS(:userLat)) * SIN(RADIANS(h.latitude))))"
                : "NULL";
        if (hasCoordinates) {
            params.put("userLat", request.getLatitude());
            params.put("userLng", request.getLongitude());
        }

        String select = """
                SELECT h.id, h.slug, COALESCE(NULLIF(h.name_vi,''), NULLIF(h.name,''), h.name_en), h.address,
                       h.main_image, h.star_rating, h.latitude, h.longitude, h.property_type,
                       h.average_rating, h.review_count, p.name_vi, w.name_vi,
                """ + distance + """
                        AS distance,
                       (SELECT MIN(rt_price.base_price) FROM room_types rt_price WHERE
                """ + pricedRoomPredicate + """
                       ) AS min_price,
                       (SELECT COUNT(*) FROM rooms r WHERE r.hotel_id=h.id AND r.status <> 'MAINTENANCE' AND COALESCE(r.maintenance_status,'NONE') NOT IN ('MAINTENANCE','OUT_OF_SERVICE')) AS total_rooms,
                       (SELECT TOP 1 rt.id FROM room_types rt WHERE rt.hotel_id=h.id AND rt.status='ACTIVE' ORDER BY rt.base_price,rt.id) AS lowest_room_id,
                       (SELECT TOP 1 rt.name_vi FROM room_types rt WHERE rt.hotel_id=h.id AND rt.status='ACTIVE' ORDER BY rt.base_price,rt.id) AS lowest_room_name,
                       (SELECT TOP 1 COALESCE(rt.max_guests,rt.max_guest) FROM room_types rt WHERE rt.hotel_id=h.id AND rt.status='ACTIVE' ORDER BY rt.base_price,rt.id) AS lowest_room_guests,
                       p.id AS province_id
                """;
        String from = " FROM hotels h LEFT JOIN locations p ON p.id=h.province_id LEFT JOIN locations w ON w.id=h.ward_id ";
        StringBuilder where = new StringBuilder(" WHERE ")
                .append(publicInventoryEligibilityPolicy.publicSearchPredicate("h"))
                .append(' ');

        if (request.getProvinceId() != null) {
            Set<Long> provinceIds = provinceCompatibilityService.provinceScopeIds(request.getProvinceId());
            where.append(" AND h.province_id IN (:provinceIds) ");
            params.put("provinceIds", provinceIds);
        }
        if (request.getWardId() != null) {
            where.append(" AND h.ward_id=:wardId ");
            params.put("wardId", request.getWardId());
        }
        String normalizedKeyword = VietnameseTextNormalizer.normalize(request.getKeyword());
        if (normalizedKeyword != null) {
            where.append(" AND (h.normalized_name LIKE :keyword OR h.normalized_address LIKE :keyword OR p.normalized_name LIKE :keyword OR w.normalized_name LIKE :keyword OR LOWER(h.code) LIKE :rawKeyword OR LOWER(h.slug) LIKE :rawKeyword) ");
            params.put("keyword", "%" + normalizedKeyword + "%");
            params.put("rawKeyword", "%" + request.getKeyword().trim().toLowerCase() + "%");
        }
        if (request.getLegacyAddressKeyword() != null && !request.getLegacyAddressKeyword().isBlank()) {
            where.append(" AND LOWER(h.address) LIKE :legacyAddressKeyword ESCAPE '\\' ");
            params.put("legacyAddressKeyword", "%"
                    + escapeLikePattern(request.getLegacyAddressKeyword().trim().toLowerCase()) + "%");
        }
        if (request.getPropertyTypes() != null && !request.getPropertyTypes().isEmpty()) {
            List<String> placeholders = new ArrayList<>();
            for (int i = 0; i < request.getPropertyTypes().size(); i++) {
                String name = "propertyType" + i;
                placeholders.add(":" + name);
                params.put(name, request.getPropertyTypes().get(i));
            }
            where.append(" AND h.property_type IN (").append(String.join(",", placeholders)).append(") ");
        }
        if (request.getStarRatings() != null && !request.getStarRatings().isEmpty()) {
            List<String> placeholders = new ArrayList<>();
            for (int i = 0; i < request.getStarRatings().size(); i++) {
                String name = "star" + i;
                placeholders.add(":" + name);
                params.put(name, request.getStarRatings().get(i));
            }
            where.append(" AND h.star_rating IN (").append(String.join(",", placeholders)).append(") ");
        }
        if (request.getMinReviewScore() != null) {
            where.append(" AND COALESCE(h.review_count,0)>0 AND h.average_rating IS NOT NULL ")
                    .append("AND h.average_rating>=:minReviewScore ");
            params.put("minReviewScore", request.getMinReviewScore());
        }
        if (request.getAmenityIds() != null && !request.getAmenityIds().isEmpty()) {
            List<Long> amenityIds = request.getAmenityIds().stream().distinct().toList();
            for (int index = 0; index < amenityIds.size(); index++) {
                String parameter = "amenity" + index;
                where.append(" AND (EXISTS (SELECT 1 FROM property_amenities pa JOIN amenities pa_a ON pa_a.id=pa.amenity_id WHERE pa.hotel_id=h.id AND pa_a.status='ACTIVE' AND pa.amenity_id=:")
                        .append(parameter)
                        .append(") OR EXISTS (SELECT 1 FROM room_type_amenities rta JOIN room_types amenity_rt ON amenity_rt.id=rta.room_type_id JOIN amenities rta_a ON rta_a.id=rta.amenity_id WHERE rta.hotel_id=h.id AND amenity_rt.status='ACTIVE' AND rta_a.status='ACTIVE' AND rta.amenity_id=:")
                        .append(parameter)
                        .append(")) ");
                params.put(parameter, amenityIds.get(index));
            }
        }
        where.append(" AND EXISTS (SELECT 1 FROM room_types rt_offer WHERE ")
                .append(qualifyingRoomPredicate)
                .append(") ");

        if (hasCoordinates && request.getRadiusKm() != null) {
            where.append(" AND ").append(distance).append("<=:radiusKm ");
            params.put("radiusKm", request.getRadiusKm());
        }

        String orderBy = switch (sortBy) {
            case "NEAREST" -> hasCoordinates ? " ORDER BY distance ASC,h.id ASC" : " ORDER BY h.id DESC";
            case "PRICE_ASC" -> " ORDER BY min_price ASC,h.id ASC";
            case "PRICE_DESC" -> " ORDER BY min_price DESC,h.id ASC";
            case "RATING" -> " ORDER BY CASE WHEN h.average_rating IS NULL OR COALESCE(h.review_count,0)<=0 THEN 1 ELSE 0 END,"
                    + " CASE WHEN h.average_rating IS NULL OR COALESCE(h.review_count,0)<=0 THEN NULL ELSE h.average_rating END DESC,"
                    + " CASE WHEN h.average_rating IS NULL OR COALESCE(h.review_count,0)<=0 THEN 0 ELSE h.review_count END DESC,h.id ASC";
            case "POPULAR" -> " ORDER BY h.review_count DESC,h.id DESC";
            default -> throw new IllegalStateException("Unexpected validated sort: " + sortBy);
        };

        Query dataQuery = entityManager.createNativeQuery(select + from + where + orderBy);
        Query countQuery = entityManager.createNativeQuery("SELECT COUNT(DISTINCT h.id)" + from + where);
        params.forEach(dataQuery::setParameter);
        params.forEach((name, value) -> {
            boolean distanceOnlyParameter = "userLat".equals(name) || "userLng".equals(name);
            if (!distanceOnlyParameter || request.getRadiusKm() != null) {
                countQuery.setParameter(name, value);
            }
        });

        int pageSize = request.getPageSize();
        int pageNumber = request.getPageNumber();
        dataQuery.setFirstResult((pageNumber - 1) * pageSize);
        dataQuery.setMaxResults(pageSize);

        List<MappedRow> mappedRows = ((List<Object[]>) dataQuery.getResultList()).stream()
                .map(this::mapBaseRow)
                .toList();
        List<PropertySearchResponseDTO> content = enrichRows(
                mappedRows, checkIn, checkOut, adults, children, roomCount,
                request.getMinPrice(), request.getMaxPrice());
        long total = ((Number) countQuery.getSingleResult()).longValue();
        return new PageImpl<>(content, PageRequest.of(pageNumber - 1, pageSize), total);
    }

    private void resolveLandmark(PropertySearchRequestDTO request) {
        if (request.getLandmarkId() == null) return;
        Location landmark = locationRepository.findById(request.getLandmarkId())
                .filter(location -> "LANDMARK".equals(location.getLocationType()))
                .filter(location -> "ACTIVE".equals(location.getStatus()))
                .filter(location -> validCoordinates(location.getLatitude(), location.getLongitude()))
                .orElseThrow(() -> new IllegalArgumentException("Địa danh không hợp lệ hoặc không còn khả dụng."));

        Long landmarkProvinceId = provinceIdFor(landmark);
        if (landmarkProvinceId == null) {
            throw new IllegalArgumentException("Landmark is not attached to a province.");
        }
        if (request.getProvinceId() != null
                && !provinceCompatibilityService.sameProvinceScope(request.getProvinceId(), landmarkProvinceId)) {
            throw new IllegalArgumentException("Địa danh không thuộc tỉnh/thành phố đã chọn.");
        }

        Double radius = request.getRadiusKm() == null ? defaultRadius(landmark.getDefaultRadiusKm()) : request.getRadiusKm();
        if (radius <= 0 || radius > 50) {
            throw new IllegalArgumentException("Bán kính địa danh phải lớn hơn 0 và không vượt quá 50 km.");
        }
        request.setLatitude(landmark.getLatitude());
        request.setLongitude(landmark.getLongitude());
        request.setRadiusKm(radius);
    }

    private void validateCoordinateRequest(PropertySearchRequestDTO request) {
        boolean hasLatitude = request.getLatitude() != null;
        boolean hasLongitude = request.getLongitude() != null;
        if (hasLatitude != hasLongitude) {
            throw new IllegalArgumentException("Latitude and longitude must be supplied together.");
        }
        if (hasLatitude && !validCoordinates(request.getLatitude(), request.getLongitude())) {
            throw new IllegalArgumentException("Coordinates are outside the valid latitude/longitude range.");
        }
        if (request.getRadiusKm() != null
                && (!hasLatitude || !Double.isFinite(request.getRadiusKm())
                || request.getRadiusKm() <= 0 || request.getRadiusKm() > 50)) {
            throw new IllegalArgumentException("Radius requires valid coordinates and must be within 0..50 km.");
        }
    }

    private Long provinceIdFor(Location location) {
        Location cursor = location.getParent();
        for (int depth = 0; cursor != null && depth < 3; depth++) {
            if ("PROVINCE".equals(cursor.getLocationType())) return cursor.getId();
            cursor = cursor.getParent();
        }
        return null;
    }

    private boolean validCoordinates(Double latitude, Double longitude) {
        return latitude != null && longitude != null
                && Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90
                && longitude >= -180 && longitude <= 180;
    }

    private double defaultRadius(Double radius) {
        return radius == null || radius <= 0 ? 5d : Math.min(radius, 50d);
    }

    private MappedRow mapBaseRow(Object[] row) {
        PropertySearchResponseDTO dto = new PropertySearchResponseDTO();
        dto.setId(number(row[0]).longValue());
        dto.setSlug((String) row[1]);
        dto.setName((String) row[2]);
        dto.setAddressLine((String) row[3]);
        dto.setMainImageUrl(normalizeImageUrl((String) row[4]));
        dto.setStarRating(integer(row[5]));
        dto.setLatitude(decimal(row[6]));
        dto.setLongitude(decimal(row[7]));
        dto.setPropertyType((String) row[8]);
        Integer storedReviewCount = integer(row[10]);
        Double storedReviewScore = decimal(row[9]);
        boolean reviewed = storedReviewCount != null && storedReviewCount > 0 && storedReviewScore != null;
        dto.setReviewScore(reviewed ? storedReviewScore : null);
        dto.setReviewCount(reviewed ? storedReviewCount : 0);
        Long storedProvinceId = row[19] == null ? null : number(row[19]).longValue();
        dto.setProvinceName((String) row[11]);
        dto.setWardName((String) row[12]);
        dto.setDistanceKm(decimal(row[13]));
        if (dto.getDistanceKm() != null) dto.setDistanceText(String.format("Cách %.1f km", dto.getDistanceKm()));

        return new MappedRow(dto, storedProvinceId, (String) row[11]);
    }

    private List<PropertySearchResponseDTO> enrichRows(List<MappedRow> rows,
                                                        LocalDate checkIn, LocalDate checkOut,
                                                        int adults, int children, int roomCount,
                                                        Double minPrice, Double maxPrice) {
        if (rows.isEmpty()) return List.of();
        Set<Long> hotelIds = rows.stream().map(row -> row.dto().getId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<Long, List<RoomType>> roomTypesByHotel = roomTypeRepository.findByHotelIdIn(hotelIds).stream()
                .filter(roomType -> "ACTIVE".equals(roomType.getStatus()))
                .filter(roomType -> canHost(roomType, adults, children, roomCount))
                .filter(roomType -> isWithinPriceBounds(roomType, minPrice, maxPrice))
                .collect(java.util.stream.Collectors.groupingBy(
                        roomType -> roomType.getHotel().getId(), java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        Set<Long> roomTypeIds = roomTypesByHotel.values().stream().flatMap(List::stream)
                .map(RoomType::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Long> availability = roomAvailabilityService.countAvailableRooms(roomTypeIds, checkIn, checkOut);
        Map<Long, List<PropertyImage>> imagesByHotel = propertyImageRepository
                .findByHotelIdInOrderByHotelIdAscSortOrderAscIdAsc(hotelIds).stream()
                .filter(image -> image.getImageUrl() != null && !image.getImageUrl().isBlank())
                .collect(java.util.stream.Collectors.groupingBy(
                        image -> image.getHotel().getId(), java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        Set<Long> provinceIds = rows.stream().map(MappedRow::storedProvinceId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Location> currentProvinces = provinceCompatibilityService.currentProvincesForIds(provinceIds);

        for (MappedRow row : rows) {
            PropertySearchResponseDTO dto = row.dto();
            Location currentProvince = row.storedProvinceId() == null
                    ? null : currentProvinces.get(row.storedProvinceId());
            dto.setProvinceName(currentProvince == null ? row.storedProvinceName() : currentProvince.getNameVi());
            enrichRoomAndMedia(dto, roomTypesByHotel.getOrDefault(dto.getId(), List.of()), availability,
                    imagesByHotel.getOrDefault(dto.getId(), List.of()), checkIn, checkOut, roomCount);
        }
        return rows.stream().map(MappedRow::dto).toList();
    }

    private void enrichRoomAndMedia(PropertySearchResponseDTO dto, List<RoomType> roomTypes,
                                    Map<Long, Long> availability, List<PropertyImage> images,
                                    LocalDate checkIn, LocalDate checkOut, int roomCount) {
        long available = roomTypes.stream()
                .mapToLong(roomType -> availability.getOrDefault(roomType.getId(), 0L)).sum();
        dto.setAvailableRoomCount((int) available);

        RoomType lowestAvailable = roomTypes.stream()
                .filter(rt -> availability.getOrDefault(rt.getId(), 0L) > 0)
                .filter(rt -> rt.getBasePrice() != null)
                .min(Comparator.comparing(RoomType::getBasePrice).thenComparing(RoomType::getId))
                .orElse(null);
        BigDecimal price = lowestAvailable == null ? null : lowestAvailable.getBasePrice();
        dto.setStartingPrice(price == null ? null : price.doubleValue());
        dto.setLowestRoomType(lowestAvailable == null ? null : new PropertySearchResponseDTO.RoomTypeSummary(
                lowestAvailable.getId(), firstNotBlank(lowestAvailable.getNameVi(), lowestAvailable.getNameEn()),
                value(lowestAvailable.getMaxGuests(), lowestAvailable.getMaxGuest())));
        if (price != null) {
            int nights = checkIn == null ? 1 : (int) ChronoUnit.DAYS.between(checkIn, checkOut);
            BigDecimal subtotal = price.multiply(BigDecimal.valueOf((long) nights * roomCount));
            BigDecimal total = roomAvailabilityService.calculateTotal(price, nights, roomCount);
            BigDecimal tax = total.subtract(subtotal);
            dto.setPricing(new PropertySearchResponseDTO.PricingSummary(
                    price, price, price, nights, roomCount, subtotal,
                    tax, BigDecimal.ZERO, total, "VND"));
        }

        PropertyImage primary = images.stream().filter(image -> Boolean.TRUE.equals(image.getIsPrimary())).findFirst()
                .orElse(images.isEmpty() ? null : images.get(0));
        String catalogMainImage = dto.getMainImageUrl();
        dto.setThumbnailUrl(primary == null ? catalogMainImage : primary.getImageUrl().trim());
        dto.setImageAltText(primary == null || primary.getAltTextVi() == null || primary.getAltTextVi().isBlank()
                ? dto.getName() : primary.getAltTextVi());
        dto.setImageProvenance(primary != null ? "PROPERTY_MEDIA"
                : catalogMainImage != null ? "PROPERTY_CATALOG_MAIN" : "NONE");
        List<String> gallery = new ArrayList<>(images.stream()
                .map(image -> image.getImageUrl().trim()).distinct().toList());
        if (gallery.isEmpty() && catalogMainImage != null) gallery.add(catalogMainImage);
        dto.setGalleryUrls(List.copyOf(gallery));
        dto.setImageCount(dto.getGalleryUrls().size());
        dto.setAmenities(amenityService.publicDisplayNames(dto.getId()));
        dto.setBadges(List.of());
        dto.setFreeCancellation(false);
        dto.setPayAtProperty(false);
        dto.setBreakfastIncluded(false);
    }

    private record MappedRow(PropertySearchResponseDTO dto, Long storedProvinceId, String storedProvinceName) { }

    private String normalizeImageUrl(String imageUrl) {
        return imageUrl == null || imageUrl.isBlank() ? null : imageUrl.trim();
    }

    private boolean canHost(RoomType roomType, int adults, int children, int roomCount) {
        int maxAdults = roomType.getMaxAdults() != null ? roomType.getMaxAdults() : value(roomType.getMaxGuests(), roomType.getMaxGuest());
        int maxChildren = roomType.getMaxChildren() != null ? roomType.getMaxChildren() : value(roomType.getMaxGuests(), roomType.getMaxGuest());
        int maxGuests = value(roomType.getMaxGuests(), roomType.getMaxGuest());
        return adults <= maxAdults * roomCount && children <= maxChildren * roomCount
                && adults + children <= maxGuests * roomCount;
    }

    private boolean isWithinPriceBounds(RoomType roomType, Double minPrice, Double maxPrice) {
        BigDecimal price = roomType == null ? null : roomType.getBasePrice();
        if (price == null) return false;
        if (minPrice != null && price.compareTo(BigDecimal.valueOf(minPrice)) < 0) return false;
        return maxPrice == null || price.compareTo(BigDecimal.valueOf(maxPrice)) <= 0;
    }

    private String eligibleRoomPredicate(String roomAlias, boolean hasMinPrice,
                                         boolean hasMaxPrice, boolean hasStayDates) {
        String physicalRoomAlias = roomAlias + "_room";
        String detailAlias = roomAlias + "_detail";
        String reservationAlias = roomAlias + "_reservation";
        StringBuilder predicate = new StringBuilder()
                .append(roomAlias).append(".hotel_id=h.id ")
                .append("AND ").append(roomAlias).append(".status='ACTIVE' ")
                .append("AND ").append(roomAlias).append(".base_price IS NOT NULL ");
        if (hasMinPrice) predicate.append("AND ").append(roomAlias).append(".base_price>=:minPrice ");
        if (hasMaxPrice) predicate.append("AND ").append(roomAlias).append(".base_price<=:maxPrice ");
        predicate.append("AND COALESCE(").append(roomAlias)
                .append(".max_adults,").append(roomAlias).append(".max_guests,")
                .append(roomAlias).append(".max_guest,999)*:roomCount>=:adultCount ")
                .append("AND COALESCE(").append(roomAlias)
                .append(".max_children,").append(roomAlias).append(".max_guests,")
                .append(roomAlias).append(".max_guest,999)*:roomCount>=:childCount ")
                .append("AND COALESCE(").append(roomAlias).append(".max_guests,")
                .append(roomAlias).append(".max_guest,999)*:roomCount>=:guestCount ")
                .append("AND ((SELECT COUNT(*) FROM rooms ").append(physicalRoomAlias)
                .append(" WHERE ").append(physicalRoomAlias).append(".room_type_id=")
                .append(roomAlias).append(".id AND ")
                .append(roomAvailabilityPolicy.sqlPredicate(physicalRoomAlias, hasStayDates))
                .append(") ");
        if (hasStayDates) {
            predicate.append("- (SELECT COALESCE(SUM(").append(detailAlias)
                    .append(".quantity),0) FROM reservation_details ").append(detailAlias)
                    .append(" JOIN reservations ").append(reservationAlias).append(" ON ")
                    .append(reservationAlias).append(".id=").append(detailAlias)
                    .append(".reservation_id WHERE ").append(detailAlias).append(".room_type_id=")
                    .append(roomAlias).append(".id AND ").append(reservationAlias)
                    .append(".status NOT IN (").append(RELEASED_STATUSES).append(") AND ")
                    .append(reservationAlias).append(".check_in_date<:checkOut AND ")
                    .append(reservationAlias).append(".check_out_date>:checkIn) ");
        }
        return predicate.append(">=:roomCount)").toString();
    }

    private int value(Integer preferred, Integer fallback) { return preferred != null ? preferred : fallback != null ? fallback : Integer.MAX_VALUE; }
    private String firstNotBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }
    private String escapeLikePattern(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
    private void validateSupportedRequestContract(PropertySearchRequestDTO request) {
        if (request.getPageNumber() < 1) {
            throw new IllegalArgumentException("pageNumber must be greater than or equal to 1.");
        }
        if (request.getPageSize() < 1 || request.getPageSize() > 100) {
            throw new IllegalArgumentException("pageSize must be within 1..100.");
        }
        request.setSortBy(normalizeSortBy(request.getSortBy()));
        request.setPropertyTypes(normalizePropertyTypes(request.getPropertyTypes()));
        request.setStarRatings(normalizeStarRatings(request.getStarRatings()));
        if (request.getMinReviewScore() != null
                && (!Double.isFinite(request.getMinReviewScore())
                || request.getMinReviewScore() < 0 || request.getMinReviewScore() > 10)) {
            throw new IllegalArgumentException("minReviewScore must be a finite value within 0..10.");
        }
        validatePriceBound("minPrice", request.getMinPrice());
        validatePriceBound("maxPrice", request.getMaxPrice());
        if (request.getMinPrice() != null && request.getMaxPrice() != null
                && request.getMinPrice() > request.getMaxPrice()) {
            throw new IllegalArgumentException("minPrice must not exceed maxPrice.");
        }
        if (request.getStayType() != null && !request.getStayType().isBlank()
                && !"OVERNIGHT".equalsIgnoreCase(request.getStayType().trim())) {
            throw new IllegalArgumentException("Only OVERNIGHT stayType is supported by property search.");
        }
        if (Boolean.TRUE.equals(request.getFreeCancellation())) {
            throw new IllegalArgumentException("freeCancellation filtering is not available yet.");
        }
        if (Boolean.TRUE.equals(request.getPayAtProperty())) {
            throw new IllegalArgumentException("payAtProperty filtering is not available yet.");
        }
        if (Boolean.TRUE.equals(request.getBreakfastIncluded())) {
            throw new IllegalArgumentException("breakfastIncluded filtering is not available yet.");
        }
    }
    private String normalizeSortBy(String sortBy) {
        String normalized = sortBy == null || sortBy.isBlank()
                ? "POPULAR"
                : sortBy.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("POPULAR", "NEAREST", "PRICE_ASC", "PRICE_DESC", "RATING").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported sortBy: " + sortBy);
        }
        return normalized;
    }
    private List<String> normalizePropertyTypes(List<String> propertyTypes) {
        if (propertyTypes == null || propertyTypes.isEmpty()) return propertyTypes;
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String propertyType : propertyTypes) {
            String value = propertyType == null ? "" : propertyType.trim().toUpperCase(Locale.ROOT);
            if (!PUBLIC_PROPERTY_TYPES.contains(value)) {
                throw new IllegalArgumentException("Unsupported property type: " + propertyType);
            }
            normalized.add(value);
        }
        return List.copyOf(normalized);
    }
    private List<Integer> normalizeStarRatings(List<Integer> starRatings) {
        if (starRatings == null || starRatings.isEmpty()) return starRatings;
        LinkedHashSet<Integer> normalized = new LinkedHashSet<>();
        for (Integer starRating : starRatings) {
            if (starRating == null || starRating < 1 || starRating > 5) {
                throw new IllegalArgumentException("starRatings values must be within 1..5.");
            }
            normalized.add(starRating);
        }
        return List.copyOf(normalized);
    }
    private void validatePriceBound(String field, Double value) {
        if (value != null && (!Double.isFinite(value) || value < 0)) {
            throw new IllegalArgumentException(field + " must be a finite non-negative value.");
        }
    }
    private LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value); }
        catch (Exception exception) { throw new IllegalArgumentException(field + " phải có định dạng yyyy-MM-dd."); }
    }
    private Number number(Object value) { return (Number) value; }
    private Integer integer(Object value) { return value == null ? null : ((Number) value).intValue(); }
    private Double decimal(Object value) { return value == null ? null : ((Number) value).doubleValue(); }
}
