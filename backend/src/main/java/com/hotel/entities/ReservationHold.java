package com.hotel.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reservation_holds",
        uniqueConstraints = @UniqueConstraint(name = "UQ_reservation_holds_hold_key", columnNames = "hold_key"),
        indexes = {
                @Index(name = "IX_reservation_holds_hotel_status", columnList = "hotel_id,status"),
                @Index(name = "IX_reservation_holds_expiry", columnList = "status,expires_at")
        })
@FilterDef(name = "reservationHoldTenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "reservationHoldTenantFilter", condition = "hotel_id = :hotelId")
public class ReservationHold extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "hold_key", nullable = false, length = 120)
    private String holdKey;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;
}
