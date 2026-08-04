package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.exceptions.PropertyNotOperationalException;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PropertyAccessServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserPropertyRepository userPropertyRepository;
    @Mock
    private HotelRepository hotelRepository;

    @InjectMocks
    private PropertyAccessService service;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(10L);
        owner.setUsername("owner");
        authenticate("owner", "ROLE_PROPERTY_OWNER");
        lenient().when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assignedScopeKeepsDraftAndPendingPropertiesWhileOperationalScopeOnlyKeepsApprovedActive() {
        Hotel draft = hotel(1L, "DRAFT", "INACTIVE");
        Hotel pending = hotel(2L, "PENDING_APPROVAL", "INACTIVE");
        Hotel rejected = hotel(3L, "REJECTED", "INACTIVE");
        Hotel suspended = hotel(4L, "APPROVED", "SUSPENDED");
        Hotel operational = hotel(5L, " approved ", " active ");
        when(userPropertyRepository.findByUserId(10L)).thenReturn(List.of(
                assignment(draft, "ACTIVE"),
                assignment(pending, "ACTIVE"),
                assignment(rejected, "ACTIVE"),
                assignment(suspended, "ACTIVE"),
                assignment(operational, "ACTIVE"),
                assignment(hotel(6L, "APPROVED", "ACTIVE"), "SUSPENDED")));
        when(hotelRepository.findAllById(Set.of(1L, 2L, 3L, 4L, 5L)))
                .thenReturn(List.of(draft, pending, rejected, suspended, operational));

        assertEquals(Set.of(1L, 2L, 3L, 4L, 5L), service.assignedHotelIds());
        assertEquals(Set.of(5L), service.accessibleHotelIds());
    }

    @Test
    void draftPropertyAllowsSetupAccessButRejectsOperationalAccessWithStableState() {
        Hotel draft = hotel(20L, "DRAFT", "INACTIVE");
        when(hotelRepository.findById(20L)).thenReturn(Optional.of(draft));
        when(userPropertyRepository.findByUserId(10L)).thenReturn(List.of(assignment(draft, "ACTIVE")));

        assertEquals(draft, service.requireAssignedHotel(20L));
        PropertyNotOperationalException exception = assertThrows(
                PropertyNotOperationalException.class,
                () -> service.requireManagedHotel(20L));

        assertEquals("approval=DRAFT;operation=INACTIVE", exception.currentState());
    }

    @Test
    void rejectedAndSuspendedPropertiesCannotReachOperationalApis() {
        Hotel rejected = hotel(21L, "REJECTED", "INACTIVE");
        Hotel suspended = hotel(22L, "APPROVED", "SUSPENDED");
        when(userPropertyRepository.findByUserId(10L)).thenReturn(List.of(
                assignment(rejected, "ACTIVE"), assignment(suspended, "ACTIVE")));
        when(hotelRepository.findById(21L)).thenReturn(Optional.of(rejected));
        when(hotelRepository.findById(22L)).thenReturn(Optional.of(suspended));

        assertThrows(PropertyNotOperationalException.class, () -> service.requireManagedHotel(21L));
        assertThrows(PropertyNotOperationalException.class, () -> service.requireManagedHotel(22L));
    }

    @Test
    void approvedActivePropertyCanReachOperationalApis() {
        Hotel operational = hotel(23L, "APPROVED", "ACTIVE");
        when(hotelRepository.findById(23L)).thenReturn(Optional.of(operational));
        when(userPropertyRepository.findByUserId(10L)).thenReturn(List.of(assignment(operational, "ACTIVE")));

        assertEquals(operational, service.requireManagedHotel(23L));
        assertTrue(service.isOperational(operational));
    }

    @Test
    void inconsistentLegacyStatusCannotReachOperationalApis() {
        Hotel inconsistent = hotel(24L, "APPROVED", "ACTIVE");
        inconsistent.setStatus("PENDING_APPROVAL");
        when(hotelRepository.findById(24L)).thenReturn(Optional.of(inconsistent));
        when(userPropertyRepository.findByUserId(10L))
                .thenReturn(List.of(assignment(inconsistent, "ACTIVE")));

        assertThrows(PropertyNotOperationalException.class,
                () -> service.requireManagedHotel(24L));
    }

    @Test
    void crossTenantPropertyRemainsHidden() {
        Hotel otherTenant = hotel(99L, "APPROVED", "ACTIVE");
        when(hotelRepository.findById(99L)).thenReturn(Optional.of(otherTenant));
        when(userPropertyRepository.findByUserId(10L)).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> service.requireAssignedHotel(99L));
        assertThrows(ResourceNotFoundException.class, () -> service.requireManagedHotel(99L));
    }

    @Test
    void systemAdministratorStillCannotOperateSuspendedProperty() {
        Hotel suspended = hotel(30L, "APPROVED", "SUSPENDED");
        authenticate("owner", "ROLE_SUPER_ADMIN");
        when(hotelRepository.findById(30L)).thenReturn(Optional.of(suspended));

        assertEquals(suspended, service.requireAssignedHotel(30L));
        assertThrows(PropertyNotOperationalException.class, () -> service.requireManagedHotel(30L));
    }

    private void authenticate(String username, String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(username, "password", authority));
    }

    private Hotel hotel(Long id, String approvalStatus, String operationStatus) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        hotel.setStatus("APPROVED".equalsIgnoreCase(approvalStatus.trim())
                && "ACTIVE".equalsIgnoreCase(operationStatus.trim()) ? "ACTIVE" : approvalStatus.trim());
        hotel.setApprovalStatus(approvalStatus);
        hotel.setOperationStatus(operationStatus);
        return hotel;
    }

    private UserProperty assignment(Hotel hotel, String status) {
        UserProperty assignment = new UserProperty();
        assignment.setUser(owner);
        assignment.setHotel(hotel);
        assignment.setStatus(status);
        return assignment;
    }
}
