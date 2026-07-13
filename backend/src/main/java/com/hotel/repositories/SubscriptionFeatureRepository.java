package com.hotel.repositories;

import com.hotel.entities.SubscriptionFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionFeatureRepository extends JpaRepository<SubscriptionFeature, Long> {}
