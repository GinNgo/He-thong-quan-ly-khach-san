package com.hotel.services;

import com.hotel.dtos.RoomDTO;
import com.hotel.dtos.BulkRoomRequest;
import com.hotel.dtos.BulkRoomResultDTO;
import com.hotel.dtos.RoomImageDTO;
import com.hotel.entities.Room;
import com.hotel.entities.RoomImage;
import com.hotel.entities.RoomType;
import com.hotel.entities.PropertyMedia;
import com.hotel.repositories.PropertyImageRepository;
import com.hotel.repositories.RoomImageRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.RoomTypeImageRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.ReservationRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final HotelRepository hotelRepository;
    private final ReservationRoomRepository reservationRoomRepository;
    private final RoomImageRepository roomImageRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final RoomTypeImageRepository roomTypeImageRepository;
    private final PropertyAccessService propertyAccessService;
    private final SubscriptionFeatureService subscriptionFeatureService;
    private final PropertyMediaService propertyMediaService;
    private final PropertyMediaPolicy propertyMediaPolicy;

    @Autowired(required = false)
    private OperationalAuditService operationalAuditService;

    @Override
    public List<RoomDTO> getAllRooms() {
        List<Room> rooms = propertyAccessService.isSystemAdministrator()
                ? roomRepository.findAll()
                : roomRepository.findByHotelIdIn(propertyAccessService.accessibleHotelIds());
        return rooms.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public RoomDTO getRoomById(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy phòng."));
        propertyAccessService.requireAccessibleOrNotFound(room.getHotel().getId(), "phòng");
        return mapToDTO(room);
    }

    @Override
    @Transactional
    public RoomDTO createRoom(RoomDTO dto) {
        normalizeAndValidate(dto);
        RoomStatePolicy.requireInitialState(dto);
        Room room = new Room();
        lockHotelForRoomType(dto.getRoomTypeId());
        RoomType roomType = roomTypeRepository.findByIdForUpdate(dto.getRoomTypeId())
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy loại phòng."));
        propertyAccessService.requireAccessibleOrNotFound(roomType.getHotel().getId(), "loại phòng");
        Long hotelId = roomType.getHotel().getId();
        requireCapacity(hotelId, "MAX_ROOMS", roomRepository.countByHotelId(hotelId), 1);
        requireImageCapacity(hotelId, imageCount(dto.getImages()));
        List<PreparedRoomImage> preparedImages = prepareImages(dto.getImages());
        if (dto.getHotelId() != null && !dto.getHotelId().equals(roomType.getHotel().getId())) {
            throw new IllegalArgumentException("Loại phòng không thuộc cơ sở đã chọn.");
        }
        if (roomRepository.findByHotelIdAndRoomNumber(roomType.getHotel().getId(), dto.getRoomNumber()).isPresent()) {
            throw new IllegalArgumentException("Số phòng đã tồn tại trong cơ sở này.");
        }
        mapToEntity(dto, room);
        room.setHotel(roomType.getHotel());
        room.setRoomType(roomType);
        RoomStatePolicy.initialize(room);
        room = roomRepository.save(room);
        
        if (!preparedImages.isEmpty()) {
            for (int index = 0; index < preparedImages.size(); index++) {
                PreparedRoomImage prepared = preparedImages.get(index);
                PropertyMedia media = propertyMediaService.createExternal(
                        room.getHotel(), prepared.imageUrl(), prepared.altTextVi(), prepared.altTextEn());
                RoomImage img = new RoomImage();
                img.setMedia(media);
                img.setImageUrl(media.getPublicUrl());
                img.setIsPrimary(prepared.primary());
                img.setSortOrder(index);
                img.setAltTextVi(media.getAltTextVi());
                img.setAltTextEn(media.getAltTextEn());
                img.setRoom(room);
                roomImageRepository.save(img);
            }
        }
        
        audit("ROOM", "ROOM_CREATED", room, null, roomSnapshot(room), "Room created");
        return mapToDTO(room);
    }

    @Override
    @Transactional
    public RoomDTO updateRoom(Long id, RoomDTO dto) {
        normalizeAndValidate(dto);
        lockHotelForRoom(id);
        Room room = roomRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy phòng."));
        propertyAccessService.requireAccessibleOrNotFound(room.getHotel().getId(), "phòng");
        java.util.Map<String, Object> before = roomSnapshot(room);
        RoomStatePolicy.requireMetadataOnlyUpdate(room, dto);
        requireFeature(room.getHotel().getId(), "MAX_ROOMS");
        RoomType roomType = roomTypeRepository.findById(dto.getRoomTypeId())
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy loại phòng."));
        if (!roomType.getHotel().getId().equals(room.getHotel().getId())) {
            throw new IllegalArgumentException("Không thể chuyển phòng sang loại phòng của cơ sở khác.");
        }
        roomRepository.findByHotelIdAndRoomNumber(room.getHotel().getId(), dto.getRoomNumber())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> { throw new IllegalArgumentException("Số phòng đã tồn tại trong cơ sở này."); });
                
        if (!roomType.getId().equals(room.getRoomType().getId())) requireNoActiveBooking(room);
        mapToEntity(dto, room);
        room.setRoomType(roomType);
        room = roomRepository.save(room);

        // Simplicity: we don't handle complex image updates here yet, we assume images are handled in a separate endpoint
        audit("ROOM", "ROOM_UPDATED", room, before, roomSnapshot(room), "Room metadata updated");
        return mapToDTO(room);
    }

    @Override
    @Transactional
    public RoomDTO startMaintenance(Long id) {
        Room room = findLockedRoom(id);
        requireFeature(room.getHotel().getId(), "MAX_ROOMS");
        java.util.Map<String, Object> before = roomSnapshot(room);
        RoomStatePolicy.startMaintenance(room);
        Room saved = roomRepository.save(room);
        audit("MAINTENANCE", "ROOM_MAINTENANCE_STARTED", saved, before, roomSnapshot(saved), "Room maintenance started");
        return mapToDTO(saved);
    }

    @Override
    @Transactional
    public RoomDTO completeMaintenance(Long id) {
        Room room = findLockedRoom(id);
        requireFeature(room.getHotel().getId(), "MAX_ROOMS");
        java.util.Map<String, Object> before = roomSnapshot(room);
        RoomStatePolicy.completeMaintenance(room);
        Room saved = roomRepository.save(room);
        audit("MAINTENANCE", "ROOM_MAINTENANCE_COMPLETED", saved, before, roomSnapshot(saved), "Room maintenance completed");
        return mapToDTO(saved);
    }

    @Override
    @Transactional
    public void deleteRoom(Long id) {
        lockHotelForRoom(id);
        Room room = findLockedRoom(id);
        requireFeature(room.getHotel().getId(), "MAX_ROOMS");
        requireNoActiveBooking(room);
        java.util.Map<String, Object> before = roomSnapshot(room);
        RoomStatePolicy.deactivate(room);
        Room saved = roomRepository.save(room);
        audit("ROOM", "ROOM_DEACTIVATED", saved, before, roomSnapshot(saved), "Room deactivated");
    }

    @Override
    @Transactional
    public BulkRoomResultDTO bulkCreate(BulkRoomRequest request) {
        if (request == null || request.getRoomTypeId() == null || request.getFromNumber() == null
                || request.getToNumber() == null || request.getFloor() == null
                || request.getToNumber() < request.getFromNumber()) {
            throw new IllegalArgumentException("Dải số phòng và tầng không hợp lệ.");
        }
        if (request.getToNumber() - request.getFromNumber() + 1 > 200) {
            throw new IllegalArgumentException("Mỗi lần chỉ được tạo tối đa 200 phòng.");
        }
        RoomStatePolicy.requireInitialStatus(request.getStatus());
        lockHotelForRoomType(request.getRoomTypeId());
        RoomType roomType = roomTypeRepository.findByIdForUpdate(request.getRoomTypeId())
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy loại phòng."));
        propertyAccessService.requireAccessibleOrNotFound(roomType.getHotel().getId(), "loại phòng");
        if (request.getHotelId() != null && !request.getHotelId().equals(roomType.getHotel().getId())) {
            throw new IllegalArgumentException("Loại phòng không thuộc cơ sở đã chọn.");
        }
        long requestedRooms = (long) request.getToNumber() - request.getFromNumber() + 1;
        Long hotelId = roomType.getHotel().getId();
        requireCapacity(hotelId, "MAX_ROOMS", roomRepository.countByHotelId(hotelId), requestedRooms);
        String prefix = normalizeRoomNumber(request.getPrefix() == null ? "" : request.getPrefix());
        List<RoomDTO> created = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        List<String> roomNumbers = new ArrayList<>();
        for (int number = request.getFromNumber(); number <= request.getToNumber(); number++) {
            String roomNumber = prefix + number;
            if (roomRepository.findByHotelIdAndRoomNumber(roomType.getHotel().getId(), roomNumber).isPresent()) {
                throw new IllegalArgumentException("Sá»‘ phÃ²ng " + roomNumber + " Ä‘Ã£ tá»“n táº¡i trong cÆ¡ sá»Ÿ.");
            }
            roomNumbers.add(roomNumber);
        }
        for (String roomNumber : roomNumbers) {
            Room room = new Room();
            room.setHotel(roomType.getHotel());
            room.setRoomType(roomType);
            room.setRoomNumber(roomNumber);
            room.setFloor(request.getFloor());
            RoomStatePolicy.initialize(room);
            room.setMaxGuests(roomType.getMaxGuests());
            room.setIsDemo(false);
            created.add(mapToDTO(roomRepository.save(room)));
        }
        if (operationalAuditService != null && (!created.isEmpty() || !failed.isEmpty())) {
            operationalAuditService.append(new OperationalAuditService.AuditCommand(
                    "TENANT", hotelId, "ROOM", "ROOM_BULK_CREATE_COMPLETED", "ROOM_TYPE",
                    String.valueOf(roomType.getId()), null, null, "Bulk room creation completed", null,
                    java.util.Map.of("createdCount", created.size(), "failedRoomNumbers", failed), null));
        }
        return new BulkRoomResultDTO(created, failed);
    }

    private void requireFeature(Long hotelId, String featureCode) {
        if (propertyAccessService.isSystemAdministrator()) return;
        subscriptionFeatureService.requireFeatureForProperty(hotelId, featureCode);
    }

    private Room findLockedRoom(Long id) {
        Room room = roomRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy phòng."));
        propertyAccessService.requireAccessibleOrNotFound(room.getHotel().getId(), "phòng");
        return room;
    }

    private void lockHotelForRoomType(Long roomTypeId) {
        RoomType snapshot = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Room type not found."));
        Long hotelId = snapshot.getHotel().getId();
        propertyAccessService.requireAccessibleOrNotFound(hotelId, "room type");
        hotelRepository.findByIdForUpdate(hotelId)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Property not found."));
    }

    private void lockHotelForRoom(Long roomId) {
        Room snapshot = roomRepository.findById(roomId)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Room not found."));
        Long hotelId = snapshot.getHotel().getId();
        propertyAccessService.requireAccessibleOrNotFound(hotelId, "room");
        hotelRepository.findByIdForUpdate(hotelId)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Property not found."));
    }

    private void requireNoActiveBooking(Room room) {
        if (reservationRoomRepository.hasActiveAssignment(
                room.getId(), RoomAvailabilityService.RELEASED_RESERVATION_STATUSES)) {
            throw new IllegalStateException("A room with an active booking cannot change type or be deactivated.");
        }
    }

    private void normalizeAndValidate(RoomDTO dto) {
        if (dto == null || dto.getRoomTypeId() == null) {
            throw new IllegalArgumentException("Room type and room number are required.");
        }
        dto.setRoomNumber(normalizeRoomNumber(dto.getRoomNumber()));
        if (dto.getRoomNumber().isBlank() || dto.getRoomNumber().length() > 50
                || !dto.getRoomNumber().matches("[\\p{L}\\p{N}_-]+")) {
            throw new IllegalArgumentException("Room number is invalid.");
        }
        if (dto.getFloor() != null && (dto.getFloor() < -10 || dto.getFloor() > 500)) throw new IllegalArgumentException("Floor is invalid.");
        if (dto.getMaxGuests() != null && dto.getMaxGuests() < 1) throw new IllegalArgumentException("Capacity is invalid.");
    }

    private String normalizeRoomNumber(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private void requireCapacity(Long hotelId, String featureCode, long currentUsage, long addition) {
        if (propertyAccessService.isSystemAdministrator()) return;
        subscriptionFeatureService.checkFeatureLimitForProperty(hotelId, featureCode, currentUsage, addition);
    }

    private void requireImageCapacity(Long hotelId, long requestedImages) {
        if (requestedImages == 0 || propertyAccessService.isSystemAdministrator()) return;
        long currentUsage = propertyImageRepository.countByHotelId(hotelId)
                + roomTypeImageRepository.countByRoomTypeHotelId(hotelId)
                + roomImageRepository.countByRoomHotelId(hotelId);
        subscriptionFeatureService.checkFeatureLimitForProperty(
                hotelId, "MAX_IMAGES", currentUsage, requestedImages);
    }

    private long imageCount(List<RoomImageDTO> images) {
        if (images == null) return 0;
        return images.stream()
                .filter(image -> image != null && image.getImageUrl() != null && !image.getImageUrl().trim().isBlank())
                .count();
    }

    private List<PreparedRoomImage> prepareImages(List<RoomImageDTO> images) {
        if (images == null || images.isEmpty()) return List.of();
        List<PreparedRoomImage> prepared = new ArrayList<>();
        java.util.Set<String> urls = new java.util.LinkedHashSet<>();
        int primaryCount = 0;
        for (RoomImageDTO image : images) {
            if (image == null || image.getImageUrl() == null || image.getImageUrl().isBlank()) continue;
            String imageUrl = propertyMediaPolicy.normalizeExternalUrl(image.getImageUrl());
            if (!urls.add(imageUrl)) {
                throw new IllegalArgumentException("Room image URLs must be unique.");
            }
            String altTextVi = propertyMediaPolicy.requireAltTextVi(image.getAltTextVi());
            String altTextEn = propertyMediaPolicy.normalizeAltTextEn(image.getAltTextEn());
            boolean primary = Boolean.TRUE.equals(image.getIsPrimary());
            if (primary) primaryCount++;
            prepared.add(new PreparedRoomImage(imageUrl, altTextVi, altTextEn, primary));
        }
        if (primaryCount > 1) {
            throw new IllegalArgumentException("Only one room image can be primary.");
        }
        if (!prepared.isEmpty() && primaryCount == 0) {
            PreparedRoomImage first = prepared.getFirst();
            prepared.set(0, new PreparedRoomImage(
                    first.imageUrl(), first.altTextVi(), first.altTextEn(), true));
        }
        return prepared;
    }

    private RoomDTO mapToDTO(Room room) {
        RoomDTO dto = new RoomDTO();
        dto.setId(room.getId());
        dto.setRoomNumber(room.getRoomNumber());
        dto.setHotelId(room.getHotel() == null ? null : room.getHotel().getId());
        dto.setFloor(room.getFloor());
        dto.setStatus(room.getStatus());
        dto.setMaintenanceStatus(room.getMaintenanceStatus());
        dto.setHousekeepingStatus(room.getHousekeepingStatus());
        dto.setIsDemo(room.getIsDemo());
        dto.setMaxGuests(room.getMaxGuests());
        dto.setDescriptionVi(room.getDescriptionVi());
        dto.setDescriptionEn(room.getDescriptionEn());
        dto.setNote(room.getNote());
        dto.setCreatedAt(room.getCreatedAt());
        dto.setUpdatedAt(room.getUpdatedAt());
        
        if (room.getRoomType() != null) {
            dto.setRoomTypeId(room.getRoomType().getId());
            dto.setRoomTypeCode(room.getRoomType().getCode());
            dto.setRoomTypeNameVi(room.getRoomType().getNameVi());
        }
        
        List<RoomImage> images = roomImageRepository.findByRoomIdOrderBySortOrderAsc(room.getId());
        dto.setImages(images.stream().map(img -> {
            RoomImageDTO imgDto = new RoomImageDTO();
            imgDto.setId(img.getId());
            imgDto.setImageUrl(img.getImageUrl());
            imgDto.setIsPrimary(img.getIsPrimary());
            imgDto.setSortOrder(img.getSortOrder());
            imgDto.setAltTextVi(img.getAltTextVi());
            imgDto.setAltTextEn(img.getAltTextEn());
            imgDto.setMediaId(img.getMedia() == null ? null : img.getMedia().getId());
            imgDto.setSourceType(img.getMedia() == null ? "LEGACY" : img.getMedia().getSourceType());
            imgDto.setCreatedAt(img.getCreatedAt());
            return imgDto;
        }).collect(Collectors.toList()));
        
        return dto;
    }

    private void mapToEntity(RoomDTO dto, Room room) {
        room.setRoomNumber(dto.getRoomNumber());
        room.setFloor(dto.getFloor());
        room.setMaxGuests(dto.getMaxGuests());
        room.setDescriptionVi(dto.getDescriptionVi());
        room.setDescriptionEn(dto.getDescriptionEn());
        room.setNote(dto.getNote());
    }

    private java.util.Map<String, Object> roomSnapshot(Room room) {
        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("id", room.getId());
        snapshot.put("roomNumber", room.getRoomNumber());
        snapshot.put("roomTypeId", room.getRoomType() == null ? null : room.getRoomType().getId());
        snapshot.put("status", room.getStatus());
        snapshot.put("maintenanceStatus", room.getMaintenanceStatus());
        snapshot.put("housekeepingStatus", room.getHousekeepingStatus());
        snapshot.put("floor", room.getFloor());
        snapshot.put("maxGuests", room.getMaxGuests());
        return snapshot;
    }

    private void audit(String domain, String eventType, Room room, Object before, Object after, String reason) {
        if (operationalAuditService == null || room.getHotel() == null) return;
        String aggregateId = room.getId() == null ? "ROOM_NUMBER:" + room.getRoomNumber() : String.valueOf(room.getId());
        operationalAuditService.append(new OperationalAuditService.AuditCommand(
                "TENANT", room.getHotel().getId(), domain, eventType, "ROOM", aggregateId,
                null, null, reason, before, after, null));
    }

    private record PreparedRoomImage(
            String imageUrl,
            String altTextVi,
            String altTextEn,
            boolean primary) {
    }
}
