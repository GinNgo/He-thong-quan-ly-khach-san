package com.hotel.entities;

<<<<<<< HEAD
import com.fasterxml.jackson.annotation.JsonIgnore;
=======
>>>>>>> codex/ui-functional-audit-polish
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nếu userId = null, tức là thông báo hệ thống gửi cho tất cả admin/staff
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String type; // BOOKING, CHAT, SYSTEM

    @Column(nullable = false, columnDefinition = "nvarchar(255)")
    private String title;

    @Column(nullable = false, columnDefinition = "nvarchar(max)")
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

<<<<<<< HEAD
    @JsonIgnore
    @Column(name = "event_key", length = 160)
    private String eventKey;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

=======
    @Column(name = "event_key", length = 160)
    private String eventKey;

>>>>>>> codex/ui-functional-audit-polish
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    @JsonProperty("isRead")
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }
<<<<<<< HEAD

    public LocalDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(LocalDateTime archivedAt) { this.archivedAt = archivedAt; }
=======
>>>>>>> codex/ui-functional-audit-polish
}
