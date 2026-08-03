package com.hotel.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.BackendApplication;
import com.hotel.entities.Hotel;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = BackendApplication.class,
        properties = "payment.property.encryption-key=test-property-payment-encryption-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StaffReadTenantIsolationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserPropertyRepository userPropertyRepository;

    private User owner;
    private User localStaff;
    private User sharedStaff;
    private User foreignStaff;
    private Hotel localProperty;
    private Hotel secondLocalProperty;
    private Hotel foreignProperty;

    @BeforeEach
    void setUp() {
        String suffix = Long.toUnsignedString(System.nanoTime(), 36);
        owner = persistUser("owner-" + suffix, "owner-" + suffix + "@example.test", "Property Owner");
        User foreignOwner = persistUser(
                "foreign-owner-" + suffix,
                "foreign-owner-" + suffix + "@example.test",
                "Foreign Owner");
        localProperty = hotel("Scoped Alpha " + suffix, "alpha-" + suffix);
        secondLocalProperty = hotel("Scoped Beta " + suffix, "beta-" + suffix);
        foreignProperty = hotel("Foreign Gamma " + suffix, "gamma-" + suffix);

        assignment(owner, localProperty, "OWNER", "ACTIVE");
        assignment(owner, secondLocalProperty, "OWNER", "ACTIVE");
        assignment(foreignOwner, foreignProperty, "OWNER", "ACTIVE");

        localStaff = persistUser(
                "local-staff-" + suffix,
                "local-staff-" + suffix + "@example.test",
                "Local Staff");
        localStaff.setPhone("0901000001");
        localStaff.setHotel(localProperty);
        userRepository.save(localStaff);
        assignment(localStaff, localProperty, "STAFF", "ACTIVE");

        sharedStaff = persistUser(
                "shared-staff-" + suffix,
                "shared-staff-" + suffix + "@example.test",
                "Shared Staff");
        sharedStaff.setHotel(foreignProperty);
        userRepository.save(sharedStaff);
        assignment(sharedStaff, secondLocalProperty, "STAFF", "ACTIVE");
        assignment(sharedStaff, foreignProperty, "STAFF", "INACTIVE");

        foreignStaff = persistUser(
                "foreign-staff-" + suffix,
                "foreign-staff-" + suffix + "@example.test",
                "Foreign Staff");
        foreignStaff.setHotel(foreignProperty);
        userRepository.save(foreignStaff);
        assignment(foreignStaff, foreignProperty, "STAFF", "ACTIVE");
    }

    @Test
    void staffListScopesMultiplePropertiesAndReturnsOnlyScreenFields() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/staff").with(user(principal())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        JsonNode local = findByUsername(body, localStaff.getUsername());
        JsonNode shared = findByUsername(body, sharedStaff.getUsername());

        assertThat(local).isNotNull();
        assertThat(shared).isNotNull();
        assertThat(findByUsername(body, foreignStaff.getUsername())).isNull();
        assertThat(shared.path("staffAssignments")).hasSize(1);
        assertThat(shared.path("staffAssignments").get(0).path("hotelId").asLong())
                .isEqualTo(secondLocalProperty.getId());
        assertThat(shared.path("hotel").isNull()).isTrue();

        assertThat(local.has("phone")).isTrue();
        assertThat(local.has("roles")).isTrue();
        assertThat(local.has("points")).isFalse();
        assertThat(local.has("emailVerifiedAt")).isFalse();
        assertThat(local.has("pendingEmail")).isFalse();
        assertThat(local.has("assignedProperties")).isFalse();
        assertThat(local.has("subscriptionStatus")).isFalse();
    }

    @Test
    void staffDetailHidesForeignPropertyAccount() throws Exception {
        mockMvc.perform(get("/api/users/{id}", foreignStaff.getId()).with(user(principal())))
                .andExpect(status().isNotFound());
    }

    @Test
    void propertyOptionsContainOnlyAuthorizedPropertiesAndMinimalFields() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/staff/properties").with(user(principal())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body).hasSize(2);
        assertThat(findById(body, localProperty.getId())).isNotNull();
        assertThat(findById(body, secondLocalProperty.getId())).isNotNull();
        assertThat(findById(body, foreignProperty.getId())).isNull();
        body.forEach(option -> assertThat(option.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("id", "name"));
    }

    private CustomUserDetails principal() {
        Map<FunctionCode, Integer> permissions = new HashMap<>();
        permissions.put(FunctionCode.USER, ActionCode.VIEW);
        return new CustomUserDetails(
                owner.getUsername(),
                owner.getPasswordHash(),
                Set.of(new SimpleGrantedAuthority("PROPERTY_OWNER")),
                permissions,
                owner.getId(),
                null,
                Map.of());
    }

    private User persistUser(String username, String email, String fullName) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("test-hash");
        user.setFullName(fullName);
        user.setStatus("ACTIVE");
        return userRepository.save(user);
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
        return hotelRepository.save(hotel);
    }

    private void assignment(User user, Hotel hotel, String relationshipType, String status) {
        UserProperty assignment = new UserProperty();
        assignment.setUser(user);
        assignment.setHotel(hotel);
        assignment.setRelationshipType(relationshipType);
        assignment.setStatus(status);
        assignment.setIsPrimaryOwner("OWNER".equals(relationshipType));
        assignment.setStartDate(LocalDateTime.now());
        userPropertyRepository.save(assignment);
    }

    private JsonNode findByUsername(JsonNode array, String username) {
        for (JsonNode item : array) {
            if (username.equals(item.path("username").asText())) return item;
        }
        return null;
    }

    private JsonNode findById(JsonNode array, Long id) {
        for (JsonNode item : array) {
            if (id.equals(item.path("id").asLong())) return item;
        }
        return null;
    }
}
