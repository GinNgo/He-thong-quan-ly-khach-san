package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyMedia;
import com.hotel.repositories.PropertyImageRepository;
import com.hotel.repositories.PropertyMediaRepository;
import com.hotel.repositories.RoomImageRepository;
import com.hotel.repositories.RoomTypeImageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyMediaServiceTest {

    @Mock private PropertyMediaRepository propertyMediaRepository;
    @Mock private PropertyImageRepository propertyImageRepository;
    @Mock private RoomTypeImageRepository roomTypeImageRepository;
    @Mock private RoomImageRepository roomImageRepository;
    @Mock private FileUploadService fileUploadService;

    private PropertyMediaPolicy policy;
    private PropertyMediaService service;
    private Hotel hotel;

    @BeforeEach
    void setUp() {
        policy = new PropertyMediaPolicy();
        service = new PropertyMediaService(
                propertyMediaRepository,
                propertyImageRepository,
                roomTypeImageRepository,
                roomImageRepository,
                policy,
                fileUploadService);
        hotel = new Hotel();
        hotel.setId(10L);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createsPropertyOwnedHttpsMediaWithRequiredAltText() {
        when(propertyMediaRepository.saveAndFlush(any(PropertyMedia.class)))
                .thenAnswer(invocation -> {
                    PropertyMedia media = invocation.getArgument(0);
                    media.setId(90L);
                    return media;
                });

        PropertyMedia result = service.createExternal(
                hotel, "https://cdn.example/property.jpg", " Sanh chinh ", " Lobby ");

        assertEquals(90L, result.getId());
        assertEquals(10L, result.getHotel().getId());
        assertEquals("EXTERNAL_HTTPS", result.getSourceType());
        assertEquals("Sanh chinh", result.getAltTextVi());
        assertEquals("Lobby", result.getAltTextEn());
    }

    @Test
    void persistsVerifiedUploadMetadataAndDeletesFileOnLaterRollback() {
        TransactionSynchronizationManager.initSynchronization();
        var stored = new FileUploadService.StoredImage(
                "/api/public/uploads/property-10-file.png",
                "image/png", 640, 480, 1234L, "a".repeat(64), "property-10-file.png");
        when(fileUploadService.storePropertyImage(eq(10L), any())).thenReturn(stored);
        when(propertyMediaRepository.saveAndFlush(any(PropertyMedia.class)))
                .thenAnswer(invocation -> {
                    PropertyMedia media = invocation.getArgument(0);
                    media.setId(91L);
                    return media;
                });

        PropertyMedia result = service.createUpload(
                hotel,
                new MockMultipartFile("file", "property.png", "image/png", new byte[]{1}),
                "Phong deluxe",
                "Deluxe room");

        assertEquals("MANAGED_UPLOAD", result.getSourceType());
        assertEquals("property-10-file.png", result.getStorageKey());
        assertEquals(1234L, result.getFileSizeBytes());
        assertEquals(640, result.getWidth());
        assertEquals("a".repeat(64), result.getChecksumSha256());
        verify(fileUploadService, never()).deleteManagedImage(any());

        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(fileUploadService).deleteManagedImage(stored.url());
    }

    @Test
    void failedMediaPersistenceDeletesTheNewManagedFileImmediately() {
        var stored = new FileUploadService.StoredImage(
                "/api/public/uploads/property-10-file.png",
                "image/png", 10, 10, 100L, "b".repeat(64), "property-10-file.png");
        when(fileUploadService.storePropertyImage(eq(10L), any())).thenReturn(stored);
        when(propertyMediaRepository.saveAndFlush(any(PropertyMedia.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, () -> service.createUpload(
                hotel,
                new MockMultipartFile("file", "property.png", "image/png", new byte[]{1}),
                "Phong",
                null));

        verify(fileUploadService).deleteManagedImage(stored.url());
    }

    @Test
    void referencedMediaIsRetainedAcrossAllImageAssociationTypes() {
        PropertyMedia media = managedMedia(92L);
        when(propertyImageRepository.countByMediaId(92L)).thenReturn(1L);

        assertFalse(service.releaseIfUnreferenced(media));

        verify(propertyMediaRepository, never()).delete(any());
        verify(fileUploadService, never()).deleteManagedImage(any());
    }

    @Test
    void unreferencedManagedMediaDeletesRecordThenFileAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        PropertyMedia media = managedMedia(93L);

        assertTrue(service.releaseIfUnreferenced(media));

        verify(propertyMediaRepository).delete(media);
        verify(fileUploadService, never()).deleteManagedImage(any());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(fileUploadService).deleteManagedImage(media.getPublicUrl());
    }

    private PropertyMedia managedMedia(Long id) {
        PropertyMedia media = new PropertyMedia();
        media.setId(id);
        media.setHotel(hotel);
        media.setSourceType("MANAGED_UPLOAD");
        media.setPublicUrl("/api/public/uploads/property-10-file.png");
        media.setStorageKey("property-10-file.png");
        media.setStatus("ACTIVE");
        return media;
    }
}
