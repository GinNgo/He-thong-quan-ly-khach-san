package com.hotel.repositories;

import com.hotel.entities.PropertyImportBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PropertyImportBatchRepository extends JpaRepository<PropertyImportBatch, Long> {
    Page<PropertyImportBatch> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
