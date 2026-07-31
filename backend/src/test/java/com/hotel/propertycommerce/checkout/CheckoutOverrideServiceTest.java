package com.hotel.propertycommerce.checkout;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.repositories.ReservationRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

@ExtendWith(MockitoExtension.class)
class CheckoutOverrideServiceTest {

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

    private CheckoutOverrideService service;

    @BeforeEach
    void setUp() {
        service = new CheckoutOverrideService(
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
    void settledCheckoutNeedsNoOverrideEvidence() {
        when(previewService.preview(42L)).thenReturn(preview(BigDecimal.ZERO));

        CheckoutOverrideService.SettlementAuthorization authorization = service.authorizeCheckout(42L, null);

        assertThat(authorization.debtOverrideApplied()).isFalse();
        assertThat(authorization.override()).isNull();
        assertThat(authorization.preview().checkoutAllowed()).isTrue();
        verify(reservationRepository, never()).findByIdForUpdate(any());
        verify(overrideRepository, never()).saveAndFlush(any());
        verify(auditService, never()).append(any());
    }

    @Test
    void outstandingCheckoutWithoutOverrideRemainsBlocked() {
        when(previewService.preview(42L)).thenReturn(preview(BigDecimal.valueOf(125_000)));

        assertThatThrownBy(() -> service.authorizeCheckout(42L, null))
                .isInstanceOfSatisfying(FinancialException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(FinancialErrorCode.OUTSTANDING_BALANCE);
                    assertThat(exception.currentState()).isEqualTo("OUTSTANDING");
                });

        verify(overrideRepository, never()).saveAndFlush(any());
        verify(auditService, never()).append(any());
    }

    @Test
    void overpaymentRemainsBlockedWithoutApprovedResolutionPolicy() {
        when(previewService.preview(42L)).thenReturn(preview(BigDecimal.valueOf(-25_000)));

        assertThatThrownBy(() -> service.authorizeCheckout(
                42L,
                new CheckoutOverrideService.DebtOverrideCommand("Customer will settle later", "corr-overpaid")))
                .isInstanceOfSatisfying(FinancialException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(FinancialErrorCode.OVERPAYMENT_REQUIRES_RESOLUTION);
                    assertThat(exception.currentState()).isEqualTo("OVERPAID");
                });

        verify(reservationRepository, never()).findByIdForUpdate(any());
        verify(overrideRepository, never()).saveAndFlush(any());
    }

    @Test
    void debtOverrideRequiresTheSeparateApprovePermission() {
        when(previewService.preview(42L)).thenReturn(preview(BigDecimal.valueOf(125_000)));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        authenticate(Map.of(FunctionCode.RESERVATION_DEBT_OVERRIDE, ActionCode.CREATE));

        assertThatThrownBy(() -> service.authorizeCheckout(
                42L,
                new CheckoutOverrideService.DebtOverrideCommand("Company account settles after departure", "corr-1")))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.TENANT_ACCESS_DENIED));

        verify(reservationRepository, never()).findByIdForUpdate(any());
        verify(overrideRepository, never()).saveAndFlush(any());
    }

    @Test
    void authorizedDebtOverrideUsesLockedAuthoritativeBalanceAndAppendsAuditEvidence() {
        CheckoutPreviewService.CheckoutPreview outstanding = preview(BigDecimal.valueOf(125_000));
        when(previewService.preview(42L)).thenReturn(outstanding, outstanding);
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(3L));
        authenticate(Map.of(FunctionCode.RESERVATION_DEBT_OVERRIDE, ActionCode.APPROVE));

        User actor = new User();
        actor.setId(9L);
        when(propertyAccessService.currentUser()).thenReturn(actor);
        Reservation reservation = reservation();
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));
        when(overrideRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CheckoutOverrideService.SettlementAuthorization authorization = service.authorizeCheckout(
                42L,
                new CheckoutOverrideService.DebtOverrideCommand(
                        "  Approved corporate receivable after departure  ",
                        "corr-debt-42"));

        assertThat(authorization.debtOverrideApplied()).isTrue();
        CheckoutOverride override = authorization.override();
        assertThat(override.getOverrideType()).isEqualTo(CheckoutOverride.OverrideType.DEBT);
        assertThat(override.getOutstandingAmount()).isEqualByComparingTo("125000");
        assertThat(override.getReason()).isEqualTo("Approved corporate receivable after departure");
        assertThat(override.getActor()).isSameAs(actor);
        assertThat(override.getApprovedBy()).isSameAs(actor);

        ArgumentCaptor<FinancialAuditService.AuditCommand> auditCaptor =
                ArgumentCaptor.forClass(FinancialAuditService.AuditCommand.class);
        verify(auditService).append(auditCaptor.capture());
        FinancialAuditService.AuditCommand audit = auditCaptor.getValue();
        assertThat(audit.context()).isEqualTo("PROPERTY_COMMERCE");
        assertThat(audit.hotelId()).isEqualTo(3L);
        assertThat(audit.aggregateType()).isEqualTo("CHECKOUT_OVERRIDE");
        assertThat(audit.source()).isEqualTo("DEBT_OVERRIDE_APPROVED");
        assertThat(audit.previousState()).isEqualTo("OUTSTANDING");
        assertThat(audit.newState()).isEqualTo("AUTHORIZED_WITH_DEBT");
        assertThat(audit.reason()).isEqualTo("Approved corporate receivable after departure");
        assertThat(audit.correlationId()).isEqualTo("corr-debt-42");
        assertThat(audit.metadata().get("outstandingAmount")).isEqualTo(BigDecimal.valueOf(125_000));
        assertThat(audit.metadata().get("folioSourceVersion")).isEqualTo(9L);
    }

    @Test
    void lockedRecalculationCanRemoveTheNeedForAnOverride() {
        when(previewService.preview(42L))
                .thenReturn(preview(BigDecimal.valueOf(125_000)), preview(BigDecimal.ZERO));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(3L));
        authenticate(Map.of(FunctionCode.RESERVATION_DEBT_OVERRIDE, ActionCode.APPROVE));

        User actor = new User();
        actor.setId(9L);
        when(propertyAccessService.currentUser()).thenReturn(actor);
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation()));

        CheckoutOverrideService.SettlementAuthorization authorization = service.authorizeCheckout(
                42L,
                new CheckoutOverrideService.DebtOverrideCommand("Payment arrived during checkout", "corr-race"));

        assertThat(authorization.debtOverrideApplied()).isFalse();
        assertThat(authorization.preview().settlementState())
                .isEqualTo(CheckoutPreviewService.SettlementState.SETTLED);
        verify(overrideRepository, never()).saveAndFlush(any());
        verify(auditService, never()).append(any());
    }

    @Test
    void overrideEntityRejectsInvalidOrMutableEvidence() {
        User actor = new User();
        actor.setId(9L);
        Reservation reservation = reservation();

        assertThatThrownBy(() -> CheckoutOverride.approveDebt(
                reservation.getHotel(), reservation, BigDecimal.ZERO, "Reason", actor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CheckoutOverride.approveDebt(
                reservation.getHotel(), reservation, BigDecimal.valueOf(1.5), "Reason", actor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CheckoutOverride.approveDebt(
                reservation.getHotel(), reservation, BigDecimal.ONE, " ", actor))
                .isInstanceOf(IllegalArgumentException.class);

        CheckoutOverride override = CheckoutOverride.approveDebt(
                reservation.getHotel(), reservation, BigDecimal.ONE, "Approved debt", actor);
        assertThatThrownBy(override::rejectUpdate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("append-only");
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
        FinancialErrorCode blockingError = state == CheckoutPreviewService.SettlementState.OUTSTANDING
                ? FinancialErrorCode.OUTSTANDING_BALANCE
                : state == CheckoutPreviewService.SettlementState.OVERPAID
                ? FinancialErrorCode.OVERPAYMENT_REQUIRES_RESOLUTION
                : null;
        return new CheckoutPreviewService.CheckoutPreview(
                folio,
                state,
                state == CheckoutPreviewService.SettlementState.SETTLED,
                blockingError,
                9L);
    }

    private void authenticate(Map<FunctionCode, Integer> permissions) {
        CustomUserDetails principal = new CustomUserDetails(
                "staff@example.com",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_STAFF")),
                permissions,
                9L,
                3L,
                Map.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
