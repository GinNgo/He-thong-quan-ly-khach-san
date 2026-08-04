package com.hotel.repositories;

import com.hotel.entities.SupportConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface SupportConversationRepository extends JpaRepository<SupportConversation, Long> {
    Page<SupportConversation> findByCustomerIdAndUpdatedAtGreaterThanEqualOrderByUpdatedAtDescIdDesc(
            Long customerId, Instant cutoff, Pageable pageable);
    Page<SupportConversation> findByUpdatedAtGreaterThanEqualOrderByUpdatedAtDescIdDesc(
            Instant cutoff, Pageable pageable);
    Optional<SupportConversation> findByIdAndCustomerId(Long id, Long customerId);
    Optional<SupportConversation> findFirstByCustomerIdOrderByUpdatedAtDesc(Long customerId);
}
