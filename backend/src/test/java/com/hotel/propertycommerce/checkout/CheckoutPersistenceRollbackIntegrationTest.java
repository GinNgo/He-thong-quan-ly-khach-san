package com.hotel.propertycommerce.checkout;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.ReservationDetail;
import com.hotel.entities.ReservationRoom;
import com.hotel.entities.Room;
import com.hotel.entities.RoomType;
import com.hotel.entities.User;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.HousekeepingTaskRepository;
import com.hotel.repositories.ReservationDetailRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.ReservationRoomRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(CheckoutOperationsService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:checkout-persistence-rollback;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class CheckoutPersistenceRollbackIntegrationTest {

    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservationDetailRepository reservationDetailRepository;
    @Autowired private ReservationRoomRepository reservationRoomRepository;
    @Autowired private HousekeepingTaskRepository housekeepingTaskRepository;
    @Autowired private CheckoutOperationsService checkoutOperationsService;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void exceptionAfterCheckoutWritesRollsBackAssignmentRoomAndHousekeepingTogether() {
        FixtureIds fixture = transactionTemplate.execute(status -> createFixture("ROLLBACK"));
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
                    fixture.hotelId(), effectKey(fixture))).isEmpty();
        });
    }

    @Test
    void committedCheckoutReplayKeepsOneHousekeepingEffect() {
        FixtureIds fixture = transactionTemplate.execute(status -> createFixture("REPLAY"));
        assertThat(fixture).isNotNull();

        CheckoutOperationsService.CheckoutOperationsResult first = transactionTemplate.execute(status -> {
            Reservation locked = reservationRepository.findByIdForUpdate(fixture.reservationId()).orElseThrow();
            return checkoutOperationsService.apply(locked);
        });
        CheckoutOperationsService.CheckoutOperationsResult replay = transactionTemplate.execute(status -> {
            Reservation locked = reservationRepository.findByIdForUpdate(fixture.reservationId()).orElseThrow();
            return checkoutOperationsService.apply(locked);
        });

        assertThat(first).isNotNull();
        assertThat(first.releasedAssignmentCount()).isEqualTo(1);
        assertThat(first.createdHousekeepingTaskCount()).isEqualTo(1);
        assertThat(replay).isNotNull();
        assertThat(replay.releasedAssignmentCount()).isZero();
        assertThat(replay.createdHousekeepingTaskCount()).isZero();
        assertThat(housekeepingTaskRepository.findByHotelIdAndCheckoutEffectKey(
                fixture.hotelId(), effectKey(fixture))).isPresent();
    }

    private FixtureIds createFixture(String suffix) {
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
        return new FixtureIds(hotel.getId(), reservation.getId(), assignment.getId(), room.getId());
    }

    private Hotel hotel(String suffix) {
        Hotel hotel = new Hotel();
        hotel.setCode("CHECKOUT-" + suffix);
        hotel.setSlug(("checkout-" + suffix).toLowerCase());
        hotel.setName("Checkout persistence hotel");
        hotel.setNameVi("Khach san kiem thu checkout");
        hotel.setAddressLine("1 Persistence Street");
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
        roomType.setNameVi("Phong checkout");
        roomType.setNameEn("Checkout room");
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
        room.setStatus("OCCUPIED");
        room.setMaintenanceStatus("NONE");
        room.setHousekeepingStatus("CLEAN");
        return room;
    }

    private User user(String suffix) {
        User user = new User();
        user.setUsername(("checkout-" + suffix).toLowerCase());
        user.setEmail(("checkout-" + suffix).toLowerCase() + "@example.test");
        user.setPasswordHash("test");
        user.setFullName("Checkout Persistence Guest");
        user.setStatus("ACTIVE");
        return user;
    }

    private Reservation reservation(Hotel hotel, User guest) {
        Reservation reservation = new Reservation();
        reservation.setHotel(hotel);
        reservation.setUser(guest);
        reservation.setStatus("CHECKED_IN");
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

    private String effectKey(FixtureIds fixture) {
        return "CHECKOUT:" + fixture.reservationId() + ":ROOM:" + fixture.roomId();
    }

    private record FixtureIds(Long hotelId, Long reservationId, Long assignmentId, Long roomId) {
    }

    private static final class ForcedRollbackException extends RuntimeException {
    }
}
