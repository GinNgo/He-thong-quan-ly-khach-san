package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.HousekeepingTask;
import com.hotel.entities.Location;
import com.hotel.entities.Room;
import com.hotel.entities.User;
import com.hotel.dtos.ManagementPropertyRequest;
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
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @Mock private PropertyProfileMapper propertyProfileMapper;

    @InjectMocks
    private ManagementPortalService service;

    @Test
    @SuppressWarnings("unchecked")
    void selectedPropertyCountsAndEntitlementReconcileIndependently() {
        User owner = new User();
        owner.setId(10L);
        owner.setFullName("Owner");
        Hotel first = operationalHotel(20L, "First");
        Hotel second = operationalHotel(21L, "Second");

        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(propertyAccessService.assignedHotelIds()).thenReturn(Set.of(20L, 21L));
        when(hotelRepository.findAllById(Set.of(20L, 21L))).thenReturn(List.of(first, second));
        when(propertyAccessService.requireAssignedHotel(21L)).thenReturn(second);
        when(propertyAccessService.isOperational(first)).thenReturn(true);
        when(propertyAccessService.isOperational(second)).thenReturn(true);
        when(propertyEntitlementService.getCurrent(21L)).thenReturn(new PropertySubscriptionEntitlementService.EntitlementView(
                21L, "PLATFORM", true, 4L, "STANDARD", "Standard", "ACTIVE",
                null, null, false, Map.of("MAX_PROPERTIES", 3, "MAX_ROOMS", 40, "MAX_STAFF", 10),
                "CONTRACT:88", null));
        when(userPropertyRepository.countActiveOwnedPropertiesByUserId(10L)).thenReturn(2L);
        when(roomTypeRepository.countByHotelId(21L)).thenReturn(3L);
        when(roomRepository.countByHotelId(21L)).thenReturn(8L);
        when(userPropertyRepository.countActiveStaffByHotelId(21L)).thenReturn(5L);
        when(roomRepository.countByHotelIdAndStatus(21L, "AVAILABLE")).thenReturn(3L);
        when(roomRepository.countByHotelIdAndStatus(21L, "RESERVED")).thenReturn(1L);
        when(roomRepository.countByHotelIdAndStatus(21L, "OCCUPIED")).thenReturn(2L);
        when(roomRepository.countByHotelIdAndStatus(21L, "MAINTENANCE")).thenReturn(1L);
        when(roomRepository.countByHotelIdAndHousekeepingStatus(21L, "DIRTY")).thenReturn(2L);
        when(housekeepingTaskRepository.countByHotelIdAndStatus(21L, "PENDING")).thenReturn(1L);

        Map<String, Object> context = service.context(21L);
        Map<String, Long> usage = (Map<String, Long>) context.get("usage");
        Map<String, Object> dashboard = (Map<String, Object>) context.get("dashboard");

        assertEquals(21L, context.get("activePropertyId"));
        assertEquals("SELECTED_PROPERTY", context.get("scope"));
        assertEquals("PLATFORM", context.get("subscriptionSource"));
        assertEquals(true, context.get("entitlementAuthoritative"));
        assertEquals("CONTRACT:88", context.get("entitlementReference"));
        assertEquals(2L, usage.get("properties"));
        assertEquals(8L, usage.get("rooms"));
        assertEquals(5L, usage.get("staff"));
        assertEquals(7L, dashboard.get("classifiedRooms"));
        assertEquals(1L, dashboard.get("unclassifiedRooms"));
        assertEquals("RECONCILED", dashboard.get("reconciliationStatus"));
        verify(propertyEntitlementService).getCurrent(21L);
        verify(roomRepository, times(2)).countByHotelId(21L);
        verify(roomRepository, never()).countByHotelId(20L);
    }

    @Test
    void foreignPropertyIsRejectedBeforeEntitlementOrOperationalQueries() {
        User owner = new User();
        owner.setId(10L);
        owner.setFullName("Owner");
        Hotel assigned = operationalHotel(20L, "Assigned");

        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(propertyAccessService.assignedHotelIds()).thenReturn(Set.of(20L));
        when(hotelRepository.findAllById(Set.of(20L))).thenReturn(List.of(assigned));
        when(propertyAccessService.requireAssignedHotel(999L))
                .thenThrow(new com.hotel.exceptions.ResourceNotFoundException("Property not found"));

        assertThrows(com.hotel.exceptions.ResourceNotFoundException.class, () -> service.context(999L));

        verify(propertyEntitlementService, never()).getCurrent(999L);
        verify(roomRepository, never()).countByHotelId(999L);
        verify(userPropertyRepository, never()).countActiveStaffByHotelId(999L);
        verify(housekeepingTaskRepository, never()).countByHotelIdAndStatus(999L, "PENDING");
    }

    @Test
    void profileUpdateUsesAllowlistedFieldsAndPreservesControlledState() {
        Hotel hotel = operationalHotel(20L, "Old name");
        hotel.setStatus("ACTIVE");
        hotel.setDataSource("USER");
        Location province = new Location();
        province.setId(1L);
        province.setLocationType("PROVINCE");
        province.setNameVi("Ha Noi");
        Location ward = new Location();
        ward.setId(2L);
        ward.setLocationType("WARD");
        ward.setParent(province);
        ManagementPropertyRequest request = new ManagementPropertyRequest();
        request.setNameVi("  New name  ");
        request.setPropertyType("HOTEL");
        request.setProvinceId(1L);
        request.setWardId(2L);
        request.setAddress("  New address  ");
        request.setEmail(" owner@example.test ");

        when(propertyAccessService.requireAssignedHotel(20L)).thenReturn(hotel);
        when(propertyAccessService.isOperational(hotel)).thenReturn(true);
        when(locationRepository.findById(1L)).thenReturn(Optional.of(province));
        when(locationRepository.findById(2L)).thenReturn(Optional.of(ward));
        when(hotelRepository.saveAndFlush(hotel)).thenReturn(hotel);

        Map<String, Object> result = service.updateProperty(20L, request);

        assertEquals("New name", result.get("nameVi"));
        assertEquals("New address", result.get("address"));
        assertEquals("owner@example.test", result.get("email"));
        assertEquals("APPROVED", result.get("approvalStatus"));
        assertEquals("ACTIVE", result.get("operationStatus"));
        assertEquals("ACTIVE", hotel.getStatus());
        assertEquals(false, hotel.getIsDemo());
        verify(hotelRepository).saveAndFlush(hotel);
    }

    @Test
    void foreignProfileUpdateIsRejectedBeforeLocationLookupOrSave() {
        ManagementPropertyRequest request = new ManagementPropertyRequest();
        request.setNameVi("Foreign");
        request.setProvinceId(1L);
        request.setWardId(2L);
        request.setAddress("Hidden");
        when(propertyAccessService.requireAssignedHotel(999L))
                .thenThrow(new com.hotel.exceptions.ResourceNotFoundException("Property not found"));

        assertThrows(com.hotel.exceptions.ResourceNotFoundException.class, () -> service.updateProperty(999L, request));

        verify(locationRepository, never()).findById(1L);
        verify(hotelRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

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
        List<com.hotel.dtos.PropertyProfileDTO> properties =
                (List<com.hotel.dtos.PropertyProfileDTO>) context.get("properties");

        assertEquals(20L, context.get("activePropertyId"));
        @SuppressWarnings("unchecked")
        Map<String, Long> usage = (Map<String, Long>) context.get("usage");
        assertEquals(2L, usage.get("staff"));
        assertEquals(false, context.get("activePropertyOperational"));
        assertFalse(properties.getFirst().isOperational());
        assertNull(context.get("dashboard"));
        assertNotNull(context.get("generatedAt"));
        assertEquals("COMPLETE", context.get("dataStatus"));
        assertEquals(List.of(), context.get("errors"));
        assertEquals("PROPERTY", context.get("usageScope"));
        verify(propertyAccessService, never()).requireManagedHotel(20L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dashboardReconcilesOnePropertySnapshotAndUsesItsAuthoritativeEntitlement() {
        User owner = new User(); owner.setId(10L); owner.setFullName("Owner");
        Hotel hotel = hotel(20L, "Hotel A");
        Room available = room(1L, hotel, "AVAILABLE", "CLEAN");
        Room occupied = room(2L, hotel, "OCCUPIED", "DIRTY");
        Room cleaning = room(3L, hotel, "CLEANING", "DIRTY");
        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(propertyAccessService.assignedHotelIds()).thenReturn(Set.of(20L));
        when(propertyAccessService.requireAssignedHotel(20L)).thenReturn(hotel);
        when(propertyAccessService.isOperational(hotel)).thenReturn(true);
        when(hotelRepository.findAllById(Set.of(20L))).thenReturn(List.of(hotel));
        when(propertyEntitlementService.getCurrent(20L)).thenReturn(entitlement(20L, "PLAN-A", 25));
        when(roomRepository.findByHotelId(20L)).thenReturn(List.of(available, occupied, cleaning));
        when(roomTypeRepository.countByHotelId(20L)).thenReturn(2L);
        when(housekeepingTaskRepository.countByHotelIdAndStatus(20L, "PENDING")).thenReturn(1L);

        Map<String, Object> context = service.context(20L);
        Map<String, Long> usage = (Map<String, Long>) context.get("usage");
        Map<String, Object> dashboard = (Map<String, Object>) context.get("dashboard");

        assertEquals("PLAN-A", context.get("planCode"));
        assertEquals(1L, usage.get("properties"));
        assertEquals(3L, usage.get("rooms"));
        assertEquals(3L, dashboard.get("totalRooms"));
        assertEquals(2L, dashboard.get("classifiedRooms"));
        assertEquals(1L, dashboard.get("unclassifiedRooms"));
        assertEquals(3L, dashboard.get("statusCountTotal"));
        assertEquals(2L, dashboard.get("dirtyRooms"));
        assertTrue((Boolean) dashboard.get("reconciled"));
        verify(roomRepository, never()).countByHotelId(20L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void switchingPropertyDoesNotLeakEntitlementOrUsageCounts() {
        User owner = new User(); owner.setId(10L); owner.setFullName("Owner");
        Hotel first = hotel(20L, "Hotel A");
        Hotel second = hotel(30L, "Hotel B");
        Set<Long> assigned = Set.of(20L, 30L);
        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(propertyAccessService.assignedHotelIds()).thenReturn(assigned);
        when(hotelRepository.findAllById(assigned)).thenReturn(List.of(first, second));
        when(propertyAccessService.requireAssignedHotel(20L)).thenReturn(first);
        when(propertyAccessService.requireAssignedHotel(30L)).thenReturn(second);
        when(propertyAccessService.isOperational(first)).thenReturn(true);
        when(propertyAccessService.isOperational(second)).thenReturn(true);
        when(propertyEntitlementService.getCurrent(20L)).thenReturn(entitlement(20L, "PLAN-A", 10));
        when(propertyEntitlementService.getCurrent(30L)).thenReturn(entitlement(30L, "PLAN-B", 100));
        when(roomRepository.findByHotelId(20L)).thenReturn(List.of(room(1L, first, "AVAILABLE", "CLEAN")));
        when(roomRepository.findByHotelId(30L)).thenReturn(List.of(
                room(2L, second, "AVAILABLE", "CLEAN"), room(3L, second, "RESERVED", "CLEAN")));

        Map<String, Object> firstContext = service.context(20L);
        Map<String, Object> secondContext = service.context(30L);

        assertEquals("PLAN-A", firstContext.get("planCode"));
        assertEquals(10, ((Map<String, Integer>) firstContext.get("limits")).get("MAX_ROOMS"));
        assertEquals(1L, ((Map<String, Long>) firstContext.get("usage")).get("rooms"));
        assertEquals("PLAN-B", secondContext.get("planCode"));
        assertEquals(100, ((Map<String, Integer>) secondContext.get("limits")).get("MAX_ROOMS"));
        assertEquals(2L, ((Map<String, Long>) secondContext.get("usage")).get("rooms"));
        verify(propertyEntitlementService).getCurrent(20L);
        verify(propertyEntitlementService).getCurrent(30L);
    }

    @Test
    void housekeepingCompletionLocksRoomAndUsesAuthoritativeTransition() {
        Hotel hotel = new Hotel();
        hotel.setId(20L);
        Room room = new Room();
        room.setId(30L);
        room.setHotel(hotel);
        room.setStatus("DIRTY");
        room.setHousekeepingStatus("DIRTY");
        room.setMaintenanceStatus("NONE");
        HousekeepingTask task = new HousekeepingTask();
        task.setId(40L);
        task.setHotel(hotel);
        task.setRoom(room);
        task.setStatus("PENDING");

        when(housekeepingTaskRepository.findById(40L)).thenReturn(Optional.of(task));
        when(roomRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(room));

        Map<String, Object> result = service.completeHousekeeping(40L);

        assertEquals("AVAILABLE", result.get("roomStatus"));
        assertEquals("CLEAN", result.get("housekeepingStatus"));
        verify(roomRepository).findByIdForUpdate(30L);
        verify(roomRepository).save(room);
        verify(housekeepingTaskRepository).save(task);
    }

    private Hotel hotel(Long id, String name) {
        Hotel hotel = new Hotel(); hotel.setId(id); hotel.setNameVi(name);
        hotel.setApprovalStatus("APPROVED"); hotel.setOperationStatus("ACTIVE"); return hotel;
    }

    private Room room(Long id, Hotel hotel, String status, String housekeepingStatus) {
        Room room = new Room(); room.setId(id); room.setHotel(hotel); room.setStatus(status);
        room.setHousekeepingStatus(housekeepingStatus); return room;
    }

    private PropertySubscriptionEntitlementService.EntitlementView entitlement(
            Long hotelId, String planCode, int maxRooms) {
        return new PropertySubscriptionEntitlementService.EntitlementView(
                hotelId, "PLATFORM", true, hotelId, planCode, planCode, "ACTIVE",
                null, null, true, Map.of("MAX_ROOMS", maxRooms), "contract", null);
    }

    private Hotel operationalHotel(Long id, String name) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        hotel.setCode("HOTEL-" + id);
        hotel.setNameVi(name);
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        hotel.setIsDemo(false);
        return hotel;
    }
}
