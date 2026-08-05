package com.hotel.services;

import com.hotel.dtos.AmenityAssignmentRequest;
import com.hotel.dtos.AmenityUpsertRequest;
import com.hotel.entities.Amenity;
import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyAmenity;
import com.hotel.entities.RoomType;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.AmenityRepository;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.PropertyAmenityRepository;
import com.hotel.repositories.RoomTypeAmenityRepository;
import com.hotel.repositories.RoomTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AmenityServiceTest {

    @Mock private AmenityRepository amenityRepository;
    @Mock private PropertyAmenityRepository propertyAmenityRepository;
    @Mock private RoomTypeAmenityRepository roomTypeAmenityRepository;
    @Mock private HotelRepository hotelRepository;
    @Mock private RoomTypeRepository roomTypeRepository;
    @Mock private PropertyAccessService propertyAccessService;

    private AmenityService service;
    private Hotel hotel;

    @BeforeEach
    void setUp() {
        service = new AmenityService(
                amenityRepository, propertyAmenityRepository, roomTypeAmenityRepository,
                hotelRepository, roomTypeRepository, propertyAccessService);
        hotel = new Hotel();
        hotel.setId(10L);
    }

    @Test
    void systemAdminCreatesNormalizedLocalizedCatalogEntry() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
        when(amenityRepository.findByCode("FREE_WIFI")).thenReturn(Optional.empty());
        when(amenityRepository.save(any(Amenity.class))).thenAnswer(invocation -> {
            Amenity amenity = invocation.getArgument(0);
            amenity.setId(1L);
            return amenity;
        });

        var result = service.create(new AmenityUpsertRequest(
                " free-wifi ", " Wi-Fi miễn phí ", " Free Wi-Fi ", " internet ", "pi pi-wifi", 10));

        assertEquals("FREE_WIFI", result.code());
        assertEquals("Wi-Fi miễn phí", result.nameVi());
        assertEquals("INTERNET", result.category());
        assertEquals("ACTIVE", result.status());
    }

    @Test
    void propertyStaffCannotMutateTheGlobalCatalog() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);

        assertThrows(SecurityException.class, () -> service.create(new AmenityUpsertRequest(
                "SPA", "Spa", "Spa", "WELLNESS", null, 10)));

        verify(amenityRepository, never()).save(any());
    }

    @Test
    void propertyReplacementUsesServerResolvedOwnerAndExactActiveIds() {
        Amenity wifi = amenity(1L, "WIFI", "Wi-Fi miễn phí");
        Amenity parking = amenity(2L, "PARKING", "Bãi đỗ xe");
        when(hotelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(hotel));
        when(amenityRepository.findByIdInAndStatus(new LinkedHashSet<>(List.of(2L, 1L)), "ACTIVE"))
                .thenReturn(List.of(wifi, parking));

        var result = service.replacePropertyAmenities(
                10L, new AmenityAssignmentRequest(List.of(2L, 1L)));

        verify(propertyAccessService).requireManagedHotel(10L);
        verify(propertyAmenityRepository).deleteByHotelId(10L);
        ArgumentCaptor<List<PropertyAmenity>> captor = ArgumentCaptor.forClass(List.class);
        verify(propertyAmenityRepository).saveAll(captor.capture());
        assertEquals(List.of(2L, 1L), captor.getValue().stream()
                .map(assignment -> assignment.getAmenity().getId()).toList());
        captor.getValue().forEach(assignment -> assertEquals(10L, assignment.getHotel().getId()));
        assertEquals(List.of("PARKING", "WIFI"), result.stream().map(item -> item.code()).toList());
    }

    @Test
    void duplicateOrInactiveIdsDoNotEraseExistingAssignments() {
        when(hotelRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(hotel));
        assertThrows(IllegalArgumentException.class, () -> service.replacePropertyAmenities(
                10L, new AmenityAssignmentRequest(List.of(1L, 1L))));
        verify(propertyAmenityRepository, never()).deleteByHotelId(any());

        when(amenityRepository.findByIdInAndStatus(new LinkedHashSet<>(List.of(9L)), "ACTIVE"))
                .thenReturn(List.of());
        assertThrows(ResourceNotFoundException.class, () -> service.replacePropertyAmenities(
                10L, new AmenityAssignmentRequest(List.of(9L))));
        verify(propertyAmenityRepository, never()).deleteByHotelId(any());
    }

    @Test
    void crossPropertyRoomTypeIsRejectedBeforeAssignmentMutation() {
        RoomType roomType = new RoomType();
        roomType.setId(20L);
        roomType.setHotel(hotel);
        when(roomTypeRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(roomType));
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("not found"))
                .when(propertyAccessService).requireAccessibleOrNotFound(10L, "loại phòng");

        assertThrows(ResourceNotFoundException.class, () -> service.replaceRoomTypeAmenities(
                20L, new AmenityAssignmentRequest(List.of(1L))));

        verify(roomTypeAmenityRepository, never()).deleteByRoomTypeId(any());
        verify(amenityRepository, never()).findByIdInAndStatus(any(), any());
    }

    @Test
    void publicDisplayCombinesPropertyAndActiveRoomTypeAmenitiesWithoutDuplicates() {
        Amenity wifi = amenity(1L, "WIFI", "Wi-Fi miễn phí");
        Amenity pool = amenity(2L, "POOL", "Hồ bơi");
        when(propertyAmenityRepository.findActiveAmenities(10L)).thenReturn(List.of(wifi));
        when(roomTypeAmenityRepository.findActiveAmenitiesByHotelId(10L)).thenReturn(List.of(wifi, pool));

        assertEquals(List.of("Wi-Fi miễn phí", "Hồ bơi"), service.publicDisplayNames(10L));
    }

    private Amenity amenity(Long id, String code, String nameVi) {
        Amenity amenity = new Amenity();
        amenity.setId(id);
        amenity.setCode(code);
        amenity.setNameVi(nameVi);
        amenity.setCategory("GENERAL");
        amenity.setSortOrder(0);
        amenity.setStatus("ACTIVE");
        return amenity;
    }
}
