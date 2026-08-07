package com.hotel.repositories;

import com.hotel.entities.LocationImportRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LocationImportRunRepository extends JpaRepository<LocationImportRun, Long> {
    Optional<LocationImportRun> findByRunId(String runId);
}
