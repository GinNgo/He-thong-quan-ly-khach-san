package com.hotel.integration;

import com.hotel.entities.*;
import com.hotel.repositories.*;
import com.hotel.services.RoomAvailabilityService;
import com.hotel.util.VietnameseTextNormalizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UnicodeAndInventoryIntegrationTest {

    @Autowired private LocationRepository locationRepository;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservationDetailRepository reservationDetailRepository;
    @Autowired private RoomAvailabilityService availabilityService;

    @Test
    void preservesVietnameseAndSearchesWithoutAccents() {
        Location province = location("P-TEST-CB", "PROVINCE", "Tỉnh Cao Bằng", null);
        province = locationRepository.saveAndFlush(province);
        Location ward = location("W-TEST-PX", "WARD", "Phường Phúc Xá", province);
        locationRepository.saveAndFlush(ward);

        assertThat(locationRepository.findById(province.getId()).orElseThrow().getNameVi()).isEqualTo("Tỉnh Cao Bằng");
        assertThat(locationRepository.findById(ward.getId()).orElseThrow().getNameVi()).isEqualTo("Phường Phúc Xá");
        assertThat(locationRepository.searchLocations("cao bang", "cao bang", null, PageRequest.of(0, 10)))
                .extracting(Location::getNameVi).contains("Tỉnh Cao Bằng");
        assertThat(locationRepository.searchLocations("phuc xa", "phuc xa", null, PageRequest.of(0, 10)))
                .extracting(Location::getNameVi).contains("Phường Phúc Xá");
    }

    @Test
    void calculatesPhysicalRoomInventoryAndReleasesCancelledBooking() {
        Hotel hotel = hotelRepository.saveAndFlush(hotel("INV-TEST-HOTEL"));
        RoomType roomType = new RoomType();
        roomType.setHotel(hotel);
        roomType.setCode("DOUBLE-INV-TEST");
        roomType.setNameVi("Phòng đôi");
        roomType.setNameEn("Double room");
        roomType.setMaxGuest(3);
        roomType.setMaxAdults(2);
        roomType.setMaxChildren(1);
        roomType.setMaxGuests(3);
        roomType.setBasePrice(new BigDecimal("800000"));
        roomType.setStatus("ACTIVE");
        roomType = roomTypeRepository.saveAndFlush(roomType);

        roomRepository.saveAll(List.of(
                room(hotel, roomType, "201", "AVAILABLE", "NONE"),
                room(hotel, roomType, "202", "AVAILABLE", "NONE"),
                room(hotel, roomType, "203", "MAINTENANCE", "OUT_OF_SERVICE")
        ));

        User user = new User();
        user.setUsername("inventory_test_user");
        user.setEmail("inventory_test_user@example.test");
        user.setPasswordHash("test");
        user.setFullName("Khách kiểm thử");
        user.setStatus("ACTIVE");
        user = userRepository.saveAndFlush(user);

        LocalDate checkIn = LocalDate.of(2027, 1, 10);
        LocalDate checkOut = LocalDate.of(2027, 1, 12);
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setHotel(hotel);
        reservation.setCheckInDate(checkIn);
        reservation.setCheckOutDate(checkOut);
        reservation.setGuests(2);
        reservation.setTotalAmount(new BigDecimal("1600000"));
        reservation.setStatus("CONFIRMED");
        reservation = reservationRepository.saveAndFlush(reservation);

        ReservationDetail detail = new ReservationDetail();
        detail.setReservation(reservation);
        detail.setRoomType(roomType);
        detail.setQuantity(1);
        detail.setAdults(2);
        detail.setChildren(0);
        detail.setPrice(roomType.getBasePrice());
        detail.setUnitPrice(roomType.getBasePrice());
        detail.setSubtotal(new BigDecimal("1600000"));
        reservationDetailRepository.saveAndFlush(detail);

        assertThat(availabilityService.countAvailableRooms(roomType.getId(), checkIn, checkOut)).isEqualTo(1);
        reservation.setStatus("CANCELLED");
        reservationRepository.saveAndFlush(reservation);
        assertThat(availabilityService.countAvailableRooms(roomType.getId(), checkIn, checkOut)).isEqualTo(2);
    }

    private Location location(String code, String type, String name, Location parent) {
        Location location = new Location();
        location.setCode(code);
        location.setSourceCode(code);
        location.setLocationType(type);
        location.setNameVi(name);
        location.setNormalizedName(VietnameseTextNormalizer.normalize(name));
        location.setParent(parent);
        location.setStatus("ACTIVE");
        return location;
    }

    private Hotel hotel(String code) {
        Hotel hotel = new Hotel();
        hotel.setCode(code);
        hotel.setSlug(code.toLowerCase());
        hotel.setName("Khách sạn kiểm thử tồn phòng");
        hotel.setNameVi("Khách sạn kiểm thử tồn phòng");
        hotel.setAddressLine("123 Lê Lợi");
        hotel.setCity("Đà Nẵng");
        hotel.setCountry("Việt Nam");
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        return hotel;
    }

    private Room room(Hotel hotel, RoomType type, String number, String status, String maintenance) {
        Room room = new Room();
        room.setHotel(hotel);
        room.setRoomType(type);
        room.setRoomNumber(number);
        room.setFloor(2);
        room.setStatus(status);
        room.setMaintenanceStatus(maintenance);
        return room;
    }
}
