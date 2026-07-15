package com.hotel.entities;

import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "rooms", uniqueConstraints = @UniqueConstraint(name = "UX_rooms_hotel_room_number", columnNames = {"hotel_id", "room_number"}))
public class Room extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_number", nullable = false, columnDefinition = "nvarchar(50)")
    private String roomNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    @Column(nullable = false)
    private Integer floor;

    @Column(nullable = false)
    private String status; // AVAILABLE, RESERVED, OCCUPIED, MAINTENANCE, CLEANING

    @Column(name = "maintenance_status", nullable = false)
    private String maintenanceStatus = "NONE";

    @Column(name = "housekeeping_status", nullable = false)
    private String housekeepingStatus = "CLEAN";

    @Column(name = "is_demo", nullable = false)
    private Boolean isDemo = false;

    @Column(name = "max_guests")
    private Integer maxGuests;

    @Column(name = "description_vi", columnDefinition = "nvarchar(max)")
    private String descriptionVi;

    @Column(name = "description_en", columnDefinition = "nvarchar(max)")
    private String descriptionEn;

    @Column(columnDefinition = "nvarchar(1000)")
    private String note;

    // Getters and Setters omitted for brevity

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public RoomType getRoomType() {
        return roomType;
    }

    public void setRoomType(RoomType roomType) {
        this.roomType = roomType;
    }

    public Integer getFloor() {
        return floor;
    }

    public void setFloor(Integer floor) {
        this.floor = floor;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescriptionVi() {
        return descriptionVi;
    }

    public void setDescriptionVi(String descriptionVi) {
        this.descriptionVi = descriptionVi;
    }

    public String getDescriptionEn() {
        return descriptionEn;
    }

    public void setDescriptionEn(String descriptionEn) {
        this.descriptionEn = descriptionEn;
    }
}
