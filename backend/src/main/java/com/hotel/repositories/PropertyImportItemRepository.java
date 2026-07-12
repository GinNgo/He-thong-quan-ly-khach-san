package com.hotel.repositories;

import com.hotel.entities.PropertyImportItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyImportItemRepository extends JpaRepository<PropertyImportItem, Long> {
    List<PropertyImportItem> findByBatchId(Long batchId);
    Page<PropertyImportItem> findByBatchId(Long batchId, Pageable pageable);
    List<PropertyImportItem> findByBatchIdAndDuplicateStatus(Long batchId, String duplicateStatus);
    boolean existsByExternalProviderAndExternalId(String provider, String externalId);
}
