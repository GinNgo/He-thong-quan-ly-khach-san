package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.services.OperationalAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = BackendApplication.class,
        properties = "payment.property.encryption-key=test-property-payment-encryption-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReservationReadJourneyIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserPropertyRepository userPropertyRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private OperationalAuditService operationalAuditService;

    private Hotel hotelA;
    private Hotel hotelB;
    private User customerA;
    private User customerB;
    private User receptionistA;
    private User managerB;
    private Reservation reservationAConfirmed;
    private Reservation reservationAPending;
    private Reservation reservationB;

    @BeforeEach
    void setUp() {
        hotelA = hotel("STAY-READ-A", "Stay Read Hotel A");
        hotelB = hotel("STAY-READ-B", "Stay Read Hotel B");
        customerA = createUser("stay-read-customer-a", "Customer A");
        customerB = createUser("stay-read-customer-b", "Customer B");
        receptionistA = createUser("stay-read-receptionist-a", "Receptionist A");
        managerB = createUser("stay-read-manager-b", "Manager B");
        assign(receptionistA, hotelA, "RECEPTIONIST");
        assign(managerB, hotelB, "MANAGER");

        reservationAConfirmed = reservation(
                customerA, hotelA, LocalDate.of(2026, 9, 20), "CONFIRMED", "1800000");
        reservationAPending = reservation(
                customerA, hotelA, LocalDate.of(2026, 9, 10), "PENDING", "900000");
        reservationB = reservation(
                customerB, hotelB, LocalDate.of(2026, 9, 25), "CONFIRMED", "2400000");

        operationalAuditService.append(new OperationalAuditService.AuditCommand(
                "TENANT", hotelA.getId(), "STAY", "RESERVATION_CREATED", "RESERVATION",
                reservationAConfirmed.getId().toString(), "SYSTEM", null,
                "Seeded reservation created for read-journey verification.", null,
                Map.of("status", "CONFIRMED"), "stay-read-seed"));
    }

    @Test
    void customerListsOnlyOwnedBookingsAndReadsImmutableHistory() throws Exception {
        mockMvc.perform(get("/api/reservations/my-bookings/page")
                        .param("page", "0")
                        .param("size", "10")
                        .with(user(customerDetails(customerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(reservationAConfirmed.getId()))
                .andExpect(jsonPath("$.content[1].id").value(reservationAPending.getId()));

        mockMvc.perform(get("/api/reservations/{id}", reservationAConfirmed.getId())
                        .with(user(customerDetails(customerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(customerA.getId()))
                .andExpect(jsonPath("$.events[0].eventType").value("RESERVATION_CREATED"));

        mockMvc.perform(get("/api/reservations/{id}", reservationB.getId())
                        .with(user(customerDetails(customerA))))
                .andExpect(status().isNotFound());
    }

    @Test
    void receptionistUsesStableFiltersAndCannotCrossProperty() throws Exception {
        mockMvc.perform(get("/api/reservations/page")
                        .param("status", "CONFIRMED")
                        .param("query", customerA.getUsername())
                        .param("page", "0")
                        .param("size", "1")
                        .with(user(staffDetails(receptionistA, hotelA, "RECEPTIONIST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(reservationAConfirmed.getId()));

        mockMvc.perform(get("/api/reservations/{id}", reservationAConfirmed.getId())
                        .with(user(staffDetails(receptionistA, hotelA, "RECEPTIONIST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].reason")
                        .value("Seeded reservation created for read-journey verification."));

        mockMvc.perform(get("/api/reservations/{id}", reservationB.getId())
                        .with(user(staffDetails(receptionistA, hotelA, "RECEPTIONIST"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void managerSeesOnlyAssignedPropertyWithDeterministicOrdering() throws Exception {
        mockMvc.perform(get("/api/reservations/page")
                        .param("page", "0")
                        .param("size", "10")
                        .with(user(staffDetails(managerB, hotelB, "HOTEL_MANAGER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(reservationB.getId()))
                .andExpect(jsonPath("$.content[0].userId").value(customerB.getId()));
    }

    private Hotel hotel(String code, String name) {
        Hotel hotel = new Hotel();
        hotel.setCode(code);
        hotel.setSlug(code.toLowerCase());
        hotel.setName(name);
        hotel.setNameVi(name);
        hotel.setAddressLine("1 Test Street");
        hotel.setCity("Da Nang");
        hotel.setCountry("Viet Nam");
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        return hotelRepository.saveAndFlush(hotel);
    }

    private User createUser(String username, String fullName) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setPasswordHash("test-password");
        user.setFullName(fullName);
        user.setStatus("ACTIVE");
        return userRepository.saveAndFlush(user);
    }

    private void assign(User staff, Hotel hotel, String relationship) {
        UserProperty assignment = new UserProperty();
        assignment.setUser(staff);
        assignment.setHotel(hotel);
        assignment.setRelationshipType(relationship);
        assignment.setStatus("ACTIVE");
        assignment.setStartDate(LocalDateTime.now());
        userPropertyRepository.saveAndFlush(assignment);
    }

    private Reservation reservation(User customer, Hotel hotel, LocalDate checkIn, String status, String amount) {
        Reservation reservation = new Reservation();
        reservation.setUser(customer);
        reservation.setHotel(hotel);
        reservation.setCheckInDate(checkIn);
        reservation.setCheckOutDate(checkIn.plusDays(2));
        reservation.setGuests(2);
        reservation.setTotalAmount(new BigDecimal(amount));
        reservation.setPaymentMethod("VNPAY");
        reservation.setStatus(status);
        return reservationRepository.saveAndFlush(reservation);
    }

    private CustomUserDetails customerDetails(User customer) {
        return new CustomUserDetails(
                customer.getUsername(), customer.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("CUSTOMER")), Map.of(),
                customer.getId(), null, Map.of());
    }

    private CustomUserDetails staffDetails(User staff, Hotel hotel, String authority) {
        return new CustomUserDetails(
                staff.getUsername(), staff.getPasswordHash(),
                List.of(new SimpleGrantedAuthority(authority)),
                Map.of(FunctionCode.RESERVATION, ActionCode.VIEW),
                staff.getId(), hotel.getId(), Map.of());
    }
}
