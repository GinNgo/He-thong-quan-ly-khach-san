package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyMedia;
import com.hotel.repositories.PropertyImageRepository;
import com.hotel.repositories.PropertyMediaRepository;
import com.hotel.repositories.RoomImageRepository;
import com.hotel.repositories.RoomTypeImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PropertyMediaService {

    private final PropertyMediaRepository propertyMediaRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final RoomTypeImageRepository roomTypeImageRepository;
    private final RoomImageRepository roomImageRepository;
    private final PropertyMediaPolicy propertyMediaPolicy;
    private final FileUploadService fileUploadService;

    public PropertyMedia createExternal(
            Hotel hotel,
            String imageUrl,
            String altTextVi,
            String altTextEn) {
        PropertyMedia media = baseMedia(hotel, altTextVi, altTextEn);
        media.setSourceType("EXTERNAL_HTTPS");
        media.setPublicUrl(propertyMediaPolicy.normalizeExternalUrl(imageUrl));
        return propertyMediaRepository.saveAndFlush(media);
    }

    public PropertyMedia createUpload(
            Hotel hotel,
            MultipartFile file,
            String altTextVi,
            String altTextEn) {
        String normalizedAltVi = propertyMediaPolicy.requireAltTextVi(altTextVi);
        String normalizedAltEn = propertyMediaPolicy.normalizeAltTextEn(altTextEn);
        FileUploadService.StoredImage stored = fileUploadService.storePropertyImage(hotel.getId(), file);
        PropertyMedia media = new PropertyMedia();
        media.setHotel(hotel);
        media.setSourceType("MANAGED_UPLOAD");
        media.setPublicUrl(stored.url());
        media.setStorageKey(stored.storageKey());
        media.setContentType(stored.contentType());
        media.setFileSizeBytes(stored.sizeBytes());
        media.setWidth(stored.width());
        media.setHeight(stored.height());
        media.setChecksumSha256(stored.checksumSha256());
        media.setAltTextVi(normalizedAltVi);
        media.setAltTextEn(normalizedAltEn);
        media.setStatus("ACTIVE");
        media.setIsDemo(false);
        try {
            PropertyMedia saved = propertyMediaRepository.saveAndFlush(media);
            scheduleRollbackCleanup(saved);
            return saved;
        } catch (RuntimeException exception) {
            fileUploadService.deleteManagedImage(stored.url());
            throw exception;
        }
    }

    public void discardAfterFailedAssociation(PropertyMedia media) {
        if (media == null || TransactionSynchronizationManager.isSynchronizationActive()) return;
        propertyMediaRepository.delete(media);
        if (media.isManagedUpload()) fileUploadService.deleteManagedImage(media.getPublicUrl());
    }

    public boolean releaseIfUnreferenced(PropertyMedia media) {
        if (media == null || media.getId() == null) return false;
        long references = propertyImageRepository.countByMediaId(media.getId())
                + roomTypeImageRepository.countByMediaId(media.getId())
                + roomImageRepository.countByMediaId(media.getId());
        if (references > 0) return false;
        propertyMediaRepository.delete(media);
        if (media.isManagedUpload()) scheduleDeleteAfterCommit(media.getPublicUrl());
        return true;
    }

    private PropertyMedia baseMedia(Hotel hotel, String altTextVi, String altTextEn) {
        if (hotel == null || hotel.getId() == null) {
            throw new IllegalArgumentException("Property owner is required for media.");
        }
        PropertyMedia media = new PropertyMedia();
        media.setHotel(hotel);
        media.setAltTextVi(propertyMediaPolicy.requireAltTextVi(altTextVi));
        media.setAltTextEn(propertyMediaPolicy.normalizeAltTextEn(altTextEn));
        media.setStatus("ACTIVE");
        media.setIsDemo(false);
        return media;
    }

    private void scheduleRollbackCleanup(PropertyMedia media) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    fileUploadService.deleteManagedImage(media.getPublicUrl());
                }
            }
        });
    }

    private void scheduleDeleteAfterCommit(String publicUrl) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            fileUploadService.deleteManagedImage(publicUrl);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fileUploadService.deleteManagedImage(publicUrl);
            }
        });
    }
}
