package com.hotel.propertycommerce.checkout;

import com.hotel.config.SecurityConfig;
import com.hotel.config.WebMvcConfig;
import com.hotel.BackendApplication;
import com.hotel.controllers.GlobalExceptionHandler;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.observability.OperationalMetrics;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.propertycommerce.booking.BookingFinancialSummaryService;
import com.hotel.propertycommerce.folio.ReservationChargeService;
import com.hotel.propertycommerce.folio.SurchargeService;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.security.JwtAccessDeniedHandler;
import com.hotel.security.JwtAuthFilter;
import com.hotel.security.JwtAuthenticationEntryPoint;
import com.hotel.security.JwtTokenProvider;
import com.hotel.security.PermissionInterceptor;
import com.hotel.security.TenantFilterInterceptor;
import com.hotel.services.PropertyAccessService;
import com.hotel.services.ReservationService;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PropertyCheckoutController.class)
@ContextConfiguration(classes = BackendApplication.class)
@Import({
        SecurityConfig.class, WebMvcConfig.class, PermissionInterceptor.class,
        JwtAuthFilter.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class, GlobalExceptionHandler.class
})
class PropertyCheckoutPreviewHttpTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ReservationChargeService chargeService;
    @MockBean private SurchargeService surchargeService;
    @MockBean private CheckoutPreviewService previewService;
    @MockBean private CheckoutOverrideService overrideService;
    @MockBean private ReservationService reservationService;
    @MockBean private BookingFinancialSummaryService financialSummaryService;
    @MockBean private PropertyAccessService propertyAccessService;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private EntityManagerFactory entityManagerFactory;
    @MockBean private TenantFilterInterceptor tenantFilterInterceptor;
    @MockBean private OperationalMetrics operationalMetrics;

    @BeforeEach
    void allowTenantInterceptorToReachTheController() throws Exception {
        when(tenantFilterInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void returnsTheAuthoritativePreviewContractForAnAuthorizedViewer() throws Exception {
        when(previewService.preview(42L)).thenReturn(preview());

        mockMvc.perform(post("/api/management/reservations/42/checkout-preview")
                        .with(user(staff(ActionCode.VIEW))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(42))
                .andExpect(jsonPath("$.hotelId").value(3))
                .andExpect(jsonPath("$.settlementState").value("OUTSTANDING"))
                .andExpect(jsonPath("$.checkoutAllowed").value(false))
                .andExpect(jsonPath("$.blockingError").value("OUTSTANDING_BALANCE"))
                .andExpect(jsonPath("$.folio.grossCharges").value(1_000_000))
                .andExpect(jsonPath("$.folio.balance").value(100_000));
    }

    @Test
    void rejectsAStaffMemberWithoutCheckoutView() throws Exception {
        mockMvc.perform(post("/api/management/reservations/42/checkout-preview")
                        .with(user(staff(ActionCode.CREATE))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_PERMISSION"));
    }

    @Test
    void preservesTenantNotFoundSemanticsOverHttp() throws Exception {
        when(previewService.preview(99L)).thenThrow(new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(post("/api/management/reservations/99/checkout-preview")
                        .with(user(staff(ActionCode.VIEW))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private CustomUserDetails staff(int checkoutMask) {
        return new CustomUserDetails(
                "staff@example.com", "password",
                List.of(new SimpleGrantedAuthority("ROLE_STAFF")),
                Map.of(FunctionCode.CHECKOUT, checkoutMask), 9L, 3L, Map.of());
    }

    private CheckoutPreviewService.CheckoutPreview preview() {
        FolioCalculationService.Folio folio = new FolioCalculationService.Folio(
                42L, 3L, VndMoney.of(1_000_000), VndMoney.zero(), VndMoney.zero(),
                VndMoney.zero(), VndMoney.zero(), VndMoney.zero(), VndMoney.of(1_000_000),
                VndMoney.of(300_000), VndMoney.of(900_000), VndMoney.zero(), VndMoney.zero(),
                VndMoney.of(900_000), BigDecimal.valueOf(100_000), List.of(), 7L,
                LocalDateTime.of(2026, 8, 4, 8, 0));
        return new CheckoutPreviewService.CheckoutPreview(
                folio, CheckoutPreviewService.SettlementState.OUTSTANDING, false,
                FinancialErrorCode.OUTSTANDING_BALANCE, 7L);
    }
}
