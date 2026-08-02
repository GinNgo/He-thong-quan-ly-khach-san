package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.dtos.AssignRoomsRequest;
import com.hotel.dtos.CheckoutResultDTO;
import com.hotel.dtos.ReservationDTO;
import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.ReservationDetail;
import com.hotel.entities.ReservationRoom;
import com.hotel.entities.Room;
import com.hotel.entities.RoomType;
import com.hotel.entities.User;
import com.hotel.propertycommerce.checkout.CheckoutOperationsService;
import com.hotel.propertycommerce.invoice.InvoiceFinalizationService;
import com.hotel.propertycommerce.invoice.PropertyInvoice;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.HousekeepingTaskRepository;
import com.hotel.repositories.InvoiceRepository;
import com.hotel.repositories.ReservationDetailRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.ReservationRoomRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.services.EmailService;
import com.hotel.services.NotificationService;
import com.hotel.services.PropertyAccessService;
import com.hotel.services.PaymentService;
import com.hotel.services.RefundService;
import com.hotel.services.ReservationHoldService;
import com.hotel.services.ReservationService;
import com.hotel.services.RoomAvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ReservationService.class, CheckoutOperationsService.class})
@ContextConfiguration(classes = BackendApplication.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "STAY_LIFECYCLE_SQLSERVER_ENABLED", matches = "true")
class StayLifecycleSqlServerIT {

    @DynamicPropertySource
    static void sqlServerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnvironment("STAY_LIFECYCLE_SQLSERVER_URL"));
        registry.add("spring.datasource.username", () -> requiredEnvironment("STAY_LIFECYCLE_SQLSERVER_USERNAME"));
        registry.add("spring.datasource.password", () -> requiredEnvironment("STAY_LIFECYCLE_SQLSERVER_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.microsoft.sqlserver.jdbc.SQLServerDriver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.SQLServerDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservationDetailRepository reservationDetailRepository;
    @Autowired private ReservationRoomRepository reservationRoomRepository;
    @Autowired private HousekeepingTaskRepository housekeepingTaskRepository;
    @Autowired private ReservationService reservationService;
    @Autowired private CheckoutOperationsService checkoutOperationsService;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockBean private RoomAvailabilityService roomAvailabilityService;
    @MockBean private NotificationService notificationService;
    @MockBean private EmailService emailService;
    @MockBean private InvoiceRepository invoiceRepository;
    @MockBean private PaymentService paymentService;
    @MockBean private RefundService refundService;
    @MockBean private PropertyAccessService propertyAccessService;
    @MockBean private ReservationHoldService reservationHoldService;
    @MockBean private InvoiceFinalizationService invoiceFinalizationService;

    @BeforeEach
    void authorizeOperationalLifecycle() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
    }

    @Test
    void simultaneousAssignmentCheckInCheckoutAndReplayProduceOnePhysicalEffect() throws Exception {
        SharedRoomFixture fixture = transactionTemplate.execute(status -> createSharedRoomFixture());
        assertThat(fixture).isNotNull();

        AssignRoomsRequest request = new AssignRoomsRequest();
        request.setRoomIds(List.of(fixture.roomId()));
        List<Attempt<ReservationDTO>> assignmentAttempts = runConcurrent(
                () -> reservationService.assignRooms(fixture.firstReservationId(), request),
                () -> reservationService.assignRooms(fixture.secondReservationId(), request));

        assertThat(assignmentAttempts).filteredOn(Attempt::succeeded).hasSize(1);
        assertThat(assignmentAttempts).filteredOn(attempt -> !attempt.succeeded()).hasSize(1);
        Long assignedReservationId = assignmentAttempts.stream()
                .filter(Attempt::succeeded)
                .map(attempt -> attempt.value().getId())
                .findFirst()
                .orElseThrow();

        reservationService.assignRooms(assignedReservationId, request);
        assertThat(reservationRoomRepository.findByReservationDetailReservationId(assignedReservationId))
                .hasSize(1);

        List<Attempt<ReservationDTO>> checkInAttempts = runConcurrent(
                () -> reservationService.updateReservationStatus(assignedReservationId, "CHECKED_IN"),
                () -> reservationService.updateReservationStatus(assignedReservationId, "CHECKED_IN"));
        assertThat(checkInAttempts).allMatch(Attempt::succeeded);

        PropertyInvoice invoice = mock(PropertyInvoice.class);
        when(invoice.getId()).thenReturn(900L);
        when(invoice.getInvoiceNumber()).thenReturn("SQLSERVER-CONCURRENCY-INVOICE");
        when(invoice.getStatus()).thenReturn(PropertyInvoice.Status.FINALIZED);
        when(invoice.getTotalAmount()).thenReturn(BigDecimal.valueOf(2_000_000));
        when(invoiceFinalizationService.finalizeInvoice(any())).thenReturn(
                new InvoiceFinalizationService.FinalizedInvoice(invoice, List.of(), List.of()));

        List<Attempt<CheckoutResultDTO>> checkoutAttempts = runConcurrent(
                () -> reservationService.checkout(assignedReservationId, null),
                () -> reservationService.checkout(assignedReservationId, null));
        assertThat(checkoutAttempts).allMatch(Attempt::succeeded);

        transactionTemplate.executeWithoutResult(status -> {
            Reservation persisted = reservationRepository.findById(assignedReservationId).orElseThrow();
            ReservationRoom assignment = reservationRoomRepository
                    .findByReservationDetailReservationId(assignedReservationId).get(0);
            Room room = roomRepository.findById(fixture.roomId()).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo("CHECKED_OUT");
            assertThat(assignment.getStatus()).isEqualTo("RELEASED");
            assertThat(room.getStatus()).isEqualTo("DIRTY");
            assertThat(room.getHousekeepingStatus()).isEqualTo("DIRTY");
            assertThat(housekeepingTaskRepository.findByHotelIdAndCheckoutEffectKey(
                    fixture.hotelId(), effectKey(assignedReservationId, fixture.roomId()))).isPresent();
            assertThat(housekeepingTaskRepository.countByHotelIdAndStatus(fixture.hotelId(), "PENDING"))
                    .isEqualTo(1);
        });
    }

    @Test
    void sqlServerTransactionRollsBackCheckoutWritesAtTheOuterBoundary() {
        AssignedFixture fixture = transactionTemplate.execute(status -> createAssignedFixture("ROLLBACK"));
        assertThat(fixture).isNotNull();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            Reservation locked = reservationRepository.findByIdForUpdate(fixture.reservationId()).orElseThrow();
            checkoutOperationsService.apply(locked);
            throw new ForcedRollbackException();
        })).isInstanceOf(ForcedRollbackException.class);

        transactionTemplate.executeWithoutResult(status -> {
            ReservationRoom assignment = reservationRoomRepository.findById(fixture.assignmentId()).orElseThrow();
            Room room = roomRepository.findById(fixture.roomId()).orElseThrow();
            assertThat(assignment.getStatus()).isEqualTo("ASSIGNED");
            assertThat(assignment.getReleasedAt()).isNull();
            assertThat(room.getStatus()).isEqualTo("OCCUPIED");
            assertThat(room.getHousekeepingStatus()).isEqualTo("CLEAN");
            assertThat(housekeepingTaskRepository.findByHotelIdAndCheckoutEffectKey(
                    fixture.hotelId(), effectKey(fixture.reservationId(), fixture.roomId()))).isEmpty();
        });
    }

    private <T> List<Attempt<T>> runConcurrent(Callable<T> firstAction, Callable<T> secondAction) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Attempt<T>> first = executor.submit(attempt(firstAction, ready, start));
            Future<Attempt<T>> second = executor.submit(attempt(secondAction, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private <T> Callable<Attempt<T>> attempt(
            Callable<T> action,
            CountDownLatch ready,
            CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                return new Attempt<>(null, new IllegalStateException("Concurrent start timed out."));
            }
            try {
                return new Attempt<>(action.call(), null);
            } catch (Throwable error) {
                return new Attempt<>(null, error);
            }
        };
    }

    private SharedRoomFixture createSharedRoomFixture() {
        String unique = "CONCURRENT-" + System.nanoTime();
        Hotel hotel = hotelRepository.saveAndFlush(hotel(unique));
        RoomType roomType = roomTypeRepository.saveAndFlush(roomType(hotel, unique));
        Room room = roomRepository.saveAndFlush(room(hotel, roomType, unique));
        User firstGuest = userRepository.saveAndFlush(user(unique + "-A"));
        User secondGuest = userRepository.saveAndFlush(user(unique + "-B"));
        Reservation first = reservationRepository.saveAndFlush(reservation(hotel, firstGuest, "CONFIRMED"));
        Reservation second = reservationRepository.saveAndFlush(reservation(hotel, secondGuest, "CONFIRMED"));
        reservationDetailRepository.saveAndFlush(detail(first, roomType));
        reservationDetailRepository.saveAndFlush(detail(second, roomType));
        return new SharedRoomFixture(hotel.getId(), room.getId(), first.getId(), second.getId());
    }

    private AssignedFixture createAssignedFixture(String suffix) {
        String unique = suffix + "-" + System.nanoTime();
        Hotel hotel = hotelRepository.saveAndFlush(hotel(unique));
        RoomType roomType = roomTypeRepository.saveAndFlush(roomType(hotel, unique));
        Room room = roomRepository.saveAndFlush(room(hotel, roomType, unique));
        room.setStatus("OCCUPIED");
        room = roomRepository.saveAndFlush(room);
        User guest = userRepository.saveAndFlush(user(unique));
        Reservation reservation = reservationRepository.saveAndFlush(reservation(hotel, guest, "CHECKED_IN"));
        ReservationDetail detail = reservationDetailRepository.saveAndFlush(detail(reservation, roomType));
        ReservationRoom assignment = new ReservationRoom();
        assignment.setReservationDetail(detail);
        assignment.setRoom(room);
        assignment.setStatus("ASSIGNED");
        assignment = reservationRoomRepository.saveAndFlush(assignment);
        return new AssignedFixture(hotel.getId(), reservation.getId(), assignment.getId(), room.getId());
    }

    private Hotel hotel(String suffix) {
        Hotel hotel = new Hotel();
        hotel.setCode("SQL-" + suffix);
        hotel.setSlug(("sql-" + suffix).toLowerCase());
        hotel.setName("SQL Server lifecycle hotel");
        hotel.setNameVi("Khach san kiem thu SQL Server");
        hotel.setAddressLine("1 SQL Server Street");
        hotel.setCity("Ho Chi Minh City");
        hotel.setCountry("Viet Nam");
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        return hotel;
    }

    private RoomType roomType(Hotel hotel, String suffix) {
        RoomType roomType = new RoomType();
        roomType.setHotel(hotel);
        roomType.setCode("TYPE-" + suffix);
        roomType.setNameVi("Phong SQL Server");
        roomType.setNameEn("SQL Server room");
        roomType.setBasePrice(BigDecimal.valueOf(1_000_000));
        roomType.setStatus("ACTIVE");
        return roomType;
    }

    private Room room(Hotel hotel, RoomType roomType, String suffix) {
        Room room = new Room();
        room.setHotel(hotel);
        room.setRoomType(roomType);
        room.setRoomNumber("ROOM-" + suffix);
        room.setFloor(1);
        room.setStatus("AVAILABLE");
        room.setMaintenanceStatus("NONE");
        room.setHousekeepingStatus("CLEAN");
        return room;
    }

    private User user(String suffix) {
        User user = new User();
        user.setUsername(("sql-" + suffix).toLowerCase());
        user.setEmail(("sql-" + suffix).toLowerCase() + "@example.test");
        user.setPasswordHash("test");
        user.setFullName("SQL Server Lifecycle Guest");
        user.setStatus("ACTIVE");
        return user;
    }

    private Reservation reservation(Hotel hotel, User guest, String status) {
        Reservation reservation = new Reservation();
        reservation.setHotel(hotel);
        reservation.setUser(guest);
        reservation.setStatus(status);
        reservation.setCheckInDate(LocalDate.of(2028, 2, 10));
        reservation.setCheckOutDate(LocalDate.of(2028, 2, 12));
        reservation.setGuests(2);
        reservation.setTotalAmount(BigDecimal.valueOf(2_000_000));
        return reservation;
    }

    private ReservationDetail detail(Reservation reservation, RoomType roomType) {
        ReservationDetail detail = new ReservationDetail();
        detail.setReservation(reservation);
        detail.setRoomType(roomType);
        detail.setQuantity(1);
        detail.setAdults(2);
        detail.setChildren(0);
        detail.setPrice(roomType.getBasePrice());
        detail.setUnitPrice(roomType.getBasePrice());
        detail.setSubtotal(roomType.getBasePrice().multiply(BigDecimal.valueOf(2)));
        return detail;
    }

    private String effectKey(Long reservationId, Long roomId) {
        return "CHECKOUT:" + reservationId + ":ROOM:" + roomId;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for SQL Server lifecycle validation.");
        }
        return value;
    }

    private record SharedRoomFixture(
            Long hotelId,
            Long roomId,
            Long firstReservationId,
            Long secondReservationId) {
    }

    private record AssignedFixture(Long hotelId, Long reservationId, Long assignmentId, Long roomId) {
    }

    private record Attempt<T>(T value, Throwable error) {
        boolean succeeded() {
            return value != null && error == null;
        }
    }

    private static final class ForcedRollbackException extends RuntimeException {
    }
}
