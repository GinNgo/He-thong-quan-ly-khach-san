package com.hotel.services;

import com.hotel.dtos.RoomTypeGalleryImageDTO;
import com.hotel.dtos.RoomTypeGalleryOrderRequest;
import com.hotel.dtos.RoomTypeImageLinkRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyMedia;
import com.hotel.entities.RoomType;
import com.hotel.entities.RoomTypeImage;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.PropertyImageRepository;
import com.hotel.repositories.RoomImageRepository;
import com.hotel.repositories.RoomTypeImageRepository;
import com.hotel.repositories.RoomTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoomTypeGalleryService {
    private final HotelRepository hotelRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomTypeImageRepository roomTypeImageRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final RoomImageRepository roomImageRepository;
    private final PropertyAccessService propertyAccessService;
    private final SubscriptionFeatureService subscriptionFeatureService;
    private final PropertyMediaService propertyMediaService;
    private final PropertyMediaPolicy propertyMediaPolicy;

    @Transactional(readOnly = true)
    public List<RoomTypeGalleryImageDTO> list(Long roomTypeId) {
        RoomType roomType = requireAccessibleRoomType(roomTypeId);
        return images(roomType.getId()).stream().map(RoomTypeGalleryImageDTO::from).toList();
    }

    @Transactional
    public RoomTypeGalleryImageDTO addLink(Long roomTypeId, RoomTypeImageLinkRequest request) {
        LockedGallery gallery = lockGallery(roomTypeId);
        requireCapacity(gallery.hotel().getId(), gallery.images().size());
        String url = propertyMediaPolicy.normalizeExternalUrl(request.imageUrl());
        ensureUniqueUrl(gallery.images(), url);
        PropertyMedia media = propertyMediaService.createExternal(
                gallery.hotel(), url, request.altTextVi(), request.altTextEn());
        try {
            return add(gallery.roomType(), gallery.images(), media, request.primary());
        } catch (RuntimeException exception) {
            propertyMediaService.discardAfterFailedAssociation(media);
            throw exception;
        }
    }

    @Transactional
    public RoomTypeGalleryImageDTO upload(Long roomTypeId, MultipartFile file, String altVi, String altEn, boolean primary) {
        LockedGallery gallery = lockGallery(roomTypeId);
        requireCapacity(gallery.hotel().getId(), gallery.images().size());
        PropertyMedia media = propertyMediaService.createUpload(gallery.hotel(), file, altVi, altEn);
        try {
            return add(gallery.roomType(), gallery.images(), media, primary);
        } catch (RuntimeException exception) {
            propertyMediaService.discardAfterFailedAssociation(media);
            throw exception;
        }
    }

    @Transactional
    public List<RoomTypeGalleryImageDTO> reorder(Long roomTypeId, RoomTypeGalleryOrderRequest request) {
        LockedGallery gallery = lockGallery(roomTypeId);
        List<Long> ids = request.imageIds();
        Set<Long> unique = new HashSet<>(ids);
        Set<Long> current = gallery.images().stream().map(RoomTypeImage::getId).collect(java.util.stream.Collectors.toSet());
        if (unique.size() != ids.size()) throw new IllegalArgumentException("Each room-type image must appear exactly once.");
        if (!current.equals(unique)) throw new ResourceNotFoundException("Room-type image not found.");
        java.util.Map<Long, RoomTypeImage> byId = gallery.images().stream()
                .collect(java.util.stream.Collectors.toMap(RoomTypeImage::getId, image -> image));
        List<RoomTypeImage> ordered = new ArrayList<>();
        for (int index = 0; index < ids.size(); index++) {
            RoomTypeImage image = byId.get(ids.get(index));
            image.setSortOrder(1_000_000 + index);
            ordered.add(image);
        }
        roomTypeImageRepository.saveAllAndFlush(ordered);
        for (int index = 0; index < ordered.size(); index++) ordered.get(index).setSortOrder(index);
        ensureSinglePrimary(ordered, null);
        roomTypeImageRepository.saveAll(ordered);
        return ordered.stream().map(RoomTypeGalleryImageDTO::from).toList();
    }

    @Transactional
    public RoomTypeGalleryImageDTO setPrimary(Long roomTypeId, Long imageId) {
        LockedGallery gallery = lockGallery(roomTypeId);
        RoomTypeImage selected = requireImage(gallery.images(), imageId);
        gallery.images().forEach(image -> image.setIsPrimary(false));
        roomTypeImageRepository.saveAllAndFlush(gallery.images());
        selected.setIsPrimary(true);
        roomTypeImageRepository.saveAndFlush(selected);
        return RoomTypeGalleryImageDTO.from(selected);
    }

    @Transactional
    public List<RoomTypeGalleryImageDTO> delete(Long roomTypeId, Long imageId) {
        LockedGallery gallery = lockGallery(roomTypeId);
        RoomTypeImage removed = requireImage(gallery.images(), imageId);
        PropertyMedia media = removed.getMedia();
        gallery.images().remove(removed);
        roomTypeImageRepository.delete(removed);
        roomTypeImageRepository.flush();
        for (int index = 0; index < gallery.images().size(); index++) {
            gallery.images().get(index).setSortOrder(1_000_000 + index);
        }
        roomTypeImageRepository.saveAllAndFlush(gallery.images());
        for (int index = 0; index < gallery.images().size(); index++) gallery.images().get(index).setSortOrder(index);
        ensureSinglePrimary(gallery.images(), Boolean.TRUE.equals(removed.getIsPrimary()) ? first(gallery.images()) : primary(gallery.images()));
        roomTypeImageRepository.saveAll(gallery.images());
        propertyMediaService.releaseIfUnreferenced(media);
        return gallery.images().stream().map(RoomTypeGalleryImageDTO::from).toList();
    }

    private LockedGallery lockGallery(Long roomTypeId) {
        RoomType snapshot = requireAccessibleRoomType(roomTypeId);
        Long hotelId = snapshot.getHotel().getId();
        Hotel hotel = hotelRepository.findByIdForUpdate(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Room type not found."));
        requireAccess(hotelId);
        requireMutable(hotel);
        RoomType locked = roomTypeRepository.findByIdForUpdate(roomTypeId)
                .filter(roomType -> hotelId.equals(roomType.getHotel().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Room type not found."));
        return new LockedGallery(hotel, locked, images(roomTypeId));
    }

    private RoomType requireAccessibleRoomType(Long roomTypeId) {
        if (roomTypeId == null) throw new ResourceNotFoundException("Room type not found.");
        RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Room type not found."));
        requireAccess(roomType.getHotel().getId());
        return roomType;
    }

    private void requireAccess(Long hotelId) {
        if (!propertyAccessService.isSystemAdministrator() && !propertyAccessService.assignedHotelIds().contains(hotelId)) {
            throw new ResourceNotFoundException("Room type not found.");
        }
    }

    private void requireMutable(Hotel hotel) {
        String approval = normalize(hotel.getApprovalStatus());
        String operation = normalize(hotel.getOperationStatus());
        if ("CLOSED".equals(operation) || "CLOSED".equals(normalize(hotel.getStatus())))
            throw new IllegalStateException("Closed properties are retained as read-only records.");
        if ("PENDING_APPROVAL".equals(approval)) throw new IllegalStateException("A property under review cannot be edited.");
    }

    private void requireCapacity(Long hotelId, long currentGallerySize) {
        long usage = propertyImageRepository.countByHotelId(hotelId)
                + roomTypeImageRepository.countByRoomTypeHotelId(hotelId)
                + roomImageRepository.countByRoomHotelId(hotelId);
        subscriptionFeatureService.checkFeatureLimitForProperty(hotelId, "MAX_IMAGES", usage, 1);
    }

    private RoomTypeGalleryImageDTO add(RoomType roomType, List<RoomTypeImage> images, PropertyMedia media, boolean primary) {
        if (!roomType.getHotel().getId().equals(media.getHotel().getId())) throw new ResourceNotFoundException("Media not found.");
        RoomTypeImage image = new RoomTypeImage();
        image.setRoomType(roomType); image.setMedia(media); image.setImageUrl(media.getPublicUrl());
        image.setAltTextVi(media.getAltTextVi()); image.setAltTextEn(media.getAltTextEn());
        image.setSortOrder(images.size()); image.setIsDemo(false);
        if (primary && !images.isEmpty()) {
            images.forEach(existing -> existing.setIsPrimary(false));
            roomTypeImageRepository.saveAllAndFlush(images);
        }
        images.add(image);
        ensureSinglePrimary(images, primary || images.size() == 1 ? image : primary(images));
        return RoomTypeGalleryImageDTO.from(roomTypeImageRepository.saveAndFlush(image));
    }

    private List<RoomTypeImage> images(Long id) { return new ArrayList<>(roomTypeImageRepository.findByRoomTypeIdOrderBySortOrderAsc(id)); }
    private void ensureUniqueUrl(List<RoomTypeImage> images, String url) {
        if (images.stream().anyMatch(image -> url.equals(image.getImageUrl()))) throw new IllegalArgumentException("This image is already present in the room-type gallery.");
    }
    private RoomTypeImage requireImage(List<RoomTypeImage> images, Long id) {
        return images.stream().filter(image -> id != null && id.equals(image.getId())).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Room-type image not found."));
    }
    private void ensureSinglePrimary(List<RoomTypeImage> images, RoomTypeImage preferred) {
        RoomTypeImage selected = preferred != null && images.contains(preferred) ? preferred : primary(images);
        if (selected == null) selected = first(images);
        for (RoomTypeImage image : images) image.setIsPrimary(image == selected);
    }
    private RoomTypeImage primary(List<RoomTypeImage> images) { return images.stream().filter(i -> Boolean.TRUE.equals(i.getIsPrimary())).findFirst().orElse(null); }
    private RoomTypeImage first(List<RoomTypeImage> images) { return images.isEmpty() ? null : images.getFirst(); }
    private String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT); }
    private record LockedGallery(Hotel hotel, RoomType roomType, List<RoomTypeImage> images) { }
}
