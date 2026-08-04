package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.Role;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
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
        assertNotNull(result.getStartDate());
        assertTrue(user.getRoles().contains(ownerRole));
        verify(userRepository).save(user);
    }

    @Test
    void deactivatePendingOwnerExpiresMappingWithoutLeavingOwnerRole() {
        User user = user(7L);
        Role ownerRole = new Role();
        ownerRole.setCode("PROPERTY_OWNER");
        user.getRoles().add(ownerRole);
        Hotel hotel = hotel(10L);
        UserProperty mapping = mapping(user, hotel, "PENDING");
        when(userPropertyRepository.findByUserIdAndHotelIdAndRelationshipType(7L, 10L, "OWNER"))
                .thenReturn(Optional.of(mapping));
        when(userPropertyRepository.findByUserIdAndRelationshipType(7L, "OWNER"))
                .thenReturn(java.util.List.of(mapping));

        boolean changed = service.deactivatePendingOwner(10L, 7L);

        assertTrue(changed);
        assertEquals("INACTIVE", mapping.getStatus());
        assertNotNull(mapping.getEndDate());
        assertFalse(user.getRoles().stream().anyMatch(role -> "PROPERTY_OWNER".equals(role.getCode())));
    }

    @Test
    void deactivatePendingOwnerDoesNotChangeActiveOwnership() {
        User user = user(7L);
        Hotel hotel = hotel(10L);
        UserProperty mapping = mapping(user, hotel, "ACTIVE");
        when(userPropertyRepository.findByUserIdAndHotelIdAndRelationshipType(7L, 10L, "OWNER"))
                .thenReturn(Optional.of(mapping));

        boolean changed = service.deactivatePendingOwner(10L, 7L);

        assertFalse(changed);
        assertEquals("ACTIVE", mapping.getStatus());
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
