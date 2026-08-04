package com.hotel.repositories;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.ReservationDetail;
import com.hotel.entities.ReservationRoom;
import com.hotel.entities.Room;
import com.hotel.entities.RoomType;
import com.hotel.entities.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:available-room-query;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class AvailableRoomRepositoryIntegrationTest {

    @Autowired private RoomRepository roomRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void excludesSnapshotOverlapButIncludesBoundaryReleasedAndUnassignedRooms() {
        Hotel hotel = persistHotel("available-room");
        RoomType roomType = persistRoomType(hotel);
        User guest = persistUser("available-room");
        Reservation reservation = persistReservation(hotel, guest);
        ReservationDetail detail = persistDetail(reservation, roomType);
        Room overlapping = persistRoom(hotel, roomType, "101");
        Room boundary = persistRoom(hotel, roomType, "102");
        Room released = persistRoom(hotel, roomType, "103");
        Room unassigned = persistRoom(hotel, roomType, "104");
        persistAssignment(detail, overlapping, "ASSIGNED",
                LocalDate.of(2028, 2, 10), LocalDate.of(2028, 2, 12));
        persistAssignment(detail, boundary, "ASSIGNED",
                LocalDate.of(2028, 2, 8), LocalDate.of(2028, 2, 10));
        persistAssignment(detail, released, "RELEASED",
                LocalDate.of(2028, 2, 10), LocalDate.of(2028, 2, 12));
        entityManager.flush();
        entityManager.clear();

        List<Room> result = roomRepository.findAvailableRoomsByRoomTypeAndDate(
                hotel.getId(),
                roomType.getId(),
                List.of("MAINTENANCE", "OUT_OF_SERVICE", "DIRTY", "CLEANING", "OCCUPIED"),
                List.of("CANCELLED", "REJECTED", "EXPIRED", "NO_SHOW", "CHECKED_OUT", "COMPLETED"),
                LocalDate.of(2028, 2, 10),
                LocalDate.of(2028, 2, 12));

        assertThat(result).extracting(Room::getRoomNumber)
                .containsExactly("102", "103", "104");
    }

    private Hotel persistHotel(String suffix) {
        Hotel hotel = new Hotel();
        hotel.setCode("HOTEL-" + suffix);
        hotel.setSlug("hotel-" + suffix);
        hotel.setName("Available room hotel");
        hotel.setNameVi("Khach san phong trong");
        hotel.setAddressLine("1 Test Street");
        hotel.setCity("Ho Chi Minh City");
        hotel.setCountry("Viet Nam");
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        entityManager.persist(hotel);
        return hotel;
    }

    private RoomType persistRoomType(Hotel hotel) {
        RoomType roomType = new RoomType();
        roomType.setHotel(hotel);
        roomType.setCode("DELUXE");
        roomType.setNameVi("Phong Deluxe");
        roomType.setNameEn("Deluxe room");
        roomType.setBasePrice(BigDecimal.valueOf(1_000_000));
        roomType.setStatus("ACTIVE");
        entityManager.persist(roomType);
        return roomType;
    }

    private User persistUser(String suffix) {
        User user = new User();
        user.setUsername("guest-" + suffix);
        user.setEmail("guest-" + suffix + "@example.test");
        user.setPasswordHash("test");
        user.setFullName("Available Room Guest");
        user.setStatus("ACTIVE");
        entityManager.persist(user);
        return user;
    }

    private Reservation persistReservation(Hotel hotel, User user) {
        Reservation reservation = new Reservation();
        reservation.setHotel(hotel);
        reservation.setUser(user);
        reservation.setStatus("CONFIRMED");
        reservation.setCheckInDate(LocalDate.of(2030, 1, 1));
        reservation.setCheckOutDate(LocalDate.of(2030, 1, 2));
        reservation.setGuests(2);
        reservation.setTotalAmount(BigDecimal.valueOf(1_000_000));
        entityManager.persist(reservation);
        return reservation;
    }

    private ReservationDetail persistDetail(Reservation reservation, RoomType roomType) {
        ReservationDetail detail = new ReservationDetail();
        detail.setReservation(reservation);
        detail.setRoomType(roomType);
        detail.setQuantity(1);
        detail.setAdults(2);
        detail.setChildren(0);
        detail.setPrice(roomType.getBasePrice());
        detail.setUnitPrice(roomType.getBasePrice());
        detail.setSubtotal(roomType.getBasePrice());
        entityManager.persist(detail);
        return detail;
    }

    private Room persistRoom(Hotel hotel, RoomType roomType, String number) {
        Room room = new Room();
        room.setHotel(hotel);
        room.setRoomType(roomType);
        room.setRoomNumber(number);
        room.setFloor(1);
        room.setStatus("AVAILABLE");
        room.setHousekeepingStatus("CLEAN");
        room.setMaintenanceStatus("NONE");
        entityManager.persist(room);
        return room;
    }

    private void persistAssignment(
            ReservationDetail detail,
            Room room,
            String status,
            LocalDate stayStart,
            LocalDate stayEnd) {
        ReservationRoom assignment = new ReservationRoom();
        assignment.setReservationDetail(detail);
        assignment.setRoom(room);
        assignment.setStatus(status);
        assignment.setStayStartDate(stayStart);
        assignment.setStayEndDate(stayEnd);
        entityManager.persist(assignment);
    }
}
