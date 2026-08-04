package com.hotel.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "maintenance_work_order_history")
@org.hibernate.annotations.FilterDef(name = "maintenanceWorkOrderHistoryTenantFilter", parameters = @org.hibernate.annotations.ParamDef(name = "hotelId", type = Long.class))
@org.hibernate.annotations.Filter(name = "maintenanceWorkOrderHistoryTenantFilter", condition = "hotel_id = :hotelId")
@Getter
@Setter
public class MaintenanceWorkOrderHistory extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private MaintenanceWorkOrder workOrder;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 20)
    private String toStatus;

    @Column(nullable = false, length = 1000)
    private String reason;
}
