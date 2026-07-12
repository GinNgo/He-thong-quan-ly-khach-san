package com.hotel.entities;

import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "property_import_batches")
public class PropertyImportBatch extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "province_id", nullable = false)
    private Long provinceId;

    @Column(name = "ward_id")
    private Long wardId;

    @Column(name = "search_keyword")
    private String searchKeyword;

    @Column(name = "radius_km")
    private Double radiusKm;

    @Column(name = "status", nullable = false)
    private String status = "CREATED"; // CREATED, SEARCHING, PREVIEW_READY, IMPORTING, COMPLETED, FAILED

    @Column(name = "total_found")
    private Integer totalFound = 0;

    @Column(name = "total_new")
    private Integer totalNew = 0;

    @Column(name = "total_duplicate")
    private Integer totalDuplicate = 0;

    @Column(name = "total_selected")
    private Integer totalSelected = 0;

    @Column(name = "total_imported")
    private Integer totalImported = 0;

    @Column(name = "total_failed")
    private Integer totalFailed = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Long getProvinceId() {
        return provinceId;
    }

    public void setProvinceId(Long provinceId) {
        this.provinceId = provinceId;
    }

    public Long getWardId() {
        return wardId;
    }

    public void setWardId(Long wardId) {
        this.wardId = wardId;
    }

    public String getSearchKeyword() {
        return searchKeyword;
    }

    public void setSearchKeyword(String searchKeyword) {
        this.searchKeyword = searchKeyword;
    }

    public Double getRadiusKm() {
        return radiusKm;
    }

    public void setRadiusKm(Double radiusKm) {
        this.radiusKm = radiusKm;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTotalFound() {
        return totalFound;
    }

    public void setTotalFound(Integer totalFound) {
        this.totalFound = totalFound;
    }

    public Integer getTotalNew() {
        return totalNew;
    }

    public void setTotalNew(Integer totalNew) {
        this.totalNew = totalNew;
    }

    public Integer getTotalDuplicate() {
        return totalDuplicate;
    }

    public void setTotalDuplicate(Integer totalDuplicate) {
        this.totalDuplicate = totalDuplicate;
    }

    public Integer getTotalSelected() {
        return totalSelected;
    }

    public void setTotalSelected(Integer totalSelected) {
        this.totalSelected = totalSelected;
    }

    public Integer getTotalImported() {
        return totalImported;
    }

    public void setTotalImported(Integer totalImported) {
        this.totalImported = totalImported;
    }

    public Integer getTotalFailed() {
        return totalFailed;
    }

    public void setTotalFailed(Integer totalFailed) {
        this.totalFailed = totalFailed;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
