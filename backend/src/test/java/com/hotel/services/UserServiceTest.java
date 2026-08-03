package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.Role;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.dtos.StaffLifecycleRequest;
import com.hotel.dtos.StaffUpdateRequest;
import com.hotel.security.AccountDisabledAuthenticationException;
import com.hotel.security.AccountStatusPolicy;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
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

    @Mock
    private PropertySubscriptionEntitlementService propertyEntitlementService;

    @Mock
    private AuthSessionRevocationService authSessionRevocationService;

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
        staff.setPasswordHash("StrongPass1");
        staff.setFullName("Staff One");
        staff.setStatus("ACTIVE");

        hotel = new Hotel();
        hotel.setId(10L);
        hotel.setName("Hotel A");

        receptionist = new Role();
        receptionist.setId(3L);
        receptionist.setCode("RECEPTIONIST");
        receptionist.setName("Lễ tân");
        receptionist.setStatus("ACTIVE");
    }

    @Test
    void createStaff_AsPropertyOwner_ChecksQuotaAndCreatesStaffMapping() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(roleRepository.findAllById(Set.of(3L))).thenReturn(List.of(receptionist));
        when(propertyAccessService.requireManagedHotel(10L)).thenReturn(hotel);
        when(propertyEntitlementService.getCurrentForUpdate(10L)).thenReturn(
                PropertySubscriptionEntitlementService.EntitlementView.none(10L, "TEST"));
        when(userPropertyRepository.countActiveStaffByHotelId(10L)).thenReturn(4L);
        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(passwordEncoder.encode("StrongPass1")).thenReturn("encoded");
        when(userRepository.saveAndFlush(staff)).thenReturn(staff);

        userService.createStaffAccount(staff, Set.of(3L), 10L);

        verify(subscriptionFeatureService).checkFeatureLimitForProperty(10L, "MAX_STAFF", 4L, 1L);
        InOrder quotaOrder = inOrder(propertyEntitlementService, userPropertyRepository, subscriptionFeatureService, userRepository);
        quotaOrder.verify(propertyEntitlementService).getCurrentForUpdate(10L);
        quotaOrder.verify(userPropertyRepository).countActiveStaffByHotelId(10L);
        quotaOrder.verify(subscriptionFeatureService).checkFeatureLimitForProperty(10L, "MAX_STAFF", 4L, 1L);
        quotaOrder.verify(userRepository).saveAndFlush(staff);
        ArgumentCaptor<UserProperty> mapping = ArgumentCaptor.forClass(UserProperty.class);
        verify(userPropertyRepository).save(mapping.capture());
        assertSame(staff, mapping.getValue().getUser());
        assertSame(hotel, mapping.getValue().getHotel());
        assertEquals("STAFF", mapping.getValue().getRelationshipType());
        assertEquals("ACTIVE", mapping.getValue().getStatus());
        assertEquals("encoded", staff.getPasswordHash());
    }

    @Test
    void createStaff_WithMultipleAccessibleProperties_UsesOnlyTargetPropertyQuota() {
        Hotel target = new Hotel();
        target.setId(11L);
        target.setName("Hotel B");
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(roleRepository.findAllById(Set.of(3L))).thenReturn(List.of(receptionist));
        when(propertyAccessService.requireManagedHotel(11L)).thenReturn(target);
        when(propertyEntitlementService.getCurrentForUpdate(11L)).thenReturn(
                PropertySubscriptionEntitlementService.EntitlementView.none(11L, "TEST"));
        when(userPropertyRepository.countActiveStaffByHotelId(11L)).thenReturn(2L);
        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(passwordEncoder.encode("StrongPass1")).thenReturn("encoded");
        when(userRepository.saveAndFlush(staff)).thenReturn(staff);

        userService.createStaffAccount(staff, Set.of(3L), 11L);

        verify(subscriptionFeatureService).checkFeatureLimitForProperty(11L, "MAX_STAFF", 2L, 1L);
        verify(userPropertyRepository, never()).countActiveStaffByHotelIds(Set.of(10L, 11L));
    }

    @Test
    void createStaff_AsPropertyOwner_RejectsHotelOutsideScope() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(roleRepository.findAllById(Set.of(3L))).thenReturn(List.of(receptionist));
        when(propertyAccessService.requireManagedHotel(99L))
                .thenThrow(new SecurityException("Bạn không có quyền quản lý cơ sở này."));

        assertThrows(SecurityException.class, () -> userService.createStaffAccount(staff, Set.of(3L), 99L));

        verify(userRepository, never()).saveAndFlush(any());
        verify(userPropertyRepository, never()).save(any());
    }

    @Test
    void createStaff_AsPropertyOwner_RejectsPrivilegedRole() {
        Role admin = new Role();
        admin.setId(1L);
        admin.setCode("ADMIN");
        admin.setStatus("ACTIVE");
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(roleRepository.findAllById(Set.of(1L))).thenReturn(List.of(admin));

        assertThrows(SecurityException.class, () -> userService.createStaffAccount(staff, Set.of(1L), 10L));

        verify(propertyAccessService, never()).requireManagedHotel(any());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void createStaff_WhenQuotaExceeded_DoesNotPersistAnything() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(roleRepository.findAllById(Set.of(3L))).thenReturn(List.of(receptionist));
        when(propertyAccessService.requireManagedHotel(10L)).thenReturn(hotel);
        when(propertyEntitlementService.getCurrentForUpdate(10L)).thenReturn(
                PropertySubscriptionEntitlementService.EntitlementView.none(10L, "TEST"));
        when(userPropertyRepository.countActiveStaffByHotelId(10L)).thenReturn(10L);
        doThrow(new RuntimeException("Bạn đã đạt giới hạn của gói dịch vụ."))
                .when(subscriptionFeatureService).checkFeatureLimitForProperty(10L, "MAX_STAFF", 10L, 1L);

        assertThrows(RuntimeException.class, () -> userService.createStaffAccount(staff, Set.of(3L), 10L));

        verify(userRepository, never()).saveAndFlush(any());
        verify(userPropertyRepository, never()).save(any());
    }

    @Test
    void createStaff_NormalizesIdentifiersBeforePersistence() {
        staff.setUsername("Ｓtaff.One");
        staff.setEmail("Staff.One@EXAMPLE.COM");
        staff.setFullName("  Staff   One  ");
        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
        when(roleRepository.findAllById(Set.of(3L))).thenReturn(List.of(receptionist));
        when(hotelRepository.findById(10L)).thenReturn(java.util.Optional.of(hotel));
        when(passwordEncoder.encode("StrongPass1")).thenReturn("encoded");
        when(userRepository.saveAndFlush(staff)).thenReturn(staff);

        userService.createStaffAccount(staff, Set.of(3L), 10L);

        assertEquals("staff.one", staff.getUsername());
        assertEquals("staff.one@example.com", staff.getEmail());
        assertEquals("Staff One", staff.getFullName());
        assertEquals("encoded", staff.getPasswordHash());
    }

    @Test
    void getAssignableStaffRoles_ExcludesInactiveAndNonStaffRoles() {
        Role inactive = new Role();
        inactive.setId(4L);
        inactive.setCode("HOUSEKEEPING");
        inactive.setName("Housekeeping");
        inactive.setStatus("INACTIVE");
        Role customer = new Role();
        customer.setId(5L);
        customer.setCode("CUSTOMER");
        customer.setName("Customer");
        customer.setStatus("ACTIVE");
        when(roleRepository.findAll()).thenReturn(List.of(customer, inactive, receptionist));

        assertEquals(List.of(3L), userService.getAssignableStaffRoles().stream().map(role -> role.id()).toList());
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
    void updateStaff_AsPropertyOwner_MovesAssignmentAndPreservesHistory() {
        Hotel newHotel = new Hotel();
        newHotel.setId(11L);
        newHotel.setName("Hotel B");
        UserProperty oldMapping = new UserProperty();
        oldMapping.setId(20L);
        oldMapping.setUser(staff);
        oldMapping.setHotel(hotel);
        oldMapping.setRelationshipType("STAFF");
        oldMapping.setStatus("ACTIVE");
        StaffUpdateRequest request = staffUpdateRequest(11L, "Transfer to Hotel B");

        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(userRepository.findByIdForUpdate(2L)).thenReturn(java.util.Optional.of(staff));
        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(10L, 11L));
        when(userRepository.isUserAccessible(2L, Set.of(10L, 11L))).thenReturn(true);
        when(roleRepository.findAllById(Set.of(3L))).thenReturn(List.of(receptionist));
        when(propertyAccessService.requireManagedHotel(11L)).thenReturn(newHotel);
        when(userPropertyRepository.findStaffAssignmentsForUpdate(2L))
                .thenReturn(List.of(oldMapping));
        when(propertyEntitlementService.getCurrentForUpdate(11L)).thenReturn(
                PropertySubscriptionEntitlementService.EntitlementView.none(11L, "TEST"));
        when(userPropertyRepository.countActiveStaffByHotelId(11L)).thenReturn(2L);
        when(userRepository.saveAndFlush(staff)).thenReturn(staff);

        userService.updateStaff(2L, request);

        assertEquals("INACTIVE", oldMapping.getStatus());
        assertEquals("Transfer to Hotel B", oldMapping.getStatusReason());
        assertSame(owner, oldMapping.getStatusChangedBy());
        assertSame(newHotel, staff.getHotel());
        ArgumentCaptor<UserProperty> mapping = ArgumentCaptor.forClass(UserProperty.class);
        verify(userPropertyRepository).saveAndFlush(mapping.capture());
        assertEquals("ACTIVE", mapping.getValue().getStatus());
        assertSame(newHotel, mapping.getValue().getHotel());
        assertEquals("Transfer to Hotel B", mapping.getValue().getStatusReason());
        verify(subscriptionFeatureService).checkFeatureLimitForProperty(11L, "MAX_STAFF", 2L, 1L);
        verify(userRepository).saveAndFlush(staff);
    }

    @Test
    void updateUser_RejectsStaffBypassOfDedicatedEndpoint() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
        when(userRepository.findById(2L)).thenReturn(java.util.Optional.of(staff));
        when(userPropertyRepository.findByUserIdAndRelationshipType(2L, "STAFF"))
                .thenReturn(List.of(activeAssignment(hotel)));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.updateUser(2L, new User(), Set.of(3L), 10L));

        assertEquals("Use the dedicated staff endpoint for staff account updates.", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivateStaff_LastAssignment_SoftLocksAccountAndRetainsHistory() {
        UserProperty mapping = activeAssignment(hotel);
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(userRepository.findById(2L)).thenReturn(java.util.Optional.of(staff));
        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(propertyAccessService.requireManagedHotel(10L)).thenReturn(hotel);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(10L));
        when(userPropertyRepository.findStaffAssignmentsForUpdate(2L)).thenReturn(List.of(mapping));

        userService.deactivateStaff(2L, lifecycleRequest(10L, "End of employment contract"));

        assertEquals("INACTIVE", mapping.getStatus());
        assertEquals("End of employment contract", mapping.getStatusReason());
        assertSame(owner, mapping.getStatusChangedBy());
        assertEquals("INACTIVE", staff.getStatus());
        assertThrows(AccountDisabledAuthenticationException.class, () -> AccountStatusPolicy.requireActive(staff));
        verify(userPropertyRepository).save(mapping);
        verify(userRepository).save(staff);
        verify(userRepository, never()).delete(any());
        verify(userPropertyRepository, never()).deleteAll(any());
    }

    @Test
    void deactivateStaff_WhenAnotherAssignmentRemains_KeepsAccountActive() {
        Hotel otherHotel = new Hotel();
        otherHotel.setId(11L);
        UserProperty target = activeAssignment(hotel);
        UserProperty other = activeAssignment(otherHotel);
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(userRepository.findById(2L)).thenReturn(java.util.Optional.of(staff));
        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(propertyAccessService.requireManagedHotel(10L)).thenReturn(hotel);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(10L));
        when(userPropertyRepository.findStaffAssignmentsForUpdate(2L)).thenReturn(List.of(target, other));
        when(userPropertyRepository.countByUserIdAndStatus(2L, "ACTIVE")).thenReturn(1L);

        userService.deactivateStaff(2L, lifecycleRequest(10L, "Property assignment ended"));

        assertEquals("INACTIVE", target.getStatus());
        assertEquals("ACTIVE", other.getStatus());
        assertEquals("ACTIVE", staff.getStatus());
        verify(userRepository, never()).save(staff);
    }

    @Test
    void reactivateStaff_CreatesNewAssignmentAndPreservesHistoricalPeriod() {
        UserProperty historical = activeAssignment(hotel);
        historical.setStatus("INACTIVE");
        historical.setEndDate(java.time.LocalDateTime.of(2026, 7, 31, 10, 0));
        historical.setStatusReason("Previous contract ended");
        staff.setStatus("INACTIVE");
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(userRepository.findById(2L)).thenReturn(java.util.Optional.of(staff));
        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(propertyAccessService.requireManagedHotel(10L)).thenReturn(hotel);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(10L));
        when(userPropertyRepository.findStaffAssignmentsForUpdate(2L)).thenReturn(List.of(historical));
        when(propertyEntitlementService.getCurrentForUpdate(10L)).thenReturn(
                PropertySubscriptionEntitlementService.EntitlementView.none(10L, "TEST"));
        when(userPropertyRepository.countActiveStaffByHotelId(10L)).thenReturn(3L);
        when(userRepository.save(staff)).thenReturn(staff);

        userService.reactivateStaff(2L, lifecycleRequest(10L, "New seasonal contract"));

        ArgumentCaptor<UserProperty> mapping = ArgumentCaptor.forClass(UserProperty.class);
        verify(userPropertyRepository).save(mapping.capture());
        assertEquals("ACTIVE", mapping.getValue().getStatus());
        assertEquals("New seasonal contract", mapping.getValue().getStatusReason());
        assertSame(owner, mapping.getValue().getStatusChangedBy());
        assertEquals("INACTIVE", historical.getStatus());
        assertEquals("Previous contract ended", historical.getStatusReason());
        assertEquals("ACTIVE", staff.getStatus());
        verify(subscriptionFeatureService).checkFeatureLimitForProperty(10L, "MAX_STAFF", 3L, 1L);
        verify(userRepository, never()).delete(any());
    }

    @Test
    void deactivateStaff_AsPropertyOwner_RejectsAssignmentOutsideScope() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(userRepository.findById(2L)).thenReturn(java.util.Optional.of(staff));
        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(propertyAccessService.requireManagedHotel(99L))
                .thenThrow(new SecurityException("Property access denied"));

        assertThrows(SecurityException.class,
                () -> userService.deactivateStaff(2L, lifecycleRequest(99L, "Invalid scope")));

        verify(userRepository, never()).delete(any());
        verify(userPropertyRepository, never()).deleteAll(any());
    }

    private UserProperty activeAssignment(Hotel assignedHotel) {
        UserProperty mapping = new UserProperty();
        mapping.setUser(staff);
        mapping.setHotel(assignedHotel);
        mapping.setRelationshipType("STAFF");
        mapping.setStatus("ACTIVE");
        mapping.setStartDate(java.time.LocalDateTime.of(2026, 1, 1, 9, 0));
        return mapping;
    }

    private StaffLifecycleRequest lifecycleRequest(Long hotelId, String reason) {
        StaffLifecycleRequest request = new StaffLifecycleRequest();
        request.setHotelId(hotelId);
        request.setReason(reason);
        return request;
    }

    private StaffUpdateRequest staffUpdateRequest(Long hotelId, String reason) {
        StaffUpdateRequest request = new StaffUpdateRequest();
        request.setFullName("Updated Staff");
        request.setPhone("0901000000");
        request.setRoleIds(Set.of(3L));
        request.setHotelId(hotelId);
        request.setAssignmentReason(reason);
        return request;
    }
}
