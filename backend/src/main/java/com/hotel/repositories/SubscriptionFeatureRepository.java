package com.hotel.repositories;

import com.hotel.entities.SubscriptionFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SubscriptionFeatureRepository extends JpaRepository<SubscriptionFeature, Long> {
    List<SubscriptionFeature> findByCodeIn(Collection<String> codes);
}
