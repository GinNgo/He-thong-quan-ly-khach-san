package com.hotel.services;

import com.hotel.dtos.CheckoutRequest;
import com.hotel.dtos.CheckoutResultDTO;
import com.hotel.entities.Hotel;
import com.hotel.entities.HousekeepingTask;
import com.hotel.entities.Reservation;
import com.hotel.entities.ReservationDetail;
import com.hotel.entities.ReservationRoom;
import com.hotel.entities.Room;
import com.hotel.entities.User;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.propertycommerce.config.PropertyPaymentConfigurationRepository;
import com.hotel.propertycommerce.checkout.CheckoutOperationsService;
import com.hotel.propertycommerce.invoice.InvoiceFinalizationService;
import com.hotel.propertycommerce.invoice.PropertyInvoice;
import com.hotel.repositories.HotelServiceRepository;
import com.hotel.repositories.HousekeepingTaskRepository;
import com.hotel.repositories.PaymentRepository;
import com.hotel.repositories.PaymentSessionRepository;
import com.hotel.repositories.RefundRequestRepository;
import com.hotel.repositories.ReservationDetailRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.ReservationRoomRepository;
import com.hotel.repositories.ReservationServiceItemRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationCheckoutTransactionTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private ReservationDetailRepository reservationDetailRepository;
    @Mock private ReservationRoomRepository reservationRoomRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private RoomTypeRepository roomTypeRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoomAvailabilityService roomAvailabilityService;
    @Mock private NotificationService notificationService;
    @Mock private EmailService emailService;
    @Mock private ReservationServiceItemRepository reservationServiceItemRepository;
    @Mock private HotelServiceRepository hotelServiceRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentSessionRepository paymentSessionRepository;
    @Mock private RefundRequestRepository refundRequestRepository;
    @Mock private RefundService refundService;
    @Mock private HousekeepingTaskRepository housekeepingTaskRepository;
    @Mock private PropertyAccessService propertyAccessService;
    @Mock private ReservationHoldService reservationHoldService;
    @Mock private PropertyPaymentConfigurationRepository propertyPaymentConfigurationRepository;
    @Mock private InvoiceFinalizationService invoiceFinalizationService;
    @Mock private CheckoutOperationsService checkoutOperationsService;

    @InjectMocks private ReservationService reservationService;

    private Reservation reservation;
    private ReservationRoom assignment;
    private Room room;
    private PropertyInvoice invoice;

    @BeforeEach
    void setUp() {
        Hotel hotel = new Hotel();
        hotel.setId(3L);
        User guest = new User();
        guest.setId(8L);
        reservation = new Reservation();
        reservation.setId(42L);
        reservation.setHotel(hotel);
        reservation.setUser(guest);
        reservation.setStatus("CHECKED_IN");
        reservation.setTotalAmount(BigDecimal.valueOf(1_000_000));

        room = new Room();
        room.setId(12L);
        room.setHotel(hotel);
        room.setStatus("OCCUPIED");
        room.setHousekeepingStatus("CLEAN");
        ReservationDetail detail = new ReservationDetail();
        detail.setId(71L);
        detail.setReservation(reservation);
        assignment = new ReservationRoom();
        assignment.setId(81L);
        assignment.setReservationDetail(detail);
        assignment.setRoom(room);
        assignment.setStatus("ASSIGNED");

        User staff = new User();
        staff.setId(9L);
        invoice = PropertyInvoice.finalized(
                hotel,
                reservation,
                "INV-3-42",
                "{}",
                "{}",
                VndMoney.of(1_000_000),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.of(1_000_000),
                VndMoney.of(1_000_000),
                VndMoney.zero(),
                VndMoney.zero(),
                staff,
                LocalDateTime.of(2026, 8, 1, 1, 0));
        ReflectionTestUtils.setField(invoice, "id", 88L);
    }

    @Test
    void locksAndCompletesInvoiceAssignmentsRoomsHousekeepingAndReservationTogether() {
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
        when(invoiceFinalizationService.finalizeInvoice(any())).thenReturn(
                new InvoiceFinalizationService.FinalizedInvoice(invoice, List.of(), List.of()));
        when(checkoutOperationsService.apply(reservation)).thenReturn(
                new CheckoutOperationsService.CheckoutOperationsResult(List.of(12L), 1, 1));

        CheckoutRequest request = new CheckoutRequest();
        request.setCheckoutOverrideId(77L);
        CheckoutResultDTO result = reservationService.checkout(42L, request);

        ArgumentCaptor<InvoiceFinalizationService.FinalizeInvoiceCommand> commandCaptor =
                ArgumentCaptor.forClass(InvoiceFinalizationService.FinalizeInvoiceCommand.class);
        verify(invoiceFinalizationService).finalizeInvoice(commandCaptor.capture());
        assertThat(commandCaptor.getValue().reservationId()).isEqualTo(42L);
        assertThat(commandCaptor.getValue().checkoutOverrideId()).isEqualTo(77L);
        assertThat(reservation.getStatus()).isEqualTo("CHECKED_OUT");
        assertThat(result.getInvoiceId()).isEqualTo(88L);
        assertThat(result.getInvoiceCode()).isEqualTo("INV-3-42");
        assertThat(result.getInvoiceStatus()).isEqualTo("FINALIZED");
        assertThat(result.getDirtyRoomIds()).containsExactly(12L);
        verify(checkoutOperationsService).apply(reservation);
        verify(reservationRepository).saveAndFlush(reservation);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void rejectsCallerAuthoritativePaymentDataBeforeLocking() {
        CheckoutRequest request = new CheckoutRequest();
        request.setPaymentAmount(BigDecimal.valueOf(1_000_000));
        request.setPaymentMethod("CASH");
        request.setTransactionId("client-reference");

        assertThatThrownBy(() -> reservationService.checkout(42L, request))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.INVALID_AMOUNT));

        verify(reservationRepository, never()).findByIdForUpdate(any());
        verify(invoiceFinalizationService, never()).finalizeInvoice(any());
    }

    @Test
    void invoiceFailureStopsAllOperationalCheckoutMutations() {
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
        when(invoiceFinalizationService.finalizeInvoice(any()))
                .thenThrow(new FinancialException(FinancialErrorCode.OUTSTANDING_BALANCE));

        assertThatThrownBy(() -> reservationService.checkout(42L, null))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.OUTSTANDING_BALANCE));

        verify(reservationRoomRepository, never()).findAssignedByReservationIdForUpdate(any());
        verify(checkoutOperationsService, never()).apply(any());
        verify(reservationRepository, never()).saveAndFlush(any());
        assertThat(reservation.getStatus()).isEqualTo("CHECKED_IN");
    }

    @Test
    void operationalFailureLeavesReservationCheckedInForTransactionRollback() {
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
        when(invoiceFinalizationService.finalizeInvoice(any())).thenReturn(
                new InvoiceFinalizationService.FinalizedInvoice(invoice, List.of(), List.of()));
        when(checkoutOperationsService.apply(reservation))
                .thenThrow(new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION));

        assertThatThrownBy(() -> reservationService.checkout(42L, null))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.CONCURRENT_MODIFICATION));

        assertThat(reservation.getStatus()).isEqualTo("CHECKED_IN");
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    void completedCheckoutRetryReturnsExistingEvidenceWithoutSavingReservationAgain() {
        reservation.setStatus("CHECKED_OUT");
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
        when(invoiceFinalizationService.finalizeInvoice(any())).thenReturn(
                new InvoiceFinalizationService.FinalizedInvoice(invoice, List.of(), List.of()));
        when(checkoutOperationsService.apply(reservation)).thenReturn(
                new CheckoutOperationsService.CheckoutOperationsResult(List.of(12L), 0, 0));

        CheckoutResultDTO result = reservationService.checkout(42L, null);

        assertThat(result.getReservationStatus()).isEqualTo("CHECKED_OUT");
        assertThat(result.getDirtyRoomIds()).containsExactly(12L);
        verify(reservationRepository, never()).saveAndFlush(any());
    }
}
