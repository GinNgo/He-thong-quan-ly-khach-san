package com.hotel.emailoutbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailDeliveryAttemptRepository extends JpaRepository<EmailDeliveryAttempt, Long> {
    List<EmailDeliveryAttempt> findByOutboxIdOrderByAttemptNumberAsc(Long outboxId);
}
