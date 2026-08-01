package com.hotel.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.paymentprovider.reporting.RevenueReportModels.FinancialContext;
import com.hotel.paymentprovider.reporting.RevenueReportModels.NormalizedFilters;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RecognitionBasis;
import com.hotel.platformbilling.reporting.PlatformRevenueController;
import com.hotel.platformbilling.reporting.PlatformRevenueRepository;
import com.hotel.platformbilling.reporting.PlatformRevenueService;
import com.hotel.propertycommerce.reporting.PropertyRevenueController;
import com.hotel.propertycommerce.reporting.PropertyRevenueRepository;
import com.hotel.propertycommerce.reporting.PropertyRevenueService;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.security.PermissionInterceptor;
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FinancialReportingSecurityIntegrationTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void propertyAndPlatformEndpointsRequireIndependentViewPermissions() throws Exception {
        PropertyRevenueController propertyController = new PropertyRevenueController(
                mock(PropertyRevenueService.class), mock(PropertyAccessService.class));
        PlatformRevenueController platformController = new PlatformRevenueController(
                mock(PlatformRevenueService.class));
        Method propertyMethod = PropertyRevenueController.class.getMethod(
                "report", LocalDate.class, LocalDate.class, String.class, Long.class,
                String.class, String.class, String.class, String.class, String.class);
        Method platformMethod = PlatformRevenueController.class.getMethod(
                "report", LocalDate.class, LocalDate.class, String.class,
                String.class, String.class, String.class, String.class, String.class);
        Permission propertyPermission = propertyMethod.getAnnotation(Permission.class);
        Permission platformPermission = platformMethod.getAnnotation(Permission.class);
        assertNotNull(propertyPermission);
        assertNotNull(platformPermission);
        assertEquals(FunctionCode.REPORT, propertyPermission.function());
        assertEquals(ActionCode.VIEW, propertyPermission.action());
        assertEquals(FunctionCode.PLATFORM_REVENUE, platformPermission.function());
        assertEquals(ActionCode.VIEW, platformPermission.action());

        PermissionInterceptor interceptor = new PermissionInterceptor(new ObjectMapper());
        HandlerMethod propertyHandler = new HandlerMethod(propertyController, propertyMethod);
        HandlerMethod platformHandler = new HandlerMethod(platformController, platformMethod);

        authenticate(Map.of(FunctionCode.REPORT, ActionCode.VIEW));
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("GET", "/api/management/reports/property-revenue"),
                new MockHttpServletResponse(), propertyHandler));
        MockHttpServletResponse platformDenied = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(new MockHttpServletRequest("GET", "/api/admin/reports/platform-revenue"),
                platformDenied, platformHandler));
        assertEquals(403, platformDenied.getStatus());

        authenticate(Map.of(FunctionCode.PLATFORM_REVENUE, ActionCode.VIEW));
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("GET", "/api/admin/reports/platform-revenue"),
                new MockHttpServletResponse(), platformHandler));
        MockHttpServletResponse propertyDenied = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(new MockHttpServletRequest("GET", "/api/management/reports/property-revenue"),
                propertyDenied, propertyHandler));
        assertEquals(403, propertyDenied.getStatus());
    }

    @Test
    void propertyAndPlatformExportsRequireIndependentExportPermissions() throws Exception {
        PropertyRevenueController propertyController = new PropertyRevenueController(
                mock(PropertyRevenueService.class), mock(PropertyAccessService.class));
        PlatformRevenueController platformController = new PlatformRevenueController(
                mock(PlatformRevenueService.class));
        Method propertyMethod = PropertyRevenueController.class.getMethod(
                "export", LocalDate.class, LocalDate.class, String.class, Long.class,
                String.class, String.class, String.class, String.class, String.class, String.class);
        Method platformMethod = PlatformRevenueController.class.getMethod(
                "export", LocalDate.class, LocalDate.class, String.class,
                String.class, String.class, String.class, String.class, String.class, String.class);
        Permission propertyPermission = propertyMethod.getAnnotation(Permission.class);
        Permission platformPermission = platformMethod.getAnnotation(Permission.class);
        assertEquals(FunctionCode.REPORT, propertyPermission.function());
        assertEquals(ActionCode.EXPORT, propertyPermission.action());
        assertEquals(FunctionCode.PLATFORM_REVENUE, platformPermission.function());
        assertEquals(ActionCode.EXPORT, platformPermission.action());

        PermissionInterceptor interceptor = new PermissionInterceptor(new ObjectMapper());
        HandlerMethod propertyHandler = new HandlerMethod(propertyController, propertyMethod);
        HandlerMethod platformHandler = new HandlerMethod(platformController, platformMethod);

        authenticate(Map.of(FunctionCode.REPORT, ActionCode.EXPORT));
        assertTrue(interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/management/reports/property-revenue/export"),
                new MockHttpServletResponse(), propertyHandler));
        MockHttpServletResponse platformDenied = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/admin/reports/platform-revenue/export"),
                platformDenied, platformHandler));
        assertEquals(403, platformDenied.getStatus());

        authenticate(Map.of(FunctionCode.PLATFORM_REVENUE, ActionCode.EXPORT));
        assertTrue(interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/admin/reports/platform-revenue/export"),
                new MockHttpServletResponse(), platformHandler));
        MockHttpServletResponse propertyDenied = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/management/reports/property-revenue/export"),
                propertyDenied, propertyHandler));
        assertEquals(403, propertyDenied.getStatus());
    }

    @Test
    void reportServicesRejectTheOtherBoundedContextBeforeQuerying() {
        PropertyRevenueRepository propertyRepository = mock(PropertyRevenueRepository.class);
        PlatformRevenueRepository platformRepository = mock(PlatformRevenueRepository.class);
        PropertyRevenueService propertyService = new PropertyRevenueService(propertyRepository);
        PlatformRevenueService platformService = new PlatformRevenueService(platformRepository);

        assertThrows(IllegalArgumentException.class, () -> propertyService.generate(platformFilters()));
        assertThrows(IllegalArgumentException.class, () -> platformService.generate(propertyFilters()));
        verify(propertyRepository, never()).load(org.mockito.ArgumentMatchers.any());
        verify(platformRepository, never()).load(org.mockito.ArgumentMatchers.any());
    }

    private void authenticate(Map<FunctionCode, Integer> permissions) {
        CustomUserDetails user = new CustomUserDetails(
                "financial-report-test", "password", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
                permissions, 1L, 1L, Map.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private NormalizedFilters propertyFilters() {
        return new NormalizedFilters(
                FinancialContext.PROPERTY_COMMERCE, RecognitionBasis.NET,
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z"),
                "UTC", 42L, null, null, null, null, null);
    }

    private NormalizedFilters platformFilters() {
        return new NormalizedFilters(
                FinancialContext.PLATFORM_BILLING, RecognitionBasis.NET,
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z"),
                "UTC", null, null, null, null, null, null);
    }
}
