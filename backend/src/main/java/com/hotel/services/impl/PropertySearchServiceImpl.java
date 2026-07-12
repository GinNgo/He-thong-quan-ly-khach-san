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

        String selectClause = "SELECT h.id, h.name, h.address_line, h.main_image, h.star_rating, h.latitude, h.longitude";
        String countClause = "SELECT COUNT(h.id)";
        
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

        String fromClause = " FROM hotels h";
        
        // Add joins if checking availability
        if (request.getCheckInDate() != null && request.getCheckOutDate() != null) {
            // Simplified logic: we assume there is a room availability check, but for now we just make sure hotel is active
            // A full implementation would join rooms and reservations
            // fromClause += " JOIN rooms r ON r.hotel_id = h.id ... ";
        }

        String whereClause = " WHERE h.status = 'ACTIVE'";

        if (request.getProvinceId() != null) {
            whereClause += " AND h.province_id = :provinceId";
            params.put("provinceId", request.getProvinceId());
        }
        
        if (request.getWardId() != null) {
            whereClause += " AND h.ward_id = :wardId";
            params.put("wardId", request.getWardId());
        }

        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            whereClause += " AND (LOWER(h.name) LIKE LOWER(:keyword) OR LOWER(h.address_line) LIKE LOWER(:keyword))";
            params.put("keyword", "%" + request.getKeyword() + "%");
        }

        if (hasLocation && request.getRadiusKm() != null) {
            whereClause += " AND (6371 * acos(cos(radians(:userLat)) * cos(radians(h.latitude)) " +
                    "* cos(radians(h.longitude) - radians(:userLng)) + sin(radians(:userLat)) * sin(radians(h.latitude)))) <= :radiusKm";
            params.put("radiusKm", request.getRadiusKm());
        }

        String orderByClause = "";
        if ("NEAREST".equalsIgnoreCase(request.getSortBy()) && hasLocation) {
            orderByClause = " ORDER BY distance ASC";
        } else {
            orderByClause = " ORDER BY h.id DESC"; // Default
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
            dto.setMainImage((String) row[3]);
            dto.setStarRating(row[4] != null ? ((Number) row[4]).intValue() : null);
            dto.setLatitude(row[5] != null ? ((Number) row[5]).doubleValue() : null);
            dto.setLongitude(row[6] != null ? ((Number) row[6]).doubleValue() : null);
            
            if (row[7] != null) {
                double dist = ((Number) row[7]).doubleValue();
                dto.setDistanceKm(dist);
                dto.setDistanceText(String.format("Cách vị trí tìm kiếm %.1f km", dist));
            }
            
            content.add(dto);
        }

        return new PageImpl<>(content, PageRequest.of(pageNumber - 1, pageSize), totalRecords);
    }
}
