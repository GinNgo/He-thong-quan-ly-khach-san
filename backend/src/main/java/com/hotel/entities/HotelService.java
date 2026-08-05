package com.hotel.entities;

import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import java.math.BigDecimal;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "services", indexes = {
        @Index(name = "IX_services_hotel_status", columnList = "hotel_id,status"),
        @Index(name = "IX_services_system_code", columnList = "is_system,code")
})
@org.hibernate.annotations.FilterDef(name = "hotelServiceTenantFilter", parameters = @org.hibernate.annotations.ParamDef(name = "hotelId", type = Long.class))
@org.hibernate.annotations.Filter(name = "hotelServiceTenantFilter", condition = "(hotel_id = :hotelId AND is_system = 0) OR (hotel_id IS NULL AND is_system = 1)")
public class HotelService extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    @Column(name = "name_vi", nullable = false, columnDefinition = "nvarchar(255)")
    private String nameVi;

    @Column(name = "name_en", nullable = false, columnDefinition = "nvarchar(255)")
    private String nameEn;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "description_vi", columnDefinition = "nvarchar(max)")
    private String descriptionVi;

    @Column(name = "description_en", columnDefinition = "nvarchar(max)")
    private String descriptionEn;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE, INACTIVE

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id")
    private Hotel hotel;

    @Column(name = "is_system", nullable = false)
    private Boolean systemService = false;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    @PreUpdate
    private void validateOwnershipScope() {
        boolean system = Boolean.TRUE.equals(systemService);
        if (system == (hotel != null)) {
            throw new IllegalStateException(
                    system ? "System service templates cannot belong to a property."
                            : "Tenant services must belong to a property.");
        }
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getNameVi() {
        return nameVi;
    }

    public void setNameVi(String nameVi) {
        this.nameVi = nameVi;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
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
