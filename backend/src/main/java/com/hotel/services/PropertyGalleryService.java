package com.hotel.services;

import com.hotel.dtos.PropertyGalleryImageDTO;
import com.hotel.dtos.PropertyGalleryOrderRequest;
import com.hotel.dtos.PropertyImageLinkRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyMedia;
import com.hotel.entities.PropertyImage;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.PropertyImageRepository;
import com.hotel.repositories.RoomImageRepository;
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
public class PropertyGalleryService {

    private final HotelRepository hotelRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final RoomTypeImageRepository roomTypeImageRepository;
    private final RoomImageRepository roomImageRepository;
    private final PropertyAccessService propertyAccessService;
    private final SubscriptionFeatureService subscriptionFeatureService;
    private final PropertyMediaService propertyMediaService;
    private final PropertyMediaPolicy propertyMediaPolicy;

    @Transactional(readOnly = true)
    public List<PropertyGalleryImageDTO> list(Long propertyId) {
        requireAccessibleProperty(propertyId);
        return sortedImages(propertyId).stream().map(PropertyGalleryImageDTO::from).toList();
    }

    @Transactional
    public PropertyGalleryImageDTO addLink(Long propertyId, PropertyImageLinkRequest request) {
        Hotel hotel = requireLockedMutableProperty(propertyId);
        List<PropertyImage> images = sortedImages(propertyId);
        requireImageCapacity(propertyId, images.size());
        String imageUrl = propertyMediaPolicy.normalizeExternalUrl(request.imageUrl());
        ensureUniqueUrl(images, imageUrl);
        PropertyMedia media = propertyMediaService.createExternal(
                hotel, imageUrl, request.altTextVi(), request.altTextEn());
        try {
            return addImage(hotel, images, media, request.primary());
        } catch (RuntimeException exception) {
            propertyMediaService.discardAfterFailedAssociation(media);
            throw exception;
        }
    }

    @Transactional
    public PropertyGalleryImageDTO upload(
            Long propertyId,
            MultipartFile file,
            String altTextVi,
            String altTextEn,
            boolean primary) {
        Hotel hotel = requireLockedMutableProperty(propertyId);
        List<PropertyImage> images = sortedImages(propertyId);
        requireImageCapacity(propertyId, images.size());
        PropertyMedia media = propertyMediaService.createUpload(hotel, file, altTextVi, altTextEn);
        try {
            return addImage(hotel, images, media, primary);
        } catch (RuntimeException exception) {
            propertyMediaService.discardAfterFailedAssociation(media);
            throw exception;
        }
    }

    @Transactional
    public List<PropertyGalleryImageDTO> reorder(Long propertyId, PropertyGalleryOrderRequest request) {
        Hotel hotel = requireLockedMutableProperty(propertyId);
        List<PropertyImage> images = sortedImages(propertyId);
        List<Long> requestedIds = request.imageIds();
        Set<Long> uniqueIds = new HashSet<>(requestedIds);
        Set<Long> currentIds = images.stream().map(PropertyImage::getId).collect(java.util.stream.Collectors.toSet());
        if (uniqueIds.size() != requestedIds.size()) {
            throw new IllegalArgumentException("Each gallery image must appear exactly once.");
        }
        if (!currentIds.equals(uniqueIds)) {
            throw new ResourceNotFoundException("Gallery image not found.");
        }

        java.util.Map<Long, PropertyImage> byId = images.stream()
                .collect(java.util.stream.Collectors.toMap(PropertyImage::getId, image -> image));
        List<PropertyImage> reordered = new ArrayList<>(images.size());
        for (int index = 0; index < requestedIds.size(); index++) {
            PropertyImage image = byId.get(requestedIds.get(index));
            image.setSortOrder(index);
            reordered.add(image);
        }
        ensureSinglePrimary(hotel, reordered, null);
        propertyImageRepository.saveAll(reordered);
        hotelRepository.save(hotel);
        return reordered.stream().map(PropertyGalleryImageDTO::from).toList();
    }

    @Transactional
    public PropertyGalleryImageDTO setPrimary(Long propertyId, Long imageId) {
        Hotel hotel = requireLockedMutableProperty(propertyId);
        List<PropertyImage> images = sortedImages(propertyId);
        PropertyImage selected = requireGalleryImage(images, imageId);
        ensureSinglePrimary(hotel, images, selected);
        propertyImageRepository.saveAll(images);
        hotelRepository.save(hotel);
        return PropertyGalleryImageDTO.from(selected);
    }

    @Transactional
    public List<PropertyGalleryImageDTO> delete(Long propertyId, Long imageId) {
        Hotel hotel = requireLockedMutableProperty(propertyId);
        List<PropertyImage> images = sortedImages(propertyId);
        PropertyImage removed = requireGalleryImage(images, imageId);
        PropertyMedia media = removed.getMedia();
        images.remove(removed);
        propertyImageRepository.delete(removed);
        propertyImageRepository.flush();
        for (int index = 0; index < images.size(); index++) {
            images.get(index).setSortOrder(index);
        }
        PropertyImage preferred = Boolean.TRUE.equals(removed.getIsPrimary()) ? firstOrNull(images) : primaryOrNull(images);
        ensureSinglePrimary(hotel, images, preferred);
        propertyImageRepository.saveAll(images);
        hotelRepository.save(hotel);
        propertyMediaService.releaseIfUnreferenced(media);
        return images.stream().map(PropertyGalleryImageDTO::from).toList();
    }

    private PropertyGalleryImageDTO addImage(
            Hotel hotel,
            List<PropertyImage> images,
            PropertyMedia media,
            boolean requestedPrimary) {
        PropertyImage image = new PropertyImage();
        image.setHotel(hotel);
        image.setMedia(media);
        image.setImageUrl(media.getPublicUrl());
        image.setAltTextVi(media.getAltTextVi());
        image.setAltTextEn(media.getAltTextEn());
        image.setSortOrder(images.size());
        image.setIsDemo(false);
        images.add(image);
        ensureSinglePrimary(hotel, images, requestedPrimary || images.size() == 1 ? image : primaryOrNull(images));
        PropertyImage saved = propertyImageRepository.saveAndFlush(image);
        hotelRepository.save(hotel);
        return PropertyGalleryImageDTO.from(saved);
    }

    private Hotel requireAccessibleProperty(Long propertyId) {
        Hotel hotel = hotelRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found."));
        requireAccess(propertyId);
        return hotel;
    }

    private Hotel requireLockedMutableProperty(Long propertyId) {
        if (propertyId == null) {
            throw new ResourceNotFoundException("Property not found.");
        }
        Hotel hotel = hotelRepository.findByIdForUpdate(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found."));
        requireAccess(propertyId);
        String approval = normalize(hotel.getApprovalStatus());
        String operation = normalize(hotel.getOperationStatus());
        if ("CLOSED".equals(operation) || "CLOSED".equals(normalize(hotel.getStatus()))) {
            throw new IllegalStateException("Closed properties are retained as read-only records.");
        }
        if ("PENDING_APPROVAL".equals(approval)) {
            throw new IllegalStateException("A property under review cannot be edited.");
        }
        return hotel;
    }

    private void requireAccess(Long propertyId) {
        if (!propertyAccessService.isSystemAdministrator()
                && !propertyAccessService.assignedHotelIds().contains(propertyId)) {
            throw new ResourceNotFoundException("Property not found.");
        }
    }

    private void requireImageCapacity(Long propertyId, long propertyImageCount) {
        long currentUsage = propertyImageCount
                + roomTypeImageRepository.countByRoomTypeHotelId(propertyId)
                + roomImageRepository.countByRoomHotelId(propertyId);
        subscriptionFeatureService.checkFeatureLimitForProperty(propertyId, "MAX_IMAGES", currentUsage, 1);
    }

    private List<PropertyImage> sortedImages(Long propertyId) {
        return new ArrayList<>(propertyImageRepository.findByHotelIdOrderBySortOrderAscIdAsc(propertyId));
    }

    private void ensureUniqueUrl(List<PropertyImage> images, String imageUrl) {
        if (images.stream().anyMatch(image -> imageUrl.equals(image.getImageUrl()))) {
            throw new IllegalArgumentException("This image is already present in the property gallery.");
        }
    }

    private PropertyImage requireGalleryImage(List<PropertyImage> images, Long imageId) {
        if (imageId == null) {
            throw new ResourceNotFoundException("Gallery image not found.");
        }
        return images.stream()
                .filter(image -> imageId.equals(image.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Gallery image not found."));
    }

    private void ensureSinglePrimary(Hotel hotel, List<PropertyImage> images, PropertyImage preferred) {
        PropertyImage primary = preferred != null && images.contains(preferred)
                ? preferred
                : primaryOrNull(images);
        if (primary == null) primary = firstOrNull(images);
        for (PropertyImage image : images) {
            image.setIsPrimary(image == primary);
        }
        hotel.setMainImage(primary == null ? null : primary.getImageUrl());
    }

    private PropertyImage primaryOrNull(List<PropertyImage> images) {
        return images.stream().filter(image -> Boolean.TRUE.equals(image.getIsPrimary())).findFirst().orElse(null);
    }

    private PropertyImage firstOrNull(List<PropertyImage> images) {
        return images.isEmpty() ? null : images.getFirst();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
