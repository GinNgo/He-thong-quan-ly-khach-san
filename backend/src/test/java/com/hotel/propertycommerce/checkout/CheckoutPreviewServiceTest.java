package com.hotel.propertycommerce.checkout;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
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
class CheckoutPreviewServiceTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private FolioCalculationService folioCalculationService;
    @Mock
    private PropertyAccessService propertyAccessService;

    private CheckoutPreviewService service;

    @BeforeEach
    void setUp() {
        service = new CheckoutPreviewService(
                reservationRepository,
                folioCalculationService,
                propertyAccessService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void settledPreviewAllowsCheckoutFromServerOwnedFolio() {
        Reservation reservation = authorizeReservation("CHECKED_IN");
        when(folioCalculationService.calculate(42L)).thenReturn(folio(BigDecimal.ZERO));

        CheckoutPreviewService.CheckoutPreview preview = service.preview(42L);

        assertThat(preview.settlementState()).isEqualTo(CheckoutPreviewService.SettlementState.SETTLED);
        assertThat(preview.checkoutAllowed()).isTrue();
        assertThat(preview.blockingError()).isNull();
        assertThat(preview.sourceVersion()).isEqualTo(9L);
        assertThat(service.requireSettled(42L).balance()).isEqualByComparingTo("0");
        verify(folioCalculationService, org.mockito.Mockito.times(2)).calculate(42L);
        assertThat(reservation.getTotalAmount()).isNull();
    }

    @Test
    void outstandingBalanceReturnsPreviewAndBlocksSettlement() {
        authorizeReservation("CHECKED_IN");
        when(folioCalculationService.calculate(42L)).thenReturn(folio(BigDecimal.valueOf(125_000)));

        CheckoutPreviewService.CheckoutPreview preview = service.preview(42L);

        assertThat(preview.checkoutAllowed()).isFalse();
        assertThat(preview.settlementState()).isEqualTo(CheckoutPreviewService.SettlementState.OUTSTANDING);
        assertThat(preview.blockingError()).isEqualTo(FinancialErrorCode.OUTSTANDING_BALANCE);
        assertThatThrownBy(() -> service.requireSettled(42L))
                .isInstanceOfSatisfying(FinancialException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(FinancialErrorCode.OUTSTANDING_BALANCE);
                    assertThat(exception.currentState()).isEqualTo("OUTSTANDING");
                });
    }

    @Test
    void overpaymentRequiresExplicitResolution() {
        authorizeReservation("CHECKED_IN");
        when(folioCalculationService.calculate(42L)).thenReturn(folio(BigDecimal.valueOf(-25_000)));

        CheckoutPreviewService.CheckoutPreview preview = service.preview(42L);

        assertThat(preview.checkoutAllowed()).isFalse();
        assertThat(preview.settlementState()).isEqualTo(CheckoutPreviewService.SettlementState.OVERPAID);
        assertThat(preview.blockingError()).isEqualTo(FinancialErrorCode.OVERPAYMENT_REQUIRES_RESOLUTION);
        assertThatThrownBy(() -> service.requireSettled(42L))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(FinancialErrorCode.OVERPAYMENT_REQUIRES_RESOLUTION));
    }

    @Test
    void invalidReservationStateStopsBeforeFolioCalculation() {
        authorizeReservation("COMPLETED");

        assertThatThrownBy(() -> service.preview(42L))
                .isInstanceOfSatisfying(FinancialException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(FinancialErrorCode.INVALID_STATE_TRANSITION);
                    assertThat(exception.currentState()).isEqualTo("COMPLETED");
                });

        verify(folioCalculationService, never()).calculate(any());
    }

    @Test
    void missingPermissionAndCrossPropertyAccessCreateNoPreview() {
        authenticate(Map.of(FunctionCode.CHECKOUT, ActionCode.CREATE));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);

        assertThatThrownBy(() -> service.preview(42L))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.TENANT_ACCESS_DENIED));
        verify(reservationRepository, never()).findById(any());

        authenticate(Map.of(FunctionCode.CHECKOUT, ActionCode.VIEW));
        Hotel hotel = new Hotel();
        hotel.setId(3L);
        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setHotel(hotel);
        reservation.setStatus("CHECKED_IN");
        when(reservationRepository.findById(42L)).thenReturn(Optional.of(reservation));
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(99L));

        assertThatThrownBy(() -> service.preview(42L))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.RESOURCE_NOT_FOUND));
        verify(folioCalculationService, never()).calculate(any());
    }

    private Reservation authorizeReservation(String status) {
        authenticate(Map.of(FunctionCode.CHECKOUT, ActionCode.VIEW));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(3L));
        Hotel hotel = new Hotel();
        hotel.setId(3L);
        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setHotel(hotel);
        reservation.setStatus(status);
        when(reservationRepository.findById(42L)).thenReturn(Optional.of(reservation));
        return reservation;
    }

    private FolioCalculationService.Folio folio(BigDecimal balance) {
        return new FolioCalculationService.Folio(
                42L,
                3L,
                VndMoney.of(1_000_000),
                VndMoney.of(150_000),
                VndMoney.of(250_000),
                VndMoney.of(30_000),
                VndMoney.of(50_000),
                VndMoney.of(100_000),
                VndMoney.of(1_380_000),
                VndMoney.of(300_000),
                VndMoney.of(1_380_000),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.of(1_380_000),
                balance,
                List.of(),
                9L,
                LocalDateTime.of(2026, 8, 1, 4, 0));
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
