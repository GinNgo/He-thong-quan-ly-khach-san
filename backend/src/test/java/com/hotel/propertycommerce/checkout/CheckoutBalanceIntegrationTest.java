package com.hotel.propertycommerce.checkout;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.repositories.ReservationRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Exercises the checkout settlement boundary with underpayment and overpayment evidence. */
@ExtendWith(MockitoExtension.class)
class CheckoutBalanceIntegrationTest {

    @Mock
    private CheckoutPreviewService previewService;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private CheckoutOverrideRepository overrideRepository;
    @Mock
    private PropertyAccessService propertyAccessService;
    @Mock
    private FinancialAuditService auditService;

    private CheckoutOverrideService overrideService;

    @BeforeEach
    void setUp() {
        overrideService = new CheckoutOverrideService(
                previewService,
                reservationRepository,
                overrideRepository,
                propertyAccessService,
                auditService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void underpaymentBlocksCheckoutUntilApprovedDebtOverride() {
        when(previewService.preview(42L)).thenReturn(preview(BigDecimal.valueOf(125_000)));

        assertThatThrownBy(() -> overrideService.authorizeCheckout(42L, null))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.OUTSTANDING_BALANCE));
        verify(overrideRepository, never()).saveAndFlush(any());

        authorizeStaff();
        when(previewService.preview(42L)).thenReturn(
                preview(BigDecimal.valueOf(125_000)), preview(BigDecimal.valueOf(125_000)));
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation()));
        when(propertyAccessService.currentUser()).thenReturn(actor());
        when(overrideRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CheckoutOverrideService.SettlementAuthorization authorization = overrideService.authorizeCheckout(
                42L,
                new CheckoutOverrideService.DebtOverrideCommand("Approved corporate receivable", "corr-42"));

        assertThat(authorization.debtOverrideApplied()).isTrue();
        assertThat(authorization.override().getOutstandingAmount()).isEqualByComparingTo("125000");
        assertThat(authorization.override().getReason()).isEqualTo("Approved corporate receivable");
    }

    @Test
    void overpaymentRemainsBlockedUntilASeparateResolutionPolicyExists() {
        when(previewService.preview(42L)).thenReturn(preview(BigDecimal.valueOf(-25_000)));

        assertThatThrownBy(() -> overrideService.authorizeCheckout(
                42L,
                new CheckoutOverrideService.DebtOverrideCommand("Resolve overpayment", "corr-over")))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(FinancialErrorCode.OVERPAYMENT_REQUIRES_RESOLUTION));
        verify(reservationRepository, never()).findByIdForUpdate(any());
        verify(overrideRepository, never()).saveAndFlush(any());
    }

    @Test
    void settledCheckoutDoesNotCreateDebtOverride() {
        when(previewService.preview(42L)).thenReturn(preview(BigDecimal.ZERO));

        CheckoutOverrideService.SettlementAuthorization authorization = overrideService.authorizeCheckout(
                42L,
                new CheckoutOverrideService.DebtOverrideCommand("Not needed", "corr-settled"));

        assertThat(authorization.preview().checkoutAllowed()).isTrue();
        assertThat(authorization.debtOverrideApplied()).isFalse();
        assertThat(authorization.override()).isNull();
        verify(overrideRepository, never()).saveAndFlush(any());
    }

    private CheckoutPreviewService.CheckoutPreview preview(BigDecimal balance) {
        FolioCalculationService.Folio folio = new FolioCalculationService.Folio(
                42L,
                3L,
                VndMoney.of(1_000_000),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.of(1_000_000),
                VndMoney.of(300_000),
                VndMoney.of(1_000_000),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.of(1_000_000),
                balance,
                List.of(),
                9L,
                LocalDateTime.of(2026, 8, 1, 5, 0));
        CheckoutPreviewService.SettlementState state = balance.signum() > 0
                ? CheckoutPreviewService.SettlementState.OUTSTANDING
                : balance.signum() < 0
                ? CheckoutPreviewService.SettlementState.OVERPAID
                : CheckoutPreviewService.SettlementState.SETTLED;
        FinancialErrorCode error = state == CheckoutPreviewService.SettlementState.OUTSTANDING
                ? FinancialErrorCode.OUTSTANDING_BALANCE
                : state == CheckoutPreviewService.SettlementState.OVERPAID
                ? FinancialErrorCode.OVERPAYMENT_REQUIRES_RESOLUTION
                : null;
        return new CheckoutPreviewService.CheckoutPreview(
                folio, state, state == CheckoutPreviewService.SettlementState.SETTLED, error, 9L);
    }

    private void authorizeStaff() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(3L));
        CustomUserDetails principal = new CustomUserDetails(
                "staff@example.com",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_STAFF")),
                Map.of(FunctionCode.RESERVATION_DEBT_OVERRIDE, ActionCode.APPROVE),
                9L,
                3L,
                Map.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Reservation reservation() {
        Hotel hotel = new Hotel();
        hotel.setId(3L);
        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setHotel(hotel);
        reservation.setStatus("CHECKED_IN");
        return reservation;
    }

    private User actor() {
        User actor = new User();
        actor.setId(9L);
        return actor;
    }
}
