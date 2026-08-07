package com.hotel.repositories;

import com.hotel.entities.LandmarkImportIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LandmarkImportIssueRepository extends JpaRepository<LandmarkImportIssue, Long> {
    List<LandmarkImportIssue> findByRun_IdOrderByIdAsc(Long runId);
}
