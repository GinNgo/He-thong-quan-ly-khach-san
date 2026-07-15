package com.hotel.services.impl;

import com.hotel.dto.PropertySearchRequestDTO;
import com.hotel.dto.PropertySearchResponseDTO;
import com.hotel.entities.RoomType;
import com.hotel.entities.PropertyImage;
import com.hotel.repositories.PropertyImageRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.services.PropertySearchService;
import com.hotel.services.RoomAvailabilityService;
import com.hotel.util.VietnameseTextNormalizer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PropertySearchServiceImpl implements PropertySearchService {

    private static final String RELEASED_STATUSES = "'CANCELLED','REJECTED','EXPIRED','NO_SHOW','CHECKED_OUT','COMPLETED'";

    private final EntityManager entityManager;
    private final RoomTypeRepository roomTypeRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final RoomAvailabilityService roomAvailabilityService;
    private final Environment environment;

    @Value("${app.demo-data.allow-public-demo:false}")
    private boolean allowPublicDemo;

    public PropertySearchServiceImpl(EntityManager entityManager, RoomTypeRepository roomTypeRepository,
                                     PropertyImageRepository propertyImageRepository,
                                     RoomAvailabilityService roomAvailabilityService, Environment environment) {
        this.entityManager = entityManager;
        this.roomTypeRepository = roomTypeRepository;
        this.propertyImageRepository = propertyImageRepository;
        this.roomAvailabilityService = roomAvailabilityService;
        this.environment = environment;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Page<PropertySearchResponseDTO> searchProperties(PropertySearchRequestDTO request) {
        LocalDate checkIn = parseDate(request.getCheckInDate(), "checkInDate");
        LocalDate checkOut = parseDate(request.getCheckOutDate(), "checkOutDate");
        if ((checkIn == null) != (checkOut == null)) {
            throw new IllegalArgumentException("Ngày nhận và trả phòng phải được cung cấp cùng nhau.");
        }
        if (checkIn != null && !checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("Ngày trả phòng phải sau ngày nhận phòng.");
        }

        int roomCount = Math.max(request.getRoomCount() == null ? 1 : request.getRoomCount(), 1);
        int adults = Math.max(request.getAdultCount() == null ? 0 : request.getAdultCount(), 0);
        int children = Math.max(request.getChildCount() == null ? 0 : request.getChildCount(), 0);
        Map<String, Object> params = new HashMap<>();

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
                       (SELECT MIN(rt.base_price) FROM room_types rt WHERE rt.hotel_id=h.id AND rt.status='ACTIVE') AS min_price,
                       (SELECT COUNT(*) FROM rooms r WHERE r.hotel_id=h.id AND r.status <> 'MAINTENANCE' AND COALESCE(r.maintenance_status,'NONE') NOT IN ('MAINTENANCE','OUT_OF_SERVICE')) AS total_rooms,
                       (SELECT TOP 1 rt.id FROM room_types rt WHERE rt.hotel_id=h.id AND rt.status='ACTIVE' ORDER BY rt.base_price,rt.id) AS lowest_room_id,
                       (SELECT TOP 1 rt.name_vi FROM room_types rt WHERE rt.hotel_id=h.id AND rt.status='ACTIVE' ORDER BY rt.base_price,rt.id) AS lowest_room_name,
                       (SELECT TOP 1 COALESCE(rt.max_guests,rt.max_guest) FROM room_types rt WHERE rt.hotel_id=h.id AND rt.status='ACTIVE' ORDER BY rt.base_price,rt.id) AS lowest_room_guests
                """;
        String from = " FROM hotels h LEFT JOIN locations p ON p.id=h.province_id LEFT JOIN locations w ON w.id=h.ward_id ";
        StringBuilder where = new StringBuilder(" WHERE h.approval_status='APPROVED' AND h.operation_status='ACTIVE' ");
        if (!allowPublicDemo && environment.acceptsProfiles(Profiles.of("production"))) {
            where.append(" AND COALESCE(h.is_demo,0)=0 ");
        }

        if (request.getProvinceId() != null) {
            where.append(" AND h.province_id=:provinceId ");
            params.put("provinceId", request.getProvinceId());
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
            where.append(" AND h.average_rating>=:minReviewScore ");
            params.put("minReviewScore", request.getMinReviewScore());
        }
        if (request.getMinPrice() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM room_types rt WHERE rt.hotel_id=h.id AND rt.status='ACTIVE' AND rt.base_price>=:minPrice) ");
            params.put("minPrice", request.getMinPrice());
        }
        if (request.getMaxPrice() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM room_types rt WHERE rt.hotel_id=h.id AND rt.status='ACTIVE' AND rt.base_price<=:maxPrice) ");
            params.put("maxPrice", request.getMaxPrice());
        }

        where.append(" AND EXISTS (SELECT 1 FROM room_types rt WHERE rt.hotel_id=h.id AND rt.status='ACTIVE' ")
                .append(" AND COALESCE(rt.max_adults,rt.max_guests,rt.max_guest,999)*:roomCount>=:adultCount ")
                .append(" AND COALESCE(rt.max_children,rt.max_guests,rt.max_guest,999)*:roomCount>=:childCount ")
                .append(" AND COALESCE(rt.max_guests,rt.max_guest,999)*:roomCount>=:guestCount ")
                .append(" AND ((SELECT COUNT(*) FROM rooms r WHERE r.room_type_id=rt.id AND r.status<>'MAINTENANCE' AND COALESCE(r.maintenance_status,'NONE') NOT IN ('MAINTENANCE','OUT_OF_SERVICE')) ");
        params.put("roomCount", roomCount);
        params.put("adultCount", adults);
        params.put("childCount", children);
        params.put("guestCount", adults + children);
        if (checkIn != null) {
            where.append(" - (SELECT COALESCE(SUM(rd.quantity),0) FROM reservation_details rd JOIN reservations rs ON rs.id=rd.reservation_id WHERE rd.room_type_id=rt.id AND rs.status NOT IN (")
                    .append(RELEASED_STATUSES)
                    .append(") AND rs.check_in_date<:checkOut AND rs.check_out_date>:checkIn) ");
            params.put("checkIn", checkIn);
            params.put("checkOut", checkOut);
        }
        where.append(" >=:roomCount)) ");

        if (hasCoordinates && request.getRadiusKm() != null) {
            where.append(" AND ").append(distance).append("<=:radiusKm ");
            params.put("radiusKm", request.getRadiusKm());
        }

        String orderBy = switch (request.getSortBy() == null ? "POPULAR" : request.getSortBy().toUpperCase()) {
            case "NEAREST" -> hasCoordinates ? " ORDER BY distance ASC" : " ORDER BY h.id DESC";
            case "PRICE_ASC" -> " ORDER BY min_price ASC";
            case "PRICE_DESC" -> " ORDER BY min_price DESC";
            case "RATING" -> " ORDER BY h.average_rating DESC,h.review_count DESC";
            default -> " ORDER BY h.review_count DESC,h.id DESC";
        };

        Query dataQuery = entityManager.createNativeQuery(select + from + where + orderBy);
        Query countQuery = entityManager.createNativeQuery("SELECT COUNT(DISTINCT h.id)" + from + where);
        params.forEach((name, value) -> {
            dataQuery.setParameter(name, value);
            countQuery.setParameter(name, value);
        });

        int pageSize = Math.min(Math.max(request.getPageSize(), 1), 100);
        int pageNumber = Math.max(request.getPageNumber(), 1);
        dataQuery.setFirstResult((pageNumber - 1) * pageSize);
        dataQuery.setMaxResults(pageSize);

        List<PropertySearchResponseDTO> content = new ArrayList<>();
        for (Object[] row : (List<Object[]>) dataQuery.getResultList()) {
            content.add(mapRow(row, checkIn, checkOut, adults, children, roomCount));
        }
        long total = ((Number) countQuery.getSingleResult()).longValue();
        return new PageImpl<>(content, PageRequest.of(pageNumber - 1, pageSize), total);
    }

    private PropertySearchResponseDTO mapRow(Object[] row, LocalDate checkIn, LocalDate checkOut,
                                             int adults, int children, int roomCount) {
        PropertySearchResponseDTO dto = new PropertySearchResponseDTO();
        dto.setId(number(row[0]).longValue());
        dto.setSlug((String) row[1]);
        dto.setName((String) row[2]);
        dto.setAddressLine((String) row[3]);
        dto.setMainImageUrl((String) row[4]);
        dto.setStarRating(integer(row[5]));
        dto.setLatitude(decimal(row[6]));
        dto.setLongitude(decimal(row[7]));
        dto.setPropertyType((String) row[8]);
        dto.setReviewScore(decimal(row[9]));
        dto.setReviewCount(integer(row[10]) == null ? 0 : integer(row[10]));
        dto.setProvinceName((String) row[11]);
        dto.setWardName((String) row[12]);
        dto.setDistanceKm(decimal(row[13]));
        if (dto.getDistanceKm() != null) dto.setDistanceText(String.format("Cách %.1f km", dto.getDistanceKm()));

        List<RoomType> roomTypes = roomTypeRepository.findByHotelId(dto.getId()).stream()
                .filter(rt -> "ACTIVE".equals(rt.getStatus()))
                .filter(rt -> canHost(rt, adults, children, roomCount)).toList();
        Map<Long, Long> availability = new HashMap<>();
        roomTypes.forEach(rt -> availability.put(rt.getId(), roomAvailabilityService.countAvailableRooms(rt.getId(), checkIn, checkOut)));
        long available = availability.values().stream().mapToLong(Long::longValue).sum();
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

        List<PropertyImage> images = propertyImageRepository.findByHotelIdOrderBySortOrderAsc(dto.getId());
        PropertyImage primary = images.stream().filter(image -> Boolean.TRUE.equals(image.getIsPrimary())).findFirst()
                .orElse(images.isEmpty() ? null : images.get(0));
        dto.setThumbnailUrl(primary == null ? null : primary.getImageUrl());
        dto.setImageAltText(primary == null ? null : primary.getAltTextVi());
        dto.setGalleryUrls(images.stream().map(PropertyImage::getImageUrl).distinct().toList());
        dto.setImageCount(dto.getGalleryUrls().size());
        dto.setAmenities(List.of());
        dto.setBadges(List.of());
        dto.setFreeCancellation(false);
        dto.setPayAtProperty(false);
        dto.setBreakfastIncluded(false);
        return dto;
    }

    private boolean canHost(RoomType roomType, int adults, int children, int roomCount) {
        int maxAdults = roomType.getMaxAdults() != null ? roomType.getMaxAdults() : value(roomType.getMaxGuests(), roomType.getMaxGuest());
        int maxChildren = roomType.getMaxChildren() != null ? roomType.getMaxChildren() : value(roomType.getMaxGuests(), roomType.getMaxGuest());
        int maxGuests = value(roomType.getMaxGuests(), roomType.getMaxGuest());
        return adults <= maxAdults * roomCount && children <= maxChildren * roomCount
                && adults + children <= maxGuests * roomCount;
    }

    private int value(Integer preferred, Integer fallback) { return preferred != null ? preferred : fallback != null ? fallback : Integer.MAX_VALUE; }
    private String firstNotBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
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
