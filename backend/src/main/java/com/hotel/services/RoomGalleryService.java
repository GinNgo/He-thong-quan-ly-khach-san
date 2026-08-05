package com.hotel.services;

import com.hotel.dtos.RoomGalleryImageDTO;
import com.hotel.dtos.RoomGalleryOrderRequest;
import com.hotel.dtos.RoomImageLinkRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyMedia;
import com.hotel.entities.Room;
import com.hotel.entities.RoomImage;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.PropertyImageRepository;
import com.hotel.repositories.RoomImageRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.RoomTypeImageRepository;
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
public class RoomGalleryService {
    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final RoomImageRepository roomImageRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final RoomTypeImageRepository roomTypeImageRepository;
    private final PropertyAccessService propertyAccessService;
    private final SubscriptionFeatureService subscriptionFeatureService;
    private final PropertyMediaService propertyMediaService;
    private final PropertyMediaPolicy propertyMediaPolicy;

    @Transactional(readOnly = true)
    public List<RoomGalleryImageDTO> list(Long roomId) {
        Room room = requireAccessibleRoom(roomId);
        return images(room.getId()).stream().map(RoomGalleryImageDTO::from).toList();
    }

    @Transactional
    public RoomGalleryImageDTO addLink(Long roomId, RoomImageLinkRequest request) {
        LockedGallery gallery = lockGallery(roomId);
        requireCapacity(gallery.hotel().getId());
        String url = propertyMediaPolicy.normalizeExternalUrl(request.imageUrl());
        ensureUniqueUrl(gallery.images(), url);
        PropertyMedia media = propertyMediaService.createExternal(
                gallery.hotel(), url, request.altTextVi(), request.altTextEn());
        try {
            return add(gallery.room(), gallery.images(), media, request.primary());
        } catch (RuntimeException exception) {
            propertyMediaService.discardAfterFailedAssociation(media);
            throw exception;
        }
    }

    @Transactional
    public RoomGalleryImageDTO upload(Long roomId, MultipartFile file, String altVi, String altEn, boolean primary) {
        LockedGallery gallery = lockGallery(roomId);
        requireCapacity(gallery.hotel().getId());
        PropertyMedia media = propertyMediaService.createUpload(gallery.hotel(), file, altVi, altEn);
        try {
            return add(gallery.room(), gallery.images(), media, primary);
        } catch (RuntimeException exception) {
            propertyMediaService.discardAfterFailedAssociation(media);
            throw exception;
        }
    }

    @Transactional
    public List<RoomGalleryImageDTO> reorder(Long roomId, RoomGalleryOrderRequest request) {
        LockedGallery gallery = lockGallery(roomId);
        List<Long> ids = request.imageIds();
        Set<Long> unique = new HashSet<>(ids);
        Set<Long> current = gallery.images().stream().map(RoomImage::getId).collect(java.util.stream.Collectors.toSet());
        if (unique.size() != ids.size()) throw new IllegalArgumentException("Each room image must appear exactly once.");
        if (!current.equals(unique)) throw new ResourceNotFoundException("Room image not found.");
        java.util.Map<Long, RoomImage> byId = gallery.images().stream()
                .collect(java.util.stream.Collectors.toMap(RoomImage::getId, image -> image));
        List<RoomImage> ordered = new ArrayList<>();
        for (int index = 0; index < ids.size(); index++) {
            RoomImage image = byId.get(ids.get(index));
            image.setSortOrder(1_000_000 + index);
            ordered.add(image);
        }
        roomImageRepository.saveAllAndFlush(ordered);
        for (int index = 0; index < ordered.size(); index++) ordered.get(index).setSortOrder(index);
        ensureSinglePrimary(ordered, null);
        roomImageRepository.saveAll(ordered);
        return ordered.stream().map(RoomGalleryImageDTO::from).toList();
    }

    @Transactional
    public RoomGalleryImageDTO setPrimary(Long roomId, Long imageId) {
        LockedGallery gallery = lockGallery(roomId);
        RoomImage selected = requireImage(gallery.images(), imageId);
        gallery.images().forEach(image -> image.setIsPrimary(false));
        roomImageRepository.saveAllAndFlush(gallery.images());
        selected.setIsPrimary(true);
        return RoomGalleryImageDTO.from(roomImageRepository.saveAndFlush(selected));
    }

    @Transactional
    public List<RoomGalleryImageDTO> delete(Long roomId, Long imageId) {
        LockedGallery gallery = lockGallery(roomId);
        RoomImage removed = requireImage(gallery.images(), imageId);
        PropertyMedia media = removed.getMedia();
        gallery.images().remove(removed);
        roomImageRepository.delete(removed);
        roomImageRepository.flush();
        for (int index = 0; index < gallery.images().size(); index++) {
            gallery.images().get(index).setSortOrder(1_000_000 + index);
        }
        roomImageRepository.saveAllAndFlush(gallery.images());
        for (int index = 0; index < gallery.images().size(); index++) gallery.images().get(index).setSortOrder(index);
        ensureSinglePrimary(gallery.images(), Boolean.TRUE.equals(removed.getIsPrimary()) ? first(gallery.images()) : primary(gallery.images()));
        roomImageRepository.saveAll(gallery.images());
        propertyMediaService.releaseIfUnreferenced(media);
        return gallery.images().stream().map(RoomGalleryImageDTO::from).toList();
    }

    private LockedGallery lockGallery(Long roomId) {
        Room snapshot = requireAccessibleRoom(roomId);
        Long hotelId = snapshot.getHotel().getId();
        Hotel hotel = hotelRepository.findByIdForUpdate(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found."));
        requireAccess(hotelId);
        requireMutable(hotel);
        Room locked = roomRepository.findByIdForUpdate(roomId)
                .filter(room -> hotelId.equals(room.getHotel().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Room not found."));
        return new LockedGallery(hotel, locked, images(roomId));
    }

    private Room requireAccessibleRoom(Long roomId) {
        if (roomId == null) throw new ResourceNotFoundException("Room not found.");
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found."));
        requireAccess(room.getHotel().getId());
        return room;
    }

    private void requireAccess(Long hotelId) {
        if (!propertyAccessService.isSystemAdministrator() && !propertyAccessService.assignedHotelIds().contains(hotelId)) {
            throw new ResourceNotFoundException("Room not found.");
        }
    }

    private void requireMutable(Hotel hotel) {
        String approval = normalize(hotel.getApprovalStatus());
        String operation = normalize(hotel.getOperationStatus());
        if ("CLOSED".equals(operation) || "CLOSED".equals(normalize(hotel.getStatus())))
            throw new IllegalStateException("Closed properties are retained as read-only records.");
        if ("PENDING_APPROVAL".equals(approval)) throw new IllegalStateException("A property under review cannot be edited.");
    }

    private void requireCapacity(Long hotelId) {
        long usage = propertyImageRepository.countByHotelId(hotelId)
                + roomTypeImageRepository.countByRoomTypeHotelId(hotelId)
                + roomImageRepository.countByRoomHotelId(hotelId);
        subscriptionFeatureService.checkFeatureLimitForProperty(hotelId, "MAX_IMAGES", usage, 1);
    }

    private RoomGalleryImageDTO add(Room room, List<RoomImage> images, PropertyMedia media, boolean makePrimary) {
        if (!room.getHotel().getId().equals(media.getHotel().getId())) throw new ResourceNotFoundException("Media not found.");
        RoomImage image = new RoomImage();
        image.setRoom(room);
        image.setMedia(media);
        image.setImageUrl(media.getPublicUrl());
        image.setAltTextVi(media.getAltTextVi());
        image.setAltTextEn(media.getAltTextEn());
        image.setSortOrder(images.size());
        if (makePrimary && !images.isEmpty()) {
            images.forEach(existing -> existing.setIsPrimary(false));
            roomImageRepository.saveAllAndFlush(images);
        }
        images.add(image);
        ensureSinglePrimary(images, makePrimary || images.size() == 1 ? image : primary(images));
        return RoomGalleryImageDTO.from(roomImageRepository.saveAndFlush(image));
    }

    private List<RoomImage> images(Long roomId) {
        return new ArrayList<>(roomImageRepository.findByRoomIdOrderBySortOrderAsc(roomId));
    }

    private void ensureUniqueUrl(List<RoomImage> images, String url) {
        if (images.stream().anyMatch(image -> url.equals(image.getImageUrl()))) {
            throw new IllegalArgumentException("This image is already present in the room gallery.");
        }
    }

    private RoomImage requireImage(List<RoomImage> images, Long id) {
        return images.stream().filter(image -> id != null && id.equals(image.getId())).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Room image not found."));
    }

    private void ensureSinglePrimary(List<RoomImage> images, RoomImage preferred) {
        RoomImage selected = preferred != null && images.contains(preferred) ? preferred : primary(images);
        if (selected == null) selected = first(images);
        for (RoomImage image : images) image.setIsPrimary(image == selected);
    }

    private RoomImage primary(List<RoomImage> images) {
        return images.stream().filter(image -> Boolean.TRUE.equals(image.getIsPrimary())).findFirst().orElse(null);
    }

    private RoomImage first(List<RoomImage> images) { return images.isEmpty() ? null : images.getFirst(); }
    private String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT); }
    private record LockedGallery(Hotel hotel, Room room, List<RoomImage> images) { }
}
