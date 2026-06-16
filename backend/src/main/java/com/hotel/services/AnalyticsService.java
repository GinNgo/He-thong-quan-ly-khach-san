package com.hotel.services;

import com.hotel.dtos.AnalyticsDataDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Service
public class AnalyticsService {

    public AnalyticsDataDTO getAnalyticsData() {
        AnalyticsDataDTO dto = new AnalyticsDataDTO();
        dto.setTotalRevenue(new BigDecimal("125000000"));
        dto.setTotalBookings(150);
        dto.setOccupancyRate(75.5);

        dto.setLabels(Arrays.asList("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul"));
        
        dto.setRevenueData(Arrays.asList(
                new BigDecimal("15000000"),
                new BigDecimal("18000000"),
                new BigDecimal("12000000"),
                new BigDecimal("22000000"),
                new BigDecimal("25000000"),
                new BigDecimal("28000000"),
                new BigDecimal("30000000")
        ));

        dto.setOccupancyData(Arrays.asList(65, 70, 60, 80, 85, 90, 95));

        // Mocked AI Predicted data for future
        dto.setAiPredictedOccupancy(Arrays.asList(68, 72, 65, 82, 88, 92, 98));

        return dto;
    }
}
