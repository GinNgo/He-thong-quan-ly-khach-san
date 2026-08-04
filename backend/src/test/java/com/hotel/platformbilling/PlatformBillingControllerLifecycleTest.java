package com.hotel.platformbilling;

import com.hotel.platformbilling.config.PlatformPaymentConfigurationService;
import com.hotel.platformbilling.order.SubscriptionOrderService;
import com.hotel.platformbilling.payment.PlatformPaymentAttemptService;
import com.hotel.platformbilling.subscription.*;
import com.hotel.services.PropertySubscriptionEntitlementService;
import com.hotel.services.SubscriptionCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformBillingControllerLifecycleTest {
    @Test void revokeForwardsAuditedRequestContext() {
        SubscriptionLifecycleService lifecycle = mock(SubscriptionLifecycleService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1"); when(request.getHeader("User-Agent")).thenReturn("browser");
        when(request.getHeader("X-Correlation-ID")).thenReturn("corr-1");
        PlatformBillingController controller = controller(mock(PlatformBillingQueryService.class), lifecycle);
        controller.revoke(9L, new PlatformBillingController.RevokeRequest("Administrative revoke reason"), request);
        verify(lifecycle).revoke(9L, "Administrative revoke reason", "127.0.0.1", "browser", "corr-1");
    }

    @Test void exportSetsCsvDownloadHeaders() {
        PlatformBillingQueryService query = mock(PlatformBillingQueryService.class);
        when(query.historyCsv(9L)).thenReturn("id\r\n");
        var response = controller(query, mock(SubscriptionLifecycleService.class)).exportHistory(9L);
        assertEquals("text/csv;charset=UTF-8", response.getHeaders().getContentType().toString());
        assertEquals("attachment; filename=\"subscription-history-9.csv\"",
                response.getHeaders().getFirst("Content-Disposition"));
    }

    private PlatformBillingController controller(PlatformBillingQueryService query,
                                                  SubscriptionLifecycleService lifecycle) {
        return new PlatformBillingController(mock(SubscriptionCatalogService.class), mock(SubscriptionOrderService.class),
                mock(PlatformPaymentAttemptService.class), mock(SubscriptionRenewalService.class),
                mock(SubscriptionUpgradeService.class), mock(SubscriptionPolicyService.class),
                mock(PlatformPaymentConfigurationService.class), query,
                mock(PropertySubscriptionEntitlementService.class), lifecycle);
    }
}
