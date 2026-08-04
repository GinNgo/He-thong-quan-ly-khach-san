package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.Notification;
import com.hotel.entities.Role;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.NotificationRepository;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@Import({
        PropertyApprovalWorkflowService.class,
        PropertyOwnershipLifecycleService.class,
        NotificationService.class
})
class PropertyApprovalWorkflowPersistenceTest {

    @Autowired private PropertyApprovalWorkflowService workflowService;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserPropertyRepository userPropertyRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private NotificationRepository notificationRepository;

    @MockBean private OperationalAuditService operationalAuditService;
    @MockBean private SimpMessagingTemplate messagingTemplate;
    @SpyBean private NotificationService notificationService;

    @Test
    void submittedDraftBecomesTypedActionableQueueItem() {
        SeedData seed = seedDraft("queue-owner@example.test");

        assertTrue(workflowService.pendingApprovals().isEmpty());

        workflowService.submitDraft(seed.userId(), seed.propertyId());

        var queue = workflowService.pendingApprovals();
        assertEquals(1, queue.size());
        assertEquals(seed.propertyId(), queue.getFirst().propertyId());
        assertEquals(seed.userId(), queue.getFirst().ownerId());
        assertEquals(seed.userId(), queue.getFirst().submittedByUserId());
        assertNotNull(queue.getFirst().submittedAt());
    }

    @Test
    void approvalCommitsCanonicalStatesOwnerRoleMetadataAndDurableNotification() {
        SeedData seed = seedPending("approve-owner@example.test");
        seedOwnerRole();

        var result = workflowService.approve(99L, seed.propertyId());

        Hotel property = hotelRepository.findById(seed.propertyId()).orElseThrow();
        UserProperty ownership = userPropertyRepository
                .findByUserIdAndHotelIdAndRelationshipType(seed.userId(), seed.propertyId(), "OWNER")
                .orElseThrow();
        User owner = userRepository.findById(seed.userId()).orElseThrow();
        Notification notification = notificationRepository.findAll().getFirst();
        assertEquals("ACTIVE", property.getStatus());
        assertEquals("APPROVED", property.getApprovalStatus());
        assertEquals("ACTIVE", property.getOperationStatus());
        assertEquals(99L, property.getReviewedByUserId());
        assertNotNull(property.getReviewedAt());
        assertNull(property.getReviewReason());
        assertEquals("ACTIVE", ownership.getStatus());
        assertTrue(owner.getRoles().stream().anyMatch(role -> "PROPERTY_OWNER".equals(role.getCode())));
        assertEquals(seed.userId(), notification.getUserId());
        assertEquals("PROPERTY_APPROVAL", notification.getType());
        assertEquals("ACTIVE", result.ownershipStatus());
    }

    @Test
    void rejectionCommitsReasonReviewerInactiveOwnershipAndExactOwnerNotification() {
        SeedData seed = seedPending("reject-owner@example.test");

        var result = workflowService.reject(
                99L, seed.propertyId(), "  Required ownership evidence is missing.  ");

        Hotel property = hotelRepository.findById(seed.propertyId()).orElseThrow();
        UserProperty ownership = userPropertyRepository
                .findByUserIdAndHotelIdAndRelationshipType(seed.userId(), seed.propertyId(), "OWNER")
                .orElseThrow();
        Notification notification = notificationRepository.findAll().getFirst();
        assertEquals("REJECTED", property.getStatus());
        assertEquals("REJECTED", property.getApprovalStatus());
        assertEquals("INACTIVE", property.getOperationStatus());
        assertEquals(99L, property.getReviewedByUserId());
        assertEquals("Required ownership evidence is missing.", property.getReviewReason());
        assertEquals("INACTIVE", ownership.getStatus());
        assertFalse(Boolean.TRUE.equals(ownership.getIsPrimaryOwner()));
        assertEquals(seed.userId(), notification.getUserId());
        assertEquals("Required ownership evidence is missing.", result.reason());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void auditFailureRollsBackApprovalAndOwnerRoleActivation() {
        SeedData seed = seedPending("audit-rollback@example.test");
        seedOwnerRole();
        doThrow(new IllegalStateException("audit unavailable"))
                .when(operationalAuditService).append(any());

        assertThrows(IllegalStateException.class,
                () -> workflowService.approve(99L, seed.propertyId()));

        Hotel property = hotelRepository.findById(seed.propertyId()).orElseThrow();
        UserProperty ownership = userPropertyRepository
                .findByUserIdAndHotelIdAndRelationshipType(seed.userId(), seed.propertyId(), "OWNER")
                .orElseThrow();
        User owner = userRepository.findById(seed.userId()).orElseThrow();
        assertEquals("PENDING_APPROVAL", property.getStatus());
        assertEquals("PENDING_APPROVAL", property.getApprovalStatus());
        assertEquals("INACTIVE", property.getOperationStatus());
        assertNull(property.getReviewedByUserId());
        assertEquals("PENDING", ownership.getStatus());
        assertTrue(owner.getRoles().isEmpty());
        assertTrue(notificationRepository.findAll().isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void notificationPersistenceFailureRollsBackRejectionTransition() {
        SeedData seed = seedPending("notification-rollback@example.test");
        doThrow(new IllegalStateException("notification store unavailable"))
                .when(notificationService).sendUserNotification(any(), any(), any(), any(), any());

        assertThrows(IllegalStateException.class, () -> workflowService.reject(
                99L, seed.propertyId(), "Required ownership evidence is missing."));

        Hotel property = hotelRepository.findById(seed.propertyId()).orElseThrow();
        UserProperty ownership = userPropertyRepository
                .findByUserIdAndHotelIdAndRelationshipType(seed.userId(), seed.propertyId(), "OWNER")
                .orElseThrow();
        assertEquals("PENDING_APPROVAL", property.getStatus());
        assertEquals("PENDING_APPROVAL", property.getApprovalStatus());
        assertEquals("INACTIVE", property.getOperationStatus());
        assertNull(property.getReviewedByUserId());
        assertEquals("PENDING", ownership.getStatus());
        assertTrue(notificationRepository.findAll().isEmpty());
    }

    private SeedData seedDraft(String email) {
        return seed(email, "DRAFT", "DRAFT", "INACTIVE", false);
    }

    private SeedData seedPending(String email) {
        return seed(email, "PENDING_APPROVAL", "PENDING_APPROVAL", "INACTIVE", true);
    }

    private SeedData seed(
            String email,
            String status,
            String approvalStatus,
            String operationStatus,
            boolean submitted) {
        User owner = new User();
        owner.setUsername(email);
        owner.setEmail(email);
        owner.setPasswordHash("hash");
        owner.setFullName("Queue Owner");
        owner.setStatus("ACTIVE");
        owner.setRoles(new HashSet<>());
        owner = userRepository.saveAndFlush(owner);

        Hotel property = new Hotel();
        property.setName("Queue Hotel");
        property.setNameVi("Queue Hotel");
        property.setCode("QUEUE-" + Math.abs(email.hashCode()));
        property.setSlug("queue-" + Math.abs(email.hashCode()));
        property.setAddressLine("12 Test Street");
        property.setCity("Da Nang");
        property.setCountry("Vietnam");
        property.setStatus(status);
        property.setApprovalStatus(approvalStatus);
        property.setOperationStatus(operationStatus);
        property.setIsDemo(false);
        if (submitted) {
            property.setSubmittedByUserId(owner.getId());
            property.setSubmittedAt(java.time.LocalDateTime.of(2026, 8, 4, 5, 0));
        }
        property = hotelRepository.saveAndFlush(property);

        UserProperty mapping = new UserProperty();
        mapping.setUser(owner);
        mapping.setHotel(property);
        mapping.setRelationshipType("OWNER");
        mapping.setStatus("PENDING");
        mapping.setIsPrimaryOwner(false);
        userPropertyRepository.saveAndFlush(mapping);
        return new SeedData(owner.getId(), property.getId());
    }

    private void seedOwnerRole() {
        if (roleRepository.findByCode("PROPERTY_OWNER").isPresent()) return;
        Role role = new Role();
        role.setCode("PROPERTY_OWNER");
        role.setName("Property Owner");
        role.setStatus("ACTIVE");
        role.setSystemRole(true);
        roleRepository.saveAndFlush(role);
    }

    private record SeedData(Long userId, Long propertyId) {
    }
}
