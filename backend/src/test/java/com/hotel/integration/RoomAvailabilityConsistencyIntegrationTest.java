package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.domain.lifecycle.ReservationStatus;
import com.hotel.dtos.ReservationRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.ReservationDetail;
import com.hotel.entities.Room;
import com.hotel.entities.RoomType;
import com.hotel.entities.User;
import com.hotel.propertycommerce.config.PropertyPaymentConfiguration;
import com.hotel.propertycommerce.config.PropertyPaymentConfigurationRepository;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.ReservationDetailRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.services.NotificationService;
import com.hotel.services.ReservationService;
import com.hotel.services.RoomAvailabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class,
        properties = "payment.property.encryption-key=test-property-payment-encryption-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoomAvailabilityConsistencyIntegrationTest {

    private static final LocalDate CHECK_IN = LocalDate.of(2030, 6, 10);
    private static final LocalDate CHECK_OUT = LocalDate.of(2030, 6, 12);

    @Autowired private MockMvc mockMvc;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservationDetailRepository reservationDetailRepository;
    @Autowired private PropertyPaymentConfigurationRepository paymentConfigurationRepository;
    @Autowired private RoomAvailabilityService availabilityService;
    @Autowired private ReservationService reservationService;

    @MockBean private NotificationService notificationService;

    @Test
    void searchDetailAndLockedBookingShareCurrentAndDatedAvailabilityPools() throws Exception {
        Hotel hotel = saveHotel("STATE-MATRIX", "T280 State Matrix Hotel");
        RoomType roomType = saveRoomType(hotel, "STATE-MATRIX", 8);
        saveRoom(hotel, roomType, "101", "AVAILABLE", "CLEAN", "NONE");
        saveRoom(hotel, roomType, "102", "AVAILABLE", "INSPECTED", "NONE");
        saveRoom(hotel, roomType, "103", "RESERVED", "CLEAN", "NONE");
        saveRoom(hotel, roomType, "104", "OCCUPIED", "INSPECTED", "NONE");
        saveRoom(hotel, roomType, "105", "AVAILABLE", "DIRTY", "NONE");
        saveRoom(hotel, roomType, "106", "AVAILABLE", "CLEANING", "NONE");
        saveRoom(hotel, roomType, "107", "MAINTENANCE", "CLEAN", "NONE");
        saveRoom(hotel, roomType, "108", "OUT_OF_SERVICE", "CLEAN", "NONE");
        saveRoom(hotel, roomType, "109", "AVAILABLE", "CLEAN", "MAINTENANCE");
        saveRoom(hotel, roomType, "110", "AVAILABLE", "INSPECTED", "OUT_OF_SERVICE");
        saveRoom(hotel, roomType, "111", "RESERVED", "DIRTY", "NONE");

        assertThat(availabilityService.countAvailableRooms(roomType.getId(), null, null)).isEqualTo(2);
        assertThat(availabilityService.countAvailableRooms(roomType.getId(), CHECK_IN, CHECK_OUT)).isEqualTo(4);

        mockMvc.perform(get("/api/public/properties/search")
                        .param("keyword", "T280 State Matrix"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].availableRoomCount").value(2));
        mockMvc.perform(get("/api/public/properties/search")
                        .param("keyword", "T280 State Matrix")
                        .param("checkInDate", CHECK_IN.toString())
                        .param("checkOutDate", CHECK_OUT.toString())
                        .param("roomCount", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].availableRoomCount").value(4));

        mockMvc.perform(get("/api/public/properties/{hotelId}/room-types", hotel.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].availableRooms").value(2));
        mockMvc.perform(get("/api/public/properties/{hotelId}/room-types", hotel.getId())
                        .param("checkIn", CHECK_IN.toString())
                        .param("checkOut", CHECK_OUT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].availableRooms").value(4));
        mockMvc.perform(get("/api/room-types/public/hotel/{hotelId}", hotel.getId())
                        .param("checkIn", CHECK_IN.toString())
                        .param("checkOut", CHECK_OUT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].availableRooms").value(4));

        User user = saveUser("t280_state_matrix");
        enablePayments(hotel);
        reservationService.createReservation(user.getUsername(), bookingRequest(roomType.getId(), 4));

        assertThat(availabilityService.countAvailableRooms(roomType.getId(), CHECK_IN, CHECK_OUT)).isZero();
        mockMvc.perform(get("/api/public/properties/search")
                        .param("keyword", "T280 State Matrix")
                        .param("checkInDate", CHECK_IN.toString())
                        .param("checkOutDate", CHECK_OUT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/public/properties/{hotelId}/room-types", hotel.getId())
                        .param("checkIn", CHECK_IN.toString())
                        .param("checkOut", CHECK_OUT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").doesNotExist());
    }

    @Test
    void everyOverlappingReservationStatusUsesTheSharedReleasedStatusContract() {
        Hotel hotel = saveHotel("STATUS-MATRIX", "T280 Reservation Status Matrix");
        User user = saveUser("t280_status_matrix");
        EnumSet<ReservationStatus> released = EnumSet.of(
                ReservationStatus.CANCELLED, ReservationStatus.REJECTED, ReservationStatus.EXPIRED,
                ReservationStatus.NO_SHOW, ReservationStatus.CHECKED_OUT, ReservationStatus.COMPLETED);

        for (ReservationStatus status : ReservationStatus.values()) {
            RoomType roomType = saveRoomType(hotel, "STATUS-" + status.name(), 2);
            saveRoom(hotel, roomType, status.name(), "AVAILABLE", "CLEAN", "NONE");
            saveReservation(user, hotel, roomType, status.name());

            long expected = released.contains(status) ? 1 : 0;
            assertThat(availabilityService.countAvailableRooms(roomType.getId(), CHECK_IN, CHECK_OUT))
                    .as("overlapping status %s", status)
                    .isEqualTo(expected);
        }
    }

    private Hotel saveHotel(String suffix, String name) {
        Hotel hotel = new Hotel();
        hotel.setCode("TEST-T280-" + suffix);
        hotel.setSlug("test-t280-" + suffix.toLowerCase());
        hotel.setName(name);
        hotel.setNameVi(name);
        hotel.setNormalizedName(name.toLowerCase());
        hotel.setAddressLine("280 Availability Street");
        hotel.setCity("Da Nang");
        hotel.setCountry("Vietnam");
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        hotel.setPropertyType("HOTEL");
        return hotelRepository.saveAndFlush(hotel);
    }

    private RoomType saveRoomType(Hotel hotel, String suffix, int maxGuests) {
        RoomType roomType = new RoomType();
        roomType.setHotel(hotel);
        roomType.setCode("T280-" + suffix);
        roomType.setNameVi("T280 room " + suffix);
        roomType.setNameEn("T280 room " + suffix);
        roomType.setBasePrice(new BigDecimal("700000"));
        roomType.setMaxGuest(maxGuests);
        roomType.setMaxAdults(maxGuests);
        roomType.setMaxChildren(maxGuests);
        roomType.setMaxGuests(maxGuests);
        roomType.setStatus("ACTIVE");
        return roomTypeRepository.saveAndFlush(roomType);
    }

    private Room saveRoom(Hotel hotel, RoomType roomType, String number, String status,
                          String housekeepingStatus, String maintenanceStatus) {
        Room room = new Room();
        room.setHotel(hotel);
        room.setRoomType(roomType);
        room.setRoomNumber("T280-" + number);
        room.setFloor(1);
        room.setStatus(status);
        room.setHousekeepingStatus(housekeepingStatus);
        room.setMaintenanceStatus(maintenanceStatus);
        return roomRepository.saveAndFlush(room);
    }

    private User saveUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setPasswordHash("test");
        user.setFullName("T280 Test User");
        user.setStatus("ACTIVE");
        return userRepository.saveAndFlush(user);
    }

    private void enablePayments(Hotel hotel) {
        PropertyPaymentConfiguration configuration = new PropertyPaymentConfiguration(hotel);
        ReflectionTestUtils.setField(configuration, "enabled", true);
        paymentConfigurationRepository.saveAndFlush(configuration);
    }

    private ReservationRequest bookingRequest(Long roomTypeId, int quantity) {
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

    private void saveReservation(User user, Hotel hotel, RoomType roomType, String status) {
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setHotel(hotel);
        reservation.setCheckInDate(CHECK_IN);
        reservation.setCheckOutDate(CHECK_OUT);
        reservation.setGuests(1);
        reservation.setTotalAmount(new BigDecimal("700000"));
        reservation.setStatus(status);
        reservation = reservationRepository.saveAndFlush(reservation);

        ReservationDetail detail = new ReservationDetail();
        detail.setReservation(reservation);
        detail.setRoomType(roomType);
        detail.setQuantity(1);
        detail.setAdults(1);
        detail.setChildren(0);
        detail.setPrice(roomType.getBasePrice());
        detail.setUnitPrice(roomType.getBasePrice());
        detail.setSubtotal(roomType.getBasePrice());
        reservationDetailRepository.saveAndFlush(detail);
    }
}
