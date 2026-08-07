package com.hotel.services;

import com.hotel.dtos.PromotionQuoteDTO;
import com.hotel.dtos.RoomTypeDTO;
import com.hotel.entities.PropertyMedia;
import com.hotel.entities.RoomType;
import com.hotel.repositories.PropertyImageRepository;
import com.hotel.repositories.RoomImageRepository;
import com.hotel.repositories.RoomTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final PropertyImageRepository propertyImageRepository;
    private final RoomImageRepository roomImageRepository;
    private final PropertyAccessService propertyAccessService;
    private final SubscriptionFeatureService subscriptionFeatureService;
    private final PublicInventoryEligibilityPolicy publicInventoryEligibilityPolicy;
    private final PropertyMediaService propertyMediaService;
    private final com.hotel.repositories.ReservationDetailRepository reservationDetailRepository;

    @Autowired(required = false)
    private PromotionQuoteService promotionQuoteService;

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
        publicInventoryEligibilityPolicy.requirePublicProperty(hotelId);
        boolean hasStayDates = checkIn != null && checkOut != null;

        return roomTypeRepository.findByHotelId(hotelId).stream()
                .filter(publicInventoryEligibilityPolicy::isPubliclySellable)
                .filter(roomType -> guests == null || guests <= 0
                        || roomAvailabilityService.canHost(roomType, 1, guests, 0))
                .map(roomType -> {
                    RoomTypeDTO dto = mapToDTO(roomType);
                    roomAvailabilityService.enrich(dto, roomType, checkIn, checkOut);
                    if (promotionQuoteService != null && hasStayDates) {
                        PromotionQuoteDTO quote = promotionQuoteService.quoteForRoom(
                                roomType,
                                checkIn,
                                checkOut,
                                1,
                                Math.max(1, guests == null ? 1 : guests),
                                0,
                                null,
                                null);
                        dto.setQuote(quote);
                        dto.setTotalPrice(quote.finalTotal());
                    }
                    return dto;
                })
                .filter(dto -> !hasStayDates || (dto.getAvailableRooms() != null && dto.getAvailableRooms() > 0))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public RoomTypeDTO getRoomTypeById(Long id) {
        RoomType roomType = roomTypeRepository.findById(id)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy loại phòng."));
        propertyAccessService.requireAccessibleOrNotFound(roomType.getHotel().getId(), "loại phòng");
        return mapToDTO(roomType);
    }

    @Override
    @Transactional
    public RoomTypeDTO createRoomType(RoomTypeDTO dto) {
        normalizeAndValidate(dto);
        RoomType roomType = new RoomType();
        mapToEntity(dto, roomType);

        com.hotel.entities.Hotel hotel = propertyAccessService.requireManagedHotel(dto.getHotelId());
        Long hotelId = hotel.getId();
        requireCapacity(hotelId, "MAX_ROOM_TYPES", roomTypeRepository.countByHotelId(hotelId), 1);
        requireImageCapacity(hotelId, 0, imageCount(dto.getImageUrls()));
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
        RoomType roomType = roomTypeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy loại phòng."));
        propertyAccessService.requireAccessibleOrNotFound(roomType.getHotel().getId(), "loại phòng");
        requireFeature(roomType.getHotel().getId(), "MAX_ROOM_TYPES");
        if (dto.getImageUrls() != null) {
            requireImageCapacity(
                    roomType.getHotel().getId(),
                    roomTypeImageRepository.findByRoomTypeIdOrderBySortOrderAsc(id).size(),
                    imageCount(dto.getImageUrls()));
        }
        if (dto.getHotelId() != null && !dto.getHotelId().equals(roomType.getHotel().getId())) {
            throw new IllegalArgumentException("Không thể chuyển loại phòng sang cơ sở khác.");
        }
        normalizeAndValidate(dto);
        if ("ACTIVE".equals(roomType.getStatus()) && "INACTIVE".equals(dto.getStatus())) {
            requireNoActiveBookings(roomType);
        }
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
        RoomType roomType = roomTypeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy loại phòng."));
        propertyAccessService.requireAccessibleOrNotFound(roomType.getHotel().getId(), "loại phòng");
        requireFeature(roomType.getHotel().getId(), "MAX_ROOM_TYPES");
        if ("INACTIVE".equals(roomType.getStatus())) return;
        requireNoActiveBookings(roomType);
        roomType.setStatus("INACTIVE");
        roomTypeRepository.save(roomType);
    }

    private void normalizeAndValidate(RoomTypeDTO dto) {
        if (dto == null) throw new IllegalArgumentException("Dữ liệu loại phòng không hợp lệ.");
        dto.setCode(dto.getCode() == null ? "" : dto.getCode().trim().toUpperCase(Locale.ROOT));
        dto.setNameVi(dto.getNameVi() == null ? "" : dto.getNameVi().trim());
        dto.setNameEn(dto.getNameEn() == null || dto.getNameEn().isBlank() ? dto.getNameVi() : dto.getNameEn().trim());
        dto.setStatus(dto.getStatus() == null || dto.getStatus().isBlank()
                ? "ACTIVE" : dto.getStatus().trim().toUpperCase(Locale.ROOT));
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
        int adults = dto.getMaxAdults() == null ? 1 : dto.getMaxAdults();
        int children = dto.getMaxChildren() == null ? 0 : dto.getMaxChildren();
        if (adults < 1 || children < 0 || dto.getMaxGuests() < adults || dto.getMaxGuests() < adults + children) {
            throw new IllegalArgumentException("Sức chứa người lớn, trẻ em và tổng khách không nhất quán.");
        }
        dto.setMaxAdults(adults);
        dto.setMaxChildren(children);
        if (dto.getBedCount() != null && dto.getBedCount() < 1) {
            throw new IllegalArgumentException("Số giường phải lớn hơn 0.");
        }
        if (dto.getArea() != null && dto.getArea().signum() <= 0) {
            throw new IllegalArgumentException("Diện tích phải lớn hơn 0.");
        }
        if (dto.getHourlyPrice() != null && dto.getHourlyPrice().signum() < 0) {
            throw new IllegalArgumentException("Giá theo giờ không hợp lệ.");
        }
        if (!java.util.Set.of("ACTIVE", "INACTIVE").contains(dto.getStatus())) {
            throw new IllegalArgumentException("Trạng thái loại phòng không hợp lệ.");
        }
    }

    private void syncImages(RoomType roomType, List<String> imageUrls) {
        if (imageUrls == null) return;
        List<com.hotel.entities.RoomTypeImage> existing =
                roomTypeImageRepository.findByRoomTypeIdOrderBySortOrderAsc(roomType.getId());
        List<PropertyMedia> previousMedia = existing.stream()
                .map(com.hotel.entities.RoomTypeImage::getMedia)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        java.util.Set<String> uniqueUrls = new java.util.LinkedHashSet<>();
        for (String rawUrl : imageUrls) {
            if (rawUrl == null || rawUrl.isBlank()) continue;
            String url = rawUrl.trim();
            if (!uniqueUrls.add(url)) {
                throw new IllegalArgumentException("Room-type image URLs must be unique.");
            }
        }
        roomTypeImageRepository.deleteByRoomTypeId(roomType.getId());
        int order = 0;
        for (String url : uniqueUrls) {
            PropertyMedia media = propertyMediaService.createExternal(
                    roomType.getHotel(), url, roomType.getNameVi(), roomType.getNameEn());
            com.hotel.entities.RoomTypeImage image = new com.hotel.entities.RoomTypeImage();
            image.setRoomType(roomType);
            image.setMedia(media);
            image.setImageUrl(media.getPublicUrl());
            image.setSortOrder(order);
            image.setIsPrimary(order == 0);
            image.setAltTextVi(media.getAltTextVi());
            image.setAltTextEn(media.getAltTextEn());
            image.setIsDemo(false);
            roomTypeImageRepository.save(image);
            order++;
        }
        roomTypeImageRepository.flush();
        previousMedia.forEach(propertyMediaService::releaseIfUnreferenced);
    }

    private void requireFeature(Long hotelId, String featureCode) {
        if (propertyAccessService.isSystemAdministrator()) return;
        subscriptionFeatureService.requireFeatureForProperty(hotelId, featureCode);
    }

    private void requireCapacity(Long hotelId, String featureCode, long currentUsage, long addition) {
        if (propertyAccessService.isSystemAdministrator()) return;
        subscriptionFeatureService.checkFeatureLimitForProperty(hotelId, featureCode, currentUsage, addition);
    }

    private void requireImageCapacity(Long hotelId, long replacedImages, long requestedImages) {
        if (replacedImages == 0 && requestedImages == 0) return;
        if (propertyAccessService.isSystemAdministrator()) return;
        long currentUsage = propertyImageRepository.countByHotelId(hotelId)
                + roomTypeImageRepository.countByRoomTypeHotelId(hotelId)
                + roomImageRepository.countByRoomHotelId(hotelId);
        long retainedUsage = Math.max(0, currentUsage - replacedImages);
        subscriptionFeatureService.checkFeatureLimitForProperty(
                hotelId, "MAX_IMAGES", retainedUsage, requestedImages);
    }

    private long imageCount(List<String> imageUrls) {
        if (imageUrls == null) return 0;
        return imageUrls.stream()
                .filter(url -> url != null && !url.trim().isBlank())
                .count();
    }

    private void requireNoActiveBookings(RoomType roomType) {
        long active = reservationDetailRepository.countActiveByRoomTypeId(
                roomType.getId(), RoomAvailabilityService.RELEASED_RESERVATION_STATUSES);
        if (active > 0) {
            throw new IllegalStateException("Không thể ngừng loại phòng khi còn booking đang hoạt động.");
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
