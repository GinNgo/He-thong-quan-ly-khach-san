package com.hotel.services.impl;

import com.hotel.dto.PropertySearchRequestDTO;
import com.hotel.dto.PropertySearchResponseDTO;
import com.hotel.services.PropertySearchService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PropertySearchServiceImpl implements PropertySearchService {

    private final EntityManager entityManager;

    public PropertySearchServiceImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<PropertySearchResponseDTO> searchProperties(PropertySearchRequestDTO request) {
        StringBuilder sql = new StringBuilder();
        StringBuilder countSql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        // Add additional fields to SELECT
        String selectClause = "SELECT h.id, h.name, h.address, h.main_image, h.star_rating, h.latitude, h.longitude, h.property_type, h.average_rating, h.review_count";
        String countClause = "SELECT COUNT(DISTINCT h.id)";
        
        boolean hasLocation = request.getLatitude() != null && request.getLongitude() != null;
        if (hasLocation) {
            String distanceCalc = ", (6371 * acos(cos(radians(:userLat)) * cos(radians(h.latitude)) " +
                    "* cos(radians(h.longitude) - radians(:userLng)) + sin(radians(:userLat)) * sin(radians(h.latitude)))) AS distance";
            selectClause += distanceCalc;
            params.put("userLat", request.getLatitude());
            params.put("userLng", request.getLongitude());
        } else {
            selectClause += ", NULL AS distance";
        }

        // Subquery for minimum price and max price handling
        selectClause += ", (SELECT MIN(rt.base_price) FROM room_types rt WHERE rt.hotel_id = h.id) AS min_price";
        selectClause += ", (SELECT SUM(r.id) FROM room_types rt JOIN rooms r ON r.room_type_id = rt.id WHERE rt.hotel_id = h.id) AS available_rooms"; // Dummy aggregate for now
        selectClause += ", (SELECT TOP 1 rt.name_vi FROM room_types rt WHERE rt.hotel_id = h.id ORDER BY rt.base_price ASC) AS lowest_room_name";
        selectClause += ", (SELECT TOP 1 rt.max_guest FROM room_types rt WHERE rt.hotel_id = h.id ORDER BY rt.base_price ASC) AS lowest_room_max_guests";

        String fromClause = " FROM hotels h";
        String whereClause = " WHERE h.status = 'ACTIVE'";

        // Location Filters
        if (request.getProvinceId() != null) {
            whereClause += " AND h.province_id = :provinceId";
            params.put("provinceId", request.getProvinceId());
        }
        
        if (request.getWardId() != null) {
            whereClause += " AND h.ward_id = :wardId";
            params.put("wardId", request.getWardId());
        }

        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            whereClause += " AND (LOWER(h.name) LIKE LOWER(:keyword) OR LOWER(h.address) LIKE LOWER(:keyword))";
            params.put("keyword", "%" + request.getKeyword() + "%");
        }

        if (hasLocation && request.getRadiusKm() != null) {
            whereClause += " AND (6371 * acos(cos(radians(:userLat)) * cos(radians(h.latitude)) " +
                    "* cos(radians(h.longitude) - radians(:userLng)) + sin(radians(:userLat)) * sin(radians(h.latitude)))) <= :radiusKm";
            params.put("radiusKm", request.getRadiusKm());
        }

        // Phase 2 Filters
        if (request.getPropertyTypes() != null && !request.getPropertyTypes().isEmpty()) {
            whereClause += " AND h.property_type IN :propertyTypes";
            params.put("propertyTypes", request.getPropertyTypes());
        }

        if (request.getStarRatings() != null && !request.getStarRatings().isEmpty()) {
            whereClause += " AND h.star_rating IN :starRatings";
            params.put("starRatings", request.getStarRatings());
        }

        if (request.getMinReviewScore() != null) {
            whereClause += " AND h.average_rating >= :minReviewScore";
            params.put("minReviewScore", request.getMinReviewScore());
        }

        if (request.getMinPrice() != null) {
            whereClause += " AND (SELECT MIN(rt.base_price) FROM room_types rt WHERE rt.hotel_id = h.id) >= :minPrice";
            params.put("minPrice", request.getMinPrice());
        }

        if (request.getMaxPrice() != null) {
            whereClause += " AND (SELECT MIN(rt.base_price) FROM room_types rt WHERE rt.hotel_id = h.id) <= :maxPrice";
            params.put("maxPrice", request.getMaxPrice());
        }

        String orderByClause = "";
        if ("NEAREST".equalsIgnoreCase(request.getSortBy()) && hasLocation) {
            orderByClause = " ORDER BY distance ASC";
        } else if ("PRICE_ASC".equalsIgnoreCase(request.getSortBy())) {
            orderByClause = " ORDER BY min_price ASC";
        } else if ("PRICE_DESC".equalsIgnoreCase(request.getSortBy())) {
            orderByClause = " ORDER BY min_price DESC";
        } else if ("RATING".equalsIgnoreCase(request.getSortBy())) {
            orderByClause = " ORDER BY h.average_rating DESC, h.review_count DESC";
        } else {
            orderByClause = " ORDER BY h.id DESC"; // Default POPULAR
        }

        sql.append(selectClause).append(fromClause).append(whereClause).append(orderByClause);
        countSql.append(countClause).append(fromClause).append(whereClause);

        Query query = entityManager.createNativeQuery(sql.toString());
        Query countQuery = entityManager.createNativeQuery(countSql.toString());

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
            countQuery.setParameter(entry.getKey(), entry.getValue());
        }

        int pageSize = request.getPageSize() > 0 ? request.getPageSize() : 20;
        int pageNumber = request.getPageNumber() > 0 ? request.getPageNumber() : 1;
        int offset = (pageNumber - 1) * pageSize;

        query.setFirstResult(offset);
        query.setMaxResults(pageSize);

        List<Object[]> results = query.getResultList();
        long totalRecords = ((Number) countQuery.getSingleResult()).longValue();

        List<PropertySearchResponseDTO> content = new ArrayList<>();
        for (Object[] row : results) {
            PropertySearchResponseDTO dto = new PropertySearchResponseDTO();
            dto.setId(((Number) row[0]).longValue());
            dto.setName((String) row[1]);
            dto.setAddressLine((String) row[2]);
            dto.setThumbnailUrl((String) row[3]);
            dto.setStarRating(row[4] != null ? ((Number) row[4]).intValue() : null);
            dto.setLatitude(row[5] != null ? ((Number) row[5]).doubleValue() : null);
            dto.setLongitude(row[6] != null ? ((Number) row[6]).doubleValue() : null);
            dto.setPropertyType((String) row[7]);
            dto.setReviewScore(row[8] != null ? ((Number) row[8]).doubleValue() : null);
            dto.setReviewCount(row[9] != null ? ((Number) row[9]).intValue() : 0);
            
            if (row[10] != null) {
                double dist = ((Number) row[10]).doubleValue();
                dto.setDistanceKm(dist);
                dto.setDistanceText(String.format("Cách trung tâm %.1f km", dist));
            }

            BigDecimal nightlyPrice = row[11] != null ? new BigDecimal(row[11].toString()) : null;
            dto.setStartingPrice(nightlyPrice != null ? nightlyPrice.doubleValue() : null);
            
            if (nightlyPrice != null) {
                PropertySearchResponseDTO.PricingSummary pricing = new PropertySearchResponseDTO.PricingSummary();
                pricing.setNightlyPrice(nightlyPrice);
                pricing.setDiscountedPrice(nightlyPrice); // placeholder
                pricing.setNumberOfNights(1);
                pricing.setTotalAmount(nightlyPrice);
                pricing.setCurrency("VND");
                dto.setPricing(pricing);
            }

            PropertySearchResponseDTO.RoomTypeSummary lowestRoom = new PropertySearchResponseDTO.RoomTypeSummary();
            lowestRoom.setName((String) row[13]);
            lowestRoom.setMaxGuests(row[14] != null ? ((Number) row[14]).intValue() : 2);
            dto.setLowestRoomType(lowestRoom);
            
            dto.setAvailableRoomCount(row[12] != null ? ((Number) row[12]).intValue() : 0);

            // Mocked attributes since they are not in DB schema yet
            dto.setFreeCancellation(true);
            dto.setPayAtProperty(false);
            dto.setBreakfastIncluded(false);
            dto.setBadges(new ArrayList<>());
            dto.setGalleryUrls(new ArrayList<>());
            dto.setAmenities(new ArrayList<>());

            content.add(dto);
        }

        return new PageImpl<>(content, PageRequest.of(pageNumber - 1, pageSize), totalRecords);
    }
}
