package com.hotel.operations;

import com.hotel.entities.AuditableEntity;
import com.hotel.entities.Hotel;
import com.hotel.entities.User;
import com.hotel.security.FunctionCode;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(name = "operational_tasks", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_operational_task_public", columnNames = "public_id"),
        @UniqueConstraint(name = "UQ_operational_task_effect", columnNames = {"hotel_id", "effect_key"})
}, indexes = {
        @Index(name = "IX_operational_task_queue", columnList = "hotel_id,status,task_type,created_at")
})
@FilterDef(name = "operationalTaskTenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "operationalTaskTenantFilter", condition = "hotel_id = :hotelId")
public class OperationalTask extends AuditableEntity {
    public enum Status { OPEN, ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED, BLOCKED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 64, updatable = false)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false, updatable = false)
    private Hotel hotel;

    @Column(name = "task_type", nullable = false, length = 40, updatable = false)
    private String taskType;

    @Enumerated(EnumType.STRING)
    @Column(name = "function_code", nullable = false, length = 60, updatable = false)
    private FunctionCode functionCode;

    @Column(name = "required_action", nullable = false, updatable = false)
    private int requiredAction;

    @Column(name = "aggregate_type", nullable = false, length = 60, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100, updatable = false)
    private String aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_user_id")
    private User assignedTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_user_id")
    private User assignedBy;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "effect_key", nullable = false, length = 160, updatable = false)
    private String effectKey;

    @Column(name = "result_reference", length = 160)
    private String resultReference;

    @Column(name = "note", columnDefinition = "nvarchar(1000)")
    private String note;

    @Version
    @Column(nullable = false)
    private long version;

    protected OperationalTask() { }

    public static OperationalTask open(Hotel hotel, String taskType, FunctionCode functionCode, int requiredAction,
                                       String aggregateType, String aggregateId, String effectKey) {
        OperationalTask task = new OperationalTask();
        task.publicId = UUID.randomUUID().toString();
        task.hotel = Objects.requireNonNull(hotel);
        task.taskType = normalize(taskType);
        task.functionCode = Objects.requireNonNull(functionCode);
        task.requiredAction = requiredAction;
        task.aggregateType = normalize(aggregateType);
        task.aggregateId = requireText(aggregateId);
        task.effectKey = requireText(effectKey);
        task.status = Status.OPEN;
        return task;
    }

    public void assign(User assignee, User actor, LocalDateTime now) {
        if (terminal()) throw new IllegalStateException("Terminal task cannot be assigned.");
        assignedTo = Objects.requireNonNull(assignee);
        assignedBy = Objects.requireNonNull(actor);
        assignedAt = Objects.requireNonNull(now);
        status = Status.ASSIGNED;
    }

    public void claim(User actor, LocalDateTime now) {
        if (status != Status.OPEN && status != Status.ASSIGNED) throw new IllegalStateException("Task cannot be claimed.");
        if (assignedTo != null && !Objects.equals(assignedTo.getId(), actor.getId())) {
            throw new IllegalStateException("Task is assigned to another user.");
        }
        assignedTo = actor;
        assignedAt = assignedAt == null ? now : assignedAt;
        startedAt = now;
        status = Status.IN_PROGRESS;
    }

    public void complete(User actor, String resultReference, String note, LocalDateTime now) {
        if (status == Status.COMPLETED) return;
        if (status != Status.ASSIGNED && status != Status.IN_PROGRESS) {
            throw new IllegalStateException("Task is not ready for completion.");
        }
        if (assignedTo != null && !Objects.equals(assignedTo.getId(), actor.getId())) {
            throw new IllegalStateException("Only the assigned user can complete the task.");
        }
        this.assignedTo = actor;
        this.resultReference = resultReference == null ? null : resultReference.trim();
        this.note = note == null ? null : note.trim();
        this.completedAt = Objects.requireNonNull(now);
        this.status = Status.COMPLETED;
    }

    public void block(String reason) {
        if (terminal()) throw new IllegalStateException("Terminal task cannot be blocked.");
        note = requireText(reason);
        status = Status.BLOCKED;
    }

    public void cancel(String reason) {
        if (status == Status.COMPLETED) throw new IllegalStateException("Completed task cannot be cancelled.");
        note = requireText(reason);
        status = Status.CANCELLED;
    }

    public boolean terminal() { return status == Status.COMPLETED || status == Status.CANCELLED; }
    private static String normalize(String value) { return requireText(value).toUpperCase(Locale.ROOT); }
    private static String requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Task value is required.");
        return value.trim();
    }
}
