package com.hotel.services;

import com.hotel.dtos.PropertyClosureRequest;
import com.hotel.dtos.PropertyProfileDTO;
import com.hotel.dtos.PropertyProfileUpdateRequest;
import com.hotel.entities.Hotel;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.services.impl.HotelManagementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotelManagementLifecycleBypassTest {

    @Mock private HotelRepository hotelRepository;
    @Mock private UserPropertyRepository userPropertyRepository;
    @Mock private PropertyAccessService propertyAccessService;
    @Mock private PropertyProfileMapper propertyProfileMapper;

    private HotelManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HotelManagementServiceImpl(
                hotelRepository, userPropertyRepository, propertyAccessService, propertyProfileMapper);
        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
    }

    @Test
    void administrativeCreateCannotBypassDraftApprovalBoundary() {
        PropertyProfileDTO request = profile("New property");
        when(hotelRepository.saveAndFlush(any(Hotel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PropertyProfileDTO saved = service.createHotel(request);

        assertEquals("DRAFT", saved.getStatus());
        assertEquals("DRAFT", saved.getApprovalStatus());
        assertEquals("INACTIVE", saved.getOperationStatus());
    }

    @Test
    void profileUpdateCannotMutateLifecycleState() {
        Hotel existing = new Hotel();
        existing.setId(7L);
        existing.setNameVi("Old name");
        existing.setStatus("SUSPENDED");
        existing.setApprovalStatus("APPROVED");
        existing.setOperationStatus("SUSPENDED");
        existing.setLifecycleAction("SUSPEND");
        existing.setLifecycleReason("Safety inspection is required.");
        when(hotelRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(existing));
        when(hotelRepository.saveAndFlush(existing)).thenReturn(existing);
        doAnswer(invocation -> {
            Hotel hotel = invocation.getArgument(0);
            PropertyProfileDTO update = invocation.getArgument(1);
            hotel.setNameVi(update.getNameVi());
            return null;
        }).when(propertyProfileMapper).apply(any(Hotel.class), any(PropertyProfileDTO.class));

        PropertyProfileDTO saved = service.updateHotel(
                7L, new PropertyProfileUpdateRequest(profile("Updated name"), "Profile correction"));

        assertEquals("Updated name", saved.getNameVi());
        assertEquals("SUSPENDED", saved.getStatus());
        assertEquals("APPROVED", saved.getApprovalStatus());
        assertEquals("SUSPENDED", saved.getOperationStatus());
        assertEquals("SUSPEND", existing.getLifecycleAction());
    }

    @Test
    void closeRetainsHistoricalPropertyRecord() {
        Hotel existing = new Hotel();
        existing.setId(7L);
        existing.setStatus("ACTIVE");
        existing.setApprovalStatus("APPROVED");
        existing.setOperationStatus("ACTIVE");
        when(hotelRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(existing));
        when(hotelRepository.saveAndFlush(existing)).thenReturn(existing);

        PropertyProfileDTO closed = service.closeHotel(7L, new PropertyClosureRequest("Permanent closure"));

        assertEquals("CLOSED", closed.getStatus());
        assertEquals("CLOSED", closed.getOperationStatus());
        assertNull(existing.getLifecycleReason());
        verify(hotelRepository, never()).deleteById(7L);
    }

    private PropertyProfileDTO profile(String name) {
        PropertyProfileDTO profile = new PropertyProfileDTO();
        profile.setNameVi(name);
        profile.setPropertyType("HOTEL");
        profile.setAddressLine("1 Test Street");
        profile.setProvinceId(1L);
        profile.setWardId(2L);
        return profile;
    }
}
