package com.hotel.services;

import com.hotel.dto.provider.AccommodationProviderSearchRequest;
import com.hotel.dto.provider.ProviderSearchResult;
import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyImportBatch;
import com.hotel.entities.PropertyImportItem;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.PropertyImportBatchRepository;
import com.hotel.repositories.PropertyImportItemRepository;
import com.hotel.services.provider.AccommodationDataProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PropertyImportService {

    private final List<AccommodationDataProvider> dataProviders;
    private final PropertyImportBatchRepository batchRepository;
    private final PropertyImportItemRepository itemRepository;
    private final HotelRepository hotelRepository;

    @Transactional
    public PropertyImportBatch searchAndStageProperties(AccommodationProviderSearchRequest request, String providerName) {
        AccommodationDataProvider provider = dataProviders.stream()
                .filter(p -> p.getProviderName().equalsIgnoreCase(providerName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerName));

        PropertyImportBatch batch = new PropertyImportBatch();
        batch.setProvider(providerName);
        batch.setSearchKeyword(request.getKeyword());
        batch.setRadiusKm(request.getRadiusKm());
        // In a real scenario, map lat/lng to province/ward
        batch.setProvinceId(1L); 
        batch.setStartedAt(LocalDateTime.now());
        batch.setStatus("SEARCHING");
        batch = batchRepository.save(batch);

        List<ProviderSearchResult> results = provider.search(request);

        int totalNew = 0;
        int totalDuplicate = 0;

        for (ProviderSearchResult result : results) {
            PropertyImportItem item = new PropertyImportItem();
            item.setBatch(batch);
            item.setExternalProvider(providerName);
            item.setExternalId(result.getExternalId());
            item.setRawName(result.getName());
            item.setNormalizedName(normalizeString(result.getName()));
            item.setRawAddress(result.getAddress());
            item.setLatitude(result.getLatitude());
            item.setLongitude(result.getLongitude());
            item.setPhone(result.getPhone());
            item.setWebsite(result.getWebsite());
            item.setRating(result.getRating());
            item.setReviewCount(result.getReviewCount());
            item.setSourceUrl(result.getSourceUrl());
            item.setRawPayloadJson(result.getRawPayloadJson());
            
            // Deduplication Check
            boolean isExactDuplicate = itemRepository.existsByExternalProviderAndExternalId(providerName, result.getExternalId());
            // More advanced duplicate checks (Name, Phone, Coordinates) could be added here
            
            if (isExactDuplicate) {
                item.setDuplicateStatus("EXACT_DUPLICATE");
                totalDuplicate++;
            } else {
                item.setDuplicateStatus("NEW");
                totalNew++;
            }
            itemRepository.save(item);
        }

        batch.setTotalFound(results.size());
        batch.setTotalNew(totalNew);
        batch.setTotalDuplicate(totalDuplicate);
        batch.setStatus("PREVIEW_READY");
        batch.setCompletedAt(LocalDateTime.now());
        return batchRepository.save(batch);
    }

    @Transactional
    public int importValidItems(Long batchId) {
        List<PropertyImportItem> items = itemRepository.findByBatchIdAndDuplicateStatus(batchId, "NEW");
        int importedCount = 0;
        
        for (PropertyImportItem item : items) {
            if ("IMPORTED".equals(item.getImportStatus())) continue;

            Hotel hotel = new Hotel();
            hotel.setName(item.getRawName());
            hotel.setAddressLine(item.getRawAddress());
            hotel.setLatitude(item.getLatitude());
            hotel.setLongitude(item.getLongitude());
            hotel.setPhone(item.getPhone());
            hotel.setWebsite(item.getWebsite());
            if (item.getRating() != null) {
                hotel.setAverageRating(item.getRating());
                hotel.setStarRating(item.getRating().intValue());
            }
            hotel.setReviewCount(item.getReviewCount());
            hotel.setExternalProvider(item.getExternalProvider());
            hotel.setExternalId(item.getExternalId());
            hotel.setApprovalStatus("IMPORTED_PENDING_REVIEW"); // Needs claim
            
            hotelRepository.save(hotel);
            
            item.setImportStatus("IMPORTED");
            itemRepository.save(item);
            importedCount++;
        }
        
        Optional<PropertyImportBatch> batchOpt = batchRepository.findById(batchId);
        if (batchOpt.isPresent()) {
            PropertyImportBatch batch = batchOpt.get();
            batch.setTotalImported(batch.getTotalImported() + importedCount);
            if (batch.getTotalImported() >= batch.getTotalNew()) {
                batch.setStatus("COMPLETED");
            }
            batchRepository.save(batch);
        }
        
        return importedCount;
    }
    
    private String normalizeString(String input) {
        if (input == null) return "";
        return input.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
