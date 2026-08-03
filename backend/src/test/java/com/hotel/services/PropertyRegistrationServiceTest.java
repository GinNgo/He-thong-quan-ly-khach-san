package com.hotel.services;

import com.hotel.dtos.PartnerRegistrationRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.Location;
import com.hotel.entities.User;
import com.hotel.exceptions.RegistrationConflictException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.LocationRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyRegistrationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private HotelRepository hotelRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private UserPropertyRepository userPropertyRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PropertyOwnershipLifecycleService ownershipLifecycleService;

    @InjectMocks
    private PropertyRegistrationService registrationService;

    private Location province;
    private Location ward;

    @BeforeEach
    void setUpLocations() {
        province = location(10L, "PROVINCE", "Da Nang", null);
        ward = location(11L, "WARD", "Hai Chau", province);
    }

    @Test
    void registerAnonymousPartnerCreatesCanonicalPendingPropertyWithoutOwnerRole() {
        when(userRepository.existsByEmailIgnoreCase("owner@example.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("owner@example.com")).thenReturn(false);
        when(locationRepository.findById(10L)).thenReturn(Optional.of(province));
        when(locationRepository.findById(11L)).thenReturn(Optional.of(ward));
        when(passwordEncoder.encode("secret123")).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(40L);
            return user;
        });
        when(hotelRepository.saveAndFlush(any(Hotel.class))).thenAnswer(invocation -> {
            Hotel hotel = invocation.getArgument(0);
            hotel.setId(55L);
            return hotel;
        });
        var result = registrationService.registerAnonymousPartner(request());

        assertEquals(40L, result.userId());
        assertEquals(55L, result.propertyId());
        assertEquals("DRAFT", result.status());

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(user.capture());
        assertEquals("owner@example.com", user.getValue().getEmail());
        assertEquals("owner@example.com", user.getValue().getUsername());
        assertTrue(user.getValue().getRoles() == null || user.getValue().getRoles().isEmpty());

        ArgumentCaptor<Hotel> property = ArgumentCaptor.forClass(Hotel.class);
        verify(hotelRepository).saveAndFlush(property.capture());
        Hotel saved = property.getValue();
        assertEquals(10L, saved.getProvinceId());
        assertEquals(11L, saved.getWardId());
        assertEquals("12 Bach Dang", saved.getAddressLine());
        assertEquals("Da Nang", saved.getCity());
        assertEquals("Việt Nam", saved.getCountry());
        assertEquals("DRAFT", saved.getStatus());
        assertEquals("DRAFT", saved.getApprovalStatus());
        assertEquals("INACTIVE", saved.getOperationStatus());
        assertFalse(saved.getIsDemo());
        assertEquals("USER", saved.getDataSource());
        assertEquals("HOTEL", saved.getPropertyType());
        assertTrue(saved.getCode().startsWith("PARTNER-"));
        assertTrue(saved.getSlug().startsWith("seaside-hotel-"));
        verify(ownershipLifecycleService).createPendingOwner(user.getValue(), saved);
    }

    @Test
    void duplicateEmailIsRejectedBeforeAnyPropertyMutation() {
        when(userRepository.existsByEmailIgnoreCase("owner@example.com")).thenReturn(true);

        RegistrationConflictException exception = assertThrows(
                RegistrationConflictException.class,
                () -> registrationService.registerAnonymousPartner(request()));

        assertEquals(RegistrationConflictException.EMAIL_CODE, exception.code());
        verify(userRepository, never()).saveAndFlush(any());
        verify(hotelRepository, never()).saveAndFlush(any());
        verify(ownershipLifecycleService, never()).createPendingOwner(any(), any());
    }

    @Test
    void wardFromAnotherProvinceIsRejectedBeforeCreatingUser() {
        Location otherProvince = location(20L, "PROVINCE", "Hue", null);
        ward.setParent(otherProvince);
        when(userRepository.existsByEmailIgnoreCase("owner@example.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("owner@example.com")).thenReturn(false);
        when(locationRepository.findById(10L)).thenReturn(Optional.of(province));
        when(locationRepository.findById(11L)).thenReturn(Optional.of(ward));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registrationService.registerAnonymousPartner(request()));

        assertEquals("Ward does not belong to the selected province.", exception.getMessage());
        verify(userRepository, never()).saveAndFlush(any());
        verify(hotelRepository, never()).saveAndFlush(any());
    }

    private PartnerRegistrationRequest request() {
        PartnerRegistrationRequest request = new PartnerRegistrationRequest();
        request.setEmail("  OWNER@Example.com ");
        request.setPassword("secret123");
        request.setFullName("  Partner   Owner ");
        request.setPhone("0900000000");
        request.setPropertyName("  Seaside   Hotel ");
        request.setProvinceId(10L);
        request.setWardId(11L);
        request.setAddress("  12   Bach Dang ");
        return request;
    }

    private Location location(Long id, String type, String name, Location parent) {
        Location location = new Location();
        location.setId(id);
        location.setLocationType(type);
        location.setNameVi(name);
        location.setParent(parent);
        location.setStatus("ACTIVE");
        return location;
    }
}
