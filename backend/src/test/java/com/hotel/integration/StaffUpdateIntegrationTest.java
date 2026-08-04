package com.hotel.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.BackendApplication;
import com.hotel.entities.Hotel;
import com.hotel.entities.Role;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.OperationalAuditEventRepository;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.services.PropertyAccessService;
import com.hotel.services.PropertySubscriptionEntitlementService;
import com.hotel.services.SubscriptionFeatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = BackendApplication.class,
        properties = "payment.property.encryption-key=test-property-payment-encryption-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StaffUpdateIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserPropertyRepository userPropertyRepository;
    @Autowired private OperationalAuditEventRepository auditRepository;

    @MockBean private PropertyAccessService propertyAccessService;
    @MockBean private PropertySubscriptionEntitlementService propertyEntitlementService;
    @MockBean private SubscriptionFeatureService subscriptionFeatureService;

    private User owner;
    private User staff;
    private Hotel sourceProperty;
    private Hotel targetProperty;
    private Hotel foreignProperty;
    private Role receptionist;
    private Role housekeeping;
    private Role administrator;
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = Long.toUnsignedString(System.nanoTime(), 36);
        owner = persistedUser("update-owner-" + suffix, Set.of());
        sourceProperty = hotel("Update Source " + suffix, "update-source-" + suffix);
        targetProperty = hotel("Update Target " + suffix, "update-target-" + suffix);
        foreignProperty = hotel("Update Foreign " + suffix, "update-foreign-" + suffix);
        receptionist = role("RECEPTIONIST", "Receptionist", "ACTIVE");
        housekeeping = role("HOUSEKEEPING", "Housekeeping", "ACTIVE");
        administrator = role("ADMIN", "Administrator", "ACTIVE");
        staff = persistedUser("update-staff-" + suffix, Set.of(receptionist), sourceProperty);
        assignment(staff, sourceProperty, "ACTIVE");

        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(propertyAccessService.accessibleHotelIds())
                .thenReturn(Set.of(sourceProperty.getId(), targetProperty.getId()));
        when(propertyAccessService.requireManagedHotel(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            if (sourceProperty.getId().equals(id)) return sourceProperty;
            if (targetProperty.getId().equals(id)) return targetProperty;
            throw new SecurityException("Property access denied");
        });
        when(propertyEntitlementService.getCurrentForUpdate(targetProperty.getId())).thenReturn(
                PropertySubscriptionEntitlementService.EntitlementView.none(targetProperty.getId(), "TEST"));
    }

    @Test
    void updatesProfileAndRoleWithoutRewritingTheExistingAssignment() throws Exception {
        mockMvc.perform(put("/api/users/staff/{id}", staff.getId())
                        .with(user(principal()))
                        .contentType("application/json")
                        .content(updatePayload(sourceProperty.getId(), null, Set.of(housekeeping.getId()), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Staff"))
                .andExpect(jsonPath("$.roles[0].code").value("HOUSEKEEPING"));

        User updated = userRepository.findById(staff.getId()).orElseThrow();
        assertThat(updated.getFullName()).isEqualTo("Updated Staff");
        assertThat(updated.getPhone()).isEqualTo("0901000000");
        assertThat(updated.getRoles()).extracting(Role::getCode).containsExactly("HOUSEKEEPING");
        assertThat(userPropertyRepository.findByUserIdAndRelationshipType(staff.getId(), "STAFF"))
                .singleElement()
                .satisfies(item -> assertThat(item.getStatus()).isEqualTo("ACTIVE"));
    }

    @Test
    void movesStaffByClosingTheOldAssignmentAndCreatingANewPeriod() throws Exception {
        mockMvc.perform(put("/api/users/staff/{id}", staff.getId())
                        .with(user(principal()))
                        .contentType("application/json")
                        .content(updatePayload(targetProperty.getId(), "Operational transfer",
                                Set.of(receptionist.getId()), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hotel.id").value(targetProperty.getId()));

        var assignments = userPropertyRepository
                .findByUserIdAndRelationshipTypeOrderByStartDateDesc(staff.getId(), "STAFF");
        assertThat(assignments).hasSize(2);
        assertThat(assignments)
                .filteredOn(item -> sourceProperty.getId().equals(item.getHotel().getId()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getStatus()).isEqualTo("INACTIVE");
                    assertThat(item.getEndDate()).isNotNull();
                    assertThat(item.getStatusReason()).isEqualTo("Operational transfer");
                });
        assertThat(assignments)
                .filteredOn(item -> targetProperty.getId().equals(item.getHotel().getId()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getStatus()).isEqualTo("ACTIVE");
                    assertThat(item.getStatusReason()).isEqualTo("Operational transfer");
                });
        assertThat(auditRepository.findAll().stream()
                .filter(event -> "STAFF_PROPERTY_MOVED".equals(event.getEventType()))
                .filter(event -> String.valueOf(staff.getId()).equals(event.getAggregateId()))
                .toList())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getActorId()).isEqualTo(owner.getId());
                    assertThat(event.getReason()).isEqualTo("Operational transfer");
                    assertThat(event.getBeforeStateJson()).contains("assignments", "version");
                    assertThat(event.getAfterStateJson()).contains("assignments", "version");
                });
        verify(subscriptionFeatureService).checkFeatureLimitForProperty(
                targetProperty.getId(), "MAX_STAFF", 0L, 1L);
    }

    @Test
    void hidesAForeignStaffAccountAsNotFound() throws Exception {
        User foreignStaff = persistedUser(
                "foreign-update-staff-" + suffix, Set.of(receptionist), foreignProperty);
        assignment(foreignStaff, foreignProperty, "ACTIVE");

        mockMvc.perform(put("/api/users/staff/{id}", foreignStaff.getId())
                        .with(user(principal()))
                        .contentType("application/json")
                        .content(updatePayload(sourceProperty.getId(), "Unauthorized transfer",
                                Set.of(receptionist.getId()), null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(userRepository.findById(foreignStaff.getId()).orElseThrow().getHotel().getId())
                .isEqualTo(foreignProperty.getId());
    }

    @Test
    void rejectsForbiddenRoleAndShortReplacementPassword() throws Exception {
        mockMvc.perform(put("/api/users/staff/{id}", staff.getId())
                        .with(user(principal()))
                        .contentType("application/json")
                        .content(updatePayload(sourceProperty.getId(), null, Set.of(administrator.getId()), null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(put("/api/users/staff/{id}", staff.getId())
                        .with(user(principal()))
                        .contentType("application/json")
                        .content(updatePayload(sourceProperty.getId(), null, Set.of(receptionist.getId()), "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void requiresAReasonBeforeMovingProperties() throws Exception {
        mockMvc.perform(put("/api/users/staff/{id}", staff.getId())
                        .with(user(principal()))
                        .contentType("application/json")
                        .content(updatePayload(targetProperty.getId(), null, Set.of(receptionist.getId()), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(userPropertyRepository.findByUserIdAndRelationshipType(staff.getId(), "STAFF"))
                .singleElement()
                .satisfies(item -> assertThat(item.getStatus()).isEqualTo("ACTIVE"));
    }

    @Test
    void blocksPropertyOwnerRoleChangesForAStaffAccountSharedWithAForeignProperty() throws Exception {
        assignment(staff, foreignProperty, "ACTIVE");

        mockMvc.perform(put("/api/users/staff/{id}", staff.getId())
                        .with(user(principal()))
                        .contentType("application/json")
                        .content(updatePayload(sourceProperty.getId(), null, Set.of(housekeeping.getId()), null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(userRepository.findById(staff.getId()).orElseThrow().getRoles())
                .extracting(Role::getCode)
                .containsExactly("RECEPTIONIST");
    }

    @Test
    void lifecycleMutationRequiresAnExpectedVersion() throws Exception {
        mockMvc.perform(post("/api/users/{id}/deactivate", staff.getId())
                        .with(user(principal()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "hotelId", sourceProperty.getId(),
                                "reason", "End of assignment"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.expectedVersion").exists());

        assertThat(userRepository.findById(staff.getId()).orElseThrow().getStatus()).isEqualTo("ACTIVE");
        assertThat(userPropertyRepository.findByUserIdAndRelationshipType(staff.getId(), "STAFF"))
                .singleElement()
                .satisfies(item -> assertThat(item.getStatus()).isEqualTo("ACTIVE"));
    }

    private byte[] updatePayload(Long hotelId, String reason, Set<Long> roleIds, String password) throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("fullName", "  Updated   Staff  ");
        payload.put("phone", "0901000000");
        payload.put("roleIds", roleIds);
        payload.put("hotelId", hotelId);
        payload.put("expectedVersion", staff.getVersion());
        payload.put("changeReason", reason == null ? "Staff profile governance update" : reason);
        if (reason != null) payload.put("assignmentReason", reason);
        if (password != null) payload.put("password", password);
        return objectMapper.writeValueAsBytes(payload);
    }

    private CustomUserDetails principal() {
        Map<FunctionCode, Integer> permissions = new HashMap<>();
        permissions.put(FunctionCode.USER, ActionCode.UPDATE | ActionCode.DELETE);
        return new CustomUserDetails(
                owner.getUsername(), owner.getPasswordHash(),
                Set.of(new SimpleGrantedAuthority("PROPERTY_OWNER")), permissions,
                owner.getId(), null, Map.of());
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

    private Role role(String code, String name, String status) {
        Role role = roleRepository.findByCode(code).orElseGet(Role::new);
        role.setCode(code);
        role.setName(name);
        role.setStatus(status);
        if (role.getSystemRole() == null) role.setSystemRole(true);
        return roleRepository.saveAndFlush(role);
    }

    private UserProperty assignment(User user, Hotel hotel, String status) {
        UserProperty assignment = new UserProperty();
        assignment.setUser(user);
        assignment.setHotel(hotel);
        assignment.setRelationshipType("STAFF");
        assignment.setStatus(status);
        assignment.setIsPrimaryOwner(false);
        assignment.setStartDate(LocalDateTime.now());
        return userPropertyRepository.saveAndFlush(assignment);
    }
}
