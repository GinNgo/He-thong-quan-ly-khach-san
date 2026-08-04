package com.hotel.propertycommerce.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.RoomType;
import com.hotel.entities.User;
import com.hotel.paymentprovider.domain.FinancialStates.BookingFinancialState;
import com.hotel.paymentprovider.domain.FinancialStates.PaymentState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.paymentprovider.idempotency.FinancialIdempotencyRecord;
import com.hotel.paymentprovider.idempotency.FinancialIdempotencyService;
import com.hotel.propertycommerce.booking.BookingFinancialSummaryService;
import com.hotel.propertycommerce.booking.ReservationAmendment;
import com.hotel.propertycommerce.config.PropertyPaymentConfiguration;
import com.hotel.propertycommerce.config.PropertyPaymentConfigurationMethod;
import com.hotel.propertycommerce.config.PropertyPaymentConfigurationRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyPaymentAttemptServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T08:00:00Z");

    @Mock private ReservationRepository reservationRepository;
    @Mock private PropertyPaymentConfigurationRepository configurationRepository;
    @Mock private PropertyPaymentAttemptRepository attemptRepository;
    @Mock private BookingFinancialSummaryService summaryService;
    @Mock private PropertyAccessService propertyAccessService;
    @Mock private FinancialIdempotencyService idempotencyService;
    @Mock private PaymentEnvironmentGuard environmentGuard;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PropertyPaymentAttemptService service;

    @BeforeEach
    void setUp() {
        service = new PropertyPaymentAttemptService(
                reservationRepository,
                configurationRepository,
                attemptRepository,
                summaryService,
                propertyAccessService,
                idempotencyService,
                environmentGuard,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsManualDepositFromServerOwnedSummaryAndMaskedReceiverSnapshot() {
        Reservation reservation = reservation();
        PropertyPaymentConfiguration configuration = configuration(reservation.getHotel(), true);
        acquired("request-hash");
        when(propertyAccessService.currentUser()).thenReturn(reservation.getUser());
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));
        when(configurationRepository.findByHotelId(3L)).thenReturn(Optional.of(configuration));
        when(summaryService.calculate(42L)).thenReturn(summary(0, 1_200_000));
        when(attemptRepository.saveAndFlush(any(PropertyPaymentAttempt.class))).thenAnswer(invocation -> {
            PropertyPaymentAttempt attempt = invocation.getArgument(0);
            ReflectionTestUtils.setField(attempt, "id", 71L);
            return attempt;
        });

        PropertyPaymentAttemptService.AttemptResponse response = service.create(new PropertyPaymentAttemptService.CreateCommand(
                42L,
                PropertyPaymentAttempt.Purpose.DEPOSIT,
                "manual_transfer",
                "idem-deposit-1",
                "correlation-1"));

        assertEquals(71L, response.id());
        assertEquals(PaymentState.PENDING_VERIFICATION, response.status());
        assertEquals(com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment.SIMULATOR,
                response.environment());
        assertAmount(360_000, response.expectedAmount());
        assertEquals("VND", response.currency());
        assertEquals(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusMinutes(30), response.expiresAt());
        assertTrue(response.uniqueTransferContent().startsWith("BOOKING LS42-"));
        assertEquals("****6789", response.receiver().accountNumberMasked());
        assertEquals("Payment instructions", response.receiver().instructionsEn());
        assertFalse(response.receiverSnapshotJson().contains("0123456789"));
        assertFalse(response.replayed());
        verify(idempotencyService).complete(91L, 201, response.publicId());
    }

    @Test
    void balanceAttemptUsesOnlyTheCurrentServerDerivedOutstandingAmount() {
        Reservation reservation = reservation();
        acquired("balance-hash");
        when(propertyAccessService.currentUser()).thenReturn(reservation.getUser());
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));
        when(configurationRepository.findByHotelId(3L)).thenReturn(Optional.of(configuration(reservation.getHotel(), true)));
        when(summaryService.calculate(42L)).thenReturn(summary(360_000, 840_000));
        when(attemptRepository.saveAndFlush(any(PropertyPaymentAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PropertyPaymentAttemptService.AttemptResponse response = service.create(new PropertyPaymentAttemptService.CreateCommand(
                42L,
                PropertyPaymentAttempt.Purpose.BALANCE,
                "MANUAL_TRANSFER",
                "idem-balance-1",
                null));

        assertAmount(840_000, response.expectedAmount());
    }

    @Test
    void amendmentDeltaUsesTheExactServerQuoteAndCannotOutliveIt() {
        Reservation reservation = reservation();
        LocalDateTime quoteExpiry = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusMinutes(2);
        ReservationAmendment amendment = amendment(reservation, quoteExpiry);
        acquired("amendment-hash");
        when(propertyAccessService.currentUser()).thenReturn(reservation.getUser());
        when(configurationRepository.findByHotelId(3L))
                .thenReturn(Optional.of(configuration(reservation.getHotel(), true)));
        when(attemptRepository.saveAndFlush(any(PropertyPaymentAttempt.class))).thenAnswer(invocation -> {
            PropertyPaymentAttempt attempt = invocation.getArgument(0);
            ReflectionTestUtils.setField(attempt, "id", 73L);
            return attempt;
        });

        PropertyPaymentAttemptService.AttemptResponse response = service.createAmendmentDelta(
                new PropertyPaymentAttemptService.CreateAmendmentCommand(
                        amendment,
                        "MANUAL_TRANSFER",
                        "idem-amendment-1",
                        "correlation-amendment"));

        assertEquals(PropertyPaymentAttempt.Purpose.AMENDMENT_DELTA, response.purpose());
        assertAmount(200_000, response.expectedAmount());
        assertEquals(quoteExpiry, response.expiresAt());
        assertEquals(response.publicId(), amendment.getPaymentAttempt().getPublicId());
    }

    @Test
    void onlineMethodInSimulatorModeUsesSignedSimulatorProviderAndServerReference() {
        Reservation reservation = reservation();
        PropertyPaymentConfiguration configuration = configuration(reservation.getHotel(), true);
        PropertyPaymentConfigurationMethod onlineMethod = new PropertyPaymentConfigurationMethod(
                "MOMO", true, "MOMO", null);
        ReflectionTestUtils.setField(onlineMethod, "configuration", configuration);
        ReflectionTestUtils.setField(onlineMethod, "hotel", reservation.getHotel());
        configuration.getMethods().add(onlineMethod);
        acquired("simulator-online-hash");
        when(propertyAccessService.currentUser()).thenReturn(reservation.getUser());
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));
        when(configurationRepository.findByHotelId(3L)).thenReturn(Optional.of(configuration));
        when(summaryService.calculate(42L)).thenReturn(summary(0, 1_200_000));
        when(attemptRepository.saveAndFlush(any(PropertyPaymentAttempt.class))).thenAnswer(invocation -> {
            PropertyPaymentAttempt attempt = invocation.getArgument(0);
            ReflectionTestUtils.setField(attempt, "id", 72L);
            return attempt;
        });

        PropertyPaymentAttemptService.AttemptResponse response = service.create(
                new PropertyPaymentAttemptService.CreateCommand(
                        42L, PropertyPaymentAttempt.Purpose.DEPOSIT, "MOMO",
                        "idem-simulator-online", "correlation-online"));

        assertEquals(PaymentState.PENDING, response.status());
        org.mockito.ArgumentCaptor<PropertyPaymentAttempt> captor =
                org.mockito.ArgumentCaptor.forClass(PropertyPaymentAttempt.class);
        verify(attemptRepository).saveAndFlush(captor.capture());
        assertEquals("SIMULATOR", captor.getValue().getProvider());
        assertEquals(response.publicId(), captor.getValue().getProviderOrderReference());
    }

    @Test
    void completedIdempotencyReplayReturnsTheOriginalAttemptWithoutRecalculation() throws Exception {
        Reservation reservation = reservation();
        PropertyPaymentConfiguration configuration = configuration(reservation.getHotel(), true);
        PropertyPaymentAttempt existing = PropertyPaymentAttempt.create(
                "attempt-original",
                reservation.getHotel(),
                reservation,
                configuration,
                reservation.getUser(),
                PropertyPaymentAttempt.Purpose.DEPOSIT,
                "MANUAL_TRANSFER",
                "BANK",
                com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment.SIMULATOR,
                VndMoney.of(360_000),
                "BOOKING LS42-ORIGINAL",
                objectMapper.writeValueAsString(new PropertyPaymentAttemptService.ReceiverSnapshot(
                        "Test Bank", "TEST", "LUXESTAY", "****6789", "VIETQR", null,
                        "Huong dan thanh toan", "Payment instructions")),
                "idem-deposit-1",
                "request-hash",
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusMinutes(30));
        existing.transitionTo(PaymentState.PENDING_VERIFICATION,
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), null, null);
        when(propertyAccessService.currentUser()).thenReturn(reservation.getUser());
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));
        when(idempotencyService.begin(any())).thenReturn(new FinancialIdempotencyService.Replay(91L, 201, "attempt-original"));
        when(attemptRepository.findByPublicId("attempt-original")).thenReturn(Optional.of(existing));

        PropertyPaymentAttemptService.AttemptResponse response = service.create(new PropertyPaymentAttemptService.CreateCommand(
                42L,
                PropertyPaymentAttempt.Purpose.DEPOSIT,
                "MANUAL_TRANSFER",
                "idem-deposit-1",
                null));

        assertEquals("attempt-original", response.publicId());
        assertTrue(response.replayed());
        verify(configurationRepository, never()).findByHotelId(any());
        verify(summaryService, never()).calculate(any());
        verify(attemptRepository, never()).saveAndFlush(any());
    }

    @Test
    void conflictingIdempotencyPayloadAndDisabledConfigurationFailClosed() {
        Reservation reservation = reservation();
        when(propertyAccessService.currentUser()).thenReturn(reservation.getUser());
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));
        when(idempotencyService.begin(any()))
                .thenThrow(new FinancialException(FinancialErrorCode.IDEMPOTENCY_KEY_REUSED));

        FinancialException reused = assertThrows(FinancialException.class, () -> service.create(
                new PropertyPaymentAttemptService.CreateCommand(42L, PropertyPaymentAttempt.Purpose.DEPOSIT,
                        "MOMO", "idem-deposit-1", null)));
        assertEquals(FinancialErrorCode.IDEMPOTENCY_KEY_REUSED, reused.code());
        verify(attemptRepository, never()).saveAndFlush(any());

        org.mockito.Mockito.reset(idempotencyService);
        when(idempotencyService.begin(any())).thenReturn(new FinancialIdempotencyService.Acquired(
                org.mockito.Mockito.mock(FinancialIdempotencyRecord.class)));
        when(configurationRepository.findByHotelId(3L))
                .thenReturn(Optional.of(configuration(reservation.getHotel(), false)));

        FinancialException disabled = assertThrows(FinancialException.class, () -> service.create(
                new PropertyPaymentAttemptService.CreateCommand(42L, PropertyPaymentAttempt.Purpose.DEPOSIT,
                        "MANUAL_TRANSFER", "idem-disabled-1", null)));
        assertEquals(FinancialErrorCode.PAYMENT_ENVIRONMENT_DISABLED, disabled.code());
        verify(attemptRepository, never()).saveAndFlush(any());
    }

    @Test
    void crossAccountCreationIsHiddenBeforeIdempotencyOrFinancialReads() {
        Reservation reservation = reservation();
        User attacker = new User();
        attacker.setId(8L);
        when(propertyAccessService.currentUser()).thenReturn(attacker);
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));

        FinancialException exception = assertThrows(FinancialException.class, () -> service.create(
                new PropertyPaymentAttemptService.CreateCommand(42L, PropertyPaymentAttempt.Purpose.DEPOSIT,
                        "MANUAL_TRANSFER", "idem-attacker", null)));

        assertEquals(FinancialErrorCode.RESOURCE_NOT_FOUND, exception.code());
        verify(idempotencyService, never()).begin(any());
        verify(summaryService, never()).calculate(any());
    }

    @Test
    void environmentGuardFailurePreventsAttemptPersistence() {
        Reservation reservation = reservation();
        when(idempotencyService.begin(any())).thenReturn(
                new FinancialIdempotencyService.Acquired(
                        org.mockito.Mockito.mock(FinancialIdempotencyRecord.class)));
        when(propertyAccessService.currentUser()).thenReturn(reservation.getUser());
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));
        when(configurationRepository.findByHotelId(3L))
                .thenReturn(Optional.of(configuration(reservation.getHotel(), true)));
        when(environmentGuard.validate(any(), any(), org.mockito.ArgumentMatchers.isNull()))
                .thenThrow(new FinancialException(FinancialErrorCode.PAYMENT_ENVIRONMENT_DISABLED));

        FinancialException exception = assertThrows(FinancialException.class, () -> service.create(
                new PropertyPaymentAttemptService.CreateCommand(42L, PropertyPaymentAttempt.Purpose.DEPOSIT,
                        "MANUAL_TRANSFER", "idem-environment", null)));

        assertEquals(FinancialErrorCode.PAYMENT_ENVIRONMENT_DISABLED, exception.code());
        verify(attemptRepository, never()).saveAndFlush(any());
        verify(summaryService, never()).calculate(any());
    }

    @Test
    void authorizedOwnerCanReadFinancialSummaryAndAttempt() throws Exception {
        Reservation reservation = reservation();
        PropertyPaymentAttempt attempt = attempt(
                reservation,
                PaymentState.PENDING_VERIFICATION,
                LocalDateTime.ofInstant(NOW.plusSeconds(900), ZoneOffset.UTC));
        BookingFinancialSummaryService.Summary summary = summary(0, 1_200_000);
        when(propertyAccessService.currentUser()).thenReturn(reservation.getUser());
        when(reservationRepository.findById(42L)).thenReturn(Optional.of(reservation));
        when(summaryService.calculate(42L)).thenReturn(summary);
        when(attemptRepository.findByPublicIdForUpdate("attempt-owned")).thenReturn(Optional.of(attempt));

        assertEquals(summary, service.financialSummary(42L));
        PropertyPaymentAttemptService.AttemptResponse response = service.getOwned("attempt-owned");

        assertEquals("attempt-owned", response.publicId());
        assertEquals(PaymentState.PENDING_VERIFICATION, response.status());
        verify(attemptRepository, never()).saveAndFlush(attempt);
    }

    @Test
    void expiredAttemptReadPersistsExpiredState() throws Exception {
        Reservation reservation = reservation();
        PropertyPaymentAttempt attempt = attempt(
                reservation,
                PaymentState.PENDING,
                LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        when(propertyAccessService.currentUser()).thenReturn(reservation.getUser());
        when(attemptRepository.findByPublicIdForUpdate("attempt-owned")).thenReturn(Optional.of(attempt));
        when(attemptRepository.saveAndFlush(attempt)).thenReturn(attempt);

        PropertyPaymentAttemptService.AttemptResponse response = service.getOwned("attempt-owned");

        assertEquals(PaymentState.EXPIRED, response.status());
        verify(attemptRepository).saveAndFlush(attempt);
    }

    @Test
    void ownerCancelsActiveAttemptThroughPersistedIdempotencyBoundary() throws Exception {
        Reservation reservation = reservation();
        PropertyPaymentAttempt attempt = attempt(
                reservation,
                PaymentState.PENDING_VERIFICATION,
                LocalDateTime.ofInstant(NOW.plusSeconds(900), ZoneOffset.UTC));
        FinancialIdempotencyRecord cancellationRecord = org.mockito.Mockito.mock(FinancialIdempotencyRecord.class);
        when(cancellationRecord.getId()).thenReturn(91L);
        when(idempotencyService.begin(any())).thenReturn(
                new FinancialIdempotencyService.Acquired(cancellationRecord));
        when(propertyAccessService.currentUser()).thenReturn(reservation.getUser());
        when(attemptRepository.findByPublicIdForUpdate("attempt-owned")).thenReturn(Optional.of(attempt));
        when(attemptRepository.saveAndFlush(attempt)).thenReturn(attempt);

        PropertyPaymentAttemptService.AttemptResponse response = service.cancelOwned(
                new PropertyPaymentAttemptService.CancelCommand(
                        "attempt-owned", "cancel-key", "cancel-correlation"));

        assertEquals(PaymentState.CANCELLED, response.status());
        assertFalse(response.replayed());
        verify(idempotencyService).complete(91L, 200, "attempt-owned");
    }

    @Test
    void unauthorizedAttemptReadIsHidden() throws Exception {
        Reservation reservation = reservation();
        PropertyPaymentAttempt attempt = attempt(
                reservation,
                PaymentState.PENDING,
                LocalDateTime.ofInstant(NOW.plusSeconds(900), ZoneOffset.UTC));
        User attacker = new User();
        attacker.setId(999L);
        when(propertyAccessService.currentUser()).thenReturn(attacker);
        when(attemptRepository.findByPublicIdForUpdate("attempt-owned")).thenReturn(Optional.of(attempt));

        FinancialException exception = assertThrows(
                FinancialException.class,
                () -> service.getOwned("attempt-owned"));

        assertEquals(FinancialErrorCode.RESOURCE_NOT_FOUND, exception.code());
        verify(attemptRepository, never()).saveAndFlush(any());
    }

    private void acquired(String requestHash) {
        FinancialIdempotencyRecord record = org.mockito.Mockito.mock(FinancialIdempotencyRecord.class);
        when(record.getId()).thenReturn(91L);
        when(record.getRequestHash()).thenReturn(requestHash);
        when(idempotencyService.begin(any())).thenReturn(new FinancialIdempotencyService.Acquired(record));
    }

    private BookingFinancialSummaryService.Summary summary(long paid, long remaining) {
        return new BookingFinancialSummaryService.Summary(
                42L,
                3L,
                VndMoney.of(1_200_000),
                VndMoney.of(360_000),
                VndMoney.of(paid),
                VndMoney.zero(),
                BigDecimal.valueOf(remaining),
                paid == 0 ? BookingFinancialState.UNPAID : BookingFinancialState.DEPOSIT_PAID,
                paid == 0 ? 0 : 1,
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    private Reservation reservation() {
        Hotel hotel = new Hotel();
        hotel.setId(3L);
        User user = new User();
        user.setId(7L);
        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setHotel(hotel);
        reservation.setUser(user);
        reservation.setStatus("PENDING_PAYMENT");
        reservation.setCheckInDate(LocalDate.of(2026, 8, 10));
        reservation.setCheckOutDate(LocalDate.of(2026, 8, 12));
        reservation.setTotalAmount(BigDecimal.valueOf(1_200_000));
        ReflectionTestUtils.setField(reservation, "depositBookingTotal", BigDecimal.valueOf(1_200_000));
        ReflectionTestUtils.setField(reservation, "depositRequired", BigDecimal.valueOf(360_000));
        return reservation;
    }

    private ReservationAmendment amendment(Reservation reservation, LocalDateTime expiresAt) {
        RoomType roomType = new RoomType();
        roomType.setId(21L);
        roomType.setHotel(reservation.getHotel());
        roomType.setBasePrice(BigDecimal.valueOf(500_000));
        ReservationAmendment amendment = ReservationAmendment.quote(new ReservationAmendment.QuoteSnapshot(
                "quote-amendment",
                reservation,
                reservation.getUser(),
                "CUSTOMER",
                roomType,
                roomType,
                reservation.getCheckInDate(),
                reservation.getCheckOutDate(),
                reservation.getCheckInDate(),
                reservation.getCheckOutDate().plusDays(1),
                1,
                1,
                2,
                2,
                0,
                0,
                BigDecimal.valueOf(1_200_000),
                BigDecimal.valueOf(1_400_000),
                BigDecimal.valueOf(200_000),
                BigDecimal.valueOf(360_000),
                BigDecimal.valueOf(420_000),
                BigDecimal.ZERO,
                1,
                "quote-idempotency",
                "a".repeat(64),
                expiresAt));
        ReflectionTestUtils.setField(amendment, "id", 301L);
        return amendment;
    }

    private PropertyPaymentConfiguration configuration(Hotel hotel, boolean enabled) {
        PropertyPaymentConfiguration configuration = new PropertyPaymentConfiguration(hotel);
        ReflectionTestUtils.setField(configuration, "id", 11L);
        ReflectionTestUtils.setField(configuration, "version", 4L);
        ReflectionTestUtils.setField(configuration, "enabled", enabled);
        ReflectionTestUtils.setField(configuration, "environment", "SIMULATOR");
        ReflectionTestUtils.setField(configuration, "bankName", "Test Bank");
        ReflectionTestUtils.setField(configuration, "bankCode", "TEST");
        ReflectionTestUtils.setField(configuration, "accountName", "LUXESTAY");
        ReflectionTestUtils.setField(configuration, "accountNumberMasked", "****6789");
        ReflectionTestUtils.setField(configuration, "paymentExpiryMinutes", 30);
        ReflectionTestUtils.setField(configuration, "transferTemplate", "BOOKING {paymentCode}");
        ReflectionTestUtils.setField(configuration, "qrProvider", "VIETQR");
        ReflectionTestUtils.setField(configuration, "instructionsVi", "Huong dan thanh toan");
        ReflectionTestUtils.setField(configuration, "instructionsEn", "Payment instructions");
        PropertyPaymentConfigurationMethod method = new PropertyPaymentConfigurationMethod(
                "MANUAL_TRANSFER", true, "BANK", null);
        ReflectionTestUtils.setField(method, "configuration", configuration);
        ReflectionTestUtils.setField(method, "hotel", hotel);
        configuration.getMethods().add(method);
        return configuration;
    }

    private PropertyPaymentAttempt attempt(
            Reservation reservation,
            PaymentState status,
            LocalDateTime expiresAt) throws Exception {
        PropertyPaymentConfiguration configuration = configuration(reservation.getHotel(), true);
        PropertyPaymentAttempt attempt = PropertyPaymentAttempt.create(
                "attempt-owned",
                reservation.getHotel(),
                reservation,
                configuration,
                reservation.getUser(),
                PropertyPaymentAttempt.Purpose.DEPOSIT,
                "MANUAL_TRANSFER",
                "BANK",
                PaymentEnvironmentGuard.PaymentEnvironment.SIMULATOR,
                VndMoney.of(360_000),
                "BOOKING LS42-OWNED",
                objectMapper.writeValueAsString(new PropertyPaymentAttemptService.ReceiverSnapshot(
                        "Test Bank", "TEST", "LUXESTAY", "****6789", "VIETQR", null,
                        "Huong dan thanh toan", "Payment instructions")),
                "attempt-key",
                "attempt-hash",
                expiresAt);
        attempt.transitionTo(status, LocalDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC), null,
                status == PaymentState.FAILED ? "FAILED" : null);
        ReflectionTestUtils.setField(attempt, "id", 71L);
        return attempt;
    }

    private void assertAmount(long expected, BigDecimal actual) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual));
    }
}
