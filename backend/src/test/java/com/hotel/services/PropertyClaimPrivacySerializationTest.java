package com.hotel.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.hotel.dtos.PropertyClaimResponseDTO;
import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyClaimRequest;
import com.hotel.entities.Role;
import com.hotel.entities.User;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyClaimPrivacySerializationTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void userCredentialsAreWriteOnlyDuringSerialization() throws Exception {
        User user = user();

        String json = objectMapper.writeValueAsString(user);

        assertFalse(json.contains("passwordHash"));
        assertFalse(json.contains("secret-hash"));
    }

    @Test
    void claimEntityCannotSerializeItsJpaRelationshipGraph() throws Exception {
        User user = user();
        Hotel hotel = new Hotel();
        hotel.setId(17L);
        hotel.setName("Safe Hotel");
        PropertyClaimRequest claim = new PropertyClaimRequest();
        claim.setId(81L);
        claim.setProperty(hotel);
        claim.setRequesterUser(user);
        claim.setReviewedBy(user);
        claim.setStatus("PENDING");

        String json = objectMapper.writeValueAsString(claim);

        assertFalse(json.contains("requesterUser"));
        assertFalse(json.contains("reviewedBy"));
        assertFalse(json.contains("property"));
        assertFalse(json.contains("secret-hash"));
    }

    @Test
    void explicitClaimDtoContainsOnlyApprovedSummaryFields() throws Exception {
        PropertyClaimResponseDTO response = new PropertyClaimResponseDTO(
                81L,
                new PropertyClaimResponseDTO.PropertySummary(
                        17L, "HOTEL-17", "Safe Hotel", "PENDING_APPROVAL", "INACTIVE"),
                new PropertyClaimResponseDTO.UserSummary(
                        42L, "owner", "owner@example.com", "Owner"),
                "EMAIL",
                "owner@example.com",
                "Please verify",
                "PENDING",
                null,
                null,
                null,
                null);

        String json = objectMapper.writeValueAsString(response);

        assertTrue(json.contains("requesterUser"));
        assertFalse(json.contains("passwordHash"));
        assertFalse(json.contains("roles"));
        assertFalse(json.contains("userProperties"));
        assertFalse(json.contains("subscriptions"));
    }

    private User user() {
        Role role = new Role();
        role.setCode("PROPERTY_OWNER");
        User user = new User();
        user.setId(42L);
        user.setUsername("owner");
        user.setEmail("owner@example.com");
        user.setPasswordHash("secret-hash");
        user.setRoles(Set.of(role));
        return user;
    }
}
