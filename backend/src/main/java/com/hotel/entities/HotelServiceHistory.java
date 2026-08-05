package com.hotel.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "service_catalog_history")
@Getter
@Setter
public class HotelServiceHistory extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private HotelService service;

    @Column(name = "hotel_id")
    private Long hotelId;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(name = "name_vi", nullable = false, columnDefinition = "nvarchar(255)")
    private String nameVi;

    @Column(name = "name_en", nullable = false, columnDefinition = "nvarchar(255)")
    private String nameEn;

    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal price;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "service_version", nullable = false)
    private Long serviceVersion;
}
