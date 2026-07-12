package com.hotel.repositories;

import com.hotel.entities.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    // Lấy thông báo của user cụ thể, hoặc thông báo chung (userId = null)
    List<Notification> findByUserIdOrUserIdIsNullOrderByCreatedAtDesc(Long userId);
    
    // Lấy tất cả thông báo chung cho admin/staff
    List<Notification> findByUserIdIsNullOrderByCreatedAtDesc();
}
