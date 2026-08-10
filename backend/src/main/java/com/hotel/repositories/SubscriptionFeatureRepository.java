package com.hotel.repositories;

import com.hotel.entities.SubscriptionFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionFeatureRepository extends JpaRepository<SubscriptionFeature, Long> {
    Optional<SubscriptionFeature> findByCode(String code);

    List<SubscriptionFeature> findByCodeIn(Collection<String> codes);
}
