package com.hotel.services;

import com.hotel.dtos.*;
import com.hotel.entities.*;
import com.hotel.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ManagementPortalService {
    private final PropertyAccessService propertyAccessService;
    private final SubscriptionFeatureService subscriptionFeatureService;
    private final PropertySubscriptionEntitlementService propertyEntitlementService;
    private final HotelRepository hotelRepository;
    private final UserPropertyRepository userPropertyRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final RoomTypeImageRepository roomTypeImageRepository;
    private final RoomImageRepository roomImageRepository;
    private final HousekeepingTaskRepository housekeepingTaskRepository;
    private final RoomTypeService roomTypeService;
    private final RoomService roomService;
    private final PropertyProfileMapper propertyProfileMapper;

    @Autowired(required = false)
    private OperationalAuditService operationalAuditService;

    @Transactional
    public Map<String, Object> context(Long activePropertyId) {
        User user = propertyAccessService.currentUser();
        Set<Long> assignedIds = propertyAccessService.assignedHotelIds();
        List<PropertyProfileDTO> properties = hotelRepository.findAllById(assignedIds).stream()
                .map(PropertyProfileDTO::from).toList();
        Long selectedId = activePropertyId != null ? activePropertyId : assignedIds.stream().findFirst().orElse(null);
        Hotel selectedProperty = selectedId == null ? null : propertyAccessService.requireAssignedHotel(selectedId);
        PropertySubscriptionEntitlementService.EntitlementView entitlement = selectedId == null
                ? PropertySubscriptionEntitlementService.EntitlementView.none(null, "PROPERTY_NOT_SELECTED")
                : propertyEntitlementService.getCurrent(selectedId);
        Map<String, Integer> limits = entitlement.limits();
        boolean activePropertyOperational = propertyAccessService.isOperational(selectedProperty);
        DashboardSnapshot dashboardSnapshot = selectedId == null ? DashboardSnapshot.empty() : dashboardSnapshot(selectedId);
        Map<String, Long> usage = usage(selectedId, dashboardSnapshot);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.getId());
        result.put("fullName", user.getFullName());
        result.put("properties", properties);
        result.put("activePropertyId", selectedId);
        result.put("activePropertyOperational", activePropertyOperational);
        result.put("planCode", entitlement.planCode());
        result.put("subscriptionStatus", entitlement.status());
        result.put("subscriptionSource", entitlement.source());
        result.put("startAt", entitlement.effectiveFrom());
        result.put("endAt", entitlement.effectiveUntil());
        result.put("lifetime", entitlement.lifetime());
        result.put("limits", limits);
        result.put("usage", usage);
        result.put("upgradeRequired", limits.isEmpty() || !"ACTIVE".equals(entitlement.status()));
        result.put("generatedAt", Instant.now().toString());
        result.put("dataStatus", "COMPLETE");
        result.put("errors", List.of());
        result.put("usageScope", selectedId == null ? "NONE" : "PROPERTY");
        if (activePropertyOperational) result.put("dashboard", dashboard(selectedId, dashboardSnapshot));
        return result;
    }

    @Transactional(readOnly = true)
    public List<PropertyProfileDTO> properties() {
        return hotelRepository.findAllById(propertyAccessService.assignedHotelIds()).stream()
                .map(PropertyProfileDTO::from).toList();
    }

    @Transactional
    public PropertyProfileDTO createProperty(PropertyProfileDTO request) {
        User user = propertyAccessService.currentUser();
        requireWithinLimit(user, "MAX_PROPERTIES",
                userPropertyRepository.countActiveOwnedPropertiesByUserId(user.getId()), 1);
        String unique = user.getId() + "-" + System.currentTimeMillis();
        Hotel hotel = new Hotel();
        hotel.setCode("OWNER-" + unique);
        hotel.setSlug("owner-property-" + unique);
        propertyProfileMapper.apply(hotel, request);
        hotel.setStatus("DRAFT");
        hotel.setApprovalStatus("DRAFT");
        hotel.setOperationStatus("INACTIVE");
        hotel.setIsDemo(false);
        hotel.setDataSource("USER");
        hotel = hotelRepository.saveAndFlush(hotel);

        Role role = roleRepository.findByCode("PROPERTY_OWNER").orElseGet(() -> {
            Role value = new Role();
            value.setCode("PROPERTY_OWNER");
            value.setName("Chủ cơ sở");
            value.setDescription("Quản lý cơ sở được gán.");
            return roleRepository.save(value);
        });
        Set<Role> roles = user.getRoles() == null ? new HashSet<>() : new HashSet<>(user.getRoles());
        roles.add(role);
        user.setRoles(roles);
        userRepository.save(user);

        UserProperty mapping = new UserProperty();
        mapping.setUser(user);
        mapping.setHotel(hotel);
        mapping.setRelationshipType("OWNER");
        mapping.setIsPrimaryOwner(true);
        mapping.setStatus("ACTIVE");
        mapping.setStartDate(LocalDateTime.now());
        userPropertyRepository.save(mapping);
        PropertyProfileDTO created = PropertyProfileDTO.from(hotel);
        audit("PROPERTY", "PROPERTY_CREATED", hotel.getId(), null, created, "Property profile created");
        return created;
    }

    @Transactional(readOnly = true)
    public List<RoomTypeDTO> roomTypes(Long hotelId) {
        propertyAccessService.requireCanManage(hotelId);
        return roomTypeRepository.findByHotelId(hotelId).stream().map(this::roomTypeDto).toList();
    }

    @Transactional
    public RoomTypeDTO createRoomType(RoomTypeDTO dto) {
        Hotel hotel = propertyAccessService.requireManagedHotel(dto.getHotelId());
        dto.setHotelId(hotel.getId());
        return roomTypeService.createRoomType(dto);
    }

    @Transactional
    public RoomTypeDTO updateRoomType(Long id, RoomTypeDTO dto) {
        RoomType roomType = roomTypeRepository.findById(id)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy loại phòng."));
        propertyAccessService.requireAccessibleOrNotFound(roomType.getHotel().getId(), "loại phòng");
        return roomTypeService.updateRoomType(id, dto);
    }

    @Transactional
    public void deleteRoomType(Long id) {
        RoomType roomType = roomTypeRepository.findById(id)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy loại phòng."));
        propertyAccessService.requireAccessibleOrNotFound(roomType.getHotel().getId(), "loại phòng");
        roomTypeService.deleteRoomType(id);
    }

    @Transactional(readOnly = true)
    public List<RoomDTO> rooms(Long hotelId) {
        propertyAccessService.requireCanManage(hotelId);
        return roomRepository.findByHotelId(hotelId).stream().map(this::roomDto).toList();
    }

    @Transactional
    public RoomDTO createRoom(RoomDTO dto) {
        RoomType roomType = roomTypeRepository.findById(dto.getRoomTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy loại phòng."));
        propertyAccessService.requireCanManage(roomType.getHotel().getId());
        dto.setHotelId(roomType.getHotel().getId());
        if (dto.getHousekeepingStatus() == null) dto.setHousekeepingStatus("CLEAN");
        return roomService.createRoom(dto);
    }

    @Transactional
    public RoomDTO updateRoom(Long id, RoomDTO dto) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy phòng."));
        propertyAccessService.requireAccessibleOrNotFound(room.getHotel().getId(), "phòng");
        return roomService.updateRoom(id, dto);
    }

    @Transactional
    public BulkRoomResultDTO bulkRooms(BulkRoomRequest request) {
        return roomService.bulkCreate(request);
    }

    @Transactional
    public void deleteRoom(Long id) {
        roomService.deleteRoom(id);
    }

    @Transactional
    public Map<String, Object> completeHousekeeping(Long taskId) {
        HousekeepingTask task = housekeepingTaskRepository.findById(taskId)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy tác vụ dọn phòng."));
        propertyAccessService.requireAccessibleOrNotFound(task.getHotel().getId(), "tác vụ dọn phòng");
        if (!Set.of("PENDING", "IN_PROGRESS").contains(task.getStatus())) {
            throw new IllegalStateException("Tác vụ dọn phòng đã kết thúc.");
        }
        Room room = roomRepository.findByIdForUpdate(task.getRoom().getId())
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy phòng của tác vụ dọn phòng."));
        task.setStatus("COMPLETED");
        task.setCompletedAt(LocalDateTime.now());
        RoomStatePolicy.completeHousekeeping(room);
        roomRepository.save(room);
        housekeepingTaskRepository.save(task);
        audit("MAINTENANCE", "HOUSEKEEPING_COMPLETED", task.getHotel().getId(),
                Map.of("taskId", task.getId(), "status", "IN_PROGRESS", "roomId", task.getRoom().getId()),
                Map.of("taskId", task.getId(), "status", task.getStatus(), "roomId", room.getId(),
                        "roomStatus", room.getStatus(), "housekeepingStatus", room.getHousekeepingStatus()),
                "Housekeeping task completed");
        return Map.of("taskId", task.getId(), "status", task.getStatus(), "roomId", room.getId(),
                "roomStatus", room.getStatus(), "housekeepingStatus", room.getHousekeepingStatus());
    }

    @Transactional
    public RoomDTO startRoomMaintenance(Long id) {
        return roomService.startMaintenance(id);
    }

    @Transactional
    public RoomDTO completeRoomMaintenance(Long id) {
        return roomService.completeMaintenance(id);
    }

    private void requireWithinLimit(User user, String code, long current, int addition) {
        if (propertyAccessService.isSystemAdministrator()) return;
        subscriptionFeatureService.checkFeatureLimit(user.getId(), code, current, addition);
    }

    private void requireWithinPropertyLimit(Long hotelId, String code, long current, int addition) {
        if (propertyAccessService.isSystemAdministrator()) return;
        subscriptionFeatureService.checkFeatureLimitForProperty(hotelId, code, current, addition);
    }

    private Map<String, Long> usage(Long hotelId, DashboardSnapshot snapshot) {
        long properties = hotelId == null ? 0 : 1;
        long roomTypes = hotelId == null ? 0 : roomTypeRepository.countByHotelId(hotelId);
        long rooms = snapshot.totalRooms();
        long staff = hotelId == null ? 0 : userPropertyRepository.countActiveStaffByHotelId(hotelId);
        long images = hotelId == null ? 0 : propertyImageRepository.countByHotelId(hotelId)
                + roomTypeImageRepository.countByRoomTypeHotelId(hotelId)
                + roomImageRepository.countByRoomHotelId(hotelId);
        return Map.of("properties", properties, "roomTypes", roomTypes, "rooms", rooms, "staff", staff, "images", images);
    }

    private DashboardSnapshot dashboardSnapshot(Long hotelId) {
        List<Room> rooms = roomRepository.findByHotelId(hotelId);
        Map<String, Long> byStatus = rooms.stream().collect(java.util.stream.Collectors.groupingBy(
                room -> normalizeStatus(room.getStatus()), LinkedHashMap::new, java.util.stream.Collectors.counting()));
        long dirtyRooms = rooms.stream().filter(room -> "DIRTY".equals(normalizeStatus(room.getHousekeepingStatus()))).count();
        return new DashboardSnapshot(rooms.size(), byStatus, dirtyRooms);
    }

    private Map<String, Object> dashboard(Long hotelId, DashboardSnapshot snapshot) {
        Map<String, Object> data = new LinkedHashMap<>();
        long available = snapshot.status("AVAILABLE");
        long reserved = snapshot.status("RESERVED");
        long occupied = snapshot.status("OCCUPIED");
        long maintenance = snapshot.status("MAINTENANCE");
        long classified = available + reserved + occupied + maintenance;
        long statusCountTotal = snapshot.byStatus().values().stream().mapToLong(Long::longValue).sum();
        data.put("totalRooms", snapshot.totalRooms());
        data.put("availableRooms", available);
        data.put("reservedRooms", reserved);
        data.put("occupiedRooms", occupied);
        data.put("dirtyRooms", snapshot.dirtyRooms());
        data.put("maintenanceRooms", maintenance);
        data.put("classifiedRooms", classified);
        data.put("unclassifiedRooms", snapshot.totalRooms() - classified);
        data.put("statusCountTotal", statusCountTotal);
        data.put("reconciled", statusCountTotal == snapshot.totalRooms());
        data.put("pendingHousekeeping", housekeepingTaskRepository.countByHotelIdAndStatus(hotelId, "PENDING"));
        return data;
    }

    private String normalizeStatus(String value) {
        return value == null ? "UNKNOWN" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record DashboardSnapshot(long totalRooms, Map<String, Long> byStatus, long dirtyRooms) {
        private static DashboardSnapshot empty() { return new DashboardSnapshot(0, Map.of(), 0); }
        private long status(String value) { return byStatus.getOrDefault(value, 0L); }
    }

    private RoomTypeDTO roomTypeDto(RoomType entity) {
        RoomTypeDTO dto = new RoomTypeDTO();
        dto.setId(entity.getId()); dto.setHotelId(entity.getHotel().getId()); dto.setCode(entity.getCode());
        dto.setNameVi(entity.getNameVi()); dto.setNameEn(entity.getNameEn()); dto.setNormalizedName(entity.getNormalizedName());
        dto.setArea(entity.getArea()); dto.setBedType(entity.getBedType()); dto.setBedCount(entity.getBedCount());
        dto.setMaxAdults(entity.getMaxAdults()); dto.setMaxChildren(entity.getMaxChildren()); dto.setMaxGuests(entity.getMaxGuests());
        dto.setMaxGuest(entity.getMaxGuest()); dto.setBasePrice(entity.getBasePrice()); dto.setHourlyPrice(entity.getHourlyPrice());
        dto.setStatus(entity.getStatus()); dto.setDescriptionVi(entity.getDescriptionVi()); dto.setDescriptionEn(entity.getDescriptionEn());
        dto.setTotalRooms(roomRepository.countByRoomTypeId(entity.getId())); dto.setIsDemo(entity.getIsDemo());
        return dto;
    }

    private RoomDTO roomDto(Room entity) {
        RoomDTO dto = new RoomDTO();
        dto.setId(entity.getId()); dto.setHotelId(entity.getHotel().getId()); dto.setRoomTypeId(entity.getRoomType().getId());
        dto.setRoomTypeCode(entity.getRoomType().getCode()); dto.setRoomTypeNameVi(entity.getRoomType().getNameVi());
        dto.setRoomNumber(entity.getRoomNumber()); dto.setFloor(entity.getFloor()); dto.setStatus(entity.getStatus());
        dto.setMaintenanceStatus(entity.getMaintenanceStatus()); dto.setHousekeepingStatus(entity.getHousekeepingStatus());
        dto.setMaxGuests(entity.getMaxGuests()); dto.setDescriptionVi(entity.getDescriptionVi()); dto.setDescriptionEn(entity.getDescriptionEn());
        dto.setIsDemo(entity.getIsDemo());
        return dto;
    }

    private void audit(String domain, String eventType, Long hotelId, Object before, Object after, String reason) {
        if (operationalAuditService == null || hotelId == null) return;
        operationalAuditService.append(new OperationalAuditService.AuditCommand(
                "TENANT", hotelId, domain, eventType, domain, String.valueOf(hotelId),
                null, null, reason, before, after, null));
    }
}
