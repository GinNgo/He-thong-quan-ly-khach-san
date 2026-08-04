package com.hotel.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance_work_orders")
@org.hibernate.annotations.FilterDef(name = "maintenanceWorkOrderTenantFilter", parameters = @org.hibernate.annotations.ParamDef(name = "hotelId", type = Long.class))
@org.hibernate.annotations.Filter(name = "maintenanceWorkOrderTenantFilter", condition = "hotel_id = :hotelId")
@Getter
@Setter
public class MaintenanceWorkOrder extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false, length = 20)
    private String priority;

    @Column(name = "assignee_user_id")
    private Long assigneeUserId;

    @Column(name = "scheduled_start")
    private LocalDateTime scheduledStart;

    @Column(name = "scheduled_end")
    private LocalDateTime scheduledEnd;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "resolution_note", length = 2000)
    private String resolutionNote;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Version
    @Column(nullable = false)
    private Long version;
}
