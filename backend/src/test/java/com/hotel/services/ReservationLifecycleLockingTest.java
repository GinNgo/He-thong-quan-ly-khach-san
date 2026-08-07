package com.hotel.services;

import com.hotel.dtos.AssignRoomsRequest;
import com.hotel.dtos.RoomAssignmentMutationRequest;
import com.hotel.dtos.RoomAssignmentReleaseRequest;
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
<<<<<<< HEAD
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.propertycommerce.stay.CheckInPolicy;
=======
import com.hotel.propertycommerce.refund.PropertyRefundRequestRepository;
>>>>>>> codex/ui-functional-audit-polish
import com.hotel.repositories.HotelServiceRepository;
import com.hotel.repositories.HousekeepingTaskRepository;
import com.hotel.repositories.InvoiceRepository;
import com.hotel.repositories.PaymentRepository;
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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
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
<<<<<<< HEAD
=======
    @Mock private PaymentSessionRepository paymentSessionRepository;
    @Mock private RefundRequestRepository refundRequestRepository;
    @Mock private RefundService refundService;
    @Mock private PropertyRefundRequestRepository propertyRefundRequestRepository;
>>>>>>> codex/ui-functional-audit-polish
    @Mock private HousekeepingTaskRepository housekeepingTaskRepository;
    @Mock private PropertyAccessService propertyAccessService;
    @Mock private ReservationHoldService reservationHoldService;
    @Mock private PropertyPaymentConfigurationRepository propertyPaymentConfigurationRepository;
    @Mock private InvoiceFinalizationService invoiceFinalizationService;
    @Mock private CheckoutOperationsService checkoutOperationsService;
    @Mock private OperationalAuditService operationalAuditService;
    @Mock private CheckInPolicy checkInPolicy;

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
        lenient().when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(reservation));
<<<<<<< HEAD
        lenient().when(reservationRepository.findById(42L)).thenReturn(Optional.of(reservation));
        lenient().when(reservationDetailRepository.findByReservationId(42L)).thenReturn(List.of(detail));
=======
        when(reservationDetailRepository.findByReservationId(42L)).thenReturn(List.of(detail));
        lenient().when(propertyRefundRequestRepository.findByReservationIdOrderByRequestedAtAsc(42L))
                .thenReturn(List.of());
>>>>>>> codex/ui-functional-audit-polish
    }

    @Test
    void assignmentLocksReservationAssignmentsAndRoomsInDeterministicOrder() {
        AssignRoomsRequest request = request(12L, 11L);
        when(reservationRoomRepository.findByReservationDetailIdAndStatusForUpdate(71L, "ASSIGNED"))
                .thenReturn(List.of());
        when(roomRepository.findAllByHotelIdAndIdInForUpdate(3L, List.of(11L, 12L)))
                .thenReturn(List.of(firstRoom, secondRoom));
        when(reservationRepository.save(reservation)).thenReturn(reservation);

        reservationService.assignRooms(42L, request);

        InOrder order = inOrder(reservationRepository, reservationRoomRepository, roomRepository);
        order.verify(reservationRepository).findByIdForUpdate(42L);
        order.verify(reservationRoomRepository)
                .findByReservationDetailIdAndStatusForUpdate(71L, "ASSIGNED");
        order.verify(roomRepository).findAllByHotelIdAndIdInForUpdate(3L, List.of(11L, 12L));
        ArgumentCaptor<List<ReservationRoom>> assignments = ArgumentCaptor.forClass(List.class);
        verify(reservationRoomRepository).saveAllAndFlush(assignments.capture());
        assertThat(assignments.getValue())
                .extracting(item -> item.getRoom().getId())
                .containsExactly(11L, 12L);
        assertThat(firstRoom.getStatus()).isEqualTo("RESERVED");
        assertThat(secondRoom.getStatus()).isEqualTo("RESERVED");
        verify(roomRepository).saveAllAndFlush(List.of(firstRoom, secondRoom));
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
        verify(operationalAuditService, never()).append(any());
    }

    @Test
    void exactReassignmentReplayReturnsWithoutWritesOrDuplicateHistory() {
        detail.setQuantity(1);
        firstRoom.setStatus("RESERVED");
        ReservationRoom current = assignment(detail, firstRoom);
        when(reservationRoomRepository.findByReservationDetailIdAndStatusForUpdate(71L, "ASSIGNED"))
                .thenReturn(List.of(current));
        when(reservationRoomRepository.findByReservationDetailIdAndStatus(71L, "ASSIGNED"))
                .thenReturn(List.of(current));

        reservationService.updateRoomAssignment(
                42L, new RoomAssignmentMutationRequest(List.of(11L), "Giữ nguyên phòng đã xác nhận"));

        verify(roomRepository, never()).findAllByHotelIdAndIdInForUpdate(any(), any());
        verify(reservationRoomRepository, never()).saveAllAndFlush(any());
        verify(operationalAuditService, never()).append(any());
    }

    @Test
    void idempotencyRecoveryReturnsOnlyCommittedAssignmentOrReleaseState() {
        detail.setQuantity(1);
        firstRoom.setStatus("RESERVED");
        ReservationRoom current = assignment(detail, firstRoom);
        when(reservationRepository.findById(42L)).thenReturn(Optional.of(reservation));
        when(reservationRoomRepository.findByReservationDetailIdAndStatus(71L, "ASSIGNED"))
                .thenReturn(List.of(current));

        assertThat(reservationService.findRoomAssignmentReplay(42L, List.of(11L))).isPresent();
        assertThat(reservationService.findRoomAssignmentReplay(42L, List.of(12L))).isEmpty();

        when(reservationRoomRepository.findByReservationDetailIdAndStatus(71L, "ASSIGNED"))
                .thenReturn(List.of());
        assertThat(reservationService.findRoomReleaseReplay(42L)).isPresent();
    }

    @Test
    void reassignmentPreservesIntersectionAndLocksTheSortedRoomUnion() {
        Room thirdRoom = room(reservation.getHotel(), detail.getRoomType(), 13L, "103");
        firstRoom.setStatus("RESERVED");
        secondRoom.setStatus("RESERVED");
        ReservationRoom firstAssignment = assignment(81L, detail, firstRoom);
        ReservationRoom retainedAssignment = assignment(82L, detail, secondRoom);
        LocalDateTime originalAssignedAt = LocalDateTime.of(2028, 1, 5, 10, 30);
        retainedAssignment.setAssignedAt(originalAssignedAt);
        when(reservationRoomRepository.findByReservationDetailIdAndStatusForUpdate(71L, "ASSIGNED"))
                .thenReturn(List.of(firstAssignment, retainedAssignment));
        when(roomRepository.findAllByHotelIdAndIdInForUpdate(3L, List.of(11L, 12L, 13L)))
                .thenReturn(List.of(firstRoom, secondRoom, thirdRoom));
        when(reservationRepository.save(reservation)).thenReturn(reservation);

        reservationService.updateRoomAssignment(
                42L,
                new RoomAssignmentMutationRequest(List.of(13L, 12L), "Đổi một phòng do yêu cầu tầng cao"));

        InOrder order = inOrder(reservationRepository, reservationRoomRepository, roomRepository);
        order.verify(reservationRepository).findByIdForUpdate(42L);
        order.verify(reservationRoomRepository)
                .findByReservationDetailIdAndStatusForUpdate(71L, "ASSIGNED");
        order.verify(roomRepository).findAllByHotelIdAndIdInForUpdate(3L, List.of(11L, 12L, 13L));
        ArgumentCaptor<List<ReservationRoom>> changed = ArgumentCaptor.forClass(List.class);
        verify(reservationRoomRepository).saveAllAndFlush(changed.capture());
        assertThat(changed.getValue()).hasSize(2);
        assertThat(changed.getValue()).anySatisfy(item -> {
            assertThat(item).isSameAs(firstAssignment);
            assertThat(item.getStatus()).isEqualTo("RELEASED");
        });
        assertThat(changed.getValue()).anySatisfy(item -> {
            assertThat(item.getRoom()).isSameAs(thirdRoom);
            assertThat(item.getStatus()).isEqualTo("ASSIGNED");
        });
        assertThat(retainedAssignment.getStatus()).isEqualTo("ASSIGNED");
        assertThat(retainedAssignment.getAssignedAt()).isEqualTo(originalAssignedAt);
        assertThat(firstRoom.getStatus()).isEqualTo("AVAILABLE");
        assertThat(secondRoom.getStatus()).isEqualTo("RESERVED");
        assertThat(thirdRoom.getStatus()).isEqualTo("RESERVED");
        assertThat(detail.getRoom()).isSameAs(secondRoom);
        ArgumentCaptor<OperationalAuditService.AuditCommand> audit =
                ArgumentCaptor.forClass(OperationalAuditService.AuditCommand.class);
        verify(operationalAuditService).append(audit.capture());
        assertThat(audit.getValue().eventType()).isEqualTo("ROOMS_REASSIGNED");
        assertThat(audit.getValue().reason()).isEqualTo("Đổi một phòng do yêu cầu tầng cao");
    }

    @Test
    void reassignmentConflictLeavesCurrentAssignmentStateUntouched() {
        Room thirdRoom = room(reservation.getHotel(), detail.getRoomType(), 13L, "103");
        firstRoom.setStatus("RESERVED");
        secondRoom.setStatus("RESERVED");
        ReservationRoom firstAssignment = assignment(81L, detail, firstRoom);
        ReservationRoom secondAssignment = assignment(82L, detail, secondRoom);
        when(reservationRoomRepository.findByReservationDetailIdAndStatusForUpdate(71L, "ASSIGNED"))
                .thenReturn(List.of(firstAssignment, secondAssignment));
        when(roomRepository.findAllByHotelIdAndIdInForUpdate(3L, List.of(11L, 12L, 13L)))
                .thenReturn(List.of(firstRoom, secondRoom, thirdRoom));
        when(reservationRoomRepository.hasConflictingAssignment(
                13L, 42L, RoomAvailabilityService.RELEASED_RESERVATION_STATUSES,
                reservation.getCheckInDate(), reservation.getCheckOutDate())).thenReturn(true);

        assertThatThrownBy(() -> reservationService.updateRoomAssignment(
                42L, new RoomAssignmentMutationRequest(List.of(12L, 13L), "Đổi phòng theo yêu cầu")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("booking khác");

        assertThat(firstAssignment.getStatus()).isEqualTo("ASSIGNED");
        assertThat(secondAssignment.getStatus()).isEqualTo("ASSIGNED");
        assertThat(firstRoom.getStatus()).isEqualTo("RESERVED");
        assertThat(secondRoom.getStatus()).isEqualTo("RESERVED");
        assertThat(thirdRoom.getStatus()).isEqualTo("AVAILABLE");
        verify(roomRepository, never()).saveAllAndFlush(any());
        verify(reservationRoomRepository, never()).saveAllAndFlush(any());
        verify(operationalAuditService, never()).append(any());
    }

    @Test
    void reassignmentDoesNotReadOrMutateForeignPropertyRoomIds() {
        firstRoom.setStatus("RESERVED");
        secondRoom.setStatus("RESERVED");
        ReservationRoom firstAssignment = assignment(81L, detail, firstRoom);
        ReservationRoom secondAssignment = assignment(82L, detail, secondRoom);
        when(reservationRoomRepository.findByReservationDetailIdAndStatusForUpdate(71L, "ASSIGNED"))
                .thenReturn(List.of(firstAssignment, secondAssignment));
        when(roomRepository.findAllByHotelIdAndIdInForUpdate(3L, List.of(11L, 12L, 99L)))
                .thenReturn(List.of(firstRoom, secondRoom));

        assertThatThrownBy(() -> reservationService.updateRoomAssignment(
                42L, new RoomAssignmentMutationRequest(List.of(12L, 99L), "Đổi phòng theo yêu cầu")))
                .isInstanceOf(com.hotel.paymentprovider.error.FinancialException.class)
                .hasMessageContaining("Kho phòng đã thay đổi");

        verify(roomRepository).findAllByHotelIdAndIdInForUpdate(3L, List.of(11L, 12L, 99L));
        verify(roomRepository, never()).findAllByIdForUpdate(List.of(11L, 12L, 99L));
        verify(operationalAuditService, never()).append(any());
    }

    @Test
    void releaseRequiresReasonAndIsReplaySafeWhenNoAssignmentRemains() {
        when(reservationRoomRepository.findByReservationDetailIdAndStatusForUpdate(71L, "ASSIGNED"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> reservationService.releaseRoomAssignment(
                42L, new RoomAssignmentReleaseRequest(" ")))
                .isInstanceOf(IllegalArgumentException.class);

        reservationService.releaseRoomAssignment(
                42L, new RoomAssignmentReleaseRequest("Khách đổi ngày nhận phòng"));

        verify(roomRepository, never()).findAllByHotelIdAndIdInForUpdate(any(), any());
        verify(reservationRoomRepository, never()).saveAllAndFlush(any());
        verify(operationalAuditService, never()).append(any());
    }

    @Test
    void releaseLocksRoomsClearsLegacyPointersAndWritesHistory() {
        firstRoom.setStatus("RESERVED");
        secondRoom.setStatus("RESERVED");
        detail.setRoom(firstRoom);
        reservation.setRoom(firstRoom);
        ReservationRoom firstAssignment = assignment(81L, detail, firstRoom);
        ReservationRoom secondAssignment = assignment(82L, detail, secondRoom);
        when(reservationRoomRepository.findByReservationDetailIdAndStatusForUpdate(71L, "ASSIGNED"))
                .thenReturn(List.of(firstAssignment, secondAssignment));
        when(roomRepository.findAllByHotelIdAndIdInForUpdate(3L, List.of(11L, 12L)))
                .thenReturn(List.of(firstRoom, secondRoom));
        when(reservationRepository.save(reservation)).thenReturn(reservation);

        reservationService.releaseRoomAssignment(
                42L, new RoomAssignmentReleaseRequest("Giải phóng để xử lý bảo trì"));

        InOrder order = inOrder(reservationRepository, reservationRoomRepository, roomRepository);
        order.verify(reservationRepository).findByIdForUpdate(42L);
        order.verify(reservationRoomRepository)
                .findByReservationDetailIdAndStatusForUpdate(71L, "ASSIGNED");
        order.verify(roomRepository).findAllByHotelIdAndIdInForUpdate(3L, List.of(11L, 12L));
        assertThat(firstRoom.getStatus()).isEqualTo("AVAILABLE");
        assertThat(secondRoom.getStatus()).isEqualTo("AVAILABLE");
        assertThat(firstAssignment.getStatus()).isEqualTo("RELEASED");
        assertThat(secondAssignment.getStatus()).isEqualTo("RELEASED");
        assertThat(detail.getRoom()).isNull();
        assertThat(reservation.getRoom()).isNull();
        ArgumentCaptor<OperationalAuditService.AuditCommand> audit =
                ArgumentCaptor.forClass(OperationalAuditService.AuditCommand.class);
        verify(operationalAuditService).append(audit.capture());
        assertThat(audit.getValue().eventType()).isEqualTo("ROOMS_RELEASED");
        assertThat(audit.getValue().reason()).isEqualTo("Giải phóng để xử lý bảo trì");
    }

    @Test
    void assignmentRejectsNullRoomIdsInsteadOfSilentlyDroppingThem() {
        assertThatThrownBy(() -> reservationService.updateRoomAssignment(
                42L, new RoomAssignmentMutationRequest(java.util.Arrays.asList(11L, null), "Phân phòng mới")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("số dương");

        verify(reservationRoomRepository, never())
                .findByReservationDetailIdAndStatusForUpdate(71L, "ASSIGNED");
    }

    @Test
    void checkInLocksAssignmentsAndRoomsBeforeChangingPhysicalRoomState() {
        detail.setQuantity(1);
        ReservationRoom assignment = assignment(detail, firstRoom);
        when(reservationRoomRepository.findAssignedByReservationIdForUpdate(42L))
                .thenReturn(List.of(assignment));
        when(roomRepository.findAllByHotelIdAndIdInForUpdate(3L, List.of(11L)))
                .thenReturn(List.of(firstRoom));
        when(reservationRepository.save(reservation)).thenReturn(reservation);
        OffsetDateTime now = OffsetDateTime.parse("2028-02-10T14:00:00+07:00");
        when(checkInPolicy.window(reservation)).thenReturn(new CheckInPolicy.Window(
                now, now, now.minusMinutes(5), now.plusDays(2),
                "Asia/Ho_Chi_Minh", 5, CheckInPolicy.VERSION));

        reservationService.checkIn(42L);

        InOrder order = inOrder(reservationRepository, reservationRoomRepository, roomRepository);
        order.verify(reservationRepository).findByIdForUpdate(42L);
        order.verify(reservationRoomRepository).findAssignedByReservationIdForUpdate(42L);
        order.verify(roomRepository).findAllByHotelIdAndIdInForUpdate(3L, List.of(11L));
        assertThat(firstRoom.getStatus()).isEqualTo("OCCUPIED");
        assertThat(reservation.getStatus()).isEqualTo("CHECKED_IN");
    }

    @Test
    void availableRoomLookupExcludesReservedOrOtherwiseNonAssignableStates() {
        detail.setQuantity(2);
        secondRoom.setStatus("RESERVED");
        when(reservationRepository.findById(42L)).thenReturn(Optional.of(reservation));
        when(reservationRoomRepository.findByReservationDetailIdAndStatus(71L, "ASSIGNED"))
                .thenReturn(List.of());
        when(roomRepository.findAvailableRoomsByRoomTypeAndDate(
                3L,
                7L,
                List.of("MAINTENANCE", "OUT_OF_SERVICE", "DIRTY", "CLEANING", "OCCUPIED"),
                RoomAvailabilityService.RELEASED_RESERVATION_STATUSES,
                reservation.getCheckInDate(),
                reservation.getCheckOutDate()))
                .thenReturn(List.of(firstRoom, secondRoom));

        assertThat(reservationService.getAvailableRoomContext(42L).candidates())
                .extracting(com.hotel.dtos.RoomDTO::getId)
                .containsExactly(11L);
        assertThat(reservationService.getAvailableRoomContext(42L).requiredQuantity()).isEqualTo(2);
    }

    @Test
    void readinessReportsMissingAssignmentWithoutMutatingReservation() {
        when(reservationRoomRepository.findByReservationDetailReservationId(42L)).thenReturn(List.of());
        when(checkInPolicy.window(reservation)).thenReturn(openWindow());

        var readiness = reservationService.getCheckInReadiness(42L);

        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.blockers()).extracting("code")
                .containsExactly("MISSING_ROOM_ASSIGNMENT");
        verify(reservationRepository, never()).save(any());
        verify(roomRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void readinessHonorsExactOpeningBoundaryAndRejectsEarlierArrival() {
        detail.setQuantity(1);
        ReservationRoom assignment = assignment(detail, firstRoom);
        when(reservationRoomRepository.findByReservationDetailReservationId(42L))
                .thenReturn(List.of(assignment));
        OffsetDateTime earliest = OffsetDateTime.parse("2028-02-10T13:55:00+07:00");
        when(checkInPolicy.window(reservation))
                .thenReturn(windowAt(earliest.minusNanos(1), earliest));
        assertThat(reservationService.getCheckInReadiness(42L).blockers())
                .extracting("code").contains("ARRIVAL_WINDOW_NOT_OPEN");

        when(checkInPolicy.window(reservation)).thenReturn(windowAt(earliest, earliest));
        assertThat(reservationService.getCheckInReadiness(42L).ready()).isTrue();
    }

    @Test
    void readinessRejectsClosedWindowAndUnreadyPhysicalRoom() {
        detail.setQuantity(1);
        firstRoom.setHousekeepingStatus("DIRTY");
        ReservationRoom assignment = assignment(detail, firstRoom);
        when(reservationRoomRepository.findByReservationDetailReservationId(42L))
                .thenReturn(List.of(assignment));
        OffsetDateTime close = OffsetDateTime.parse("2028-02-12T12:00:00+07:00");
        when(checkInPolicy.window(reservation)).thenReturn(new CheckInPolicy.Window(
                close, close.minusDays(2), close.minusDays(2), close,
                "Asia/Ho_Chi_Minh", 5, CheckInPolicy.VERSION));

        assertThat(reservationService.getCheckInReadiness(42L).blockers())
                .extracting("code")
                .containsExactly("STAY_WINDOW_CLOSED", "ROOM_NOT_READY");
    }

    @Test
    void failedCheckInExposesStableBlockerCodeAndLeavesStateUntouched() {
        when(reservationRoomRepository.findAssignedByReservationIdForUpdate(42L))
                .thenReturn(List.of());
        when(checkInPolicy.window(reservation)).thenReturn(openWindow());

        assertThatThrownBy(() -> reservationService.checkIn(42L))
                .isInstanceOfSatisfying(FinancialException.class, exception ->
                        assertThat(exception.fieldErrors())
                                .containsEntry("checkInReadiness", "MISSING_ROOM_ASSIGNMENT"));

        assertThat(reservation.getStatus()).isEqualTo("CONFIRMED");
        verify(reservationRepository, never()).save(any());
        verify(roomRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void replayRequiresExactAssignmentsAndOccupiedRooms() {
        reservation.setStatus("CHECKED_IN");
        detail.setQuantity(1);
        ReservationRoom assignment = assignment(detail, firstRoom);
        when(reservationRoomRepository.findByReservationDetailReservationId(42L))
                .thenReturn(List.of(assignment));

        assertThat(reservationService.findCheckInReplay(42L)).isEmpty();
        assertThatThrownBy(() -> reservationService.checkIn(42L))
                .isInstanceOfSatisfying(FinancialException.class, exception ->
                        assertThat(exception.fieldErrors())
                                .containsEntry("checkInReadiness", "CHECKED_IN_STATE_INCOMPLETE"));

        firstRoom.setStatus("OCCUPIED");
        assertThat(reservationService.findCheckInReplay(42L)).isPresent();
    }

    @Test
    void genericStatusMutationCannotBypassDedicatedCheckInReadiness() {
        assertThatThrownBy(() -> reservationService.updateReservationStatus(42L, "CHECKED_IN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dedicated check-in endpoint");

        verify(reservationRoomRepository, never()).findAssignedByReservationIdForUpdate(any());
        verify(roomRepository, never()).findAllByIdForUpdate(any());
    }

    @Test
    void availableRoomLookupAcceptsOnlyAvailableCleanOrInspectedRooms() {
        Room inspected = room(reservation.getHotel(), detail.getRoomType(), 13L, "103");
        inspected.setHousekeepingStatus("INSPECTED");
        Room dirty = room(reservation.getHotel(), detail.getRoomType(), 14L, "104");
        dirty.setStatus("DIRTY");
        dirty.setHousekeepingStatus("DIRTY");
        Room cleaning = room(reservation.getHotel(), detail.getRoomType(), 15L, "105");
        cleaning.setStatus("CLEANING");
        cleaning.setHousekeepingStatus("CLEANING");
        Room maintenance = room(reservation.getHotel(), detail.getRoomType(), 16L, "106");
        maintenance.setStatus("MAINTENANCE");
        maintenance.setMaintenanceStatus("MAINTENANCE");
        Room occupied = room(reservation.getHotel(), detail.getRoomType(), 17L, "107");
        occupied.setStatus("OCCUPIED");
        Room outOfService = room(reservation.getHotel(), detail.getRoomType(), 18L, "108");
        outOfService.setStatus("OUT_OF_SERVICE");
        outOfService.setMaintenanceStatus("OUT_OF_SERVICE");
        Room inconsistentMaintenance = room(reservation.getHotel(), detail.getRoomType(), 19L, "109");
        inconsistentMaintenance.setMaintenanceStatus("MAINTENANCE");
        when(reservationRepository.findById(42L)).thenReturn(Optional.of(reservation));
        when(reservationRoomRepository.findByReservationDetailIdAndStatus(71L, "ASSIGNED"))
                .thenReturn(List.of());
        when(roomRepository.findAvailableRoomsByRoomTypeAndDate(
                3L, 7L,
                List.of("MAINTENANCE", "OUT_OF_SERVICE", "DIRTY", "CLEANING", "OCCUPIED"),
                RoomAvailabilityService.RELEASED_RESERVATION_STATUSES,
                reservation.getCheckInDate(), reservation.getCheckOutDate()))
                .thenReturn(List.of(firstRoom, inspected, dirty, cleaning, maintenance, occupied,
                        outOfService, inconsistentMaintenance));

        assertThat(reservationService.getAvailableRoomContext(42L).candidates())
                .extracting(com.hotel.dtos.RoomDTO::getId)
                .containsExactly(11L, 13L);
    }

    @Test
    void availableRoomLookupRejectsReleasedReservationStates() {
        reservation.setStatus("CANCELLED");
        when(reservationRepository.findById(42L)).thenReturn(Optional.of(reservation));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> reservationService.getAvailableRoomContext(42L))
                .isInstanceOf(IllegalStateException.class);

        verify(roomRepository, never()).findAvailableRoomsByRoomTypeAndDate(
                any(), any(), any(), any(), any(), any());
    }

    private AssignRoomsRequest request(Long... roomIds) {
        AssignRoomsRequest request = new AssignRoomsRequest();
        request.setRoomIds(List.of(roomIds));
        return request;
    }

    private ReservationRoom assignment(ReservationDetail reservationDetail, Room room) {
        return assignment(81L, reservationDetail, room);
    }

    private ReservationRoom assignment(Long id, ReservationDetail reservationDetail, Room room) {
        ReservationRoom assignment = new ReservationRoom();
        assignment.setId(id);
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

    private CheckInPolicy.Window openWindow() {
        OffsetDateTime now = OffsetDateTime.parse("2028-02-10T14:00:00+07:00");
        return windowAt(now, now.minusMinutes(5));
    }

    private CheckInPolicy.Window windowAt(OffsetDateTime evaluatedAt, OffsetDateTime earliest) {
        return new CheckInPolicy.Window(
                evaluatedAt,
                earliest.plusMinutes(5),
                earliest,
                earliest.plusDays(2),
                "Asia/Ho_Chi_Minh",
                5,
                CheckInPolicy.VERSION);
    }
}
