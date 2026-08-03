package com.hotel.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.BackendApplication;
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
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = BackendApplication.class,
        properties = "payment.property.encryption-key=test-property-payment-encryption-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffCreationRollbackIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;

    @MockBean private UserPropertyRepository userPropertyRepository;
    @MockBean private PropertyAccessService propertyAccessService;

    private Hotel property;
    private Role receptionist;
    private String username;

    @BeforeEach
    void setUp() {
        String suffix = Long.toUnsignedString(System.nanoTime(), 36);
        username = "rollback-staff-" + suffix;
        property = new Hotel();
        property.setName("Rollback Property " + suffix);
        property.setCode("rollback-property-" + suffix);
        property.setSlug("rollback-property-" + suffix);
        property.setAddressLine("1 Test Street");
        property.setCity("Da Nang");
        property.setCountry("Vietnam");
        property.setStatus("ACTIVE");
        property.setApprovalStatus("APPROVED");
        property.setOperationStatus("ACTIVE");
        property = hotelRepository.saveAndFlush(property);

        receptionist = roleRepository.findByCode("RECEPTIONIST").orElseThrow();
        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
        when(userPropertyRepository.save(any(UserProperty.class)))
                .thenThrow(new DataIntegrityViolationException("forced assignment failure"));
    }

    @AfterEach
    void cleanUp() {
        userRepository.findByUsername(username).ifPresent(userRepository::delete);
        if (property != null && property.getId() != null && hotelRepository.existsById(property.getId())) {
            hotelRepository.deleteById(property.getId());
        }
    }

    @Test
    void assignmentFailureRollsBackThePreviouslyFlushedUser() throws Exception {
        mockMvc.perform(post("/api/users/staff")
                        .with(user(principal()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "username", username,
                                "email", username + "@example.test",
                                "password", "StrongPass1",
                                "fullName", "Rollback Staff",
                                "roleIds", Set.of(receptionist.getId()),
                                "hotelId", property.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT"));

        assertThat(userRepository.findByUsername(username)).isEmpty();
    }

    private CustomUserDetails principal() {
        Map<FunctionCode, Integer> permissions = new HashMap<>();
        permissions.put(FunctionCode.USER, ActionCode.CREATE);
        return new CustomUserDetails(
                "system-admin", "test-hash",
                Set.of(new SimpleGrantedAuthority("SUPER_ADMIN")), permissions,
                1L, null, Map.of());
    }
}
