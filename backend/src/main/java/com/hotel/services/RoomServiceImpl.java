package com.hotel.services;

import com.hotel.dtos.RoomDTO;
import com.hotel.dtos.BulkRoomRequest;
import com.hotel.dtos.BulkRoomResultDTO;
import com.hotel.dtos.RoomImageDTO;
import com.hotel.entities.Room;
import com.hotel.entities.RoomImage;
import com.hotel.entities.RoomType;
import com.hotel.repositories.PropertyImageRepository;
import com.hotel.repositories.RoomImageRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.RoomTypeImageRepository;
import com.hotel.repositories.RoomTypeRepository;
import lombok.RequiredArgsConstructor;
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
    private final RoomImageRepository roomImageRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final RoomTypeImageRepository roomTypeImageRepository;
    private final PropertyAccessService propertyAccessService;
    private final SubscriptionFeatureService subscriptionFeatureService;

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
        Room room = new Room();
        RoomType roomType = roomTypeRepository.findById(dto.getRoomTypeId())
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy loại phòng."));
        propertyAccessService.requireAccessibleOrNotFound(roomType.getHotel().getId(), "loại phòng");
        Long hotelId = roomType.getHotel().getId();
        requireCapacity(hotelId, "MAX_ROOMS", roomRepository.countByHotelId(hotelId), 1);
        requireImageCapacity(hotelId, imageCount(dto.getImages()));
        if (dto.getHotelId() != null && !dto.getHotelId().equals(roomType.getHotel().getId())) {
            throw new IllegalArgumentException("Loại phòng không thuộc cơ sở đã chọn.");
        }
        if (roomRepository.findByHotelIdAndRoomNumber(roomType.getHotel().getId(), dto.getRoomNumber()).isPresent()) {
            throw new IllegalArgumentException("Số phòng đã tồn tại trong cơ sở này.");
        }
        mapToEntity(dto, room);
        room.setHotel(roomType.getHotel());
        room.setRoomType(roomType);
        room = roomRepository.save(room);
        
        if (dto.getImages() != null && !dto.getImages().isEmpty()) {
            for (RoomImageDTO imgDto : dto.getImages()) {
                if (imgDto == null || imgDto.getImageUrl() == null || imgDto.getImageUrl().trim().isBlank()) continue;
                RoomImage img = new RoomImage();
                img.setImageUrl(imgDto.getImageUrl().trim());
                img.setIsPrimary(imgDto.getIsPrimary());
                img.setRoom(room);
                roomImageRepository.save(img);
            }
        }
        
        return mapToDTO(room);
    }

    @Override
    @Transactional
    public RoomDTO updateRoom(Long id, RoomDTO dto) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy phòng."));
        propertyAccessService.requireAccessibleOrNotFound(room.getHotel().getId(), "phòng");
                
        requireFeature(room.getHotel().getId(), "MAX_ROOMS");
        RoomType roomType = roomTypeRepository.findById(dto.getRoomTypeId())
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy loại phòng."));
        if (!roomType.getHotel().getId().equals(room.getHotel().getId())) {
            throw new IllegalArgumentException("Không thể chuyển phòng sang loại phòng của cơ sở khác.");
        }
        roomRepository.findByHotelIdAndRoomNumber(room.getHotel().getId(), dto.getRoomNumber())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> { throw new IllegalArgumentException("Số phòng đã tồn tại trong cơ sở này."); });
                
        mapToEntity(dto, room);
        room.setRoomType(roomType);
        room = roomRepository.save(room);
        
        // Simplicity: we don't handle complex image updates here yet, we assume images are handled in a separate endpoint
        
        return mapToDTO(room);
    }

    @Override
    @Transactional
    public void deleteRoom(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy phòng."));
        propertyAccessService.requireAccessibleOrNotFound(room.getHotel().getId(), "phòng");
        requireFeature(room.getHotel().getId(), "MAX_ROOMS");
        if ("OCCUPIED".equals(room.getStatus())) {
            throw new IllegalStateException("Không thể ngừng phòng đang có khách.");
        }
        room.setStatus("OUT_OF_SERVICE");
        room.setMaintenanceStatus("OUT_OF_SERVICE");
        roomRepository.save(room);
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
        RoomType roomType = roomTypeRepository.findById(request.getRoomTypeId())
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy loại phòng."));
        propertyAccessService.requireAccessibleOrNotFound(roomType.getHotel().getId(), "loại phòng");
        if (request.getHotelId() != null && !request.getHotelId().equals(roomType.getHotel().getId())) {
            throw new IllegalArgumentException("Loại phòng không thuộc cơ sở đã chọn.");
        }
        long requestedRooms = (long) request.getToNumber() - request.getFromNumber() + 1;
        Long hotelId = roomType.getHotel().getId();
        requireCapacity(hotelId, "MAX_ROOMS", roomRepository.countByHotelId(hotelId), requestedRooms);
        String prefix = request.getPrefix() == null ? "" : request.getPrefix().trim();
        List<RoomDTO> created = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (int number = request.getFromNumber(); number <= request.getToNumber(); number++) {
            String roomNumber = prefix + number;
            if (roomRepository.findByHotelIdAndRoomNumber(roomType.getHotel().getId(), roomNumber).isPresent()) {
                failed.add(roomNumber);
                continue;
            }
            Room room = new Room();
            room.setHotel(roomType.getHotel());
            room.setRoomType(roomType);
            room.setRoomNumber(roomNumber);
            room.setFloor(request.getFloor());
            room.setStatus(request.getStatus() == null ? "AVAILABLE" : request.getStatus());
            room.setHousekeepingStatus("CLEAN");
            room.setMaintenanceStatus("NONE");
            room.setMaxGuests(roomType.getMaxGuests());
            room.setIsDemo(false);
            created.add(mapToDTO(roomRepository.save(room)));
        }
        return new BulkRoomResultDTO(created, failed);
    }

    private void requireFeature(Long hotelId, String featureCode) {
        if (propertyAccessService.isSystemAdministrator()) return;
        subscriptionFeatureService.requireFeatureForProperty(hotelId, featureCode);
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
        
        List<RoomImage> images = roomImageRepository.findByRoomId(room.getId());
        dto.setImages(images.stream().map(img -> {
            RoomImageDTO imgDto = new RoomImageDTO();
            imgDto.setId(img.getId());
            imgDto.setImageUrl(img.getImageUrl());
            imgDto.setIsPrimary(img.getIsPrimary());
            imgDto.setCreatedAt(img.getCreatedAt());
            return imgDto;
        }).collect(Collectors.toList()));
        
        return dto;
    }

    private void mapToEntity(RoomDTO dto, Room room) {
        room.setRoomNumber(dto.getRoomNumber());
        room.setFloor(dto.getFloor());
        room.setStatus(dto.getStatus());
        if (dto.getMaintenanceStatus() != null) room.setMaintenanceStatus(dto.getMaintenanceStatus());
        if (dto.getHousekeepingStatus() != null) room.setHousekeepingStatus(dto.getHousekeepingStatus());
        room.setMaxGuests(dto.getMaxGuests());
        room.setDescriptionVi(dto.getDescriptionVi());
        room.setDescriptionEn(dto.getDescriptionEn());
        room.setNote(dto.getNote());
    }
}
