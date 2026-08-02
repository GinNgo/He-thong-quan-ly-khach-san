package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.domain.lifecycle.ReservationStatus;
import com.hotel.dtos.ReservationDTO;
import com.hotel.dtos.ReservationRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.Room;
import com.hotel.entities.RoomType;
import com.hotel.entities.User;
import com.hotel.propertycommerce.config.PropertyPaymentConfiguration;
import com.hotel.propertycommerce.config.PropertyPaymentConfigurationRepository;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.ReservationHoldRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.services.NotificationService;
import com.hotel.services.ReservationService;
import com.hotel.services.RoomAvailabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
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

@SpringBootTest(classes = BackendApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:reservation-concurrency-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000",
        "app.reservation-hold.expiry-scan-ms=3600000",
        "payment.property.encryption-key=test-property-payment-encryption-key"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReservationConcurrencyIntegrationTest {

    @Autowired
    private HotelRepository hotelRepository;
    @Autowired
    private RoomTypeRepository roomTypeRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private ReservationHoldRepository holdRepository;
    @Autowired
    private PropertyPaymentConfigurationRepository paymentConfigurationRepository;
    @Autowired
    private ReservationService reservationService;
    @Autowired
    private RoomAvailabilityService availabilityService;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    private NotificationService notificationService;

    @Test
    void twoSimultaneousBookingsForOneRoomProduceOneSuccessAndOneSoldOutFailure() throws Exception {
        LocalDate checkIn = LocalDate.of(2028, 2, 10);
        LocalDate checkOut = checkIn.plusDays(2);
        FixtureIds ids = transactionTemplate.execute(status -> createFixture("BOOKING"));
        assertThat(ids).isNotNull();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Attempt> booking = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    return new Attempt(null, new IllegalStateException("Concurrent start timed out."));
                }
                try {
                    return new Attempt(
                            reservationService.createReservation(
                                    ids.username(),
                                    request(ids.roomTypeId(), checkIn, checkOut)),
                            null);
                } catch (Throwable error) {
                    return new Attempt(null, error);
                }
            };

            Future<Attempt> first = executor.submit(booking);
            Future<Attempt> second = executor.submit(booking);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Attempt> attempts = List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS));

            assertThat(attempts)
                    .as("concurrent booking outcomes: %s", describeAttempts(attempts))
                    .filteredOn(Attempt::succeeded)
                    .hasSize(1);
            assertThat(attempts)
                    .as("concurrent booking outcomes: %s", describeAttempts(attempts))
                    .filteredOn(attempt -> !attempt.succeeded())
                    .hasSize(1);
            assertThat(attempts.stream()
                    .filter(attempt -> !attempt.succeeded())
                    .map(Attempt::error)
                    .findFirst()
                    .orElseThrow()).isInstanceOf(IllegalStateException.class);
        } finally {
            executor.shutdownNow();
        }

        assertThat(reservationRepository.countByUserIdAndStatusIn(
                ids.userId(),
                List.of(ReservationStatus.PENDING_PAYMENT.name()))).isEqualTo(1);
        assertThat(holdRepository.count()).isEqualTo(1);
        assertThat(availabilityService.countAvailableRooms(ids.roomTypeId(), checkIn, checkOut)).isZero();
    }

    @Test
    void concurrentRoomTypeDeactivationWinsBeforeBookingAndPreventsStaleSale() throws Exception {
        LocalDate checkIn = LocalDate.of(2028, 3, 10);
        LocalDate checkOut = checkIn.plusDays(2);
        FixtureIds ids = transactionTemplate.execute(status -> createFixture("DEACTIVATE"));
        assertThat(ids).isNotNull();
        long holdsBefore = holdRepository.count();

        CountDownLatch roomTypeLocked = new CountDownLatch(1);
        CountDownLatch allowDeactivationCommit = new CountDownLatch(1);
        CountDownLatch bookingStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> deactivation = executor.submit(() -> {
                transactionTemplate.executeWithoutResult(status -> {
                    RoomType roomType = roomTypeRepository.findByIdForUpdate(ids.roomTypeId()).orElseThrow();
                    roomType.setStatus("INACTIVE");
                    roomTypeLocked.countDown();
                    try {
                        if (!allowDeactivationCommit.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Deactivation commit timed out.");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Deactivation was interrupted.", exception);
                    }
                    roomTypeRepository.saveAndFlush(roomType);
                });
                return null;
            });

            assertThat(roomTypeLocked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<Attempt> booking = executor.submit(() -> {
                bookingStarted.countDown();
                try {
                    return new Attempt(
                            reservationService.createReservation(
                                    ids.username(),
                                    request(ids.roomTypeId(), checkIn, checkOut)),
                            null);
                } catch (Throwable error) {
                    return new Attempt(null, error);
                }
            });

            assertThat(bookingStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(booking.isDone()).isFalse();
            allowDeactivationCommit.countDown();
            deactivation.get(20, TimeUnit.SECONDS);

            Attempt attempt = booking.get(20, TimeUnit.SECONDS);
            assertThat(attempt.succeeded()).isFalse();
            assertThat(attempt.error()).isInstanceOf(IllegalStateException.class);
            assertThat(attempt.error().getMessage()).contains("no longer available");
        } finally {
            allowDeactivationCommit.countDown();
            executor.shutdownNow();
        }

        assertThat(reservationRepository.countByUserIdAndStatusIn(
                ids.userId(), List.of(ReservationStatus.PENDING_PAYMENT.name()))).isZero();
        assertThat(holdRepository.count()).isEqualTo(holdsBefore);
    }

    private FixtureIds createFixture(String suffix) {
        Hotel hotel = hotelRepository.saveAndFlush(hotel("CONCURRENCY-TEST-HOTEL-" + suffix));
        PropertyPaymentConfiguration paymentConfiguration = new PropertyPaymentConfiguration(hotel);
        ReflectionTestUtils.setField(paymentConfiguration, "enabled", true);
        paymentConfigurationRepository.saveAndFlush(paymentConfiguration);
        RoomType roomType = roomTypeRepository.saveAndFlush(roomType(hotel, "CONCURRENT-DELUXE-" + suffix));
        roomRepository.saveAndFlush(room(hotel, roomType, "C-101-" + suffix));
        User user = userRepository.saveAndFlush(user("concurrent_booking_user_" + suffix.toLowerCase()));
        return new FixtureIds(user.getId(), user.getUsername(), roomType.getId());
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

    private Hotel hotel(String code) {
        Hotel hotel = new Hotel();
        hotel.setCode(code);
        hotel.setSlug(code.toLowerCase());
        hotel.setName("Concurrency test hotel");
        hotel.setNameVi("Khach san kiem thu dong thoi");
        hotel.setAddressLine("2 Test Street");
        hotel.setCity("Ho Chi Minh City");
        hotel.setCountry("Viet Nam");
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        return hotel;
    }

    private RoomType roomType(Hotel hotel, String code) {
        RoomType roomType = new RoomType();
        roomType.setHotel(hotel);
        roomType.setCode(code);
        roomType.setNameVi("Phong dong thoi");
        roomType.setNameEn("Concurrent room");
        roomType.setMaxGuest(2);
        roomType.setMaxAdults(2);
        roomType.setMaxChildren(0);
        roomType.setMaxGuests(2);
        roomType.setBasePrice(new BigDecimal("1200000"));
        roomType.setStatus("ACTIVE");
        return roomType;
    }

    private Room room(Hotel hotel, RoomType roomType, String number) {
        Room room = new Room();
        room.setHotel(hotel);
        room.setRoomType(roomType);
        room.setRoomNumber(number);
        room.setFloor(1);
        room.setStatus("AVAILABLE");
        room.setMaintenanceStatus("NONE");
        room.setHousekeepingStatus("CLEAN");
        return room;
    }

    private User user(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setPasswordHash("test");
        user.setFullName("Concurrent Booking User");
        user.setStatus("ACTIVE");
        return user;
    }

    private String describeAttempts(List<Attempt> attempts) {
        return attempts.stream()
                .map(attempt -> attempt.succeeded()
                        ? "success(reservationId=" + attempt.reservation().getId() + ")"
                        : "failure(" + attempt.error().getClass().getSimpleName() + ": "
                        + attempt.error().getMessage() + ")")
                .toList()
                .toString();
    }

    private record FixtureIds(Long userId, String username, Long roomTypeId) {
    }

    private record Attempt(ReservationDTO reservation, Throwable error) {
        boolean succeeded() {
            return reservation != null && error == null;
        }
    }
}
