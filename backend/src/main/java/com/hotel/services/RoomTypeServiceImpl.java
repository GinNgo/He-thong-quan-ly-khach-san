package com.hotel.services;

import com.hotel.dtos.RoomTypeDTO;
import com.hotel.entities.RoomType;
import com.hotel.repositories.RoomTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomTypeServiceImpl implements RoomTypeService {

    private final RoomTypeRepository roomTypeRepository;
    private final RoomAvailabilityService roomAvailabilityService;
    private final com.hotel.repositories.RoomRepository roomRepository;
    private final com.hotel.repositories.RoomTypeImageRepository roomTypeImageRepository;
    private final PropertyAccessService propertyAccessService;

    @Override
    @Transactional(readOnly = true)
    public List<RoomTypeDTO> getAllRoomTypes() {
        List<RoomType> roomTypes = propertyAccessService.isSystemAdministrator()
                ? roomTypeRepository.findAll()
                : roomTypeRepository.findByHotelIdIn(propertyAccessService.accessibleHotelIds());
        return roomTypes.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<RoomTypeDTO> getRoomTypesByHotelId(Long hotelId) {
        return getRoomTypesByHotelId(hotelId, null, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomTypeDTO> getRoomTypesByHotelId(Long hotelId, LocalDate checkIn, LocalDate checkOut, Integer guests) {
        boolean hasStayDates = checkIn != null && checkOut != null;

        return roomTypeRepository.findByHotelId(hotelId).stream()
                .filter(roomType -> roomAvailabilityService.canHost(roomType, guests))
                .map(roomType -> {
                    RoomTypeDTO dto = mapToDTO(roomType);
                    roomAvailabilityService.enrich(dto, roomType, checkIn, checkOut);
                    return dto;
                })
                .filter(dto -> !hasStayDates || (dto.getAvailableRooms() != null && dto.getAvailableRooms() > 0))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public RoomTypeDTO getRoomTypeById(Long id) {
        RoomType roomType = roomTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Room type not found"));
        propertyAccessService.requireCanManage(roomType.getHotel().getId());
        return mapToDTO(roomType);
    }

    @Override
    @Transactional
    public RoomTypeDTO createRoomType(RoomTypeDTO dto) {
        normalizeAndValidate(dto);
        RoomType roomType = new RoomType();
        mapToEntity(dto, roomType);

        com.hotel.entities.Hotel hotel = propertyAccessService.requireManagedHotel(dto.getHotelId());
        if (roomTypeRepository.findByCodeAndHotelId(dto.getCode(), hotel.getId()).isPresent()) {
            throw new IllegalArgumentException("Mã loại phòng đã tồn tại trong cơ sở này.");
        }
        roomType.setHotel(hotel);

        roomType = roomTypeRepository.save(roomType);
        syncImages(roomType, dto.getImageUrls());
        return mapToDTO(roomType);
    }

    @Override
    @Transactional
    public RoomTypeDTO updateRoomType(Long id, RoomTypeDTO dto) {
        RoomType roomType = roomTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Room type not found"));
        propertyAccessService.requireCanManage(roomType.getHotel().getId());
        if (dto.getHotelId() != null && !dto.getHotelId().equals(roomType.getHotel().getId())) {
            throw new IllegalArgumentException("Không thể chuyển loại phòng sang cơ sở khác.");
        }
        normalizeAndValidate(dto);
        roomTypeRepository.findByCodeAndHotelId(dto.getCode(), roomType.getHotel().getId())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> { throw new IllegalArgumentException("Mã loại phòng đã tồn tại trong cơ sở này."); });
        mapToEntity(dto, roomType);
        roomType = roomTypeRepository.save(roomType);
        syncImages(roomType, dto.getImageUrls());
        return mapToDTO(roomType);
    }

    @Override
    @Transactional
    public void deleteRoomType(Long id) {
        RoomType roomType = roomTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Room type not found"));
        propertyAccessService.requireCanManage(roomType.getHotel().getId());
        roomType.setStatus("INACTIVE");
        roomTypeRepository.save(roomType);
    }

    private void normalizeAndValidate(RoomTypeDTO dto) {
        if (dto == null) throw new IllegalArgumentException("Dữ liệu loại phòng không hợp lệ.");
        dto.setCode(dto.getCode() == null ? "" : dto.getCode().trim().toUpperCase(Locale.ROOT));
        dto.setNameVi(dto.getNameVi() == null ? "" : dto.getNameVi().trim());
        if (dto.getHotelId() == null || dto.getCode().isBlank() || dto.getNameVi().isBlank()) {
            throw new IllegalArgumentException("Cơ sở, mã và tên loại phòng là bắt buộc.");
        }
        if (dto.getBasePrice() == null || dto.getBasePrice().signum() < 0) {
            throw new IllegalArgumentException("Giá cơ bản không hợp lệ.");
        }
        if (dto.getMaxGuests() == null) dto.setMaxGuests(dto.getMaxGuest());
        if (dto.getMaxGuests() == null || dto.getMaxGuests() < 1) {
            throw new IllegalArgumentException("Sức chứa tối đa phải lớn hơn 0.");
        }
    }

    private void syncImages(RoomType roomType, List<String> imageUrls) {
        if (imageUrls == null) return;
        roomTypeImageRepository.deleteByRoomTypeId(roomType.getId());
        int order = 0;
        for (String rawUrl : imageUrls) {
            String url = rawUrl == null ? "" : rawUrl.trim();
            if (url.isBlank()) continue;
            com.hotel.entities.RoomTypeImage image = new com.hotel.entities.RoomTypeImage();
            image.setRoomType(roomType);
            image.setImageUrl(url);
            image.setSortOrder(order);
            image.setIsPrimary(order == 0);
            image.setAltTextVi(roomType.getNameVi());
            roomTypeImageRepository.save(image);
            order++;
        }
    }

    private RoomTypeDTO mapToDTO(RoomType entity) {
        RoomTypeDTO dto = new RoomTypeDTO();
        dto.setId(entity.getId());
        dto.setHotelId(entity.getHotel() != null ? entity.getHotel().getId() : null);
        dto.setCode(entity.getCode());
        dto.setNameVi(entity.getNameVi());
        dto.setNameEn(entity.getNameEn());
        dto.setNormalizedName(entity.getNormalizedName());
        dto.setArea(entity.getArea());
        dto.setIsDemo(entity.getIsDemo());
        dto.setMaxGuest(entity.getMaxGuest());
        dto.setBedType(entity.getBedType());
        dto.setBedCount(entity.getBedCount());
        dto.setMaxAdults(entity.getMaxAdults());
        dto.setMaxChildren(entity.getMaxChildren());
        dto.setMaxGuests(entity.getMaxGuests());
        dto.setHourlyPrice(entity.getHourlyPrice());
        dto.setStatus(entity.getStatus());
        dto.setTotalRooms(roomRepository.countByRoomTypeId(entity.getId()));
        dto.setBasePrice(entity.getBasePrice());
        dto.setDescriptionVi(entity.getDescriptionVi());
        dto.setDescriptionEn(entity.getDescriptionEn());
        dto.setImageUrls(roomTypeImageRepository.findByRoomTypeIdOrderBySortOrderAsc(entity.getId()).stream()
                .map(com.hotel.entities.RoomTypeImage::getImageUrl)
                .filter(url -> url != null && !url.isBlank())
                .distinct().toList());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private void mapToEntity(RoomTypeDTO dto, RoomType entity) {
        entity.setCode(dto.getCode());
        entity.setNameVi(dto.getNameVi());
        entity.setNameEn(dto.getNameEn());
        entity.setArea(dto.getArea());
        entity.setMaxGuest(dto.getMaxGuest());
        entity.setBedType(dto.getBedType());
        entity.setBedCount(dto.getBedCount());
        entity.setMaxAdults(dto.getMaxAdults());
        entity.setMaxChildren(dto.getMaxChildren());
        entity.setMaxGuests(dto.getMaxGuests() != null ? dto.getMaxGuests() : dto.getMaxGuest());
        entity.setHourlyPrice(dto.getHourlyPrice());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        entity.setBasePrice(dto.getBasePrice());
        entity.setDescriptionVi(dto.getDescriptionVi());
        entity.setDescriptionEn(dto.getDescriptionEn());
    }
}
