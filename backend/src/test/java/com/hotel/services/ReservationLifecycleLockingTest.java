package com.hotel.services;

import com.hotel.dtos.AssignRoomsRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.ReservationDetail;
import com.hotel.entities.ReservationRoom;
import com.hotel.entities.Room;
import com.hotel.entities.RoomType;
import com.hotel.entities.User;
import com.hotel.propertycommerce.checkout.CheckoutOperationsService;
import com.hotel.propertycommerce.config.PropertyPaymentConfigurationRepository;
import com.hotel.propertycommerce.invoice.InvoiceFinalizationService;
import com.hotel.repositories.HotelServiceRepository;
import com.hotel.repositories.HousekeepingTaskRepository;
import com.hotel.repositories.InvoiceRepository;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationLifecycleLockingTest {

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
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentService paymentService;
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
    private ReservationDetail detail;
    private Room firstRoom;
    private Room secondRoom;

    @BeforeEach
    void setUp() {
        Hotel hotel = new Hotel();
        hotel.setId(3L);

        RoomType roomType = new RoomType();
        roomType.setId(7L);
        roomType.setHotel(hotel);
        roomType.setCode("DELUXE");
        roomType.setNameVi("Deluxe");
        roomType.setNameEn("Deluxe");
        roomType.setBasePrice(BigDecimal.valueOf(1_000_000));

        User guest = new User();
        guest.setId(9L);
        guest.setUsername("locking-guest");

        reservation = new Reservation();
        reservation.setId(42L);
        reservation.setHotel(hotel);
        reservation.setUser(guest);
        reservation.setStatus("CONFIRMED");
        reservation.setCheckInDate(LocalDate.of(2028, 2, 10));
        reservation.setCheckOutDate(LocalDate.of(2028, 2, 12));
        reservation.setTotalAmount(BigDecimal.valueOf(2_000_000));

        detail = new ReservationDetail();
        detail.setId(71L);
        detail.setReservation(reservation);
        detail.setRoomType(roomType);
        detail.setQuantity(2);
        detail.setPrice(roomType.getBasePrice());

        firstRoom = room(hotel, roomType, 11L, "101");
        secondRoom = room(hotel, roomType, 12L, "102");

        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));
        when(reservationDetailRepository.findByReservationId(42L)).thenReturn(List.of(detail));
    }

    @Test
    void assignmentLocksReservationAssignmentsAndRoomsInDeterministicOrder() {
        AssignRoomsRequest request = request(12L, 11L);
        when(reservationRoomRepository.findByReservationDetailIdAndStatusForUpdate(71L, "ASSIGNED"))
                .thenReturn(List.of());
        when(roomRepository.findAllByIdForUpdate(List.of(11L, 12L)))
                .thenReturn(List.of(firstRoom, secondRoom));
        when(reservationRepository.save(reservation)).thenReturn(reservation);

        reservationService.assignRooms(42L, request);

        InOrder order = inOrder(reservationRepository, reservationRoomRepository, roomRepository);
        order.verify(reservationRepository).findByIdForUpdate(42L);
        order.verify(reservationRoomRepository)
                .findByReservationDetailIdAndStatusForUpdate(71L, "ASSIGNED");
        order.verify(roomRepository).findAllByIdForUpdate(List.of(11L, 12L));
        ArgumentCaptor<List<ReservationRoom>> assignments = ArgumentCaptor.forClass(List.class);
        verify(reservationRoomRepository).saveAllAndFlush(assignments.capture());
        assertThat(assignments.getValue())
                .extracting(item -> item.getRoom().getId())
                .containsExactly(11L, 12L);
    }

    @Test
    void identicalAssignmentReplayReturnsExistingResultWithoutWritingAgain() {
        detail.setQuantity(1);
        ReservationRoom current = assignment(detail, firstRoom);
        when(reservationRoomRepository.findByReservationDetailIdAndStatusForUpdate(71L, "ASSIGNED"))
                .thenReturn(List.of(current));
        when(reservationRoomRepository.findByReservationDetailIdAndStatus(71L, "ASSIGNED"))
                .thenReturn(List.of(current));

        reservationService.assignRooms(42L, request(11L));

        verify(roomRepository, never()).findAllByIdForUpdate(any());
        verify(reservationRoomRepository, never()).saveAllAndFlush(any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void checkInLocksAssignmentsAndRoomsBeforeChangingPhysicalRoomState() {
        detail.setQuantity(1);
        ReservationRoom assignment = assignment(detail, firstRoom);
        when(reservationRoomRepository.findAssignedByReservationIdForUpdate(42L))
                .thenReturn(List.of(assignment));
        when(roomRepository.findAllByIdForUpdate(List.of(11L))).thenReturn(List.of(firstRoom));
        when(reservationRepository.save(reservation)).thenReturn(reservation);
        when(reservationRoomRepository.findByReservationDetailIdAndStatus(71L, "ASSIGNED"))
                .thenReturn(List.of(assignment));

        reservationService.updateReservationStatus(42L, "CHECKED_IN");

        InOrder order = inOrder(reservationRepository, reservationRoomRepository, roomRepository);
        order.verify(reservationRepository).findByIdForUpdate(42L);
        order.verify(reservationRoomRepository).findAssignedByReservationIdForUpdate(42L);
        order.verify(roomRepository).findAllByIdForUpdate(List.of(11L));
        assertThat(firstRoom.getStatus()).isEqualTo("OCCUPIED");
        assertThat(reservation.getStatus()).isEqualTo("CHECKED_IN");
    }

    private AssignRoomsRequest request(Long... roomIds) {
        AssignRoomsRequest request = new AssignRoomsRequest();
        request.setRoomIds(List.of(roomIds));
        return request;
    }

    private ReservationRoom assignment(ReservationDetail reservationDetail, Room room) {
        ReservationRoom assignment = new ReservationRoom();
        assignment.setId(81L);
        assignment.setReservationDetail(reservationDetail);
        assignment.setRoom(room);
        assignment.setStatus("ASSIGNED");
        return assignment;
    }

    private Room room(Hotel hotel, RoomType roomType, Long id, String number) {
        Room room = new Room();
        room.setId(id);
        room.setHotel(hotel);
        room.setRoomType(roomType);
        room.setRoomNumber(number);
        room.setFloor(1);
        room.setStatus("AVAILABLE");
        room.setHousekeepingStatus("CLEAN");
        room.setMaintenanceStatus("NONE");
        return room;
    }
}
