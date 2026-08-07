package com.hotel.propertycommerce.folio;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.paymentprovider.idempotency.FinancialIdempotencyService;
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
class SurchargeServiceTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ReservationChargeLineRepository chargeLineRepository;
    @Mock
    private PropertyAccessService propertyAccessService;
    @Mock
    private FinancialAuditService auditService;
    @Mock
    private FinancialIdempotencyService idempotencyService;

    private SurchargeService service;

    @BeforeEach
    void setUp() {
        service = new SurchargeService(
                reservationRepository,
                chargeLineRepository,
                propertyAccessService,
                auditService,
                idempotencyService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsTypedPositiveSurchargeWithActorAndAuditEvidence() {
        Fixture fixture = fixture();
        authorize(fixture, Map.of(FunctionCode.RESERVATION_SURCHARGE, ActionCode.CREATE));
        persist(fixture);

        ReservationChargeLine line = service.addSurcharge(new SurchargeService.AddSurchargeCommand(
                42L,
                SurchargeService.SurchargeType.LATE_CHECK_OUT,
                "Late checkout approved until 16:00",
                BigDecimal.valueOf(250_000),
                "surcharge-42",
                "corr-surcharge")).line();

        assertThat(line.getChargeType()).isEqualTo(ReservationChargeLine.ChargeType.SURCHARGE);
        assertThat(line.getCode()).isEqualTo("SURCHARGE:LATE_CHECK_OUT");
        assertThat(line.getUnitPrice()).isEqualByComparingTo("250000");
        assertThat(line.getTotalAmount()).isEqualByComparingTo("250000");
        assertThat(line.getActor()).isSameAs(fixture.actor());

        ArgumentCaptor<FinancialAuditService.AuditCommand> audit =
                ArgumentCaptor.forClass(FinancialAuditService.AuditCommand.class);
        verify(auditService).append(audit.capture());
        assertThat(audit.getValue().source()).isEqualTo("SURCHARGE_CREATED");
        assertThat(audit.getValue().reason()).contains("16:00");
        assertThat(audit.getValue().metadata().get("typedReason")).isEqualTo("LATE_CHECK_OUT");
    }

    @Test
    void negativeAdjustmentRequiresSeparateApprovalPermission() {
        Fixture fixture = fixture();
        authenticate(Map.of(FunctionCode.RESERVATION_SURCHARGE, ActionCode.CREATE));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);

        assertThatThrownBy(() -> service.addNegativeAdjustment(adjustmentCommand()))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.TENANT_ACCESS_DENIED));

        verify(reservationRepository, never()).findByIdForUpdate(any());
        verify(chargeLineRepository, never()).saveAndFlush(any());
        verify(auditService, never()).append(any());
    }

    @Test
    void createsNegativeAdjustmentAsDiscountMagnitudeWithSeparatePermission() {
        Fixture fixture = fixture();
        authorize(fixture, Map.of(
                FunctionCode.RESERVATION_SURCHARGE, ActionCode.CREATE,
                FunctionCode.INVOICE_ADJUST, ActionCode.APPROVE));
        persist(fixture);

        ReservationChargeLine line = service.addNegativeAdjustment(adjustmentCommand()).line();

        assertThat(line.getChargeType()).isEqualTo(ReservationChargeLine.ChargeType.DISCOUNT);
        assertThat(line.getCode()).isEqualTo("ADJUSTMENT:SERVICE_RECOVERY");
        assertThat(line.getUnitPrice()).isEqualByComparingTo("0");
        assertThat(line.getDiscountAmount()).isEqualByComparingTo("100000");
        assertThat(line.getTotalAmount()).isEqualByComparingTo("100000");
        verify(auditService).append(any());
    }

    @Test
    void missingSurchargePermissionStopsBeforeReservationLookup() {
        authenticate(Map.of(FunctionCode.RESERVATION_SURCHARGE, ActionCode.VIEW));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);

        assertThatThrownBy(() -> service.addSurcharge(surchargeCommand()))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.TENANT_ACCESS_DENIED));

        verify(reservationRepository, never()).findByIdForUpdate(any());
        verify(chargeLineRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsCrossPropertyInactiveReservationAndFractionalVnd() {
        Fixture fixture = fixture();
        authenticate(Map.of(FunctionCode.RESERVATION_SURCHARGE, ActionCode.CREATE));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.currentUser()).thenReturn(fixture.actor());
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(99L));
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(fixture.reservation()));

        assertThatThrownBy(() -> service.addSurcharge(surchargeCommand()))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.RESOURCE_NOT_FOUND));
        verify(chargeLineRepository, never()).saveAndFlush(any());

        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(3L));
        fixture.reservation().setStatus("COMPLETED");
        assertThatThrownBy(() -> service.addSurcharge(surchargeCommand()))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.INVALID_STATE_TRANSITION));

        fixture.reservation().setStatus("CHECKED_IN");
        assertThatThrownBy(() -> service.addSurcharge(new SurchargeService.AddSurchargeCommand(
                42L,
                SurchargeService.SurchargeType.OTHER,
                "Other surcharge",
                new BigDecimal("100.50"),
                "surcharge-fractional",
                null)))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.INVALID_AMOUNT));
    }

    @Test
    void returnsThePersistedLineForDuplicateReplayWithoutAppendingAnotherLine() {
        Fixture fixture = fixture();
        authorize(fixture, Map.of(FunctionCode.RESERVATION_SURCHARGE, ActionCode.CREATE));
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(fixture.reservation()));
        when(idempotencyService.begin(any())).thenReturn(
                new FinancialIdempotencyService.Replay(11L, 201, "71"));
        when(chargeLineRepository.findByIdAndHotelIdAndReservationId(71L, 3L, 42L))
                .thenReturn(Optional.of(savedLine(fixture)));

        SurchargeService.AddSurchargeResult result = service.addSurcharge(surchargeCommand());

        assertThat(result.replayed()).isTrue();
        verify(chargeLineRepository, never()).saveAndFlush(any());
        verify(auditService, never()).append(any());
    }

    @Test
    void rejectsAnInProgressDuplicateAsConcurrentModification() {
        Fixture fixture = fixture();
        authorize(fixture, Map.of(FunctionCode.RESERVATION_SURCHARGE, ActionCode.CREATE));
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(fixture.reservation()));
        when(idempotencyService.begin(any())).thenReturn(
                new FinancialIdempotencyService.InProgress(11L, "corr-existing"));

        assertThatThrownBy(() -> service.addSurcharge(surchargeCommand()))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.CONCURRENT_MODIFICATION));
        verify(chargeLineRepository, never()).saveAndFlush(any());
    }

    @Test
    void acceptsVietnameseTypeAliasesAndExposesAdjustmentHistory() {
        assertThat(SurchargeService.parseSurchargeType("trả phòng muộn"))
                .isEqualTo(SurchargeService.SurchargeType.LATE_CHECK_OUT);
        assertThat(SurchargeService.parseNegativeAdjustmentType("bồi hoàn dịch vụ"))
                .isEqualTo(SurchargeService.NegativeAdjustmentType.SERVICE_RECOVERY);

        Fixture fixture = fixture();
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(3L));
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(fixture.reservation()));
        ReservationChargeLine line = savedLine(fixture);
        when(chargeLineRepository.findByHotelIdAndReservationIdOrderByCreatedAtAscIdAsc(3L, 42L))
                .thenReturn(List.of(line));

        List<SurchargeService.AdjustmentHistoryEntry> history = service.adjustmentHistory(42L);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).reasonType()).isEqualTo("LATE_CHECK_OUT");
        assertThat(history.get(0).actorId()).isEqualTo(9L);
    }

    private SurchargeService.AddSurchargeCommand surchargeCommand() {
        return new SurchargeService.AddSurchargeCommand(
                42L,
                SurchargeService.SurchargeType.LATE_CHECK_OUT,
                "Late checkout",
                BigDecimal.valueOf(250_000),
                "surcharge-42",
                "corr-surcharge");
    }

    private SurchargeService.AddNegativeAdjustmentCommand adjustmentCommand() {
        return new SurchargeService.AddNegativeAdjustmentCommand(
                42L,
                SurchargeService.NegativeAdjustmentType.SERVICE_RECOVERY,
                "Service recovery approved by manager",
                BigDecimal.valueOf(100_000),
                "adjustment-42",
                "corr-adjustment");
    }

    private void persist(Fixture fixture) {
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(fixture.reservation()));
        when(chargeLineRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ReservationChargeLine line = invocation.getArgument(0);
            ReflectionTestUtils.setField(line, "id", 71L);
            return line;
        });
    }

    private ReservationChargeLine savedLine(Fixture fixture) {
        ReservationChargeLine line = ReservationChargeLine.create(
                fixture.reservation().getHotel(), fixture.reservation(),
                ReservationChargeLine.ChargeType.SURCHARGE, null, "SURCHARGE-V1",
                "SURCHARGE:LATE_CHECK_OUT", "Surcharge - Late check out", "Late checkout",
                BigDecimal.valueOf(250_000), BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(250_000), null, fixture.actor(), null);
        ReflectionTestUtils.setField(line, "id", 71L);
        return line;
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
        return new Fixture(reservation, actor);
    }

    private void authorize(Fixture fixture, Map<FunctionCode, Integer> permissions) {
        authenticate(permissions);
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

    private record Fixture(Reservation reservation, User actor) {
    }
}
