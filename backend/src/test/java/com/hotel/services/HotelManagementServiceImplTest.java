package com.hotel.services;

import com.hotel.dtos.PropertyClosureRequest;
import com.hotel.dtos.PropertyProfileDTO;
import com.hotel.dtos.PropertyProfileUpdateRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.Location;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.LocationRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.services.impl.HotelManagementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotelManagementServiceImplTest {

    @Mock private HotelRepository hotelRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private UserPropertyRepository userPropertyRepository;
    @Mock private PropertyAccessService propertyAccessService;
    @Mock private OperationalAuditService operationalAuditService;

    private HotelManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HotelManagementServiceImpl(
                hotelRepository, userPropertyRepository, propertyAccessService,
                new PropertyProfileMapper(locationRepository));
        ReflectionTestUtils.setField(service, "operationalAuditService", operationalAuditService);
    }

    @Test
    void adminCreateOwnsLifecycleAndWritesAudit() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
        arrangeLocation();
        when(hotelRepository.saveAndFlush(any(Hotel.class))).thenAnswer(invocation -> {
            Hotel hotel = invocation.getArgument(0);
            hotel.setId(90L);
            return hotel;
        });

        var result = service.createHotel(createRequest());

        assertEquals("DRAFT", result.getStatus());
        assertEquals("DRAFT", result.getApprovalStatus());
        assertEquals("INACTIVE", result.getOperationStatus());
        assertEquals("ADMIN", result.getDataSource());
        verify(operationalAuditService).append(any());
    }

    @Test
    void activeOwnerCanEditDraftPropertyButCannotChangeLifecycle() {
        arrangeLocation();
        User owner = user(10L);
        Hotel hotel = hotel(20L, "DRAFT", "INACTIVE");
        UserProperty mapping = ownership(owner, hotel, "ACTIVE");
        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(userPropertyRepository.findByUserIdAndHotelIdAndRelationshipType(10L, 20L, "OWNER"))
                .thenReturn(Optional.of(mapping));
        when(hotelRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(hotel));
        when(hotelRepository.saveAndFlush(hotel)).thenReturn(hotel);

        PropertyProfileDTO profile = profile("Updated property");
        PropertyProfileUpdateRequest request = new PropertyProfileUpdateRequest(profile, "Correct owner profile");
        var result = service.updateOwnedHotel(20L, request);

        assertEquals("Updated property", result.getNameVi());
        assertEquals("DRAFT", result.getApprovalStatus());
        assertEquals("INACTIVE", result.getOperationStatus());
        ArgumentCaptor<OperationalAuditService.AuditCommand> audit =
                ArgumentCaptor.forClass(OperationalAuditService.AuditCommand.class);
        verify(operationalAuditService).append(audit.capture());
        assertEquals("PROPERTY_OWNER_UPDATED", audit.getValue().eventType());
        assertEquals("Correct owner profile", audit.getValue().reason());
    }

    @Test
    void ownerCannotEnumerateOrEditAnotherProperty() {
        User owner = user(10L);
        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(userPropertyRepository.findByUserIdAndHotelIdAndRelationshipType(10L, 99L, "OWNER"))
                .thenReturn(Optional.empty());
        PropertyProfileUpdateRequest request =
                new PropertyProfileUpdateRequest(profile("Unauthorized"), "Cross property edit");

        assertThrows(ResourceNotFoundException.class, () -> service.updateOwnedHotel(99L, request));
        verify(hotelRepository, never()).findByIdForUpdate(any());
        verify(hotelRepository, never()).saveAndFlush(any());
    }

    @Test
    void ownerCannotReadAnotherPropertyProfile() {
        User owner = user(10L);
        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(userPropertyRepository.findByUserIdAndHotelIdAndRelationshipType(10L, 99L, "OWNER"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getOwnedProfile(99L));
        verify(hotelRepository, never()).findById(99L);
    }

    @Test
    void propertyUnderReviewRejectsOwnerEdit() {
        User owner = user(10L);
        Hotel hotel = hotel(20L, "PENDING_APPROVAL", "INACTIVE");
        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(userPropertyRepository.findByUserIdAndHotelIdAndRelationshipType(10L, 20L, "OWNER"))
                .thenReturn(Optional.of(ownership(owner, hotel, "ACTIVE")));
        when(hotelRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(hotel));
        PropertyProfileUpdateRequest request =
                new PropertyProfileUpdateRequest(profile("Changed during review"), "Unsafe review mutation");

        assertThrows(IllegalStateException.class, () -> service.updateOwnedHotel(20L, request));
        verify(hotelRepository, never()).saveAndFlush(any());
    }

    @Test
    void adminCloseRetainsAggregateAndRecordsReason() {
        Hotel hotel = hotel(20L, "APPROVED", "ACTIVE");
        hotel.setStatus("ACTIVE");
        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
        when(hotelRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(hotel));
        when(hotelRepository.saveAndFlush(hotel)).thenReturn(hotel);

        var result = service.closeHotel(20L, new PropertyClosureRequest("Owner requested permanent closure"));

        assertEquals("CLOSED", result.getStatus());
        assertEquals("CLOSED", result.getOperationStatus());
        verify(hotelRepository, never()).delete(any());
        verify(hotelRepository, never()).deleteById(any());
        ArgumentCaptor<OperationalAuditService.AuditCommand> audit =
                ArgumentCaptor.forClass(OperationalAuditService.AuditCommand.class);
        verify(operationalAuditService).append(audit.capture());
        assertEquals("Owner requested permanent closure", audit.getValue().reason());
    }

    private PropertyProfileDTO createRequest() {
        return profile("Administrative draft");
    }

    private PropertyProfileDTO profile(String nameVi) {
        PropertyProfileDTO profile = new PropertyProfileDTO();
        profile.setNameVi(nameVi);
        profile.setPropertyType("HOTEL");
        profile.setAddressLine("1 Test Street");
        profile.setProvinceId(1L);
        profile.setWardId(2L);
        profile.setLatitude(10.5);
        profile.setLongitude(106.7);
        profile.setPhone("+84 901 234 567");
        profile.setEmail("owner@example.com");
        profile.setWebsite("https://example.com/property");
        profile.setCheckinTime("14:00");
        profile.setCheckoutTime("12:00");
        profile.setMinPrice(500000D);
        profile.setMaxPrice(1500000D);
        return profile;
    }

    private void arrangeLocation() {
        Location province = new Location();
        province.setId(1L);
        province.setLocationType("PROVINCE");
        province.setNameVi("Province");
        Location ward = new Location();
        ward.setId(2L);
        ward.setLocationType("WARD");
        ward.setParent(province);
        when(locationRepository.findById(1L)).thenReturn(Optional.of(province));
        when(locationRepository.findById(2L)).thenReturn(Optional.of(ward));
    }

    private Hotel hotel(Long id, String approvalStatus, String operationStatus) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        hotel.setName("Property");
        hotel.setNameVi("Property");
        hotel.setAddressLine("Address");
        hotel.setCity("City");
        hotel.setCountry("Vietnam");
        hotel.setPropertyType("HOTEL");
        hotel.setStatus(approvalStatus);
        hotel.setApprovalStatus(approvalStatus);
        hotel.setOperationStatus(operationStatus);
        return hotel;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private UserProperty ownership(User owner, Hotel hotel, String status) {
        UserProperty mapping = new UserProperty();
        mapping.setUser(owner);
        mapping.setHotel(hotel);
        mapping.setRelationshipType("OWNER");
        mapping.setStatus(status);
        return mapping;
    }
}
