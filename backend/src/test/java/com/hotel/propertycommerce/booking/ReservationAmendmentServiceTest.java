package com.hotel.propertycommerce.booking;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.ReservationDetail;
import com.hotel.entities.ReservationRoom;
import com.hotel.entities.RoomType;
import com.hotel.entities.User;
import com.hotel.paymentprovider.domain.FinancialStates.BookingFinancialState;
import com.hotel.paymentprovider.domain.FinancialStates.PaymentState;
import com.hotel.paymentprovider.domain.FinancialStates.RefundState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.propertycommerce.config.PropertyPaymentConfigurationRepository;
import com.hotel.propertycommerce.config.PropertyPaymentConfiguration;
import com.hotel.propertycommerce.payment.PropertyPaymentAttempt;
import com.hotel.propertycommerce.payment.PropertyFinancialTransactionRepository;
import com.hotel.propertycommerce.payment.PropertyPaymentAttemptService;
import com.hotel.propertycommerce.refund.PropertyRefundRequest;
import com.hotel.propertycommerce.refund.PropertyRefundRequestRepository;
import com.hotel.propertycommerce.refund.PropertyRefundService;
import com.hotel.repositories.ReservationDetailRepository;
import com.hotel.repositories.ReservationHoldRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.ReservationRoomRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.services.OperationalAuditService;
import com.hotel.services.PropertyAccessService;
import com.hotel.services.PublicInventoryEligibilityPolicy;
import com.hotel.services.RoomAvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservationAmendmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-04T03:00:00Z");

    @Mock private ReservationRepository reservationRepository;
    @Mock private ReservationDetailRepository detailRepository;
    @Mock private ReservationRoomRepository reservationRoomRepository;
    @Mock private ReservationHoldRepository holdRepository;
    @Mock private RoomTypeRepository roomTypeRepository;
    @Mock private ReservationAmendmentRepository amendmentRepository;
    @Mock private PropertyFinancialTransactionRepository transactionRepository;
    @Mock private PropertyRefundRequestRepository refundRequestRepository;
    @Mock private PropertyPaymentConfigurationRepository paymentConfigurationRepository;
    @Mock private RoomAvailabilityService availabilityService;
    @Mock private PublicInventoryEligibilityPolicy inventoryEligibilityPolicy;
    @Mock private PropertyAccessService propertyAccessService;
    @Mock private PropertyPaymentAttemptService paymentAttemptService;
    @Mock private PropertyRefundService refundService;
    @Mock private BookingFinancialSummaryService financialSummaryService;
    @Mock private OperationalAuditService auditService;

    private ReservationAmendmentService service;
    private Hotel hotel;
    private RoomType standard;
    private RoomType deluxe;
    private User owner;
    private Reservation reservation;
    private ReservationDetail detail;

    @BeforeEach
    void setUp() {
        ReservationAmendmentPolicy policy = new ReservationAmendmentPolicy(
                Clock.fixed(NOW, ZoneOffset.UTC), 5, 2);
        service = new ReservationAmendmentService(
                reservationRepository,
                detailRepository,
                reservationRoomRepository,
                holdRepository,
                roomTypeRepository,
                amendmentRepository,
                transactionRepository,
                refundRequestRepository,
                paymentConfigurationRepository,
                policy,
                availabilityService,
                inventoryEligibilityPolicy,
                propertyAccessService,
                paymentAttemptService,
                refundService,
                financialSummaryService,
                auditService);

        hotel = new Hotel();
        hotel.setId(10L);
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        hotel.setCheckinTime("14:00");
        standard = roomType(100L, "Standard", 500_000);
        deluxe = roomType(200L, "Deluxe", 750_000);
        owner = user(5L);
        reservation = reservation(owner, BigDecimal.valueOf(1_050_000));
        detail = detail(reservation, standard, 1, 2, 0);

        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(reservationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));
        when(detailRepository.findByReservationId(1L)).thenReturn(List.of(detail));
        when(amendmentRepository.findByHotelIdAndIdempotencyKey(anyLong(), any())).thenReturn(Optional.empty());
        when(amendmentRepository.findActiveByReservationIdForUpdate(anyLong(), any())).thenReturn(List.of());
        when(amendmentRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationRoomRepository.findAssignedByReservationIdForUpdate(1L)).thenReturn(List.of());
        when(roomTypeRepository.findAllByIdForUpdate(any())).thenReturn(List.of(standard, deluxe));
        when(availabilityService.countAvailableRoomsExcludingReservation(
                anyLong(), any(), any(), anyLong(), any(), any())).thenReturn(5L);
    }

    @Test
    void quotePreservesFixedDiscountAndUsesServerOwnedRepricing() {
        when(availabilityService.getNights(any(), any())).thenReturn(3L, 2L);
        when(availabilityService.calculateTotal(deluxe.getBasePrice(), 3L, 1))
                .thenReturn(BigDecimal.valueOf(2_587_500));
        when(availabilityService.calculateTotal(standard.getBasePrice(), 2L, 1))
                .thenReturn(BigDecimal.valueOf(1_150_000));

        ReservationAmendmentService.QuoteResponse quote = service.quote(
                1L,
                new ReservationAmendmentService.QuoteRequest(
                        200L,
                        LocalDate.of(2026, 8, 11),
                        LocalDate.of(2026, 8, 14),
                        1,
                        2,
                        0),
                "quote-key",
                "corr-1",
                ReservationAmendmentService.AccessMode.CUSTOMER);

        assertEquals(ReservationAmendment.Status.AWAITING_PAYMENT, quote.status());
        assertEquals(0, BigDecimal.valueOf(100_000).compareTo(quote.preservedDiscount().amount()));
        assertEquals(0, BigDecimal.valueOf(1_437_500).compareTo(quote.priceDelta().amount()));
        assertEquals("2026-08-04T03:02:00Z", quote.expiresAt());
    }

    @Test
    void customerCannotQuoteAnotherUsersReservation() {
        when(propertyAccessService.currentUser()).thenReturn(user(99L));

        FinancialException exception = assertThrows(FinancialException.class,
                () -> service.context(1L, ReservationAmendmentService.AccessMode.CUSTOMER));

        assertEquals(FinancialErrorCode.RESOURCE_NOT_FOUND, exception.code());
    }

    @Test
    void structuralChangeIsBlockedWhilePhysicalRoomsAreAssigned() {
        when(reservationRoomRepository.findAssignedByReservationIdForUpdate(1L))
                .thenReturn(List.of(mock(ReservationRoom.class)));
        when(availabilityService.getNights(any(), any())).thenReturn(2L);

        FinancialException exception = assertThrows(FinancialException.class,
                () -> service.quote(
                        1L,
                        new ReservationAmendmentService.QuoteRequest(
                                200L,
                                reservation.getCheckInDate(),
                                reservation.getCheckOutDate(),
                                1,
                                2,
                                0),
                        "assigned-key",
                        "corr-2",
                        ReservationAmendmentService.AccessMode.CUSTOMER));

        assertEquals(FinancialErrorCode.CONCURRENT_MODIFICATION, exception.code());
    }

    @Test
    void positiveDeltaCannotApplyWithoutExactSuccessfulPaymentEvidence() {
        ReservationAmendment amendment = amendment(
                BigDecimal.valueOf(1_050_000),
                BigDecimal.valueOf(1_250_000),
                BigDecimal.valueOf(315_000));
        when(amendmentRepository.findByPublicIdForUpdate("quote-positive")).thenReturn(Optional.of(amendment));
        when(roomTypeRepository.findAllByIdForUpdate(any())).thenReturn(List.of(standard));

        FinancialException exception = assertThrows(FinancialException.class,
                () -> service.apply(
                        1L,
                        "quote-positive",
                        "apply-key",
                        "corr-3",
                        ReservationAmendmentService.AccessMode.CUSTOMER));

        assertEquals(FinancialErrorCode.INVALID_STATE_TRANSITION, exception.code());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void successfulAttemptStillCannotApplyWithoutItsExactLedgerEffect() {
        ReservationAmendment amendment = amendment(
                BigDecimal.valueOf(1_050_000),
                BigDecimal.valueOf(1_250_000),
                BigDecimal.valueOf(315_000));
        ReflectionTestUtils.setField(amendment, "id", 301L);
        PropertyPaymentConfiguration configuration = new PropertyPaymentConfiguration(hotel);
        PropertyPaymentAttempt attempt = PropertyPaymentAttempt.create(
                "attempt-amendment",
                hotel,
                reservation,
                configuration,
                owner,
                amendment,
                PropertyPaymentAttempt.Purpose.AMENDMENT_DELTA,
                "SIMULATOR",
                "SIMULATOR",
                PaymentEnvironment.SIMULATOR,
                VndMoney.of(200_000),
                "AMENDMENT TEST",
                "{}",
                "attempt-key",
                "b".repeat(64),
                LocalDateTime.of(2026, 8, 4, 3, 2));
        ReflectionTestUtils.setField(attempt, "id", 401L);
        attempt.transitionTo(PaymentState.PENDING, LocalDateTime.of(2026, 8, 4, 3, 0), null, null);
        attempt.transitionTo(PaymentState.SUCCESS, LocalDateTime.of(2026, 8, 4, 3, 1), null, null);
        amendment.bindPaymentAttempt(attempt);
        when(amendmentRepository.findByPublicIdForUpdate("quote-positive")).thenReturn(Optional.of(amendment));
        when(roomTypeRepository.findAllByIdForUpdate(any())).thenReturn(List.of(standard));
        when(transactionRepository.findByAttemptIdOrderByOccurredAtAsc(401L)).thenReturn(List.of());

        FinancialException exception = assertThrows(FinancialException.class,
                () -> service.apply(
                        1L,
                        "quote-positive",
                        "apply-key",
                        "corr-ledger",
                        ReservationAmendmentService.AccessMode.CUSTOMER));

        assertEquals(FinancialErrorCode.CONCURRENT_MODIFICATION, exception.code());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void decreaseRefundsOnlyThePaidExcessAboveTheNewDeposit() {
        ReservationAmendment amendment = amendment(
                BigDecimal.valueOf(1_050_000),
                BigDecimal.valueOf(850_000),
                BigDecimal.valueOf(255_000));
        when(amendmentRepository.findByPublicIdForUpdate("quote-positive")).thenReturn(Optional.of(amendment));
        when(roomTypeRepository.findAllByIdForUpdate(any())).thenReturn(List.of(standard));
        when(financialSummaryService.calculate(1L)).thenReturn(new BookingFinancialSummaryService.Summary(
                1L,
                10L,
                VndMoney.of(1_050_000),
                VndMoney.of(315_000),
                VndMoney.of(400_000),
                VndMoney.zero(),
                BigDecimal.valueOf(650_000),
                BookingFinancialState.DEPOSIT_PAID,
                1L,
                LocalDateTime.of(2026, 8, 4, 3, 0)));
        when(refundService.requestSingleSource(any())).thenReturn(new PropertyRefundService.RefundResult(
                "refund-1",
                "transaction-1",
                BigDecimal.valueOf(145_000),
                "VND",
                RefundState.REQUESTED,
                BigDecimal.ZERO,
                LocalDateTime.of(2026, 8, 4, 3, 0),
                null,
                false));
        PropertyRefundRequest refund = mock(PropertyRefundRequest.class);
        when(refund.getPublicId()).thenReturn("refund-1");
        when(refund.getRequestedAmount()).thenReturn(BigDecimal.valueOf(145_000));
        when(refundRequestRepository.findByPublicId("refund-1")).thenReturn(Optional.of(refund));

        ReservationAmendmentService.QuoteResponse result = service.apply(
                1L,
                "quote-positive",
                "apply-decrease",
                "corr-4",
                ReservationAmendmentService.AccessMode.CUSTOMER);

        ArgumentCaptor<PropertyRefundService.SingleSourceCommand> captor =
                ArgumentCaptor.forClass(PropertyRefundService.SingleSourceCommand.class);
        verify(refundService).requestSingleSource(captor.capture());
        assertEquals(0, BigDecimal.valueOf(145_000).compareTo(captor.getValue().amount()));
        assertEquals(ReservationAmendment.Status.APPLIED, result.status());
        assertEquals("refund-1", result.settlement().refundRequestPublicId());
        assertEquals(LocalDate.of(2026, 8, 10), reservation.getCheckInDate());
    }

    @Test
    void unpaidDecreaseAppliesWithoutInventingARefund() {
        ReservationAmendment amendment = amendment(
                BigDecimal.valueOf(1_050_000),
                BigDecimal.valueOf(850_000),
                BigDecimal.valueOf(255_000));
        when(amendmentRepository.findByPublicIdForUpdate("quote-positive")).thenReturn(Optional.of(amendment));
        when(roomTypeRepository.findAllByIdForUpdate(any())).thenReturn(List.of(standard));
        when(financialSummaryService.calculate(1L)).thenReturn(new BookingFinancialSummaryService.Summary(
                1L,
                10L,
                VndMoney.of(1_050_000),
                VndMoney.of(315_000),
                VndMoney.zero(),
                VndMoney.zero(),
                BigDecimal.valueOf(1_050_000),
                BookingFinancialState.UNPAID,
                0L,
                LocalDateTime.of(2026, 8, 4, 3, 0)));

        ReservationAmendmentService.QuoteResponse result = service.apply(
                1L,
                "quote-positive",
                "apply-unpaid",
                "corr-5",
                ReservationAmendmentService.AccessMode.CUSTOMER);

        verify(refundService, never()).requestSingleSource(any());
        assertNull(result.settlement().refundRequestPublicId());
        assertEquals(ReservationAmendment.Status.APPLIED, result.status());
    }

    private ReservationAmendment amendment(
            BigDecimal originalTotal,
            BigDecimal proposedTotal,
            BigDecimal proposedDeposit) {
        return ReservationAmendment.quote(new ReservationAmendment.QuoteSnapshot(
                "quote-positive",
                reservation,
                owner,
                "CUSTOMER",
                standard,
                standard,
                reservation.getCheckInDate(),
                reservation.getCheckOutDate(),
                reservation.getCheckInDate(),
                reservation.getCheckOutDate(),
                1,
                1,
                2,
                2,
                0,
                0,
                originalTotal,
                proposedTotal,
                proposedTotal.subtract(originalTotal),
                BigDecimal.valueOf(315_000),
                proposedDeposit,
                BigDecimal.valueOf(100_000),
                0,
                "quote-existing-key",
                "a".repeat(64),
                LocalDateTime.of(2026, 8, 4, 3, 2)));
    }

    private Reservation reservation(User user, BigDecimal total) {
        Reservation item = new Reservation();
        item.setId(1L);
        item.setHotel(hotel);
        item.setUser(user);
        item.setStatus("CONFIRMED");
        item.setCheckInDate(LocalDate.of(2026, 8, 10));
        item.setCheckOutDate(LocalDate.of(2026, 8, 12));
        item.setGuests(2);
        item.setTotalAmount(total);
        item.captureDepositPolicy(new DepositPolicySnapshot(
                10L,
                11L,
                1L,
                DepositPolicySnapshot.PolicyType.PERCENTAGE,
                BigDecimal.valueOf(30),
                VndMoney.of(total),
                VndMoney.of(total.multiply(BigDecimal.valueOf(30)).divide(BigDecimal.valueOf(100)))));
        return item;
    }

    private ReservationDetail detail(
            Reservation reservation,
            RoomType roomType,
            int quantity,
            int adults,
            int children) {
        ReservationDetail item = new ReservationDetail();
        item.setId(20L);
        item.setReservation(reservation);
        item.setRoomType(roomType);
        item.setQuantity(quantity);
        item.setAdults(adults);
        item.setChildren(children);
        item.setUnitPrice(roomType.getBasePrice());
        item.setPrice(roomType.getBasePrice());
        return item;
    }

    private RoomType roomType(Long id, String name, long basePrice) {
        RoomType item = new RoomType();
        item.setId(id);
        item.setHotel(hotel);
        item.setNameVi(name);
        item.setNameEn(name);
        item.setStatus("ACTIVE");
        item.setBasePrice(BigDecimal.valueOf(basePrice));
        item.setMaxAdults(4);
        item.setMaxChildren(3);
        item.setMaxGuests(5);
        return item;
    }

    private User user(Long id) {
        User item = new User();
        item.setId(id);
        item.setUsername("user-" + id);
        return item;
    }
}
