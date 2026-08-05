package com.hotel.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.BackendApplication;
import com.hotel.entities.Hotel;
import com.hotel.entities.Role;
import com.hotel.entities.User;
import com.hotel.repositories.HotelRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = BackendApplication.class,
        properties = "payment.property.encryption-key=test-property-payment-encryption-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StaffCreationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserPropertyRepository userPropertyRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockBean private PropertyAccessService propertyAccessService;
    @MockBean private PropertySubscriptionEntitlementService propertyEntitlementService;
    @MockBean private SubscriptionFeatureService subscriptionFeatureService;

    private User owner;
    private Hotel property;
    private Role receptionist;
    private Role administrator;
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = Long.toUnsignedString(System.nanoTime(), 36);
        owner = persistUser("staff-owner-" + suffix, "staff-owner-" + suffix + "@example.test");
        property = persistHotel("Staff Property " + suffix, "staff-property-" + suffix);
        receptionist = role("RECEPTIONIST", "Receptionist", "ACTIVE");
        administrator = role("ADMIN", "Administrator", "ACTIVE");

        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.currentUser()).thenReturn(owner);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(property.getId()));
        when(propertyAccessService.requireManagedHotel(property.getId())).thenReturn(property);
        when(propertyAccessService.requireManagedHotel(anyLong())).thenAnswer(invocation -> {
            Long hotelId = invocation.getArgument(0);
            if (property.getId().equals(hotelId)) return property;
            throw new SecurityException("Property access denied");
        });
        when(propertyEntitlementService.getCurrentForUpdate(property.getId())).thenReturn(
                PropertySubscriptionEntitlementService.EntitlementView.none(property.getId(), "TEST"));
    }

    @Test
    void createsNormalizedStaffAndPersistsTheTargetPropertyAssignment() throws Exception {
        String rawUsername = "Ｓtaff." + suffix;
        String normalizedUsername = "staff." + suffix;
        String rawEmail = "Staff." + suffix + "@EXAMPLE.TEST";

        MvcResult result = mockMvc.perform(post("/api/users/staff")
                        .with(user(principal()))
                        .contentType("application/json")
                        .content(staffPayload(rawUsername, rawEmail, "StrongPass1", receptionist.getId(), property.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(normalizedUsername))
                .andExpect(jsonPath("$.email").value(rawEmail.toLowerCase()))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        User created = userRepository.findById(body.path("id").asLong()).orElseThrow();
        assertThat(passwordEncoder.matches("StrongPass1", created.getPasswordHash())).isTrue();
        assertThat(created.getFullName()).isEqualTo("New Staff");
        assertThat(userPropertyRepository.findByUserIdAndRelationshipType(created.getId(), "STAFF"))
                .singleElement()
                .satisfies(assignment -> {
                    assertThat(assignment.getHotel().getId()).isEqualTo(property.getId());
                    assertThat(assignment.getStatus()).isEqualTo("ACTIVE");
                    assertThat(assignment.getStatusChangedBy().getId()).isEqualTo(owner.getId());
                });
    }

    @Test
    void rejectsInvalidInitialPasswordBeforeAnyPersistence() throws Exception {
        String username = "short-password-" + suffix;

        mockMvc.perform(post("/api/users/staff")
                        .with(user(principal()))
                        .contentType("application/json")
                        .content(staffPayload(username, username + "@example.test", "short", receptionist.getId(), property.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        assertThat(userRepository.existsByUsernameIgnoreCase(username)).isFalse();
    }

    @Test
    void returnsStableConflictForCaseInsensitiveDuplicateUsername() throws Exception {
        String username = "duplicate-" + suffix;
        persistUser(username, "existing-" + suffix + "@example.test");

        mockMvc.perform(post("/api/users/staff")
                        .with(user(principal()))
                        .contentType("application/json")
                        .content(staffPayload(username.toUpperCase(), "new-" + suffix + "@example.test",
                                "StrongPass1", receptionist.getId(), property.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.fieldErrors.username").exists());
    }

    @Test
    void returnsStableConflictForCaseInsensitiveDuplicateEmail() throws Exception {
        String email = "duplicate-email-" + suffix + "@example.test";
        persistUser("existing-email-" + suffix, email);

        mockMvc.perform(post("/api/users/staff")
                        .with(user(principal()))
                        .contentType("application/json")
                        .content(staffPayload("new-email-" + suffix, email.toUpperCase(),
                                "StrongPass1", receptionist.getId(), property.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void rejectsAForbiddenRoleWithoutCreatingTheAccount() throws Exception {
        String username = "forbidden-role-" + suffix;

        mockMvc.perform(post("/api/users/staff")
                        .with(user(principal()))
                        .contentType("application/json")
                        .content(staffPayload(username, username + "@example.test", "StrongPass1",
                                administrator.getId(), property.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(userRepository.existsByUsernameIgnoreCase(username)).isFalse();
    }

    @Test
    void rejectsCrossPropertyAssignmentWithoutCreatingTheAccount() throws Exception {
        String username = "foreign-property-" + suffix;

        mockMvc.perform(post("/api/users/staff")
                        .with(user(principal()))
                        .contentType("application/json")
                        .content(staffPayload(username, username + "@example.test", "StrongPass1",
                                receptionist.getId(), property.getId() + 9999)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(userRepository.existsByUsernameIgnoreCase(username)).isFalse();
    }

    @Test
    void roleCatalogReturnsOnlyActiveAssignableStaffRoles() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/staff/roles").with(user(principal())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(findRole(body, "RECEPTIONIST")).isNotNull();
        assertThat(findRole(body, "ADMIN")).isNull();
        assertThat(findRole(body, "CUSTOMER")).isNull();
    }

    private byte[] staffPayload(String username, String email, String password, Long roleId, Long hotelId)
            throws Exception {
        return objectMapper.writeValueAsBytes(Map.of(
                "username", username,
                "email", email,
                "password", password,
                "fullName", "  New   Staff  ",
                "phone", "0901000000",
                "roleIds", Set.of(roleId),
                "hotelId", hotelId));
    }

    private CustomUserDetails principal() {
        Map<FunctionCode, Integer> permissions = new HashMap<>();
        permissions.put(FunctionCode.USER, ActionCode.VIEW | ActionCode.CREATE);
        return new CustomUserDetails(
                owner.getUsername(), owner.getPasswordHash(),
                Set.of(new SimpleGrantedAuthority("PROPERTY_OWNER")), permissions,
                owner.getId(), null, Map.of());
    }

    private User persistUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("test-hash");
        user.setFullName("Fixture User");
        user.setStatus("ACTIVE");
        return userRepository.saveAndFlush(user);
    }

    private Hotel persistHotel(String name, String code) {
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

    private JsonNode findRole(JsonNode roles, String code) {
        for (JsonNode role : roles) {
            if (code.equals(role.path("code").asText())) return role;
        }
        return null;
    }
}
