package com.hotel.services;

import com.hotel.controllers.AdminPartnerController;
import com.hotel.entities.Hotel;
import com.hotel.entities.OperationalAuditEvent;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@Import(PropertyApprovalWorkflowService.class)
class PropertyApprovalWorkflowPersistenceTest {

    @Autowired private PropertyApprovalWorkflowService workflowService;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserPropertyRepository userPropertyRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockBean
    private OperationalAuditService operationalAuditService;

    @Test
    void submittedDraftBecomesActionableInAdminApprovalQueue() {
        SeedData seed = seedDraft("queue-owner@example.test");
        AdminPartnerController adminController = new AdminPartnerController(jdbcTemplate);
        when(operationalAuditService.append(any())).thenReturn(auditEvent(seed, LocalDateTime.of(2026, 8, 4, 4, 0)));

        assertTrue(adminController.approvals().isEmpty());

        workflowService.submitDraft(seed.userId(), seed.propertyId());

        var queue = adminController.approvals();
        assertEquals(1, queue.size());
        assertTrue(queue.getFirst().values().contains(seed.propertyId()));
        assertTrue(queue.getFirst().values().contains("PENDING_APPROVAL"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void auditFailureRollsBackPersistedPropertyTransition() {
        SeedData seed = seedDraft("rollback-owner@example.test");
        doThrow(new IllegalStateException("audit unavailable"))
                .when(operationalAuditService).append(any());

        assertThrows(IllegalStateException.class,
                () -> workflowService.submitDraft(seed.userId(), seed.propertyId()));

        Hotel reloaded = hotelRepository.findById(seed.propertyId()).orElseThrow();
        assertEquals("DRAFT", reloaded.getStatus());
        assertEquals("DRAFT", reloaded.getApprovalStatus());
        assertEquals("INACTIVE", reloaded.getOperationStatus());
    }

    private SeedData seedDraft(String email) {
        User owner = new User();
        owner.setUsername(email);
        owner.setEmail(email);
        owner.setPasswordHash("hash");
        owner.setFullName("Queue Owner");
        owner.setStatus("ACTIVE");
        owner = userRepository.saveAndFlush(owner);

        Hotel property = new Hotel();
        property.setName("Queue Hotel");
        property.setNameVi("Queue Hotel");
        property.setCode("QUEUE-" + email.hashCode());
        property.setSlug("queue-" + Math.abs(email.hashCode()));
        property.setAddressLine("12 Test Street");
        property.setCity("Da Nang");
        property.setCountry("Vietnam");
        property.setStatus("DRAFT");
        property.setApprovalStatus("DRAFT");
        property.setOperationStatus("INACTIVE");
        property.setIsDemo(false);
        property = hotelRepository.saveAndFlush(property);

        UserProperty mapping = new UserProperty();
        mapping.setUser(owner);
        mapping.setHotel(property);
        mapping.setRelationshipType("OWNER");
        mapping.setStatus("PENDING");
        userPropertyRepository.saveAndFlush(mapping);
        return new SeedData(owner.getId(), property.getId());
    }

    private OperationalAuditEvent auditEvent(SeedData seed, LocalDateTime occurredAt) {
        return new OperationalAuditEvent(
                "TENANT", seed.propertyId(), "PROPERTY", "PROPERTY_SUBMITTED_FOR_APPROVAL",
                "HOTEL", String.valueOf(seed.propertyId()), "USER", seed.userId(),
                "Owner submitted property for approval", null, null, "corr", occurredAt);
    }

    private record SeedData(Long userId, Long propertyId) {
    }
}
