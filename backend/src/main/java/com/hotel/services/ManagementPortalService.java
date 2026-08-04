package com.hotel.services;

import com.hotel.dtos.*;
import com.hotel.entities.*;
import com.hotel.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ManagementPortalService {
    private final PropertyAccessService propertyAccessService;
    private final SubscriptionFeatureService subscriptionFeatureService;
    private final PropertySubscriptionEntitlementService propertyEntitlementService;
    private final HotelRepository hotelRepository;
    private final LocationRepository locationRepository;
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

    @Autowired(required = false)
    private OperationalAuditService operationalAuditService;

    @Transactional
    public Map<String, Object> context(Long activePropertyId) {
        User user = propertyAccessService.currentUser();
        Set<Long> assignedIds = propertyAccessService.assignedHotelIds();
        List<Map<String, Object>> properties = hotelRepository.findAllById(assignedIds).stream()
                .map(this::propertySummary).toList();
        Long selectedId = activePropertyId != null ? activePropertyId : assignedIds.stream().findFirst().orElse(null);
        Hotel selectedProperty = selectedId == null ? null : propertyAccessService.requireAssignedHotel(selectedId);
        PropertySubscriptionEntitlementService.EntitlementView entitlement = selectedId == null
                ? PropertySubscriptionEntitlementService.EntitlementView.none(null, "PROPERTY_NOT_SELECTED")
                : propertyEntitlementService.getCurrent(selectedId);
        Map<String, Integer> limits = entitlement.limits();
        boolean activePropertyOperational = propertyAccessService.isOperational(selectedProperty);
        Map<String, Long> usage = usage(user.getId(), selectedId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.getId());
        result.put("fullName", user.getFullName());
        result.put("properties", properties);
        result.put("activePropertyId", selectedId);
        result.put("activePropertyOperational", activePropertyOperational);
        result.put("planCode", entitlement.planCode());
        result.put("subscriptionStatus", entitlement.status());
        result.put("subscriptionSource", entitlement.source());
        result.put("entitlementAuthoritative", entitlement.platformAuthoritative());
        result.put("entitlementReference", entitlement.sourceReference());
        result.put("startAt", entitlement.effectiveFrom());
        result.put("endAt", entitlement.effectiveUntil());
        result.put("lifetime", entitlement.lifetime());
        result.put("limits", limits);
        result.put("usage", usage);
        result.put("usageScope", Map.of(
                "properties", "OWNER_ACCOUNT",
                "roomTypes", "SELECTED_PROPERTY",
                "rooms", "SELECTED_PROPERTY",
                "staff", "SELECTED_PROPERTY",
                "images", "SELECTED_PROPERTY"));
        result.put("scope", "SELECTED_PROPERTY");
        result.put("generatedAt", LocalDateTime.now(ZoneOffset.UTC).toString() + "Z");
        result.put("sourceWatermark", selectedId == null ? "PROPERTY_NOT_SELECTED" : "PROPERTY:" + selectedId);
        result.put("upgradeRequired", limits.isEmpty() || !"ACTIVE".equals(entitlement.status()));
        if (activePropertyOperational) result.put("dashboard", dashboard(selectedId));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> properties() {
        return hotelRepository.findAllById(propertyAccessService.assignedHotelIds()).stream()
                .map(this::propertySummary).toList();
    }

    @Transactional
    public Map<String, Object> createProperty(ManagementPropertyRequest request) {
        User user = propertyAccessService.currentUser();
        requireWithinLimit(user, "MAX_PROPERTIES",
                userPropertyRepository.countActiveOwnedPropertiesByUserId(user.getId()), 1);
        if (request == null || request.getNameVi() == null || request.getNameVi().isBlank()
                || request.getProvinceId() == null || request.getWardId() == null
                || request.getAddress() == null || request.getAddress().isBlank()) {
            throw new IllegalArgumentException("Tên, tỉnh, phường/xã và địa chỉ là bắt buộc.");
        }
        Location province = locationRepository.findById(request.getProvinceId())
                .filter(location -> "PROVINCE".equals(location.getLocationType()))
                .orElseThrow(() -> new IllegalArgumentException("Tỉnh/thành phố không hợp lệ."));
        Location ward = locationRepository.findById(request.getWardId())
                .filter(location -> "WARD".equals(location.getLocationType()))
                .orElseThrow(() -> new IllegalArgumentException("Phường/xã không hợp lệ."));
        if (ward.getParent() == null || !province.getId().equals(ward.getParent().getId())) {
            throw new IllegalArgumentException("Phường/xã không thuộc tỉnh/thành phố đã chọn.");
        }
        String unique = user.getId() + "-" + System.currentTimeMillis();
        Hotel hotel = new Hotel();
        hotel.setCode("OWNER-" + unique);
        hotel.setSlug("owner-property-" + unique);
        hotel.setName(request.getNameVi());
        hotel.setNameVi(request.getNameVi());
        hotel.setNameEn(request.getNameEn());
        hotel.setDescription(request.getDescriptionVi());
        hotel.setDescriptionVi(request.getDescriptionVi());
        hotel.setDescriptionEn(request.getDescriptionEn());
        hotel.setPropertyType(request.getPropertyType() == null ? "HOTEL" : request.getPropertyType());
        hotel.setProvinceId(province.getId());
        hotel.setWardId(ward.getId());
        hotel.setAddressLine(request.getAddress());
        hotel.setCity(province.getNameVi());
        hotel.setCountry("Việt Nam");
        hotel.setLatitude(request.getLatitude());
        hotel.setLongitude(request.getLongitude());
        hotel.setPhone(request.getPhone());
        hotel.setEmail(request.getEmail());
        hotel.setWebsite(request.getWebsite());
        hotel.setCheckinTime(request.getCheckinTime());
        hotel.setCheckoutTime(request.getCheckoutTime());
        hotel.setMinPrice(request.getMinPrice());
        hotel.setMaxPrice(request.getMaxPrice());
        hotel.setStarRating(request.getStarRating());
        hotel.setMainImage(request.getMainImage());
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
        audit("PROPERTY", "PROPERTY_CREATED", hotel.getId(), null, propertySummary(hotel), "Property profile created");
        return propertySummary(hotel);
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
    public List<RoomDTO> bulkRooms(BulkRoomRequest request) {
        if (request == null || request.getRoomTypeId() == null || request.getFromNumber() == null
                || request.getToNumber() == null || request.getFloor() == null
                || request.getToNumber() < request.getFromNumber()) {
            throw new IllegalArgumentException("Dải số phòng và tầng không hợp lệ.");
        }
        int quantity = request.getToNumber() - request.getFromNumber() + 1;
        if (quantity > 200) throw new IllegalArgumentException("Mỗi lần chỉ được tạo tối đa 200 phòng.");
        RoomStatePolicy.requireInitialStatus(request.getStatus());
        RoomType roomType = roomTypeRepository.findById(request.getRoomTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy loại phòng."));
        Hotel hotel = propertyAccessService.requireManagedHotel(roomType.getHotel().getId());
        if (request.getHotelId() != null && !request.getHotelId().equals(hotel.getId())) {
            throw new IllegalArgumentException("Loại phòng không thuộc cơ sở đã chọn.");
        }
        requireWithinPropertyLimit(hotel.getId(), "MAX_ROOMS",
                roomRepository.countByHotelId(hotel.getId()), quantity);
        List<RoomDTO> created = new ArrayList<>();
        String prefix = request.getPrefix() == null ? "" : request.getPrefix().trim();
        for (int number = request.getFromNumber(); number <= request.getToNumber(); number++) {
            String roomNumber = prefix + number;
            if (roomRepository.findByHotelIdAndRoomNumber(hotel.getId(), roomNumber).isPresent()) {
                throw new IllegalArgumentException("Số phòng " + roomNumber + " đã tồn tại trong cơ sở.");
            }
            Room room = new Room();
            room.setHotel(hotel);
            room.setRoomType(roomType);
            room.setRoomNumber(roomNumber);
            room.setFloor(request.getFloor());
            RoomStatePolicy.initialize(room);
            room.setMaxGuests(roomType.getMaxGuests());
            room.setIsDemo(false);
            created.add(roomDto(roomRepository.save(room)));
        }
        return created;
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

    private Map<String, Long> usage(Long userId, Long hotelId) {
        long properties = userPropertyRepository.countActiveOwnedPropertiesByUserId(userId);
        long roomTypes = hotelId == null ? 0 : roomTypeRepository.countByHotelId(hotelId);
        long rooms = hotelId == null ? 0 : roomRepository.countByHotelId(hotelId);
        long staff = hotelId == null ? 0 : userPropertyRepository.countActiveStaffByHotelId(hotelId);
        long images = hotelId == null ? 0 : propertyImageRepository.countByHotelId(hotelId)
                + roomTypeImageRepository.countByRoomTypeHotelId(hotelId)
                + roomImageRepository.countByRoomHotelId(hotelId);
        return Map.of("properties", properties, "roomTypes", roomTypes, "rooms", rooms, "staff", staff, "images", images);
    }

    private Map<String, Object> dashboard(Long hotelId) {
        Map<String, Object> data = new LinkedHashMap<>();
        long totalRooms = roomRepository.countByHotelId(hotelId);
        long availableRooms = roomRepository.countByHotelIdAndStatus(hotelId, "AVAILABLE");
        long reservedRooms = roomRepository.countByHotelIdAndStatus(hotelId, "RESERVED");
        long occupiedRooms = roomRepository.countByHotelIdAndStatus(hotelId, "OCCUPIED");
        long maintenanceRooms = roomRepository.countByHotelIdAndStatus(hotelId, "MAINTENANCE");
        long classifiedRooms = availableRooms + reservedRooms + occupiedRooms + maintenanceRooms;
        long unclassifiedRooms = Math.max(0, totalRooms - classifiedRooms);
        data.put("totalRooms", totalRooms);
        data.put("availableRooms", availableRooms);
        data.put("reservedRooms", reservedRooms);
        data.put("occupiedRooms", occupiedRooms);
        data.put("dirtyRooms", roomRepository.countByHotelIdAndHousekeepingStatus(hotelId, "DIRTY"));
        data.put("maintenanceRooms", maintenanceRooms);
        data.put("unclassifiedRooms", unclassifiedRooms);
        data.put("pendingHousekeeping", housekeepingTaskRepository.countByHotelIdAndStatus(hotelId, "PENDING"));
        data.put("classifiedRooms", classifiedRooms);
        data.put("reconciliationStatus", classifiedRooms + unclassifiedRooms == totalRooms ? "RECONCILED" : "MISMATCH");
        data.put("countBasis", "ROOM_STATUS_BY_SELECTED_PROPERTY");
        return data;
    }

    private Map<String, Object> propertySummary(Hotel hotel) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", hotel.getId());
        data.put("code", hotel.getCode());
        data.put("nameVi", hotel.getNameVi());
        data.put("propertyType", hotel.getPropertyType());
        data.put("address", hotel.getAddressLine());
        data.put("provinceId", hotel.getProvinceId());
        data.put("wardId", hotel.getWardId());
        data.put("approvalStatus", hotel.getApprovalStatus());
        data.put("operationStatus", hotel.getOperationStatus());
        data.put("operational", propertyAccessService.isOperational(hotel));
        data.put("mainImage", hotel.getMainImage());
        data.put("isDemo", hotel.getIsDemo());
        return data;
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
