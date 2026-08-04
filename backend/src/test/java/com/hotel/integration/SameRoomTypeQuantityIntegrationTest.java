package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.domain.lifecycle.ReservationStatus;
import com.hotel.dtos.AssignRoomsRequest;
import com.hotel.dtos.ReservationDTO;
import com.hotel.dtos.ReservationRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.ReservationDetail;
import com.hotel.entities.ReservationHold;
import com.hotel.entities.Room;
import com.hotel.entities.RoomType;
import com.hotel.entities.User;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.propertycommerce.config.PropertyPaymentConfiguration;
import com.hotel.propertycommerce.config.PropertyPaymentConfigurationRepository;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.ReservationDetailRepository;
import com.hotel.repositories.ReservationHoldRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.ReservationRoomRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.services.NotificationService;
import com.hotel.services.PropertyAccessService;
import com.hotel.services.ReservationService;
import com.hotel.services.RoomAvailabilityService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = BackendApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:t290-same-room-quantity;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000",
        "app.reservation-hold.expiry-scan-ms=3600000",
        "payment.property.encryption-key=test-property-payment-encryption-key"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SameRoomTypeQuantityIntegrationTest {

    private static final LocalDate CHECK_IN = LocalDate.of(2031, 4, 10);
    private static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2);

    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservationDetailRepository reservationDetailRepository;
    @Autowired private ReservationHoldRepository holdRepository;
    @Autowired private ReservationRoomRepository reservationRoomRepository;
    @Autowired private PropertyPaymentConfigurationRepository paymentConfigurationRepository;
    @Autowired private ReservationService reservationService;
    @Autowired private RoomAvailabilityService availabilityService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private EntityManager entityManager;

    @MockBean private NotificationService notificationService;
    @MockBean private PropertyAccessService propertyAccessService;

    @BeforeEach
    void allowOperationalAccess() {
        reset(propertyAccessService);
        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
    }

    @Test
    void quantityBookingPersistsOneDetailAndHoldWhileSoldOutRetryCreatesNothing() {
        Fixture fixture = fixture("SUCCESS", 3);

        ReservationDTO booking = reservationService.createReservation(
                fixture.username(), request(fixture.roomTypeId(), 2));

        ReservationDetail detail = reservationDetailRepository.findByReservationId(booking.getId()).get(0);
        ReservationHold hold = holdRepository.findAll().stream()
                .filter(item -> item.getReservation().getId().equals(booking.getId()))
                .findFirst().orElseThrow();
        assertThat(booking.getDetails()).singleElement().satisfies(item -> assertThat(item.getQuantity()).isEqualTo(2));
        assertThat(booking.getTotalAmount()).isEqualByComparingTo("3220000");
        assertThat(detail.getQuantity()).isEqualTo(2);
        assertThat(detail.getSubtotal()).isEqualByComparingTo("2800000");
        assertThat(hold.getQuantity()).isEqualTo(2);
        assertThat(availabilityService.countAvailableRooms(fixture.roomTypeId(), CHECK_IN, CHECK_OUT)).isEqualTo(1);

        long reservationsBefore = reservationRepository.count();
        long detailsBefore = reservationDetailRepository.count();
        long holdsBefore = holdRepository.count();
        assertThatThrownBy(() -> reservationService.createReservation(
                fixture.username(), request(fixture.roomTypeId(), 2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1");

        assertThat(reservationRepository.count()).isEqualTo(reservationsBefore);
        assertThat(reservationDetailRepository.count()).isEqualTo(detailsBefore);
        assertThat(holdRepository.count()).isEqualTo(holdsBefore);
        assertThat(availabilityService.countAvailableRooms(fixture.roomTypeId(), CHECK_IN, CHECK_OUT)).isEqualTo(1);
    }

    @Test
    void concurrentLastTwoRoomsProduceExactlyOneCompleteQuantityBooking() throws Exception {
        Fixture fixture = fixture("CONCURRENT", 2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Attempt> call = () -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                try {
                    return new Attempt(reservationService.createReservation(
                            fixture.username(), request(fixture.roomTypeId(), 2)), null);
                } catch (Throwable error) {
                    return new Attempt(null, error);
                }
            };
            Future<Attempt> first = executor.submit(call);
            Future<Attempt> second = executor.submit(call);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Attempt> attempts = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));

            assertThat(attempts).filteredOn(Attempt::succeeded).hasSize(1);
            assertThat(attempts).filteredOn(attempt -> !attempt.succeeded()).hasSize(1);
            assertThat(attempts.stream().filter(attempt -> !attempt.succeeded()).findFirst().orElseThrow().error())
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            executor.shutdownNow();
        }

        assertThat(reservationRepository.countByUserIdAndStatusIn(
                fixture.userId(), List.of(ReservationStatus.PENDING_PAYMENT.name()))).isEqualTo(1);
        Long reservationId = reservationRepository.findByUserId(fixture.userId()).get(0).getId();
        assertThat(reservationDetailRepository.findByReservationId(reservationId)).singleElement()
                .satisfies(detail -> assertThat(detail.getQuantity()).isEqualTo(2));
        assertThat(holdRepository.findAll().stream()
                .filter(hold -> hold.getReservation().getId().equals(reservationId)).toList()).singleElement()
                .satisfies(hold -> assertThat(hold.getQuantity()).isEqualTo(2));
        assertThat(availabilityService.countAvailableRooms(fixture.roomTypeId(), CHECK_IN, CHECK_OUT)).isZero();
    }

    @Test
    void assignmentRequiresExactUniqueQuantityAndSuccessfulReplayIsIdempotent() {
        Fixture fixture = fixture("ASSIGN", 2);
        ReservationDTO booking = reservationService.createReservation(
                fixture.username(), request(fixture.roomTypeId(), 2));

        assertThatThrownBy(() -> reservationService.assignRooms(
                booking.getId(), assignment(List.of(fixture.roomIds().get(0)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reservationService.assignRooms(
                booking.getId(), assignment(List.of(fixture.roomIds().get(0), fixture.roomIds().get(0)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(assignmentsFor(booking.getId())).isEmpty();

        reservationService.assignRooms(booking.getId(), assignment(fixture.roomIds()));
        reservationService.assignRooms(booking.getId(), assignment(fixture.roomIds()));

        assertThat(assignmentsFor(booking.getId())).hasSize(2);
        entityManager.clear();
        assertThat(roomRepository.findAllById(fixture.roomIds()))
                .allSatisfy(room -> assertThat(room.getStatus()).isEqualTo("RESERVED"));
    }

    @Test
    void invalidFinalRoomAndCrossPropertyDenialRollbackEveryAssignmentMutation() {
        Fixture fixture = fixture("ROLLBACK", 3);
        ReservationDTO booking = reservationService.createReservation(
                fixture.username(), request(fixture.roomTypeId(), 2));
        List<Long> selected = fixture.roomIds().subList(0, 2);
        transactionTemplate.executeWithoutResult(status -> {
            Room invalid = roomRepository.findById(selected.get(1)).orElseThrow();
            invalid.setHousekeepingStatus("DIRTY");
            roomRepository.saveAndFlush(invalid);
        });

        assertThatThrownBy(() -> reservationService.assignRooms(booking.getId(), assignment(selected)))
                .isInstanceOf(IllegalStateException.class);
        entityManager.clear();
        assertThat(assignmentsFor(booking.getId())).isEmpty();
        assertThat(roomRepository.findById(selected.get(0)).orElseThrow().getStatus()).isEqualTo("AVAILABLE");
        assertThat(roomRepository.findById(selected.get(1)).orElseThrow().getStatus()).isEqualTo("AVAILABLE");
        assertThat(roomRepository.findById(selected.get(1)).orElseThrow().getHousekeepingStatus()).isEqualTo("DIRTY");

        reset(propertyAccessService);
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        doThrow(new ResourceNotFoundException("booking not found"))
                .when(propertyAccessService).requireAccessibleOrNotFound(fixture.hotelId(), "booking");
        assertThatThrownBy(() -> reservationService.assignRooms(
                booking.getId(), assignment(List.of(selected.get(0), fixture.roomIds().get(2)))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("booking not found");
        assertThat(assignmentsFor(booking.getId())).isEmpty();
    }

    private Fixture fixture(String suffix, int roomCount) {
        return transactionTemplate.execute(status -> {
            Hotel hotel = hotelRepository.saveAndFlush(hotel(suffix));
            PropertyPaymentConfiguration configuration = new PropertyPaymentConfiguration(hotel);
            ReflectionTestUtils.setField(configuration, "enabled", true);
            paymentConfigurationRepository.saveAndFlush(configuration);
            RoomType roomType = roomTypeRepository.saveAndFlush(roomType(hotel, suffix));
            List<Long> roomIds = java.util.stream.IntStream.rangeClosed(1, roomCount)
                    .mapToObj(index -> roomRepository.saveAndFlush(room(hotel, roomType, suffix + "-" + index)).getId())
                    .toList();
            User user = userRepository.saveAndFlush(user(suffix));
            return new Fixture(hotel.getId(), user.getId(), user.getUsername(), roomType.getId(), roomIds);
        });
    }

    private Hotel hotel(String suffix) {
        Hotel hotel = new Hotel();
        hotel.setCode("T290-" + suffix);
        hotel.setSlug("t290-" + suffix.toLowerCase());
        hotel.setName("T290 " + suffix);
        hotel.setNameVi("T290 " + suffix);
        hotel.setAddressLine("290 Test Street");
        hotel.setCity("Da Nang");
        hotel.setCountry("Viet Nam");
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        return hotel;
    }

    private RoomType roomType(Hotel hotel, String suffix) {
        RoomType roomType = new RoomType();
        roomType.setHotel(hotel);
        roomType.setCode("T290-ROOM-" + suffix);
        roomType.setNameVi("Phong T290 " + suffix);
        roomType.setNameEn("T290 room " + suffix);
        roomType.setMaxGuest(10);
        roomType.setMaxAdults(10);
        roomType.setMaxChildren(10);
        roomType.setMaxGuests(10);
        roomType.setBasePrice(new BigDecimal("700000"));
        roomType.setStatus("ACTIVE");
        return roomType;
    }

    private Room room(Hotel hotel, RoomType roomType, String number) {
        Room room = new Room();
        room.setHotel(hotel);
        room.setRoomType(roomType);
        room.setRoomNumber("T290-" + number);
        room.setFloor(1);
        room.setStatus("AVAILABLE");
        room.setHousekeepingStatus("CLEAN");
        room.setMaintenanceStatus("NONE");
        return room;
    }

    private User user(String suffix) {
        User user = new User();
        user.setUsername("t290_" + suffix.toLowerCase());
        user.setEmail(user.getUsername() + "@example.test");
        user.setPasswordHash("test");
        user.setFullName("T290 Test User");
        user.setStatus("ACTIVE");
        return user;
    }

    private ReservationRequest request(Long roomTypeId, int quantity) {
        ReservationRequest request = new ReservationRequest();
        request.setRoomTypeId(roomTypeId);
        request.setCheckInDate(CHECK_IN);
        request.setCheckOutDate(CHECK_OUT);
        request.setQuantity(quantity);
        request.setAdults(2);
        request.setChildren(0);
        request.setPaymentMethod("VNPAY");
        return request;
    }

    private AssignRoomsRequest assignment(List<Long> roomIds) {
        AssignRoomsRequest request = new AssignRoomsRequest();
        request.setRoomIds(roomIds);
        return request;
    }

    private List<com.hotel.entities.ReservationRoom> assignmentsFor(Long reservationId) {
        return reservationRoomRepository.findByReservationDetailReservationId(reservationId);
    }

    private record Fixture(Long hotelId, Long userId, String username, Long roomTypeId, List<Long> roomIds) { }
    private record Attempt(ReservationDTO booking, Throwable error) {
        boolean succeeded() { return booking != null && error == null; }
    }
}
