package com.hotel.propertycommerce.folio;

import com.hotel.entities.Hotel;
import com.hotel.entities.HotelService;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.repositories.HotelServiceRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationChargeServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-01T02:30:00Z");
    private static final LocalDateTime USED_AT = LocalDateTime.of(2026, 8, 1, 2, 0);

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private HotelServiceRepository hotelServiceRepository;
    @Mock
    private ReservationChargeLineRepository chargeLineRepository;
    @Mock
    private PropertyAccessService propertyAccessService;

    private ReservationChargeService service;

    @BeforeEach
    void setUp() {
        service = new ReservationChargeService(
                reservationRepository,
                hotelServiceRepository,
                chargeLineRepository,
                propertyAccessService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsServiceLineFromServerCatalogPriceAndSnapshots() {
        Fixture fixture = fixture();
        authorize(fixture);
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(fixture.reservation()));
        when(hotelServiceRepository.findById(15L)).thenReturn(Optional.of(fixture.catalogService()));
        when(chargeLineRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationChargeLine line = service.addServiceCharge(new ReservationChargeService.AddServiceChargeCommand(
                42L,
                15L,
                ReservationChargeLine.ChargeType.SERVICE,
                new BigDecimal("2.000"),
                USED_AT));

        assertThat(line.getUnitPrice()).isEqualByComparingTo("150000");
        assertThat(line.getQuantity()).isEqualByComparingTo("2.000");
        assertThat(line.getTaxAmount()).isEqualByComparingTo("0");
        assertThat(line.getTotalAmount()).isEqualByComparingTo("300000");
        assertThat(line.getSourceId()).isEqualTo(15L);
        assertThat(line.getSourceVersion()).isEqualTo("2026-07-31T10:15");
        assertThat(line.getName()).isEqualTo("Breakfast / Breakfast buffet");
        assertThat(line.getActor()).isSameAs(fixture.actor());
        assertThat(line.getServiceUsedAt()).isEqualTo(USED_AT);
    }

    @Test
    void rejectsMissingPermissionBeforeReadingFinancialResources() {
        authenticate(Map.of(FunctionCode.RESERVATION_SERVICE, ActionCode.VIEW));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);

        assertThatThrownBy(() -> service.addServiceCharge(command()))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.TENANT_ACCESS_DENIED));

        verify(reservationRepository, never()).findByIdForUpdate(any());
        verify(chargeLineRepository, never()).saveAndFlush(any());
    }

    @Test
    void hidesCrossPropertyCatalogAndRejectsInactiveReservation() {
        Fixture fixture = fixture();
        authorize(fixture);
        Hotel other = new Hotel();
        other.setId(99L);
        fixture.catalogService().setHotel(other);
        fixture.catalogService().setSystemService(false);
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(fixture.reservation()));
        when(hotelServiceRepository.findById(15L)).thenReturn(Optional.of(fixture.catalogService()));

        assertThatThrownBy(() -> service.addServiceCharge(command()))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.RESOURCE_NOT_FOUND));
        verify(chargeLineRepository, never()).saveAndFlush(any());

        fixture.reservation().setStatus("CONFIRMED");
        assertThatThrownBy(() -> service.addServiceCharge(command()))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.INVALID_STATE_TRANSITION));
    }

    @Test
    void appendsReversalAndServerPricedReplacementWithoutMutatingOriginal() {
        Fixture fixture = fixture();
        authorize(fixture);
        ReservationChargeLine original = originalLine(fixture);
        ReflectionTestUtils.setField(original, "id", 71L);
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(fixture.reservation()));
        when(chargeLineRepository.findByIdForUpdate(71L, 3L, 42L)).thenReturn(Optional.of(original));
        when(chargeLineRepository.existsByReversesLineId(71L)).thenReturn(false);
        when(hotelServiceRepository.findById(15L)).thenReturn(Optional.of(fixture.catalogService()));
        when(chargeLineRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationChargeService.CorrectionResult result = service.correctServiceCharge(
                new ReservationChargeService.CorrectServiceChargeCommand(
                        42L, 71L, "Guest consumed one breakfast, not two", BigDecimal.ONE, USED_AT));

        assertThat(result.reversal().getChargeType()).isEqualTo(ReservationChargeLine.ChargeType.ADJUSTMENT);
        assertThat(result.reversal().getReversesLine()).isSameAs(original);
        assertThat(result.reversal().getTotalAmount()).isEqualByComparingTo("300000");
        assertThat(result.reversal().getDescription()).contains("one breakfast");
        assertThat(result.replacement().getChargeType()).isEqualTo(ReservationChargeLine.ChargeType.SERVICE);
        assertThat(result.replacement().getQuantity()).isEqualByComparingTo("1.000");
        assertThat(result.replacement().getTotalAmount()).isEqualByComparingTo("150000");
        assertThat(original.getQuantity()).isEqualByComparingTo("2.000");
        verify(chargeLineRepository, times(2)).saveAndFlush(any());
    }

    @Test
    void rejectsDuplicateCorrectionAndFutureUsage() {
        Fixture fixture = fixture();
        authorize(fixture);
        ReservationChargeLine original = originalLine(fixture);
        ReflectionTestUtils.setField(original, "id", 71L);
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(fixture.reservation()));
        when(chargeLineRepository.findByIdForUpdate(71L, 3L, 42L)).thenReturn(Optional.of(original));
        when(chargeLineRepository.existsByReversesLineId(71L)).thenReturn(true);

        assertThatThrownBy(() -> service.correctServiceCharge(
                new ReservationChargeService.CorrectServiceChargeCommand(
                        42L, 71L, "Duplicate", null, null)))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.INVALID_STATE_TRANSITION));
        verify(chargeLineRepository, never()).saveAndFlush(any());

        assertThatThrownBy(() -> service.addServiceCharge(new ReservationChargeService.AddServiceChargeCommand(
                42L,
                15L,
                ReservationChargeLine.ChargeType.MINIBAR,
                BigDecimal.ONE,
                LocalDateTime.ofInstant(NOW.plusSeconds(1), ZoneOffset.UTC))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");
    }

    private ReservationChargeService.AddServiceChargeCommand command() {
        return new ReservationChargeService.AddServiceChargeCommand(
                42L, 15L, ReservationChargeLine.ChargeType.SERVICE, BigDecimal.ONE, USED_AT);
    }

    private ReservationChargeLine originalLine(Fixture fixture) {
        return ReservationChargeLine.create(
                fixture.hotel(),
                fixture.reservation(),
                ReservationChargeLine.ChargeType.SERVICE,
                15L,
                "2026-07-31T10:15",
                "BREAKFAST",
                "Breakfast / Breakfast buffet",
                "Buffet breakfast",
                BigDecimal.valueOf(150_000),
                BigDecimal.valueOf(2),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(300_000),
                USED_AT,
                fixture.actor(),
                null);
    }

    private Fixture fixture() {
        Hotel hotel = new Hotel();
        hotel.setId(3L);
        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setHotel(hotel);
        reservation.setStatus("CHECKED_IN");
        User actor = new User();
        actor.setId(9L);
        HotelService catalogService = new HotelService();
        catalogService.setId(15L);
        catalogService.setHotel(hotel);
        catalogService.setSystemService(false);
        catalogService.setCode("BREAKFAST");
        catalogService.setNameVi("Breakfast");
        catalogService.setNameEn("Breakfast buffet");
        catalogService.setDescriptionVi("Buffet breakfast");
        catalogService.setDescriptionEn("Daily buffet breakfast");
        catalogService.setPrice(BigDecimal.valueOf(150_000));
        catalogService.setStatus("ACTIVE");
        catalogService.setUpdatedAt(LocalDateTime.of(2026, 7, 31, 10, 15));
        return new Fixture(hotel, reservation, actor, catalogService);
    }

    private void authorize(Fixture fixture) {
        authenticate(Map.of(FunctionCode.RESERVATION_SERVICE, ActionCode.CREATE));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.currentUser()).thenReturn(fixture.actor());
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(3L));
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

    private record Fixture(
            Hotel hotel,
            Reservation reservation,
            User actor,
            HotelService catalogService) {
    }
}
