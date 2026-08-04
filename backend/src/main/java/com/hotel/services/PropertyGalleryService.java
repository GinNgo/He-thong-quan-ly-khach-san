package com.hotel.services;

import com.hotel.dtos.PropertyGalleryImageDTO;
import com.hotel.dtos.PropertyGalleryOrderRequest;
import com.hotel.dtos.PropertyImageLinkRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyImage;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.PropertyImageRepository;
import com.hotel.repositories.RoomImageRepository;
import com.hotel.repositories.RoomTypeImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PropertyGalleryService {

    private static final String MANAGED_UPLOAD_PREFIX = "/api/public/uploads/";

    private final HotelRepository hotelRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final RoomTypeImageRepository roomTypeImageRepository;
    private final RoomImageRepository roomImageRepository;
    private final PropertyAccessService propertyAccessService;
    private final SubscriptionFeatureService subscriptionFeatureService;
    private final FileUploadService fileUploadService;

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
        String imageUrl = normalizeImageUrl(request.imageUrl());
        ensureUniqueUrl(images, imageUrl);
        return addImage(hotel, images, imageUrl, request.altTextVi(), request.altTextEn(), request.primary());
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
        FileUploadService.StoredImage stored = fileUploadService.storePropertyImage(propertyId, file);
        scheduleRollbackCleanup(stored.url());
        try {
            return addImage(hotel, images, stored.url(), cleanAlt(altTextVi), cleanAlt(altTextEn), primary);
        } catch (RuntimeException exception) {
            fileUploadService.deleteManagedImage(stored.url());
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
        images.remove(removed);
        propertyImageRepository.delete(removed);
        for (int index = 0; index < images.size(); index++) {
            images.get(index).setSortOrder(index);
        }
        PropertyImage preferred = Boolean.TRUE.equals(removed.getIsPrimary()) ? firstOrNull(images) : primaryOrNull(images);
        ensureSinglePrimary(hotel, images, preferred);
        propertyImageRepository.saveAll(images);
        hotelRepository.save(hotel);
        scheduleDeleteAfterCommit(removed.getImageUrl());
        return images.stream().map(PropertyGalleryImageDTO::from).toList();
    }

    private PropertyGalleryImageDTO addImage(
            Hotel hotel,
            List<PropertyImage> images,
            String imageUrl,
            String altTextVi,
            String altTextEn,
            boolean requestedPrimary) {
        PropertyImage image = new PropertyImage();
        image.setHotel(hotel);
        image.setImageUrl(imageUrl);
        image.setAltTextVi(cleanAlt(altTextVi));
        image.setAltTextEn(cleanAlt(altTextEn));
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
        return new ArrayList<>(propertyImageRepository.findByHotelIdOrderBySortOrderAsc(propertyId));
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

    private String normalizeImageUrl(String value) {
        String url = value == null ? "" : value.trim();
        if (url.startsWith(MANAGED_UPLOAD_PREFIX)) {
            throw new IllegalArgumentException("Managed images must be added through the upload endpoint.");
        }
        try {
            URI parsed = new URI(url);
            String scheme = parsed.getScheme() == null ? "" : parsed.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || parsed.getHost() == null) {
                throw new IllegalArgumentException("Image URL must use HTTP or HTTPS.");
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Image URL is invalid.");
        }
        return url;
    }

    private String cleanAlt(String value) {
        if (value == null || value.isBlank()) return null;
        String cleaned = value.trim();
        if (cleaned.length() > 255) {
            throw new IllegalArgumentException("Image alternative text must not exceed 255 characters.");
        }
        return cleaned;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private void scheduleRollbackCleanup(String imageUrl) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    fileUploadService.deleteManagedImage(imageUrl);
                }
            }
        });
    }

    private void scheduleDeleteAfterCommit(String imageUrl) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            fileUploadService.deleteManagedImage(imageUrl);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fileUploadService.deleteManagedImage(imageUrl);
            }
        });
    }
}
