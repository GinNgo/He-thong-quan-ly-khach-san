package com.hotel.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Append-only record of a role permission matrix mutation. */
@Entity
@Table(name = "app_role_permission_audit", indexes = {
        @Index(name = "IX_role_permission_audit_role", columnList = "role_id,occurred_at")
})
public class RolePermissionAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "expected_version", nullable = false)
    private Long expectedVersion;

    @Column(name = "resulting_version", nullable = false)
    private Long resultingVersion;

    @Column(name = "previous_state_json", nullable = false, columnDefinition = "nvarchar(max)")
    private String previousStateJson;

    @Column(name = "new_state_json", nullable = false, columnDefinition = "nvarchar(max)")
    private String newStateJson;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    protected RolePermissionAudit() {
    }

    public RolePermissionAudit(Long roleId, Long actorUserId, Long expectedVersion, Long resultingVersion,
                               String previousStateJson, String newStateJson, LocalDateTime occurredAt) {
        this.roleId = roleId;
        this.actorUserId = actorUserId;
        this.expectedVersion = expectedVersion;
        this.resultingVersion = resultingVersion;
        this.previousStateJson = previousStateJson;
        this.newStateJson = newStateJson;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public Long getRoleId() { return roleId; }
    public Long getActorUserId() { return actorUserId; }
    public Long getExpectedVersion() { return expectedVersion; }
    public Long getResultingVersion() { return resultingVersion; }
    public String getPreviousStateJson() { return previousStateJson; }
    public String getNewStateJson() { return newStateJson; }
    public LocalDateTime getOccurredAt() { return occurredAt; }

    @PreUpdate
    void rejectMutation() {
        throw new IllegalStateException("Role permission audit records are append-only");
    }
}
