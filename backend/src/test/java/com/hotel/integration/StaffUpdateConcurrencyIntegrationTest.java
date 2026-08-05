package com.hotel.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.BackendApplication;
import com.hotel.dtos.StaffUpdateRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.Role;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.services.OperationalAuditService;
import com.hotel.services.PropertyAccessService;
import com.hotel.services.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = BackendApplication.class,
        properties = "payment.property.encryption-key=test-property-payment-encryption-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffUpdateConcurrencyIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserService userService;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @SpyBean private UserPropertyRepository userPropertyRepository;

    @MockBean private PropertyAccessService propertyAccessService;
    @MockBean private OperationalAuditService operationalAuditService;

    private User owner;
    private User staff;
    private Hotel sourceProperty;
    private Hotel firstTarget;
    private Hotel secondTarget;
    private Role receptionist;

    @BeforeEach
    void setUp() {
        String suffix = Long.toUnsignedString(System.nanoTime(), 36);
        receptionist = roleRepository.findByCode("RECEPTIONIST").orElseThrow();
        owner = persistedUser("move-owner-" + suffix, Set.of());
        sourceProperty = hotel("Move Source " + suffix, "move-source-" + suffix);
        firstTarget = hotel("Move Target A " + suffix, "move-target-a-" + suffix);
        secondTarget = hotel("Move Target B " + suffix, "move-target-b-" + suffix);
        staff = persistedUser("move-staff-" + suffix, Set.of(receptionist), sourceProperty);
        assignment(staff, sourceProperty);

        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
        when(propertyAccessService.currentUser()).thenReturn(owner);
    }

    @AfterEach
    void cleanUp() {
        if (staff != null && staff.getId() != null) {
            userPropertyRepository.deleteAll(userPropertyRepository.findByUserId(staff.getId()));
            userRepository.findById(staff.getId()).ifPresent(userRepository::delete);
        }
        if (owner != null && owner.getId() != null) {
            userRepository.findById(owner.getId()).ifPresent(userRepository::delete);
        }
        for (Hotel hotel : new Hotel[]{sourceProperty, firstTarget, secondTarget}) {
            if (hotel != null && hotel.getId() != null && hotelRepository.existsById(hotel.getId())) {
                hotelRepository.deleteById(hotel.getId());
            }
        }
    }

    @Test
    void concurrentMovesSerializeAndLeaveExactlyOneActiveAssignment() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Throwable> first = executor.submit(() -> moveAfter(start, firstTarget, "Concurrent move A"));
            Future<Throwable> second = executor.submit(() -> moveAfter(start, secondTarget, "Concurrent move B"));
            start.countDown();
            assertThat(java.util.Arrays.asList(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .filteredOn(java.util.Objects::nonNull)
                    .singleElement()
                    .isInstanceOf(OptimisticLockingFailureException.class);
        } finally {
            executor.shutdownNow();
        }

        assertThat(userPropertyRepository.findByUserIdAndRelationshipType(staff.getId(), "STAFF"))
                .hasSize(2)
                .filteredOn(item -> "ACTIVE".equals(item.getStatus()))
                .singleElement();
    }

    @Test
    void assignmentInsertFailureRollsBackProfileRolesAndSourceAssignment() throws Exception {
        doThrow(new DataIntegrityViolationException("forced move assignment failure"))
                .when(userPropertyRepository)
                .saveAndFlush(argThat(item -> item != null
                        && item.getId() == null
                        && item.getHotel() != null
                        && firstTarget.getId().equals(item.getHotel().getId())));

        mockMvc.perform(put("/api/users/staff/{id}", staff.getId())
                        .with(user(principal()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "fullName", "Changed Before Failure",
                                "roleIds", Set.of(receptionist.getId()),
                                "hotelId", firstTarget.getId(),
                                "assignmentReason", "Rollback move",
                                "expectedVersion", staff.getVersion(),
                                "changeReason", "Rollback move audit"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT"));

        User reloaded = userRepository.findById(staff.getId()).orElseThrow();
        assertThat(reloaded.getFullName()).isEqualTo("Original Staff");
        assertThat(reloaded.getHotel().getId()).isEqualTo(sourceProperty.getId());
        assertThat(userPropertyRepository.findByUserIdAndRelationshipType(staff.getId(), "STAFF"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getHotel().getId()).isEqualTo(sourceProperty.getId());
                    assertThat(item.getStatus()).isEqualTo("ACTIVE");
                    assertThat(item.getEndDate()).isNull();
                });
    }

    private Throwable moveAfter(CountDownLatch start, Hotel target, String reason) {
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start latch timed out");
            userService.updateStaff(staff.getId(), updateRequest(target.getId(), reason));
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new IllegalStateException(exception);
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private StaffUpdateRequest updateRequest(Long hotelId, String reason) {
        StaffUpdateRequest request = new StaffUpdateRequest();
        request.setFullName("Original Staff");
        request.setRoleIds(Set.of(receptionist.getId()));
        request.setHotelId(hotelId);
        request.setAssignmentReason(reason);
        request.setExpectedVersion(staff.getVersion());
        request.setChangeReason(reason);
        return request;
    }

    private CustomUserDetails principal() {
        Map<FunctionCode, Integer> permissions = new HashMap<>();
        permissions.put(FunctionCode.USER, ActionCode.UPDATE);
        return new CustomUserDetails(
                "system-admin", "test-hash",
                Set.of(new SimpleGrantedAuthority("SUPER_ADMIN")), permissions,
                1L, null, Map.of());
    }

    private User persistedUser(String username, Set<Role> roles) {
        return persistedUser(username, roles, null);
    }

    private User persistedUser(String username, Set<Role> roles, Hotel hotel) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setPasswordHash("test-hash");
        user.setFullName("Original Staff");
        user.setStatus("ACTIVE");
        user.setRoles(new java.util.HashSet<>(roles));
        user.setHotel(hotel);
        return userRepository.saveAndFlush(user);
    }

    private Hotel hotel(String name, String code) {
        Hotel hotel = new Hotel();
        hotel.setName(name);
        hotel.setCode(code);
        hotel.setSlug(code);
        hotel.setAddressLine("1 Test Street");
        hotel.setCity("Da Nang");
        hotel.setCountry("Vietnam");
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        return hotelRepository.saveAndFlush(hotel);
    }

    private UserProperty assignment(User user, Hotel hotel) {
        UserProperty assignment = new UserProperty();
        assignment.setUser(user);
        assignment.setHotel(hotel);
        assignment.setRelationshipType("STAFF");
        assignment.setStatus("ACTIVE");
        assignment.setIsPrimaryOwner(false);
        assignment.setStartDate(LocalDateTime.now());
        return userPropertyRepository.saveAndFlush(assignment);
    }
}
