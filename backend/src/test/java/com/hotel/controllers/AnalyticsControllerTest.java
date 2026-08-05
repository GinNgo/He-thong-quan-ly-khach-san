package com.hotel.controllers;

import com.hotel.dtos.AnalyticsDataDTO;
import com.hotel.security.CustomUserDetails;
import com.hotel.services.AnalyticsService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsControllerTest {
    @Test
    void forwardsTheAuthenticatedContextToTheAuthoritativeDashboardService() {
        AnalyticsService service = mock(AnalyticsService.class);
        AnalyticsDataDTO expected = new AnalyticsDataDTO();
        CustomUserDetails user = new CustomUserDetails(
                "admin", "hash", Set.of(), Map.of(), 1L, null, Map.of());
        when(service.getAnalyticsData(user)).thenReturn(expected);

        AnalyticsDataDTO result = new AnalyticsController(service)
                .getDashboardData(user).getBody();

        assertSame(expected, result);
        verify(service).getAnalyticsData(user);
    }
}
