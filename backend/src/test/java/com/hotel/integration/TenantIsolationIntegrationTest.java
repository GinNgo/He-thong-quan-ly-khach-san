package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.dtos.RoomDTO;
import com.hotel.entities.Hotel;
import com.hotel.entities.HousekeepingTask;
import com.hotel.entities.Invoice;
import com.hotel.entities.Reservation;
import com.hotel.entities.Role;
import com.hotel.entities.Room;
import com.hotel.entities.RoomType;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.repositories.*;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = BackendApplication.class,
        properties = "payment.property.encryption-key=test-property-payment-encryption-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class TenantIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserPropertyRepository userPropertyRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private HousekeepingTaskRepository housekeepingTaskRepository;

    private Hotel hotelA;
    private Hotel hotelB;
    private User staffA;
    private User staffB;
    private User customerC;
    private User customerD;
    private RoomType typeA;
    private RoomType typeB;
    private Room roomB;
    private Reservation reservationD;
    private Reservation reservationDWithoutInvoice;
    private HousekeepingTask taskA;
    private HousekeepingTask taskB;

    @BeforeEach
    void setUp() {
        Role ownerRole = roleRepository.findByCode("PROPERTY_OWNER").orElseGet(() -> {
            Role role = new Role();
            role.setCode("PROPERTY_OWNER");
            role.setName("Owner");
            return roleRepository.save(role);
        });
        Role housekeepingRole = roleRepository.findByCode("HOUSEKEEPING").orElseGet(() -> {
            Role role = new Role();
            role.setCode("HOUSEKEEPING");
            role.setName("Housekeeping");
            role.setStatus("ACTIVE");
            role.setSystemRole(true);
            return roleRepository.save(role);
        });

        // Tenant A
        hotelA = new Hotel();
        hotelA.setCode("HTL-A");
        hotelA.setName("Hotel A");
        hotelA.setNameVi("Khach San A");
        hotelA.setAddressLine("123 Street A");
        hotelA.setProvinceId(1L);
        hotelA.setWardId(1L);
        hotelA.setCity("City A");
        hotelA.setCountry("Vietnam");
        hotelA.setStatus("ACTIVE");
        hotelA.setApprovalStatus("APPROVED");
        hotelA.setOperationStatus("ACTIVE");
        hotelA = hotelRepository.save(hotelA);

        staffA = new User();
        staffA.setUsername("staffA");
        staffA.setEmail("a@test.com");
        staffA.setPasswordHash("pass");
        staffA.setFullName("Staff A");
        staffA.setStatus("ACTIVE");
        staffA.setRoles(Set.of(ownerRole, housekeepingRole));
        staffA = userRepository.save(staffA);

        UserProperty upA = new UserProperty();
        upA.setUser(staffA);
        upA.setHotel(hotelA);
        upA.setStatus("ACTIVE");
        upA.setStartDate(LocalDateTime.now());
        upA.setIsPrimaryOwner(true);
        upA.setRelationshipType("OWNER");
        userPropertyRepository.save(upA);

        typeA = new RoomType();
        typeA.setHotel(hotelA);
        typeA.setCode("RT-A");
        typeA.setNameEn("Type A");
        typeA.setNameVi("Loai A");
        typeA.setMaxGuest(2);
        typeA.setMaxAdults(2);
        typeA.setMaxChildren(1);
        typeA.setMaxGuests(3);
        typeA.setBedType("KING");
        typeA.setBedCount(1);
        typeA.setBasePrice(BigDecimal.valueOf(100000));
        typeA.setStatus("ACTIVE");
        typeA = roomTypeRepository.save(typeA);

        // Tenant B
        hotelB = new Hotel();
        hotelB.setCode("HTL-B");
        hotelB.setName("Hotel B");
        hotelB.setNameVi("Khach San B");
        hotelB.setAddressLine("456 Street B");
        hotelB.setProvinceId(2L);
        hotelB.setWardId(2L);
        hotelB.setCity("City B");
        hotelB.setCountry("Vietnam");
        hotelB.setStatus("ACTIVE");
        hotelB.setApprovalStatus("APPROVED");
        hotelB.setOperationStatus("ACTIVE");
        hotelB = hotelRepository.save(hotelB);

        staffB = new User();
        staffB.setUsername("staffB");
        staffB.setEmail("b@test.com");
        staffB.setPasswordHash("pass");
        staffB.setFullName("Staff B");
        staffB.setStatus("ACTIVE");
        staffB.setRoles(Set.of(ownerRole, housekeepingRole));
        staffB = userRepository.save(staffB);

        UserProperty upB = new UserProperty();
        upB.setUser(staffB);
        upB.setHotel(hotelB);
        upB.setStatus("ACTIVE");
        upB.setStartDate(LocalDateTime.now());
        upB.setIsPrimaryOwner(true);
        upB.setRelationshipType("OWNER");
        userPropertyRepository.save(upB);

        typeB = new RoomType();
        typeB.setHotel(hotelB);
        typeB.setCode("RT-B");
        typeB.setNameEn("Type B");
        typeB.setNameVi("Loai B");
        typeB.setMaxGuest(2);
        typeB.setMaxAdults(2);
        typeB.setMaxChildren(1);
        typeB.setMaxGuests(3);
        typeB.setBedType("KING");
        typeB.setBedCount(1);
        typeB.setBasePrice(BigDecimal.valueOf(200000));
        typeB.setStatus("ACTIVE");
        typeB = roomTypeRepository.save(typeB);

        roomB = new Room();
        roomB.setHotel(hotelB);
        roomB.setRoomType(typeB);
        roomB.setRoomNumber("101");
<<<<<<< HEAD
        roomB.setStatus("DIRTY");
        roomB.setHousekeepingStatus("DIRTY");
=======
        roomB.setStatus("CLEANING");
        roomB.setHousekeepingStatus("CLEANING");
>>>>>>> codex/ui-functional-audit-polish
        roomB.setFloor(1);
        roomB.setMaintenanceStatus("NONE");
        roomB.setMaxGuests(3);
        roomB = roomRepository.save(roomB);

        Room roomA = new Room();
        roomA.setHotel(hotelA);
        roomA.setRoomType(typeA);
        roomA.setRoomNumber("A101");
        roomA.setStatus("CLEANING");
        roomA.setHousekeepingStatus("CLEANING");
        roomA.setFloor(1);
        roomA.setMaintenanceStatus("NONE");
        roomA.setMaxGuests(3);
        roomA = roomRepository.save(roomA);

        customerC = createUser("customerC", "c@test.com", "Customer C");
        customerD = createUser("customerD", "d@test.com", "Customer D");
        reservationD = createReservation(customerD, hotelB, "100000");
        reservationDWithoutInvoice = createReservation(customerD, hotelB, "200000");

        Invoice invoiceD = new Invoice();
        invoiceD.setInvoiceCode("INV-TENANT-D");
        invoiceD.setReservation(reservationD);
        invoiceD.setIssueDate(LocalDate.now());
        invoiceD.setTotalAmount(reservationD.getTotalAmount());
        invoiceD.setStatus("PENDING");
        invoiceRepository.save(invoiceD);

        taskA = new HousekeepingTask();
        taskA.setHotel(hotelA);
        taskA.setRoom(roomA);
        taskA.setStatus("IN_PROGRESS");
        taskA.setAssignedTo(staffA);
        taskA.setAssignedAt(LocalDateTime.now().minusMinutes(10));
        taskA.setStartedAt(LocalDateTime.now().minusMinutes(5));
        taskA = housekeepingTaskRepository.save(taskA);

        taskB = new HousekeepingTask();
        taskB.setHotel(hotelB);
        taskB.setRoom(roomB);
        taskB.setStatus("IN_PROGRESS");
        taskB.setAssignedTo(staffB);
        taskB.setAssignedAt(LocalDateTime.now().minusMinutes(10));
        taskB.setStartedAt(LocalDateTime.now().minusMinutes(5));
        taskB = housekeepingTaskRepository.save(taskB);
    }

    private User createUser(String username, String email, String fullName) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("pass");
        user.setFullName(fullName);
        user.setStatus("ACTIVE");
        return userRepository.save(user);
    }

    private Reservation createReservation(User user, Hotel hotel, String totalAmount) {
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setHotel(hotel);
        reservation.setCheckInDate(LocalDate.now().plusDays(1));
        reservation.setCheckOutDate(LocalDate.now().plusDays(3));
        reservation.setGuests(2);
        reservation.setStatus("PENDING");
        reservation.setTotalAmount(new BigDecimal(totalAmount));
        return reservationRepository.save(reservation);
    }

    private CustomUserDetails createUserDetails(User user, Hotel hotel) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("PROPERTY_OWNER"));
        authorities.add(new SimpleGrantedAuthority("HOUSEKEEPING"));

        Map<String, Integer> limits = new HashMap<>();
        limits.put("MAX_ROOMS", 100);
        limits.put("MAX_ROOM_TYPES", 100);

        Map<FunctionCode, Integer> permissions = new HashMap<>();
<<<<<<< HEAD
        permissions.put(FunctionCode.ROOM, ActionCode.UPDATE);
        permissions.put(FunctionCode.ROOM_TYPE, ActionCode.UPDATE);
=======
        permissions.put(FunctionCode.HOUSEKEEPING,
                ActionCode.VIEW | ActionCode.UPDATE | ActionCode.APPROVE);
>>>>>>> codex/ui-functional-audit-polish

        return new CustomUserDetails(
                user.getUsername(),
                user.getPasswordHash(),
                authorities,
                permissions,
                user.getId(),
                hotel.getId(),
                limits
        );
    }

    private CustomUserDetails createSuperAdminDetails() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("SUPER_ADMIN"));

        return new CustomUserDetails(
                "admin",
                "hash",
                authorities,
                new HashMap<>(),
                999L,
                null,
                new HashMap<>()
        );
    }

    private CustomUserDetails createCustomerDetails(User user) {
        Map<FunctionCode, Integer> permissions = new HashMap<>();
        permissions.put(FunctionCode.INVOICE, ActionCode.VIEW | ActionCode.CREATE);
        permissions.put(FunctionCode.FINANCE, ActionCode.VIEW);
        return new CustomUserDetails(
                user.getUsername(),
                user.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("CUSTOMER")),
                permissions,
                user.getId(),
                null,
                new HashMap<>()
        );
    }

    @Test
    void tenantACannotUpdateRoomOfTenantB() throws Exception {
        mockMvc.perform(put("/api/management/rooms/{id}", roomB.getId())
                        .with(user(createUserDetails(staffA, hotelA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomNumber":"101","roomTypeId":%d,"hotelId":%d,"floor":1}
                                """.formatted(typeB.getId(), hotelB.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void tenantACannotUpdateRoomTypeOfTenantB() throws Exception {
        mockMvc.perform(put("/api/management/room-types/{id}", typeB.getId())
                        .with(user(createUserDetails(staffA, hotelA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hotelId":%d,"code":"DELUXE-B","nameVi":"Deluxe B","basePrice":200000}
                                """.formatted(hotelB.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void tenantACannotCompleteHousekeepingOfTenantB() throws Exception {
        mockMvc.perform(post("/api/housekeeping/tasks/{taskId}/complete", taskB.getId())
                        .with(user(createUserDetails(staffA, hotelA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + taskB.getVersion() + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void customerCCannotReadInvoiceOfCustomerD() throws Exception {
        mockMvc.perform(get("/api/invoices/reservation/{reservationId}", reservationD.getId())
                        .with(user(createCustomerDetails(customerC))))
                .andExpect(status().isNotFound());
    }

    @Test
    void customerCCannotGenerateInvoiceForCustomerD() throws Exception {
        mockMvc.perform(post("/api/invoices/reservation/{reservationId}", reservationDWithoutInvoice.getId())
                        .with(user(createCustomerDetails(customerC))))
                .andExpect(status().isNotFound());
    }

    @Test
    void customerDReceivesConflictForOwnReservationBeforeInvoiceFinalization() throws Exception {
        mockMvc.perform(post("/api/invoices/reservation/{reservationId}", reservationDWithoutInvoice.getId())
                        .with(user(createCustomerDetails(customerD))))
                .andExpect(status().isConflict());
    }

    @Test
    void customerCReceivesNotFoundForNonexistentReservation() throws Exception {
        mockMvc.perform(post("/api/invoices/reservation/{reservationId}", Long.MAX_VALUE)
                        .with(user(createCustomerDetails(customerC))))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedInvoiceGenerationReturns401() throws Exception {
        mockMvc.perform(post("/api/invoices/reservation/{reservationId}", reservationDWithoutInvoice.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCCannotReadPaymentsOfCustomerD() throws Exception {
        mockMvc.perform(get("/api/payments/reservation/{reservationId}", reservationD.getId())
                        .with(user(createCustomerDetails(customerC))))
                .andExpect(status().isNotFound());
    }

    @Test
    void customerCCannotCreatePaymentUrlForCustomerD() throws Exception {
        mockMvc.perform(get("/api/payments/create-url")
                        .param("reservationId", reservationD.getId().toString())
                        .param("method", "MOMO")
                        .header("Idempotency-Key", "tenant-isolation-customer-c-payment")
                        .with(user(createCustomerDetails(customerC))))
                .andExpect(status().isNotFound());
    }

    @Test
    void tenantACanCompleteOwnHousekeepingTask() throws Exception {
        String request = "{\"expectedVersion\":" + taskA.getVersion() + "}";
        mockMvc.perform(post("/api/housekeeping/tasks/{taskId}/complete", taskA.getId())
                        .with(user(createUserDetails(staffA, hotelA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/housekeeping/tasks/{taskId}/complete", taskA.getId())
                        .with(user(createUserDetails(staffA, hotelA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());
    }

    @Test
    void superAdminCannotBypassHousekeepingTaskOwnership() throws Exception {
        mockMvc.perform(post("/api/housekeeping/tasks/{taskId}/complete", taskB.getId())
                        .with(user(createSuperAdminDetails()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + taskB.getVersion() + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void unauthenticatedManagementRequestReturns401() throws Exception {
        mockMvc.perform(put("/api/management/rooms/{id}", roomB.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
