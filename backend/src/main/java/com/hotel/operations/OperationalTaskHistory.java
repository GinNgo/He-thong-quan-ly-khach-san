package com.hotel.operations;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "operational_task_history", indexes = {
        @Index(name = "IX_operational_task_history_task", columnList = "task_id,occurred_at")
})
public class OperationalTaskHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;
    @Column(name = "previous_status", length = 20)
    private String previousStatus;
    @Column(name = "new_status", nullable = false, length = 20)
    private String newStatus;
    @Column(name = "actor_user_id")
    private Long actorUserId;
    @Column(name = "reason", columnDefinition = "nvarchar(500)")
    private String reason;
    @Column(name = "result_reference", length = 160)
    private String resultReference;
    @Column(name = "correlation_id", length = 100)
    private String correlationId;
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    protected OperationalTaskHistory() { }

    public OperationalTaskHistory(Long taskId, String previousStatus, String newStatus, Long actorUserId,
                                  String reason, String resultReference, String correlationId, LocalDateTime occurredAt) {
        this.taskId = taskId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.actorUserId = actorUserId;
        this.reason = reason;
        this.resultReference = resultReference;
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
    }

    @PreUpdate
    void rejectMutation() { throw new IllegalStateException("Operational task history is append-only."); }
}

