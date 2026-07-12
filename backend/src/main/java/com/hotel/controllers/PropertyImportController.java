package com.hotel.controllers;

import com.hotel.dto.provider.AccommodationProviderSearchRequest;
import com.hotel.entities.PropertyImportBatch;
import com.hotel.entities.PropertyImportItem;
import com.hotel.repositories.PropertyImportBatchRepository;
import com.hotel.repositories.PropertyImportItemRepository;
import com.hotel.services.PropertyImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/property-imports")
@RequiredArgsConstructor
public class PropertyImportController {

    private final PropertyImportService importService;
    private final PropertyImportBatchRepository batchRepository;
    private final PropertyImportItemRepository itemRepository;

    @PostMapping("/search")
    @PreAuthorize("hasAuthority('PROPERTY_IMPORT_CREATE') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<PropertyImportBatch> searchAndStage(@RequestBody AccommodationProviderSearchRequest request,
                                                              @RequestParam(defaultValue = "NOMINATIM") String provider) {
        PropertyImportBatch batch = importService.searchAndStageProperties(request, provider);
        return ResponseEntity.ok(batch);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROPERTY_IMPORT_VIEW') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Page<PropertyImportBatch>> getBatches(Pageable pageable) {
        return ResponseEntity.ok(batchRepository.findAllByOrderByCreatedAtDesc(pageable));
    }

    @GetMapping("/{batchId}/items")
    @PreAuthorize("hasAuthority('PROPERTY_IMPORT_VIEW') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Page<PropertyImportItem>> getBatchItems(@PathVariable Long batchId, Pageable pageable) {
        return ResponseEntity.ok(itemRepository.findByBatchId(batchId, pageable));
    }

    @PostMapping("/{batchId}/import")
    @PreAuthorize("hasAuthority('PROPERTY_IMPORT_EXECUTE') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> importBatch(@PathVariable Long batchId) {
        int count = importService.importValidItems(batchId);
        return ResponseEntity.ok(Map.of("message", "Imported " + count + " properties successfully.", "count", count));
    }
}
