package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.dtos.AssignRoomsRequest;
import com.hotel.dtos.ReservationDTO;
import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.ReservationDetail;
import com.hotel.entities.ReservationRoom;
import com.hotel.entities.Room;
import com.hotel.entities.RoomType;
import com.hotel.entities.User;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.ReservationDetailRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.ReservationRoomRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.services.NotificationService;
import com.hotel.services.PropertyAccessService;
import com.hotel.services.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
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
import static org.mockito.Mockito.when;

@SpringBootTest(classes = BackendApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:reservation-assignment-concurrency;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000",
        "app.reservation-hold.expiry-scan-ms=3600000",
        "payment.property.encryption-key=test-property-payment-encryption-key"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReservationAssignmentConcurrencyIntegrationTest {

    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservationDetailRepository reservationDetailRepository;
    @Autowired private ReservationRoomRepository reservationRoomRepository;
    @Autowired private ReservationService reservationService;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockBean private NotificationService notificationService;
    @MockBean private PropertyAccessService propertyAccessService;

    @Test
    void simultaneousAssignmentsOfOnePhysicalRoomProduceOneWinner() throws Exception {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
        Fixture fixture = transactionTemplate.execute(status -> createFixture());
        assertThat(fixture).isNotNull();

        AssignRoomsRequest request = new AssignRoomsRequest();
        request.setRoomIds(List.of(fixture.roomId()));
        List<Attempt> attempts = runConcurrent(
                () -> reservationService.assignRooms(fixture.firstReservationId(), request),
                () -> reservationService.assignRooms(fixture.secondReservationId(), request));

        assertThat(attempts).filteredOn(Attempt::succeeded).hasSize(1);
        assertThat(attempts).filteredOn(attempt -> !attempt.succeeded()).hasSize(1);
        assertThat(attempts.stream()
                .filter(attempt -> !attempt.succeeded())
                .map(Attempt::error)
                .findFirst()
                .orElseThrow())
                .isInstanceOf(IllegalStateException.class);

        List<ReservationRoom> assignments = reservationRoomRepository.findAll().stream()
                .filter(item -> "ASSIGNED".equals(item.getStatus()))
                .toList();
        assertThat(assignments).hasSize(1);
        assertThat(assignments.get(0).getRoom().getId()).isEqualTo(fixture.roomId());
        assertThat(assignments.get(0).getStayStartDate()).isEqualTo(LocalDate.of(2028, 5, 10));
        assertThat(assignments.get(0).getStayEndDate()).isEqualTo(LocalDate.of(2028, 5, 12));
    }

    private Fixture createFixture() {
        Hotel hotel = hotelRepository.saveAndFlush(hotel());
        RoomType roomType = roomTypeRepository.saveAndFlush(roomType(hotel));
        Room room = roomRepository.saveAndFlush(room(hotel, roomType));
        User firstUser = userRepository.saveAndFlush(user("assignment-first"));
        User secondUser = userRepository.saveAndFlush(user("assignment-second"));
        Reservation first = reservationRepository.saveAndFlush(reservation(hotel, firstUser));
        Reservation second = reservationRepository.saveAndFlush(reservation(hotel, secondUser));
        reservationDetailRepository.saveAndFlush(detail(first, roomType));
        reservationDetailRepository.saveAndFlush(detail(second, roomType));
        return new Fixture(room.getId(), first.getId(), second.getId());
    }

    private <T extends ReservationDTO> List<Attempt> runConcurrent(
            Callable<T> firstAction,
            Callable<T> secondAction) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Attempt> first = executor.submit(attempt(firstAction, ready, start));
            Future<Attempt> second = executor.submit(attempt(secondAction, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private <T extends ReservationDTO> Callable<Attempt> attempt(
            Callable<T> action,
            CountDownLatch ready,
            CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                return new Attempt(null, new IllegalStateException("Concurrent start timed out."));
            }
            try {
                return new Attempt(action.call(), null);
            } catch (Throwable error) {
                return new Attempt(null, error);
            }
        };
    }

    private Hotel hotel() {
        Hotel hotel = new Hotel();
        hotel.setCode("ASSIGNMENT-CONCURRENCY-HOTEL");
        hotel.setSlug("assignment-concurrency-hotel");
        hotel.setName("Assignment concurrency hotel");
        hotel.setNameVi("Khach san kiem thu gan phong");
        hotel.setAddressLine("1 Test Street");
        hotel.setCity("Da Nang");
        hotel.setCountry("Viet Nam");
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        return hotel;
    }

    private RoomType roomType(Hotel hotel) {
        RoomType roomType = new RoomType();
        roomType.setHotel(hotel);
        roomType.setCode("ASSIGNMENT-CONCURRENCY-TYPE");
        roomType.setNameVi("Phong gan dong thoi");
        roomType.setNameEn("Concurrent assignment room");
        roomType.setMaxGuest(2);
        roomType.setMaxAdults(2);
        roomType.setMaxChildren(0);
        roomType.setMaxGuests(2);
        roomType.setBasePrice(new BigDecimal("1000000"));
        roomType.setStatus("ACTIVE");
        return roomType;
    }

    private Room room(Hotel hotel, RoomType roomType) {
        Room room = new Room();
        room.setHotel(hotel);
        room.setRoomType(roomType);
        room.setRoomNumber("A-101");
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
        user.setFullName("Assignment test user");
        user.setStatus("ACTIVE");
        return user;
    }

    private Reservation reservation(Hotel hotel, User user) {
        Reservation reservation = new Reservation();
        reservation.setHotel(hotel);
        reservation.setUser(user);
        reservation.setStatus("CONFIRMED");
        reservation.setCheckInDate(LocalDate.of(2028, 5, 10));
        reservation.setCheckOutDate(LocalDate.of(2028, 5, 12));
        reservation.setGuests(2);
        reservation.setTotalAmount(new BigDecimal("2000000"));
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
        detail.setSubtotal(new BigDecimal("2000000"));
        return detail;
    }

    private record Fixture(Long roomId, Long firstReservationId, Long secondReservationId) {
    }

    private record Attempt(ReservationDTO value, Throwable error) {
        boolean succeeded() {
            return value != null && error == null;
        }
    }
}
