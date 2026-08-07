package com.hotel.emailoutbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmailOutboxRepository extends JpaRepository<EmailOutboxMessage, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<EmailOutboxMessage> {

    Optional<EmailOutboxMessage> findByIdempotencyKey(String idempotencyKey);

    List<EmailOutboxMessage> findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(
            Collection<EmailOutboxStatus> statuses, LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select message from EmailOutboxMessage message where message.id = :id")
    Optional<EmailOutboxMessage> findForUpdate(@Param("id") Long id);

    @Override
    Page<EmailOutboxMessage> findAll(Specification<EmailOutboxMessage> specification, Pageable pageable);
}
