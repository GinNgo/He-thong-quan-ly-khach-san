package com.hotel.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantFilterArchitectureTest {

    @Test
    void everyDeclaredTenantFilterHasRequestActivation() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/hotel/security/TenantFilterInterceptor.java"), StandardCharsets.UTF_8);
        List<String> filters = List.of(
                "chatMessageTenantFilter",
                "paymentSessionTenantFilter",
                "refundRequestTenantFilter",
                "refundAttemptTenantFilter",
                "reservationHoldTenantFilter",
                "supportConversationTenantFilter",
                "supportConversationEventTenantFilter",
                "financialAuditTenantFilter",
                "housekeepingTaskTenantFilter",
                "hotelServiceTenantFilter",
                "propertyImageTenantFilter",
                "roomTenantFilter",
                "roomTypeTenantFilter",
                "reservationTenantFilter",
                "propertyPaymentConfigurationTenantFilter",
                "propertyPaymentMethodTenantFilter",
                "propertyPaymentAttemptTenantFilter",
                "propertyFinancialTransactionTenantFilter",
                "bookingFinancialSummaryTenantFilter");
        filters.forEach(filter -> assertTrue(source.contains("\"" + filter + "\""), () -> "Filter is not activated: " + filter));
    }

    @Test
    void tenantOwnedBusinessEntitiesDeclareHibernateFilters() throws Exception {
        Map<String, String> entityFilters = Map.ofEntries(
                Map.entry("HousekeepingTask.java", "housekeepingTaskTenantFilter"),
                Map.entry("HotelService.java", "hotelServiceTenantFilter"),
                Map.entry("PropertyImage.java", "propertyImageTenantFilter"),
                Map.entry("Room.java", "roomTenantFilter"),
                Map.entry("RoomType.java", "roomTypeTenantFilter"),
                Map.entry("Reservation.java", "reservationTenantFilter"));
        Path root = Path.of("src/main/java/com/hotel/entities");
        entityFilters.forEach((file, filter) -> {
            try {
                String source = Files.readString(root.resolve(file), StandardCharsets.UTF_8);
                assertTrue(source.contains(filter), () -> file + " is missing " + filter);
                assertTrue(source.contains("hotel_id"), () -> file + " is missing hotel ownership");
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
    }

    @Test
    void publicDiscoveryDoesNotInheritAnAuthenticatedPropertyFilter() {
        TenantFilterInterceptor interceptor = new TenantFilterInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/properties/search");
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }
}
