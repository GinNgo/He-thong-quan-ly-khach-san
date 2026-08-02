package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.User;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.HousekeepingTaskRepository;
import com.hotel.repositories.LocationRepository;
import com.hotel.repositories.PropertyImageRepository;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.RoomImageRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.RoomTypeImageRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ManagementPortalServiceTest {

    @Mock private PropertyAccessService propertyAccessService;
    @Mock private SubscriptionFeatureService subscriptionFeatureService;
    @Mock private PropertySubscriptionEntitlementService propertyEntitlementService;
    @Mock private HotelRepository hotelRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private UserPropertyRepository userPropertyRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoomTypeRepository roomTypeRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private PropertyImageRepository propertyImageRepository;
    @Mock private RoomTypeImageRepository roomTypeImageRepository;
    @Mock private RoomImageRepository roomImageRepository;
    @Mock private HousekeepingTaskRepository housekeepingTaskRepository;
    @Mock private RoomTypeService roomTypeService;
    @Mock private RoomService roomService;

    @InjectMocks
    private ManagementPortalService service;

    @Test
    @SuppressWarnings("unchecked")
    void pendingPropertyRemainsSelectableWithoutOperationalDashboardData() {
        User owner = new User();
        owner.setId(10L);
        owner.setFullName("Owner");
        Hotel pending = new Hotel();
        pending.setId(20L);
        pending.setNameVi("Pending Hotel");
        pending.setApprovalStatus("PENDING_APPROVAL");
        pending.setOperationStatus("INACTIVE");

        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(propertyAccessService.assignedHotelIds()).thenReturn(Set.of(20L));
        when(propertyAccessService.requireAssignedHotel(20L)).thenReturn(pending);
        when(propertyAccessService.isOperational(pending)).thenReturn(false);
        when(hotelRepository.findAllById(Set.of(20L))).thenReturn(List.of(pending));
        when(propertyEntitlementService.getCurrent(20L)).thenReturn(
                PropertySubscriptionEntitlementService.EntitlementView.none(20L, "NO_ENTITLEMENT"));
        lenient().when(userPropertyRepository.countActiveOwnedPropertiesByUserId(10L)).thenReturn(1L);
        when(userPropertyRepository.countActiveStaffByHotelId(20L)).thenReturn(2L);

        Map<String, Object> context = service.context(20L);
        List<Map<String, Object>> properties = (List<Map<String, Object>>) context.get("properties");

        assertEquals(20L, context.get("activePropertyId"));
        @SuppressWarnings("unchecked")
        Map<String, Long> usage = (Map<String, Long>) context.get("usage");
        assertEquals(2L, usage.get("staff"));
        assertEquals(false, context.get("activePropertyOperational"));
        assertFalse((Boolean) properties.getFirst().get("operational"));
        assertNull(context.get("dashboard"));
        verify(propertyAccessService, never()).requireManagedHotel(20L);
    }
}
