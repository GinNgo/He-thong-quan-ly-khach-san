package com.hotel.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class AnalyticsDataDTO {
    private BigDecimal totalRevenue;
    private Long totalBookings;
    private Double occupancyRate;
    private Long totalRooms;
    private Long occupiedRooms;
    private Long operationalProperties;
    private String scope;
    private String revenueBasis;
    private String occupancyBasis;
    private String reconciliationStatus;
    private String sourceWatermark;
    private Instant generatedAt;
    private LocalDate periodFrom;
    private LocalDate periodTo;
    private List<String> labels;
    private List<BigDecimal> revenueData;
    private List<Integer> occupancyData;

    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }

    public Long getTotalBookings() { return totalBookings; }
    public void setTotalBookings(Long totalBookings) { this.totalBookings = totalBookings; }

    public Double getOccupancyRate() { return occupancyRate; }
    public void setOccupancyRate(Double occupancyRate) { this.occupancyRate = occupancyRate; }
    public Long getTotalRooms() { return totalRooms; }
    public void setTotalRooms(Long totalRooms) { this.totalRooms = totalRooms; }
    public Long getOccupiedRooms() { return occupiedRooms; }
    public void setOccupiedRooms(Long occupiedRooms) { this.occupiedRooms = occupiedRooms; }
    public Long getOperationalProperties() { return operationalProperties; }
    public void setOperationalProperties(Long operationalProperties) { this.operationalProperties = operationalProperties; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getRevenueBasis() { return revenueBasis; }
    public void setRevenueBasis(String revenueBasis) { this.revenueBasis = revenueBasis; }
    public String getOccupancyBasis() { return occupancyBasis; }
    public void setOccupancyBasis(String occupancyBasis) { this.occupancyBasis = occupancyBasis; }
    public String getReconciliationStatus() { return reconciliationStatus; }
    public void setReconciliationStatus(String reconciliationStatus) { this.reconciliationStatus = reconciliationStatus; }
    public String getSourceWatermark() { return sourceWatermark; }
    public void setSourceWatermark(String sourceWatermark) { this.sourceWatermark = sourceWatermark; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
    public LocalDate getPeriodFrom() { return periodFrom; }
    public void setPeriodFrom(LocalDate periodFrom) { this.periodFrom = periodFrom; }
    public LocalDate getPeriodTo() { return periodTo; }
    public void setPeriodTo(LocalDate periodTo) { this.periodTo = periodTo; }

    public List<String> getLabels() { return labels; }
    public void setLabels(List<String> labels) { this.labels = labels; }

    public List<BigDecimal> getRevenueData() { return revenueData; }
    public void setRevenueData(List<BigDecimal> revenueData) { this.revenueData = revenueData; }

    public List<Integer> getOccupancyData() { return occupancyData; }
    public void setOccupancyData(List<Integer> occupancyData) { this.occupancyData = occupancyData; }

}
