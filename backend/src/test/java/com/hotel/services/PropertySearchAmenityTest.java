package com.hotel.services;

import com.hotel.dto.PropertySearchRequestDTO;
import com.hotel.repositories.PropertyImageRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.services.impl.PropertySearchServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertySearchAmenityTest {

    @Mock private EntityManager entityManager;
    @Mock private Query dataQuery;
    @Mock private Query countQuery;
    @Mock private RoomTypeRepository roomTypeRepository;
    @Mock private PropertyImageRepository propertyImageRepository;
    @Mock private RoomAvailabilityService roomAvailabilityService;
    @Mock private AmenityService amenityService;
    @Mock private Environment environment;

    @Test
    void amenityFiltersUseIndexedPropertyOrActiveRoomTypePredicatesAndPopulateBadges() {
        AtomicReference<String> dataSql = new AtomicReference<>();
        when(entityManager.createNativeQuery(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.startsWith("SELECT COUNT")) return countQuery;
            dataSql.set(sql);
            return dataQuery;
        });
        when(dataQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(dataQuery);
        when(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
        when(dataQuery.setFirstResult(anyInt())).thenReturn(dataQuery);
        when(dataQuery.setMaxResults(anyInt())).thenReturn(dataQuery);
        Object[] row = {10L, "hotel-10", "Hotel 10", "Address", null, 4, null, null,
                "HOTEL", null, 0, "Đà Nẵng", "Hải Châu", null};
        when(dataQuery.getResultList()).thenReturn(java.util.Collections.singletonList(row));
        when(countQuery.getSingleResult()).thenReturn(1L);
        when(roomTypeRepository.findByHotelId(10L)).thenReturn(List.of());
        when(propertyImageRepository.findByHotelIdOrderBySortOrderAsc(10L)).thenReturn(List.of());
        when(amenityService.publicDisplayNames(10L)).thenReturn(List.of("Wi-Fi miễn phí", "Hồ bơi"));

        PropertySearchServiceImpl service = new PropertySearchServiceImpl(
                entityManager, roomTypeRepository, propertyImageRepository,
                roomAvailabilityService, amenityService, environment);
        PropertySearchRequestDTO request = new PropertySearchRequestDTO();
        request.setAmenityIds(List.of(1L, 2L, 2L));

        var result = service.searchProperties(request);

        assertTrue(dataSql.get().contains("FROM property_amenities pa"));
        assertTrue(dataSql.get().contains("FROM room_type_amenities rta"));
        assertEquals(2, countOccurrences(dataSql.get(), "FROM property_amenities pa"));
        org.mockito.Mockito.verify(dataQuery).setParameter("amenity0", 1L);
        org.mockito.Mockito.verify(dataQuery).setParameter("amenity1", 2L);
        org.mockito.Mockito.verify(countQuery).setParameter("amenity0", 1L);
        assertEquals(List.of("Wi-Fi miễn phí", "Hồ bơi"), result.getContent().getFirst().getAmenities());
    }

    private int countOccurrences(String source, String fragment) {
        return (source.length() - source.replace(fragment, "").length()) / fragment.length();
    }
}
