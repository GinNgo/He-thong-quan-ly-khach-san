package com.hotel.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.BackendApplication;
import com.hotel.dtos.CheckoutResultDTO;
import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.ReservationDetail;
import com.hotel.entities.ReservationRoom;
import com.hotel.entities.Room;
import com.hotel.entities.RoomType;
import com.hotel.entities.User;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.propertycommerce.checkout.CheckoutOperationsService;
import com.hotel.propertycommerce.checkout.FolioCalculationService;
import com.hotel.propertycommerce.invoice.InvoiceFinalizationService;
import com.hotel.propertycommerce.invoice.PropertyInvoice;
import com.hotel.propertycommerce.invoice.PropertyInvoiceRepository;
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
import com.hotel.services.PublicInventoryEligibilityPolicy;
import com.hotel.services.RefundService;
import com.hotel.services.ReservationHoldService;
import com.hotel.services.ReservationService;
import com.hotel.services.RoomAvailabilityService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import static org.mockito.Mockito.when;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ReservationService.class, CheckoutOperationsService.class, InvoiceFinalizationService.class})
@ContextConfiguration(classes = BackendApplication.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "STAY_LIFECYCLE_SQLSERVER_ENABLED", matches = "true")
class CheckoutAggregateSqlServerIT {

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
    @Autowired private PropertyInvoiceRepository propertyInvoiceRepository;
    @Autowired private ReservationService reservationService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @MockBean private RoomAvailabilityService roomAvailabilityService;
    @MockBean private NotificationService notificationService;
    @MockBean private EmailService emailService;
    @MockBean private InvoiceRepository invoiceRepository;
    @MockBean private RefundService refundService;
    @MockBean private PropertyAccessService propertyAccessService;
    @MockBean private ReservationHoldService reservationHoldService;
    @MockBean private PublicInventoryEligibilityPolicy publicInventoryEligibilityPolicy;
    @MockBean private FolioCalculationService folioCalculationService;
    @MockBean private FinancialAuditService financialAuditService;
    @MockBean private ObjectMapper objectMapper;

    @BeforeEach
    void configureAggregate() throws Exception {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(financialAuditService.append(any())).thenReturn(null);
        ensureProductionUniquenessIndexes();
    }

    @Test
    void persistenceFailureAfterInvoiceAndRoomWritesRollsBackTheWholeCheckoutAggregate() {
        Fixture fixture = transactionTemplate.execute(status -> createFixture("ROLLBACK"));
        assertThat(fixture).isNotNull();
        configureCheckout(fixture);

        String constraintName = "CK_T211_CHECKOUT_" + fixture.reservationId();
        jdbcTemplate.execute("ALTER TABLE reservations ADD CONSTRAINT [" + constraintName + "] "
                + "CHECK (NOT (id = " + fixture.reservationId() + " AND status = 'CHECKED_OUT'))");
        try {
            assertThatThrownBy(() -> reservationService.checkout(fixture.reservationId(), null))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbcTemplate.execute("ALTER TABLE reservations DROP CONSTRAINT [" + constraintName + "]");
        }

        transactionTemplate.executeWithoutResult(status -> {
            Reservation reservation = reservationRepository.findById(fixture.reservationId()).orElseThrow();
            ReservationRoom assignment = reservationRoomRepository.findById(fixture.assignmentId()).orElseThrow();
            Room room = roomRepository.findById(fixture.roomId()).orElseThrow();
            assertThat(reservation.getStatus()).isEqualTo("CHECKED_IN");
            assertThat(assignment.getStatus()).isEqualTo("ASSIGNED");
            assertThat(assignment.getReleasedAt()).isNull();
            assertThat(room.getStatus()).isEqualTo("OCCUPIED");
            assertThat(room.getHousekeepingStatus()).isEqualTo("CLEAN");
            assertThat(propertyInvoiceRepository.findByReservationIdAndStatus(
                    fixture.reservationId(), PropertyInvoice.Status.FINALIZED)).isEmpty();
            assertThat(invoiceLineCount(fixture.reservationId())).isZero();
            assertThat(housekeepingTaskRepository.findByHotelIdAndCheckoutEffectKey(
                    fixture.hotelId(), effectKey(fixture))).isEmpty();
        });
    }

    @Test
    void concurrentCheckoutAndRestartLikeReplayKeepOneInvoiceAndOneHousekeepingEffect() throws Exception {
        Fixture fixture = transactionTemplate.execute(status -> createFixture("REPLAY"));
        assertThat(fixture).isNotNull();
        configureCheckout(fixture);

        List<Attempt<CheckoutResultDTO>> attempts = runConcurrent(
                () -> reservationService.checkout(fixture.reservationId(), null),
                () -> reservationService.checkout(fixture.reservationId(), null));
        assertThat(attempts).allMatch(Attempt::succeeded);

        entityManager.clear();
        CheckoutResultDTO replay = reservationService.checkout(fixture.reservationId(), null);
        assertThat(replay.getReservationStatus()).isEqualTo("CHECKED_OUT");

        transactionTemplate.executeWithoutResult(status -> {
            Long invoiceCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT_BIG(*) FROM property_invoices WHERE reservation_id = ? AND status = 'FINALIZED'",
                    Long.class,
                    fixture.reservationId());
            Long taskCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT_BIG(*) FROM housekeeping_tasks WHERE hotel_id = ? AND checkout_effect_key = ?",
                    Long.class,
                    fixture.hotelId(),
                    effectKey(fixture));
            ReservationRoom assignment = reservationRoomRepository.findById(fixture.assignmentId()).orElseThrow();
            Room room = roomRepository.findById(fixture.roomId()).orElseThrow();
            assertThat(invoiceCount).isEqualTo(1L);
            assertThat(taskCount).isEqualTo(1L);
            assertThat(invoiceLineCount(fixture.reservationId())).isEqualTo(1L);
            assertThat(assignment.getStatus()).isEqualTo("RELEASED");
            assertThat(room.getStatus()).isEqualTo("DIRTY");
        });
    }

    private void configureCheckout(Fixture fixture) {
        when(propertyAccessService.currentUser()).thenAnswer(
                invocation -> userRepository.findById(fixture.userId()).orElseThrow());
        when(folioCalculationService.calculate(fixture.reservationId()))
                .thenReturn(zeroBalanceFolio(fixture));
    }

    private FolioCalculationService.Folio zeroBalanceFolio(Fixture fixture) {
        FolioCalculationService.FolioLine line = new FolioCalculationService.FolioLine(
                "SERVER_COMPONENT",
                fixture.reservationId(),
                "ROOM",
                "ZERO-VALUE-STAY",
                "Checkout aggregate transaction fixture",
                null,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null);
        return new FolioCalculationService.Folio(
                fixture.reservationId(),
                fixture.hotelId(),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.zero(),
                BigDecimal.ZERO,
                List.of(line),
                1L,
                LocalDateTime.of(2026, 8, 3, 0, 0));
    }

    private void ensureProductionUniquenessIndexes() {
        jdbcTemplate.execute("""
                IF NOT EXISTS (
                    SELECT 1 FROM sys.indexes
                    WHERE object_id = OBJECT_ID('property_invoices')
                      AND name = 'UX_property_invoice_finalized_reservation'
                )
                    CREATE UNIQUE INDEX UX_property_invoice_finalized_reservation
                        ON property_invoices(reservation_id) WHERE status = 'FINALIZED'
                """);
        jdbcTemplate.execute("""
                IF NOT EXISTS (
                    SELECT 1 FROM sys.indexes
                    WHERE object_id = OBJECT_ID('housekeeping_tasks')
                      AND name = 'UX_housekeeping_checkout_effect'
                )
                    CREATE UNIQUE INDEX UX_housekeeping_checkout_effect
                        ON housekeeping_tasks(hotel_id, checkout_effect_key)
                        WHERE checkout_effect_key IS NOT NULL
                """);
    }

    private Long invoiceLineCount(Long reservationId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT_BIG(*)
                FROM property_invoice_lines invoice_line
                JOIN property_invoices invoice ON invoice.id = invoice_line.invoice_id
                WHERE invoice.reservation_id = ?
                """, Long.class, reservationId);
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

    private Fixture createFixture(String suffix) {
        String unique = suffix + "-" + System.nanoTime();
        Hotel hotel = hotelRepository.saveAndFlush(hotel(unique));
        RoomType roomType = roomTypeRepository.saveAndFlush(roomType(hotel, unique));
        Room room = roomRepository.saveAndFlush(room(hotel, roomType, unique));
        User guest = userRepository.saveAndFlush(user(unique));
        Reservation reservation = reservationRepository.saveAndFlush(reservation(hotel, guest));
        ReservationDetail detail = reservationDetailRepository.saveAndFlush(detail(reservation, roomType));
        ReservationRoom assignment = new ReservationRoom();
        assignment.setReservationDetail(detail);
        assignment.setRoom(room);
        assignment.setStatus("ASSIGNED");
        assignment = reservationRoomRepository.saveAndFlush(assignment);
        return new Fixture(hotel.getId(), reservation.getId(), assignment.getId(), room.getId(), guest.getId());
    }

    private Hotel hotel(String suffix) {
        Hotel hotel = new Hotel();
        hotel.setCode("T211-" + suffix);
        hotel.setSlug(("t211-" + suffix).toLowerCase());
        hotel.setName("Atomic checkout hotel");
        hotel.setNameVi("Khach san checkout nguyen tu");
        hotel.setAddressLine("211 Transaction Street");
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
        roomType.setNameVi("Phong checkout nguyen tu");
        roomType.setNameEn("Atomic checkout room");
        roomType.setBasePrice(BigDecimal.ZERO);
        roomType.setStatus("ACTIVE");
        return roomType;
    }

    private Room room(Hotel hotel, RoomType roomType, String suffix) {
        Room room = new Room();
        room.setHotel(hotel);
        room.setRoomType(roomType);
        room.setRoomNumber("ROOM-" + suffix);
        room.setFloor(1);
        room.setStatus("OCCUPIED");
        room.setMaintenanceStatus("NONE");
        room.setHousekeepingStatus("CLEAN");
        return room;
    }

    private User user(String suffix) {
        User user = new User();
        user.setUsername(("t211-" + suffix).toLowerCase());
        user.setEmail(("t211-" + suffix).toLowerCase() + "@example.test");
        user.setPasswordHash("test");
        user.setFullName("Atomic Checkout Guest");
        user.setStatus("ACTIVE");
        return user;
    }

    private Reservation reservation(Hotel hotel, User guest) {
        Reservation reservation = new Reservation();
        reservation.setHotel(hotel);
        reservation.setUser(guest);
        reservation.setStatus("CHECKED_IN");
        reservation.setCheckInDate(LocalDate.of(2028, 3, 10));
        reservation.setCheckOutDate(LocalDate.of(2028, 3, 12));
        reservation.setGuests(2);
        reservation.setTotalAmount(BigDecimal.ZERO);
        return reservation;
    }

    private ReservationDetail detail(Reservation reservation, RoomType roomType) {
        ReservationDetail detail = new ReservationDetail();
        detail.setReservation(reservation);
        detail.setRoomType(roomType);
        detail.setQuantity(1);
        detail.setAdults(2);
        detail.setChildren(0);
        detail.setPrice(BigDecimal.ZERO);
        detail.setUnitPrice(BigDecimal.ZERO);
        detail.setSubtotal(BigDecimal.ZERO);
        return detail;
    }

    private String effectKey(Fixture fixture) {
        return "CHECKOUT:" + fixture.reservationId() + ":ROOM:" + fixture.roomId();
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for SQL Server checkout validation.");
        }
        return value;
    }

    private record Fixture(Long hotelId, Long reservationId, Long assignmentId, Long roomId, Long userId) {
    }

    private record Attempt<T>(T value, Throwable error) {
        boolean succeeded() {
            return value != null && error == null;
        }
    }
}
