package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.Role;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyOwnershipLifecycleServiceTest {

    @Mock private UserPropertyRepository userPropertyRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private HotelRepository hotelRepository;

    @InjectMocks
    private PropertyOwnershipLifecycleService service;

    @Test
    void createPendingOwner_DoesNotGrantRoleOrStartOperationalOwnership() {
        User user = user(7L);
        Hotel hotel = hotel(10L);
        when(userPropertyRepository.findByUserIdAndHotelIdAndRelationshipType(7L, 10L, "OWNER"))
                .thenReturn(Optional.empty());
        when(userPropertyRepository.save(any(UserProperty.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProperty result = service.createPendingOwner(user, hotel);

        assertEquals("PENDING", result.getStatus());
        assertFalse(result.getIsPrimaryOwner());
        assertFalse(result.getBillingAdmin());
        assertEquals(null, result.getStartDate());
        assertTrue(user.getRoles().isEmpty());
    }

    @Test
    void activateOwner_ActivatesMappingAndGrantsOwnerRoleExactlyAtApproval() {
        User user = user(7L);
        Hotel hotel = hotel(10L);
        UserProperty mapping = mapping(user, hotel, "PENDING");
        Role ownerRole = new Role();
        ownerRole.setCode("PROPERTY_OWNER");
        when(userPropertyRepository.findByUserIdAndHotelIdAndRelationshipType(7L, 10L, "OWNER"))
                .thenReturn(Optional.of(mapping));
        when(userPropertyRepository.countByHotelIdAndRelationshipTypeAndStatus(10L, "OWNER", "ACTIVE"))
                .thenReturn(0L);
        when(userPropertyRepository.save(mapping)).thenReturn(mapping);
        when(roleRepository.findByCode("PROPERTY_OWNER")).thenReturn(Optional.of(ownerRole));

        UserProperty result = service.activateOwner(10L, 7L);

        assertEquals("ACTIVE", result.getStatus());
        assertTrue(result.getIsPrimaryOwner());
        assertTrue(result.getBillingAdmin());
        assertNotNull(result.getStartDate());
        assertTrue(user.getRoles().contains(ownerRole));
        verify(userRepository).save(user);
    }

    @Test
    void rejectProperty_ExpiresPendingMappingWithoutLeavingOwnerRole() {
        User user = user(7L);
        Role ownerRole = new Role();
        ownerRole.setCode("PROPERTY_OWNER");
        user.getRoles().add(ownerRole);
        Hotel hotel = hotel(10L);
        UserProperty mapping = mapping(user, hotel, "PENDING");
        when(hotelRepository.findById(10L)).thenReturn(Optional.of(hotel));
        when(userPropertyRepository.findByHotelIdAndRelationshipTypeAndStatus(10L, "OWNER", "PENDING"))
                .thenReturn(List.of(mapping));
        when(userPropertyRepository.findByUserIdAndRelationshipType(7L, "OWNER"))
                .thenReturn(List.of(mapping));
        when(hotelRepository.save(hotel)).thenReturn(hotel);

        Hotel result = service.rejectProperty(10L);

        assertEquals("INACTIVE", mapping.getStatus());
        assertFalse(mapping.getBillingAdmin());
        assertNotNull(mapping.getEndDate());
        assertFalse(user.getRoles().stream().anyMatch(role -> "PROPERTY_OWNER".equals(role.getCode())));
        assertEquals("REJECTED", result.getApprovalStatus());
        assertEquals("INACTIVE", result.getOperationStatus());
    }

    @Test
    void rejectProperty_DoesNotRelabelAnApprovedPropertyWithActiveOwnership() {
        Hotel hotel = hotel(10L);
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        when(hotelRepository.findById(10L)).thenReturn(Optional.of(hotel));

        assertThrows(IllegalStateException.class, () -> service.rejectProperty(10L));

        assertEquals("APPROVED", hotel.getApprovalStatus());
        assertEquals("ACTIVE", hotel.getOperationStatus());
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setRoles(new HashSet<>());
        return user;
    }

    private Hotel hotel(Long id) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        hotel.setApprovalStatus("PENDING_APPROVAL");
        hotel.setOperationStatus("INACTIVE");
        return hotel;
    }

    private UserProperty mapping(User user, Hotel hotel, String status) {
        UserProperty mapping = new UserProperty();
        mapping.setUser(user);
        mapping.setHotel(hotel);
        mapping.setRelationshipType("OWNER");
        mapping.setStatus(status);
        return mapping;
    }
}
