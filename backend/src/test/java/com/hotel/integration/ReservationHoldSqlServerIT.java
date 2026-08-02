package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.domain.lifecycle.ReservationHoldStatus;
import com.hotel.domain.lifecycle.ReservationStatus;
import com.hotel.domain.payment.PaymentCompletionResult;
import com.hotel.dtos.ReservationDTO;
import com.hotel.dtos.ReservationRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.ReservationDetail;
import com.hotel.entities.ReservationHold;
import com.hotel.entities.Room;
import com.hotel.entities.RoomType;
import com.hotel.entities.User;
import com.hotel.propertycommerce.config.PropertyPaymentConfiguration;
import com.hotel.propertycommerce.config.PropertyPaymentConfigurationRepository;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.PaymentRepository;
import com.hotel.repositories.ReservationDetailRepository;
import com.hotel.repositories.ReservationHoldRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.services.NotificationService;
import com.hotel.services.PaymentService;
import com.hotel.services.ReservationHoldService;
import com.hotel.services.ReservationService;
import com.hotel.services.RoomAvailabilityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
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

@SpringBootTest(classes = BackendApplication.class, properties = {
        "app.reservation-hold.expiry-scan-ms=3600000",
        "payment.property.encryption-key=test-property-payment-encryption-key"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "BOOKING_HOLD_SQLSERVER_ENABLED", matches = "true")
class ReservationHoldSqlServerIT {

    @DynamicPropertySource
    static void sqlServerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnvironment("BOOKING_HOLD_SQLSERVER_URL"));
        registry.add("spring.datasource.username", () -> requiredEnvironment("BOOKING_HOLD_SQLSERVER_USERNAME"));
        registry.add("spring.datasource.password", () -> requiredEnvironment("BOOKING_HOLD_SQLSERVER_PASSWORD"));
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
    @Autowired private ReservationHoldRepository holdRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PropertyPaymentConfigurationRepository paymentConfigurationRepository;
    @Autowired private ReservationService reservationService;
    @Autowired private ReservationHoldService holdService;
    @Autowired private PaymentService paymentService;
    @Autowired private RoomAvailabilityService availabilityService;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockBean private NotificationService notificationService;

    @Test
    void sqlServerPessimisticLockAllowsOnlyOneLastRoomBooking() throws Exception {
        LocalDate checkIn = LocalDate.of(2028, 4, 10);
        LocalDate checkOut = checkIn.plusDays(2);
        FixtureIds ids = transactionTemplate.execute(status -> createBookingFixture("LAST-ROOM"));
        assertThat(ids).isNotNull();

        List<Attempt<ReservationDTO>> attempts = runConcurrent(
                () -> reservationService.createReservation(
                        ids.username(), request(ids.roomTypeId(), checkIn, checkOut)),
                () -> reservationService.createReservation(
                        ids.username(), request(ids.roomTypeId(), checkIn, checkOut)));

        assertThat(attempts).filteredOn(Attempt::succeeded).hasSize(1);
        assertThat(attempts).filteredOn(attempt -> !attempt.succeeded()).hasSize(1);
        assertThat(reservationRepository.countByUserIdAndStatusIn(
                ids.userId(), List.of(ReservationStatus.PENDING_PAYMENT.name()))).isEqualTo(1);
        assertThat(holdRepository.count()).isEqualTo(1);
        assertThat(availabilityService.countAvailableRooms(ids.roomTypeId(), checkIn, checkOut)).isZero();
    }

    @Test
    void sqlServerSerializesPaymentAgainstHoldExpiry() throws Exception {
        LocalDate checkIn = LocalDate.of(2028, 4, 20);
        LocalDate checkOut = checkIn.plusDays(2);
        HoldFixture fixture = transactionTemplate.execute(
                status -> createExpiredHoldFixture(checkIn, checkOut, "PAYMENT-EXPIRY"));
        assertThat(fixture).isNotNull();

        String transactionId = "SQLSERVER-RACE-" + fixture.reservationId();
        LocalDateTime scanTime = LocalDateTime.now();
        List<Attempt<Object>> attempts = runConcurrent(
                () -> holdService.expireDueHolds(scanTime),
                () -> paymentService.handleSuccessfulPayment(
                        fixture.reservationId(), "VNPAY", transactionId));

        assertThat(attempts).allMatch(Attempt::succeeded);
        Reservation reservation = reservationRepository.findById(fixture.reservationId()).orElseThrow();
        ReservationHold hold = holdRepository.findById(fixture.holdId()).orElseThrow();
        assertThat(paymentRepository.findByTransactionId(transactionId)).isPresent();

        if (reservation.getStatus().equals(ReservationStatus.CONFIRMED.name())) {
            assertThat(hold.getStatus()).isEqualTo(ReservationHoldStatus.CONSUMED.name());
            assertThat(attempts.stream().map(Attempt::value))
                    .contains(PaymentCompletionResult.APPLIED, 0);
            assertThat(availabilityService.countAvailableRooms(fixture.roomTypeId(), checkIn, checkOut)).isZero();
        } else {
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED.name());
            assertThat(hold.getStatus()).isEqualTo(ReservationHoldStatus.EXPIRED.name());
            assertThat(attempts.stream().map(Attempt::value))
                    .contains(PaymentCompletionResult.RECONCILIATION_REQUIRED, 1);
            assertThat(availabilityService.countAvailableRooms(fixture.roomTypeId(), checkIn, checkOut)).isEqualTo(1);
        }
    }

    private FixtureIds createBookingFixture(String suffix) {
        Hotel hotel = hotelRepository.saveAndFlush(hotel("SQL-HOLD-" + suffix));
        PropertyPaymentConfiguration configuration = new PropertyPaymentConfiguration(hotel);
        ReflectionTestUtils.setField(configuration, "enabled", true);
        paymentConfigurationRepository.saveAndFlush(configuration);
        RoomType roomType = roomTypeRepository.saveAndFlush(roomType(hotel, "SQL-TYPE-" + suffix));
        roomRepository.saveAndFlush(room(hotel, roomType, "SQL-ROOM-" + suffix));
        User user = userRepository.saveAndFlush(user("sql-hold-" + suffix));
        return new FixtureIds(user.getId(), user.getUsername(), roomType.getId());
    }

    private HoldFixture createExpiredHoldFixture(LocalDate checkIn, LocalDate checkOut, String suffix) {
        Hotel hotel = hotelRepository.saveAndFlush(hotel("SQL-HOLD-" + suffix));
        RoomType roomType = roomTypeRepository.saveAndFlush(roomType(hotel, "SQL-TYPE-" + suffix));
        roomRepository.saveAndFlush(room(hotel, roomType, "SQL-ROOM-" + suffix));
        User user = userRepository.saveAndFlush(user("sql-hold-" + suffix));

        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setHotel(hotel);
        reservation.setCheckInDate(checkIn);
        reservation.setCheckOutDate(checkOut);
        reservation.setGuests(2);
        reservation.setTotalAmount(new BigDecimal("2000000"));
        reservation.setStatus(ReservationStatus.PENDING_PAYMENT.name());
        reservation = reservationRepository.saveAndFlush(reservation);

        ReservationDetail detail = new ReservationDetail();
        detail.setReservation(reservation);
        detail.setRoomType(roomType);
        detail.setQuantity(1);
        detail.setAdults(2);
        detail.setChildren(0);
        detail.setPrice(roomType.getBasePrice());
        detail.setUnitPrice(roomType.getBasePrice());
        detail.setSubtotal(new BigDecimal("2000000"));
        reservationDetailRepository.saveAndFlush(detail);

        ReservationHold hold = holdService.createHold(
                reservation.getId(),
                roomType.getId(),
                1,
                suffix + "-" + reservation.getId(),
                LocalDateTime.now().minusMinutes(16));
        return new HoldFixture(reservation.getId(), roomType.getId(), hold.getId());
    }

    private <T> List<Attempt<T>> runConcurrent(Callable<? extends T> first, Callable<? extends T> second)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Attempt<T>> firstFuture = executor.submit(attempt(first, ready, start));
            Future<Attempt<T>> secondFuture = executor.submit(attempt(second, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(
                    firstFuture.get(30, TimeUnit.SECONDS),
                    secondFuture.get(30, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private <T> Callable<Attempt<T>> attempt(
            Callable<? extends T> action,
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

    private ReservationRequest request(Long roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        ReservationRequest request = new ReservationRequest();
        request.setRoomTypeId(roomTypeId);
        request.setCheckInDate(checkIn);
        request.setCheckOutDate(checkOut);
        request.setQuantity(1);
        request.setAdults(2);
        request.setChildren(0);
        request.setPaymentMethod("VNPAY");
        return request;
    }

    private Hotel hotel(String suffix) {
        String unique = suffix + "-" + System.nanoTime();
        Hotel hotel = new Hotel();
        hotel.setCode(unique);
        hotel.setSlug(unique.toLowerCase());
        hotel.setName("SQL Server hold hotel");
        hotel.setNameVi("Khach san SQL Server hold");
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
        roomType.setCode(suffix + "-" + System.nanoTime());
        roomType.setNameVi("Phong SQL Server hold");
        roomType.setNameEn("SQL Server hold room");
        roomType.setMaxGuest(2);
        roomType.setMaxAdults(2);
        roomType.setMaxChildren(0);
        roomType.setMaxGuests(2);
        roomType.setBasePrice(new BigDecimal("1000000"));
        roomType.setStatus("ACTIVE");
        return roomType;
    }

    private Room room(Hotel hotel, RoomType roomType, String number) {
        Room room = new Room();
        room.setHotel(hotel);
        room.setRoomType(roomType);
        room.setRoomNumber(number + "-" + System.nanoTime());
        room.setFloor(1);
        room.setStatus("AVAILABLE");
        room.setMaintenanceStatus("NONE");
        room.setHousekeepingStatus("CLEAN");
        return room;
    }

    private User user(String suffix) {
        String unique = suffix.toLowerCase() + "-" + System.nanoTime();
        User user = new User();
        user.setUsername(unique);
        user.setEmail(unique + "@example.test");
        user.setPasswordHash("test");
        user.setFullName("SQL Server Hold User");
        user.setStatus("ACTIVE");
        return user;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for SQL Server hold validation.");
        }
        return value;
    }

    private record FixtureIds(Long userId, String username, Long roomTypeId) {
    }

    private record HoldFixture(Long reservationId, Long roomTypeId, Long holdId) {
    }

    private record Attempt<T>(T value, Throwable error) {
        boolean succeeded() {
            return error == null;
        }
    }
}
