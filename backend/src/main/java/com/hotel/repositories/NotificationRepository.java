package com.hotel.repositories;

import com.hotel.entities.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    // Lấy thông báo của user cụ thể, hoặc thông báo chung (userId = null)
    List<Notification> findByUserIdOrUserIdIsNullOrderByCreatedAtDesc(Long userId);
    
    // Lấy tất cả thông báo chung cho admin/staff
    List<Notification> findByUserIdIsNullOrderByCreatedAtDesc();

    Optional<Notification> findByEventKey(String eventKey);

    @Query("""
            select notification
            from Notification notification
            where notification.createdAt >= :cutoff
              and (notification.userId is null or notification.userId = :userId)
            """)
    Page<Notification> findVisibleToUser(
            @Param("userId") Long userId,
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable);

    @Query("""
            select count(notification)
            from Notification notification
            where notification.createdAt >= :cutoff
              and notification.isRead = false
              and (notification.userId is null or notification.userId = :userId)
            """)
    long countUnreadVisibleToUser(
            @Param("userId") Long userId,
            @Param("cutoff") LocalDateTime cutoff);

    Optional<Notification> findByIdAndCreatedAtGreaterThanEqual(Long id, LocalDateTime cutoff);
}
