package com.hotel.dtos;

import java.math.BigDecimal;
import java.util.List;

public class AnalyticsDataDTO {
    private BigDecimal totalRevenue;
    private Integer totalBookings;
    private Double occupancyRate;
    private List<String> labels;
    private List<BigDecimal> revenueData;
    private List<Integer> occupancyData;
    private List<Integer> aiPredictedOccupancy;

    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }

    public Integer getTotalBookings() { return totalBookings; }
    public void setTotalBookings(Integer totalBookings) { this.totalBookings = totalBookings; }

    public Double getOccupancyRate() { return occupancyRate; }
    public void setOccupancyRate(Double occupancyRate) { this.occupancyRate = occupancyRate; }

    public List<String> getLabels() { return labels; }
    public void setLabels(List<String> labels) { this.labels = labels; }

    public List<BigDecimal> getRevenueData() { return revenueData; }
    public void setRevenueData(List<BigDecimal> revenueData) { this.revenueData = revenueData; }

    public List<Integer> getOccupancyData() { return occupancyData; }
    public void setOccupancyData(List<Integer> occupancyData) { this.occupancyData = occupancyData; }

    public List<Integer> getAiPredictedOccupancy() { return aiPredictedOccupancy; }
    public void setAiPredictedOccupancy(List<Integer> aiPredictedOccupancy) { this.aiPredictedOccupancy = aiPredictedOccupancy; }
}
