package com.hotel.repositories;

import com.hotel.entities.PlanFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanFeatureRepository extends JpaRepository<PlanFeature, Long> {}
