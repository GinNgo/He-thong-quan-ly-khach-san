package com.hotel.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

@Entity
@Table(name = "property_policy_versions", uniqueConstraints =
        @UniqueConstraint(name = "UK_property_policy_version", columnNames = {"hotel_id", "version_number"}))
@org.hibernate.annotations.FilterDef(name = "propertyPolicyTenantFilter", parameters =
        @org.hibernate.annotations.ParamDef(name = "hotelId", type = Long.class))
@org.hibernate.annotations.Filter(name = "propertyPolicyTenantFilter", condition = "hotel_id = :hotelId")
public class OperationalPolicyVersion extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Column(name = "version_number", nullable = false)
    private Long versionNumber;

    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_until")
    private LocalDateTime effectiveUntil;

    @Column(name = "check_in_vi", nullable = false, columnDefinition = "nvarchar(2000)")
    private String checkInVi;

    @Column(name = "check_in_en", columnDefinition = "nvarchar(2000)")
    private String checkInEn;

    @Column(name = "check_out_vi", nullable = false, columnDefinition = "nvarchar(2000)")
    private String checkOutVi;

    @Column(name = "check_out_en", columnDefinition = "nvarchar(2000)")
    private String checkOutEn;

    @Column(name = "cancellation_vi", nullable = false, columnDefinition = "nvarchar(3000)")
    private String cancellationVi;

    @Column(name = "cancellation_en", columnDefinition = "nvarchar(3000)")
    private String cancellationEn;

    @Column(name = "child_policy_vi", nullable = false, columnDefinition = "nvarchar(2000)")
    private String childPolicyVi;

    @Column(name = "child_policy_en", columnDefinition = "nvarchar(2000)")
    private String childPolicyEn;

    @Column(name = "pet_policy_vi", nullable = false, columnDefinition = "nvarchar(2000)")
    private String petPolicyVi;

    @Column(name = "pet_policy_en", columnDefinition = "nvarchar(2000)")
    private String petPolicyEn;

    @Column(name = "smoking_policy_vi", nullable = false, columnDefinition = "nvarchar(2000)")
    private String smokingPolicyVi;

    @Column(name = "smoking_policy_en", columnDefinition = "nvarchar(2000)")
    private String smokingPolicyEn;

    @Column(name = "house_rules_vi", nullable = false, columnDefinition = "nvarchar(4000)")
    private String houseRulesVi;

    @Column(name = "house_rules_en", columnDefinition = "nvarchar(4000)")
    private String houseRulesEn;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Hotel getHotel() { return hotel; }
    public void setHotel(Hotel hotel) { this.hotel = hotel; }
    public Long getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Long versionNumber) { this.versionNumber = versionNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDateTime getEffectiveUntil() { return effectiveUntil; }
    public void setEffectiveUntil(LocalDateTime effectiveUntil) { this.effectiveUntil = effectiveUntil; }
    public String getCheckInVi() { return checkInVi; }
    public void setCheckInVi(String checkInVi) { this.checkInVi = checkInVi; }
    public String getCheckInEn() { return checkInEn; }
    public void setCheckInEn(String checkInEn) { this.checkInEn = checkInEn; }
    public String getCheckOutVi() { return checkOutVi; }
    public void setCheckOutVi(String checkOutVi) { this.checkOutVi = checkOutVi; }
    public String getCheckOutEn() { return checkOutEn; }
    public void setCheckOutEn(String checkOutEn) { this.checkOutEn = checkOutEn; }
    public String getCancellationVi() { return cancellationVi; }
    public void setCancellationVi(String cancellationVi) { this.cancellationVi = cancellationVi; }
    public String getCancellationEn() { return cancellationEn; }
    public void setCancellationEn(String cancellationEn) { this.cancellationEn = cancellationEn; }
    public String getChildPolicyVi() { return childPolicyVi; }
    public void setChildPolicyVi(String childPolicyVi) { this.childPolicyVi = childPolicyVi; }
    public String getChildPolicyEn() { return childPolicyEn; }
    public void setChildPolicyEn(String childPolicyEn) { this.childPolicyEn = childPolicyEn; }
    public String getPetPolicyVi() { return petPolicyVi; }
    public void setPetPolicyVi(String petPolicyVi) { this.petPolicyVi = petPolicyVi; }
    public String getPetPolicyEn() { return petPolicyEn; }
    public void setPetPolicyEn(String petPolicyEn) { this.petPolicyEn = petPolicyEn; }
    public String getSmokingPolicyVi() { return smokingPolicyVi; }
    public void setSmokingPolicyVi(String smokingPolicyVi) { this.smokingPolicyVi = smokingPolicyVi; }
    public String getSmokingPolicyEn() { return smokingPolicyEn; }
    public void setSmokingPolicyEn(String smokingPolicyEn) { this.smokingPolicyEn = smokingPolicyEn; }
    public String getHouseRulesVi() { return houseRulesVi; }
    public void setHouseRulesVi(String houseRulesVi) { this.houseRulesVi = houseRulesVi; }
    public String getHouseRulesEn() { return houseRulesEn; }
    public void setHouseRulesEn(String houseRulesEn) { this.houseRulesEn = houseRulesEn; }
    public Long getRowVersion() { return rowVersion; }
}
