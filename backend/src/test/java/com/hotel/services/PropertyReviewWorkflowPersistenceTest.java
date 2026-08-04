package com.hotel.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Hotel;
import com.hotel.entities.Notification;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.propertyreview.PropertyReviewEmailDeliveryAttempt;
import com.hotel.propertyreview.PropertyReviewEmailDeliveryAttemptRepository;
import com.hotel.propertyreview.PropertyReviewEmailOutcome;
import com.hotel.propertyreview.PropertyReviewEmailOutbox;
import com.hotel.propertyreview.PropertyReviewEmailOutboxRepository;
import com.hotel.propertyreview.PropertyReviewEmailOutboxService;
import com.hotel.propertyreview.PropertyReviewEmailStatus;
import com.hotel.propertyreview.PropertyReviewInAppNotificationService;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.NotificationRepository;
import com.hotel.repositories.OperationalAuditEventRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.mail.property-review.max-attempts=3"
})
@ActiveProfiles("test")
@Import({
        PropertyApprovalWorkflowService.class,
        PropertyReviewInAppNotificationService.class,
        PropertyReviewEmailOutboxService.class,
        OperationalAuditService.class,
        PropertyReviewWorkflowPersistenceTest.TestBeans.class
})
class PropertyReviewWorkflowPersistenceTest {

    @Autowired private PropertyApprovalWorkflowService workflowService;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserPropertyRepository userPropertyRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private OperationalAuditEventRepository auditRepository;
    @SpyBean private PropertyReviewEmailOutboxRepository outboxRepository;
    @Autowired private PropertyReviewEmailDeliveryAttemptRepository attemptRepository;

    @MockBean private PropertyOwnershipLifecycleService ownershipLifecycleService;
    @MockBean private PropertyAccessService propertyAccessService;
    @MockBean private SimpMessagingTemplate messagingTemplate;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void submissionCommitsTransitionAuditNotificationOutboxAndOneAfterCommitPush() {
        SeedData seed = seedDraft("review-commit@example.test");

        workflowService.submitDraft(seed.userId(), seed.propertyId());

        Hotel property = hotelRepository.findById(seed.propertyId()).orElseThrow();
        assertEquals("PENDING_APPROVAL", property.getStatus());
        assertEquals("PENDING_APPROVAL", property.getApprovalStatus());
        assertEquals("INACTIVE", property.getOperationStatus());
        assertTrue(auditRepository.findAll().stream().anyMatch(event ->
                seed.propertyId().equals(event.getHotelId())
                        && "PROPERTY_SUBMITTED_FOR_APPROVAL".equals(event.getEventType())));
        assertTrue(notificationRepository.findAll().stream().anyMatch(notification ->
                seed.userId().equals(notification.getUserId())));

        PropertyReviewEmailOutbox outbox = outboxRepository.findAll().stream()
                .filter(item -> seed.propertyId().equals(item.getHotelId()))
                .findFirst()
                .orElseThrow();
        assertEquals(PropertyReviewEmailStatus.PENDING, outbox.getStatus());
        assertEquals(seed.userId(), outbox.getRecipientUserId());
        assertNotNull(outbox.getAuditEventId());
        assertTrue(attemptRepository.findByOutboxIdOrderByAttemptNumberAsc(outbox.getId()).isEmpty());
        verify(messagingTemplate, times(1)).convertAndSendToUser(
                eq(String.valueOf(seed.userId())),
                eq("/queue/notifications"),
                any(Notification.class));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void outboxPersistenceFailureRollsBackEveryDurableEffectAndNeverPushes() {
        SeedData seed = seedDraft("review-rollback@example.test");
        doThrow(new IllegalStateException("outbox unavailable"))
                .when(outboxRepository).saveAndFlush(any(PropertyReviewEmailOutbox.class));

        assertThrows(IllegalStateException.class,
                () -> workflowService.submitDraft(seed.userId(), seed.propertyId()));

        Hotel property = hotelRepository.findById(seed.propertyId()).orElseThrow();
        assertEquals("DRAFT", property.getStatus());
        assertEquals("DRAFT", property.getApprovalStatus());
        assertEquals("INACTIVE", property.getOperationStatus());
        assertFalse(auditRepository.findAll().stream().anyMatch(event ->
                seed.propertyId().equals(event.getHotelId())));
        assertFalse(notificationRepository.findAll().stream().anyMatch(notification ->
                seed.userId().equals(notification.getUserId())));
        assertFalse(outboxRepository.findAll().stream().anyMatch(item ->
                seed.propertyId().equals(item.getHotelId())));
        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void invalidLegacyRecipientDeadLettersWithoutRollingBackAndAttemptIsAppendOnly() {
        SeedData seed = seedDraft("invalid-email");

        workflowService.submitDraft(seed.userId(), seed.propertyId());

        Hotel property = hotelRepository.findById(seed.propertyId()).orElseThrow();
        assertEquals("PENDING_APPROVAL", property.getStatus());
        PropertyReviewEmailOutbox outbox = outboxRepository.findAll().stream()
                .filter(item -> seed.propertyId().equals(item.getHotelId()))
                .findFirst()
                .orElseThrow();
        assertEquals(PropertyReviewEmailStatus.DEAD_LETTER, outbox.getStatus());
        assertEquals("RECIPIENT_INVALID", outbox.getLastErrorCode());
        assertEquals(null, outbox.getRecipientEmail());

        PropertyReviewEmailDeliveryAttempt attempt = attemptRepository
                .findByOutboxIdOrderByAttemptNumberAsc(outbox.getId())
                .getFirst();
        assertEquals(1, attempt.getAttemptNumber());
        assertEquals(PropertyReviewEmailOutcome.FAILED, attempt.getOutcome());
        assertEquals("RECIPIENT_INVALID", attempt.getErrorCode());

        ReflectionTestUtils.setField(attempt, "errorCode", "ALTERED");
        assertThrows(RuntimeException.class, () -> attemptRepository.saveAndFlush(attempt));
        PropertyReviewEmailDeliveryAttempt persisted = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertEquals("RECIPIENT_INVALID", persisted.getErrorCode());
        verify(messagingTemplate, times(1)).convertAndSendToUser(
                eq(String.valueOf(seed.userId())),
                eq("/queue/notifications"),
                any(Notification.class));
    }

    private SeedData seedDraft(String email) {
        User owner = new User();
        owner.setUsername("owner-" + Math.abs(email.hashCode()));
        owner.setEmail(email);
        owner.setPasswordHash("hash");
        owner.setFullName("Review Owner");
        owner.setStatus("ACTIVE");
        owner.setRoles(new HashSet<>());
        owner = userRepository.saveAndFlush(owner);

        Hotel property = new Hotel();
        property.setName("Review Hotel " + Math.abs(email.hashCode()));
        property.setNameVi("Review Hotel " + Math.abs(email.hashCode()));
        property.setCode("REVIEW-" + Math.abs(email.hashCode()));
        property.setSlug("review-" + Math.abs(email.hashCode()));
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
        mapping.setIsPrimaryOwner(false);
        userPropertyRepository.saveAndFlush(mapping);
        return new SeedData(owner.getId(), property.getId());
    }

    private record SeedData(Long userId, Long propertyId) {
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
