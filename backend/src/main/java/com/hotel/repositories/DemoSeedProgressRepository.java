package com.hotel.repositories;

import com.hotel.entities.DemoSeedProgress;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DemoSeedProgressRepository extends JpaRepository<DemoSeedProgress, String> {
    long countByCoverageModeAndStatus(String coverageMode, String status);
}
