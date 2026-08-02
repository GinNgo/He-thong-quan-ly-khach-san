package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.domain.payment.PaymentCompletionResult;
import com.hotel.domain.lifecycle.ReservationHoldStatus;
import com.hotel.domain.lifecycle.ReservationStatus;
import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.ReservationDetail;
import com.hotel.entities.ReservationHold;
import com.hotel.entities.Room;
import com.hotel.entities.RoomType;
import com.hotel.entities.User;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.ReservationDetailRepository;
import com.hotel.repositories.ReservationHoldRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.PaymentRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.services.ReservationHoldExpiryScheduler;
import com.hotel.services.ReservationHoldService;
import com.hotel.services.PaymentService;
import com.hotel.services.RoomAvailabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
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
        "spring.datasource.url=jdbc:h2:mem:reservation-hold-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "app.reservation-hold.expiry-scan-ms=3600000",
        "payment.property.encryption-key=test-property-payment-encryption-key"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReservationHoldIntegrationTest {

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
    private PaymentRepository paymentRepository;
    @Autowired
    private ReservationDetailRepository reservationDetailRepository;
    @Autowired
    private ReservationHoldRepository holdRepository;
    @Autowired
    private ReservationHoldService holdService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private ReservationHoldExpiryScheduler scheduler;
    @Autowired
    private RoomAvailabilityService availabilityService;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void schedulerDiscoversPersistedExpiredHoldAndReleasesInventoryOnce() {
        LocalDate checkIn = LocalDate.of(2028, 1, 10);
        LocalDate checkOut = checkIn.plusDays(2);
        FixtureIds ids = transactionTemplate.execute(
                status -> createExpiredFixture(checkIn, checkOut, "RESTART"));

        assertThat(ids).isNotNull();
        assertThat(availabilityService.countAvailableRooms(ids.roomTypeId(), checkIn, checkOut)).isZero();

        scheduler.releaseExpiredHolds();

        Reservation afterFirstScan = reservationRepository.findById(ids.reservationId()).orElseThrow();
        ReservationHold holdAfterFirstScan = holdRepository.findById(ids.holdId()).orElseThrow();
        LocalDateTime firstReleasedAt = holdAfterFirstScan.getReleasedAt();
        assertThat(afterFirstScan.getStatus()).isEqualTo(ReservationStatus.EXPIRED.name());
        assertThat(holdAfterFirstScan.getStatus()).isEqualTo(ReservationHoldStatus.EXPIRED.name());
        assertThat(firstReleasedAt).isNotNull();
        assertThat(availabilityService.countAvailableRooms(ids.roomTypeId(), checkIn, checkOut)).isEqualTo(1);

        scheduler.releaseExpiredHolds();

        ReservationHold holdAfterReplay = holdRepository.findById(ids.holdId()).orElseThrow();
        assertThat(holdAfterReplay.getStatus()).isEqualTo(ReservationHoldStatus.EXPIRED.name());
        assertThat(holdAfterReplay.getReleasedAt()).isEqualTo(firstReleasedAt);
        assertThat(availabilityService.countAvailableRooms(ids.roomTypeId(), checkIn, checkOut)).isEqualTo(1);
    }

    @Test
    void concurrentExpiryScansCompleteOnePersistedHoldExactlyOnce() throws Exception {
        LocalDate checkIn = LocalDate.of(2028, 1, 20);
        LocalDate checkOut = checkIn.plusDays(2);
        FixtureIds ids = transactionTemplate.execute(
                status -> createExpiredFixture(checkIn, checkOut, "CONCURRENT-EXPIRY"));
        assertThat(ids).isNotNull();

        LocalDateTime scanTime = LocalDateTime.now();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> scan = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent expiry start timed out.");
                }
                return holdService.expireDueHolds(scanTime);
            };

            Future<Integer> first = executor.submit(scan);
            Future<Integer> second = executor.submit(scan);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(0, 1);
        } finally {
            executor.shutdownNow();
        }

        assertThat(reservationRepository.findById(ids.reservationId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.EXPIRED.name());
        assertThat(holdRepository.findById(ids.holdId()).orElseThrow().getStatus())
                .isEqualTo(ReservationHoldStatus.EXPIRED.name());
        assertThat(availabilityService.countAvailableRooms(ids.roomTypeId(), checkIn, checkOut)).isEqualTo(1);
    }

    @Test
    void paymentAndExpiryRaceResolveToOneLockedReservationOutcome() throws Exception {
        LocalDate checkIn = LocalDate.of(2028, 1, 25);
        LocalDate checkOut = checkIn.plusDays(2);
        FixtureIds ids = transactionTemplate.execute(
                status -> createExpiredFixture(checkIn, checkOut, "PAYMENT-RACE"));
        assertThat(ids).isNotNull();

        LocalDateTime scanTime = LocalDateTime.now();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        String transactionId = "PAYMENT-RACE-" + ids.reservationId();
        try {
            Future<Integer> expiry = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Expiry race start timed out.");
                }
                return holdService.expireDueHolds(scanTime);
            });
            Future<PaymentCompletionResult> payment = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Payment race start timed out.");
                }
                return paymentService.handleSuccessfulPayment(
                        ids.reservationId(), "VNPAY", transactionId);
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int expiredCount = expiry.get(20, TimeUnit.SECONDS);
            PaymentCompletionResult paymentResult = payment.get(20, TimeUnit.SECONDS);
            Reservation reservation = reservationRepository.findById(ids.reservationId()).orElseThrow();
            ReservationHold hold = holdRepository.findById(ids.holdId()).orElseThrow();

            assertThat(paymentRepository.findByTransactionId(transactionId)).isPresent();
            if (reservation.getStatus().equals(ReservationStatus.CONFIRMED.name())) {
                assertThat(expiredCount).isZero();
                assertThat(paymentResult).isEqualTo(PaymentCompletionResult.APPLIED);
                assertThat(hold.getStatus()).isEqualTo(ReservationHoldStatus.CONSUMED.name());
                assertThat(availabilityService.countAvailableRooms(ids.roomTypeId(), checkIn, checkOut)).isZero();
            } else {
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED.name());
                assertThat(expiredCount).isEqualTo(1);
                assertThat(paymentResult).isEqualTo(PaymentCompletionResult.RECONCILIATION_REQUIRED);
                assertThat(hold.getStatus()).isEqualTo(ReservationHoldStatus.EXPIRED.name());
                assertThat(availabilityService.countAvailableRooms(ids.roomTypeId(), checkIn, checkOut)).isEqualTo(1);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private FixtureIds createExpiredFixture(LocalDate checkIn, LocalDate checkOut, String key) {
        Hotel hotel = hotelRepository.saveAndFlush(hotel("HOLD-TEST-HOTEL-" + key));

        RoomType roomType = roomTypeRepository.saveAndFlush(roomType(hotel, "HOLD-DELUXE-" + key));
        roomRepository.saveAndFlush(room(hotel, roomType, "H-" + key));

        User user = userRepository.saveAndFlush(user("hold_" + key.toLowerCase() + "_user"));

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
                key + "-HOLD-" + reservation.getId(),
                LocalDateTime.now().minusMinutes(16));

        return new FixtureIds(reservation.getId(), roomType.getId(), hold.getId());
    }

    private Hotel hotel(String code) {
        Hotel hotel = new Hotel();
        hotel.setCode(code);
        hotel.setSlug(code.toLowerCase());
        hotel.setName("Reservation hold test hotel");
        hotel.setNameVi("Khach san kiem thu hold");
        hotel.setAddressLine("1 Test Street");
        hotel.setCity("Da Nang");
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
        roomType.setNameVi("Phong deluxe");
        roomType.setNameEn("Deluxe room");
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
        user.setFullName("Hold Restart User");
        user.setStatus("ACTIVE");
        return user;
    }

    private record FixtureIds(Long reservationId, Long roomTypeId, Long holdId) {
    }
}
