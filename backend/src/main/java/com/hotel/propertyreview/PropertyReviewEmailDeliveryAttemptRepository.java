package com.hotel.propertyreview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PropertyReviewEmailDeliveryAttemptRepository
        extends JpaRepository<PropertyReviewEmailDeliveryAttempt, Long> {

    List<PropertyReviewEmailDeliveryAttempt> findByOutboxIdOrderByAttemptNumberAsc(Long outboxId);
}
