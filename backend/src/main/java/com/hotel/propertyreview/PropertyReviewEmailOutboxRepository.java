package com.hotel.propertyreview;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface PropertyReviewEmailOutboxRepository extends JpaRepository<PropertyReviewEmailOutbox, Long> {

    Optional<PropertyReviewEmailOutbox> findByAuditEventIdAndRecipientUserId(
            Long auditEventId,
            Long recipientUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select item
            from PropertyReviewEmailOutbox item
            where (item.status in :dueStatuses and item.nextAttemptAt <= :now)
               or (item.status = :processingStatus and item.claimedAt <= :staleBefore)
            order by item.nextAttemptAt asc, item.id asc
            """)
    List<PropertyReviewEmailOutbox> findDueForUpdate(
            @Param("now") LocalDateTime now,
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("dueStatuses") Collection<PropertyReviewEmailStatus> dueStatuses,
            @Param("processingStatus") PropertyReviewEmailStatus processingStatus,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from PropertyReviewEmailOutbox item where item.id = :id")
    Optional<PropertyReviewEmailOutbox> findByIdForUpdate(@Param("id") Long id);
}
