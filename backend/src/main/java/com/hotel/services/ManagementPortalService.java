package com.hotel.services;

import com.hotel.dtos.*;
import com.hotel.entities.*;
import com.hotel.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ManagementPortalService {
    private static final Map<String, Integer> NO_PLAN_LIMITS = Map.of(
            "MAX_PROPERTIES", 1, "MAX_ROOM_TYPES", 2, "MAX_ROOMS", 5, "MAX_IMAGES", 5, "MAX_STAFF", 0);

    private final PropertyAccessService propertyAccessService;
    private final SubscriptionFeatureService subscriptionFeatureService;
    private final AccountSubscriptionRepository accountSubscriptionRepository;
    private final HotelRepository hotelRepository;
    private final LocationRepository locationRepository;
    private final UserPropertyRepository userPropertyRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final RoomTypeImageRepository roomTypeImageRepository;
    private final HousekeepingTaskRepository housekeepingTaskRepository;
    private final RoomTypeService roomTypeService;
    private final RoomService roomService;

    @Transactional(readOnly = true)
    public Map<String, Object> context(Long activePropertyId) {
        User user = propertyAccessService.currentUser();
        Set<Long> accessibleIds = propertyAccessService.accessibleHotelIds();
        List<Map<String, Object>> properties = hotelRepository.findAllById(accessibleIds).stream()
                .map(this::propertySummary).toList();
        AccountSubscription subscription = accountSubscriptionRepository.findByUserIdAndStatus(user.getId(), "ACTIVE")
                .stream().findFirst().orElseGet(() -> accountSubscriptionRepository
                        .findFirstByUserIdOrderByStartAtDesc(user.getId()).orElse(null));
        Map<String, Integer> limits = subscription != null && "ACTIVE".equals(subscription.getStatus())
                ? subscriptionFeatureService.getActiveFeaturesForUser(user.getId()) : NO_PLAN_LIMITS;
        Long selectedId = activePropertyId != null ? activePropertyId : accessibleIds.stream().findFirst().orElse(null);
        if (selectedId != null) propertyAccessService.requireCanManage(selectedId);
        Map<String, Long> usage = usage(accessibleIds);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.getId());
        result.put("fullName", user.getFullName());
        result.put("properties", properties);
        result.put("activePropertyId", selectedId);
        result.put("planCode", subscription == null ? "NO_PLAN" : subscription.getPlan().getCode());
        result.put("subscriptionStatus", subscription == null ? "NONE" : subscription.getStatus());
        result.put("startAt", subscription == null ? null : subscription.getStartAt());
        result.put("endAt", subscription == null ? null : subscription.getEndAt());
        result.put("lifetime", subscription != null && Boolean.TRUE.equals(subscription.getIsLifetime()));
        result.put("limits", limits);
        result.put("usage", usage);
        result.put("upgradeRequired", subscription == null || !"ACTIVE".equals(subscription.getStatus()));
        if (selectedId != null) result.put("dashboard", dashboard(selectedId));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> properties() {
        return hotelRepository.findAllById(propertyAccessService.accessibleHotelIds()).stream()
                .map(this::propertySummary).toList();
    }

    @Transactional
    public Map<String, Object> createProperty(ManagementPropertyRequest request) {
        User user = propertyAccessService.currentUser();
        Set<Long> accessible = propertyAccessService.accessibleHotelIds();
        requireWithinLimit(user, "MAX_PROPERTIES", accessible.size(), 1);
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
        User user = propertyAccessService.currentUser();
        requireWithinLimit(user, "MAX_ROOM_TYPES", roomTypeRepository.countByHotelIdIn(propertyAccessService.accessibleHotelIds()), 1);
        dto.setHotelId(hotel.getId());
        return roomTypeService.createRoomType(dto);
    }

    @Transactional
    public RoomTypeDTO updateRoomType(Long id, RoomTypeDTO dto) {
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
        requireWithinLimit(propertyAccessService.currentUser(), "MAX_ROOMS",
                roomRepository.countByHotelIdIn(propertyAccessService.accessibleHotelIds()), 1);
        dto.setHotelId(roomType.getHotel().getId());
        if (dto.getHousekeepingStatus() == null) dto.setHousekeepingStatus("CLEAN");
        return roomService.createRoom(dto);
    }

    @Transactional
    public RoomDTO updateRoom(Long id, RoomDTO dto) {
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
        RoomType roomType = roomTypeRepository.findById(request.getRoomTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy loại phòng."));
        Hotel hotel = propertyAccessService.requireManagedHotel(roomType.getHotel().getId());
        if (request.getHotelId() != null && !request.getHotelId().equals(hotel.getId())) {
            throw new IllegalArgumentException("Loại phòng không thuộc cơ sở đã chọn.");
        }
        requireWithinLimit(propertyAccessService.currentUser(), "MAX_ROOMS",
                roomRepository.countByHotelIdIn(propertyAccessService.accessibleHotelIds()), quantity);
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
            room.setStatus(request.getStatus() == null ? "AVAILABLE" : request.getStatus());
            room.setHousekeepingStatus("CLEAN");
            room.setMaintenanceStatus("NONE");
            room.setMaxGuests(roomType.getMaxGuests());
            room.setIsDemo(false);
            created.add(roomDto(roomRepository.save(room)));
        }
        return created;
    }

    @Transactional
    public Map<String, Object> completeHousekeeping(Long taskId) {
        HousekeepingTask task = housekeepingTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tác vụ dọn phòng."));
        propertyAccessService.requireCanManage(task.getHotel().getId());
        if (!Set.of("PENDING", "IN_PROGRESS").contains(task.getStatus())) {
            throw new IllegalStateException("Tác vụ dọn phòng đã kết thúc.");
        }
        Room room = task.getRoom();
        task.setStatus("COMPLETED");
        task.setCompletedAt(LocalDateTime.now());
        room.setHousekeepingStatus("CLEAN");
        if ("NONE".equals(room.getMaintenanceStatus())) room.setStatus("AVAILABLE");
        roomRepository.save(room);
        housekeepingTaskRepository.save(task);
        return Map.of("taskId", task.getId(), "status", task.getStatus(), "roomId", room.getId(),
                "roomStatus", room.getStatus(), "housekeepingStatus", room.getHousekeepingStatus());
    }

    private void requireWithinLimit(User user, String code, long current, int addition) {
        Map<String, Integer> limits = subscriptionFeatureService.getActiveFeaturesForUser(user.getId());
        int limit = limits.getOrDefault(code, NO_PLAN_LIMITS.getOrDefault(code, 0));
        if (limit != -1 && current + addition > limit) {
            throw new IllegalStateException("Đã vượt giới hạn " + code + " của gói hiện tại. Vui lòng nâng cấp gói dịch vụ.");
        }
    }

    private Map<String, Long> usage(Set<Long> ids) {
        long properties = ids.size();
        long roomTypes = ids.isEmpty() ? 0 : roomTypeRepository.countByHotelIdIn(ids);
        long rooms = ids.isEmpty() ? 0 : roomRepository.countByHotelIdIn(ids);
        long images = ids.stream().mapToLong(id -> propertyImageRepository.countByHotelId(id)
                + roomTypeImageRepository.countByRoomTypeHotelId(id)).sum();
        return Map.of("properties", properties, "roomTypes", roomTypes, "rooms", rooms, "images", images);
    }

    private Map<String, Object> dashboard(Long hotelId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalRooms", roomRepository.countByHotelId(hotelId));
        data.put("availableRooms", roomRepository.countByHotelIdAndStatus(hotelId, "AVAILABLE"));
        data.put("reservedRooms", roomRepository.countByHotelIdAndStatus(hotelId, "RESERVED"));
        data.put("occupiedRooms", roomRepository.countByHotelIdAndStatus(hotelId, "OCCUPIED"));
        data.put("dirtyRooms", roomRepository.countByHotelIdAndHousekeepingStatus(hotelId, "DIRTY"));
        data.put("maintenanceRooms", roomRepository.countByHotelIdAndStatus(hotelId, "MAINTENANCE"));
        data.put("pendingHousekeeping", housekeepingTaskRepository.countByHotelIdAndStatus(hotelId, "PENDING"));
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
}
