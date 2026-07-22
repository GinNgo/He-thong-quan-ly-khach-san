package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.Role;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.RoleRepository;
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

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private HotelRepository hotelRepository;

    @Mock
    private PropertyAccessService propertyAccessService;

    @Mock
    private UserPropertyRepository userPropertyRepository;

    @Mock
    private SubscriptionFeatureService subscriptionFeatureService;

    @InjectMocks
    private UserService userService;

    private User owner;
    private User staff;
    private Hotel hotel;
    private Role receptionist;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);

        staff = new User();
        staff.setId(2L);
        staff.setUsername("staff1");
        staff.setEmail("staff1@example.com");
        staff.setPasswordHash("secret");
        staff.setStatus("ACTIVE");

        hotel = new Hotel();
        hotel.setId(10L);
        hotel.setName("Hotel A");

        receptionist = new Role();
        receptionist.setId(3L);
        receptionist.setCode("RECEPTIONIST");
        receptionist.setName("Lễ tân");
    }

    @Test
    void createUser_AsPropertyOwner_ChecksQuotaAndCreatesStaffMapping() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(roleRepository.findAllById(Set.of(3L))).thenReturn(List.of(receptionist));
        when(propertyAccessService.requireManagedHotel(10L)).thenReturn(hotel);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(10L));
        when(userPropertyRepository.countActiveStaffByHotelIds(Set.of(10L))).thenReturn(4L);
        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(passwordEncoder.encode("secret")).thenReturn("encoded");
        when(userRepository.save(staff)).thenReturn(staff);

        userService.createUser(staff, Set.of(3L), 10L);

        verify(subscriptionFeatureService).checkFeatureLimit(1L, "MAX_STAFF", 4);
        ArgumentCaptor<UserProperty> mapping = ArgumentCaptor.forClass(UserProperty.class);
        verify(userPropertyRepository).save(mapping.capture());
        assertSame(staff, mapping.getValue().getUser());
        assertSame(hotel, mapping.getValue().getHotel());
        assertEquals("STAFF", mapping.getValue().getRelationshipType());
        assertEquals("ACTIVE", mapping.getValue().getStatus());
        assertEquals("encoded", staff.getPasswordHash());
    }

    @Test
    void createUser_AsPropertyOwner_RejectsHotelOutsideScope() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(roleRepository.findAllById(Set.of(3L))).thenReturn(List.of(receptionist));
        when(propertyAccessService.requireManagedHotel(99L))
                .thenThrow(new SecurityException("Bạn không có quyền quản lý cơ sở này."));

        assertThrows(SecurityException.class, () -> userService.createUser(staff, Set.of(3L), 99L));

        verify(userRepository, never()).save(any());
        verify(userPropertyRepository, never()).save(any());
    }

    @Test
    void createUser_AsPropertyOwner_RejectsPrivilegedRole() {
        Role admin = new Role();
        admin.setId(1L);
        admin.setCode("ADMIN");
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(roleRepository.findAllById(Set.of(1L))).thenReturn(List.of(admin));

        assertThrows(SecurityException.class, () -> userService.createUser(staff, Set.of(1L), 10L));

        verify(propertyAccessService, never()).requireManagedHotel(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_WhenQuotaExceeded_DoesNotPersistAnything() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(roleRepository.findAllById(Set.of(3L))).thenReturn(List.of(receptionist));
        when(propertyAccessService.requireManagedHotel(10L)).thenReturn(hotel);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(10L));
        when(userPropertyRepository.countActiveStaffByHotelIds(Set.of(10L))).thenReturn(10L);
        when(propertyAccessService.currentUser()).thenReturn(owner);
        doThrow(new RuntimeException("Bạn đã đạt giới hạn của gói dịch vụ."))
                .when(subscriptionFeatureService).checkFeatureLimit(1L, "MAX_STAFF", 10);

        assertThrows(RuntimeException.class, () -> userService.createUser(staff, Set.of(3L), 10L));

        verify(userRepository, never()).save(any());
        verify(userPropertyRepository, never()).save(any());
    }

    @Test
    void getAllUsers_AsPropertyOwner_ReturnsOnlyAccessibleUsers() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(10L));
        when(userRepository.findAccessibleUsers(Set.of(10L))).thenReturn(List.of(staff));

        assertEquals(List.of(2L), userService.getAllUsers().stream().map(dto -> dto.getId()).toList());

        verify(userRepository).findAccessibleUsers(Set.of(10L));
        verify(userRepository, never()).findAll();
    }

    @Test
    void updateUser_AsPropertyOwner_MovesActiveStaffMapping() {
        Hotel newHotel = new Hotel();
        newHotel.setId(11L);
        UserProperty oldMapping = new UserProperty();
        oldMapping.setUser(staff);
        oldMapping.setHotel(hotel);
        oldMapping.setRelationshipType("STAFF");
        oldMapping.setStatus("ACTIVE");
        User details = new User();
        details.setFullName("Nhân viên mới");
        details.setStatus("ACTIVE");

        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(userRepository.findById(2L)).thenReturn(java.util.Optional.of(staff));
        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(10L, 11L));
        when(userRepository.isUserAccessible(2L, Set.of(10L, 11L))).thenReturn(true);
        when(roleRepository.findAllById(Set.of(3L))).thenReturn(List.of(receptionist));
        when(propertyAccessService.requireManagedHotel(11L)).thenReturn(newHotel);
        when(userRepository.save(staff)).thenReturn(staff);
        when(userPropertyRepository.findByUserIdAndRelationshipType(2L, "STAFF"))
                .thenReturn(List.of(oldMapping));

        userService.updateUser(2L, details, Set.of(3L), 11L);

        assertEquals("INACTIVE", oldMapping.getStatus());
        ArgumentCaptor<UserProperty> mapping = ArgumentCaptor.forClass(UserProperty.class);
        verify(userPropertyRepository).save(mapping.capture());
        assertSame(newHotel, mapping.getValue().getHotel());
        assertEquals("ACTIVE", mapping.getValue().getStatus());
    }

    @Test
    void deleteUser_AsPropertyOwner_DeletesMappingsWithinScope() {
        UserProperty mapping = new UserProperty();
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(userRepository.findById(2L)).thenReturn(java.util.Optional.of(staff));
        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(10L));
        when(userRepository.isUserAccessible(2L, Set.of(10L))).thenReturn(true);
        when(userPropertyRepository.findByUserId(2L)).thenReturn(List.of(mapping));

        userService.deleteUser(2L);

        verify(userPropertyRepository).deleteAll(List.of(mapping));
        verify(userRepository).delete(staff);
    }

    @Test
    void deleteUser_AsPropertyOwner_RejectsUserOutsideScope() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(userRepository.findById(2L)).thenReturn(java.util.Optional.of(staff));
        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(10L));
        when(userRepository.isUserAccessible(2L, Set.of(10L))).thenReturn(false);

        assertThrows(SecurityException.class, () -> userService.deleteUser(2L));

        verify(userRepository, never()).delete(any());
        verify(userPropertyRepository, never()).deleteAll(any());
    }
}
