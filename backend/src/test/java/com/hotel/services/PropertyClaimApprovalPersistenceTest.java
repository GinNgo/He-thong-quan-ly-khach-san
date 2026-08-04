package com.hotel.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Hotel;
import com.hotel.entities.Notification;
import com.hotel.entities.PropertyClaimRequest;
import com.hotel.entities.Role;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.propertyreview.PropertyReviewEmailOutbox;
import com.hotel.propertyreview.PropertyReviewEmailOutboxRepository;
import com.hotel.propertyreview.PropertyReviewEmailOutboxService;
import com.hotel.propertyreview.PropertyReviewInAppNotificationService;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.NotificationRepository;
import com.hotel.repositories.OperationalAuditEventRepository;
import com.hotel.repositories.PropertyClaimRequestRepository;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.CustomUserDetailsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.mail.property-review.max-attempts=3"
})
@ActiveProfiles("test")
@Import({
        PropertyClaimService.class,
        PropertyApprovalWorkflowService.class,
        PropertyOwnershipLifecycleService.class,
        PropertyReviewInAppNotificationService.class,
        PropertyReviewEmailOutboxService.class,
        OperationalAuditService.class,
        PropertyAccessService.class,
        CustomUserDetailsService.class,
        PropertyClaimApprovalPersistenceTest.TestBeans.class
})
class PropertyClaimApprovalPersistenceTest {

    @Autowired private PropertyClaimService claimService;
    @Autowired private CustomUserDetailsService userDetailsService;
    @Autowired private PropertyAccessService propertyAccessService;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserPropertyRepository userPropertyRepository;
    @Autowired private RoleRepository roleRepository;
    @SpyBean private PropertyClaimRequestRepository claimRepository;
    @SpyBean private OperationalAuditEventRepository auditRepository;
    @Autowired private NotificationRepository notificationRepository;
    @SpyBean private PropertyReviewEmailOutboxRepository outboxRepository;

    @MockBean private SubscriptionFeatureService subscriptionFeatureService;
    @MockBean private PropertyClaimRateLimiter rateLimiter;
    @MockBean private SimpMessagingTemplate messagingTemplate;

    @BeforeEach
    void provideEmptySubscriptionFeatureMap() {
        when(subscriptionFeatureService.getActiveFeaturesForUser(anyLong())).thenReturn(Map.of());
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void approvalCommitsClaimPropertyOwnerRoleAuditNotificationsAndTenantAccessTogether() {
        SeedData seed = seedImportedClaim("claim-commit@example.test");

        var result = claimService.approveClaim(seed.claimId(), seed.adminId());

        PropertyClaimRequest claim = claimRepository.findById(seed.claimId()).orElseThrow();
        Hotel property = hotelRepository.findById(seed.propertyId()).orElseThrow();
        UserProperty mapping = userPropertyRepository
                .findByUserIdAndHotelIdAndRelationshipType(seed.claimantId(), seed.propertyId(), "OWNER")
                .orElseThrow();
        User claimant = userRepository.findById(seed.claimantId()).orElseThrow();
        assertEquals("APPROVED", result.status());
        assertEquals("APPROVED", claim.getStatus());
        assertEquals(seed.adminId(), claim.getReviewedBy().getId());
        assertEquals(property.getReviewedAt(), claim.getReviewedAt());
        assertEquals("ACTIVE", property.getStatus());
        assertEquals("APPROVED", property.getApprovalStatus());
        assertEquals("ACTIVE", property.getOperationStatus());
        assertEquals("ACTIVE", mapping.getStatus());
        assertTrue(Boolean.TRUE.equals(mapping.getIsPrimaryOwner()));
        assertTrue(claimant.getRoles().stream().anyMatch(role -> "PROPERTY_OWNER".equals(role.getCode())));

        var audit = auditRepository.findAll().stream()
                .filter(event -> seed.propertyId().equals(event.getHotelId()))
                .findFirst()
                .orElseThrow();
        assertEquals("PROPERTY_APPROVED", audit.getEventType());
        assertEquals(seed.adminId(), audit.getActorId());
        assertTrue(audit.getBeforeStateJson().contains("\"claimId\":" + seed.claimId()));
        assertTrue(audit.getBeforeStateJson().contains("\"claimStatus\":\"PENDING\""));
        assertTrue(audit.getAfterStateJson().contains("\"claimStatus\":\"APPROVED\""));

        Notification notification = notificationRepository.findAll().stream()
                .filter(item -> seed.claimantId().equals(item.getUserId()))
                .findFirst()
                .orElseThrow();
        assertEquals("PROPERTY_APPROVAL", notification.getType());
        PropertyReviewEmailOutbox outbox = outboxRepository.findAll().stream()
                .filter(item -> seed.propertyId().equals(item.getHotelId()))
                .findFirst()
                .orElseThrow();
        assertEquals(seed.claimantId(), outbox.getRecipientUserId());
        assertEquals(audit.getId(), outbox.getAuditEventId());

        CustomUserDetails principal = (CustomUserDetails) userDetailsService
                .loadUserByUsername("claim-commit@example.test");
        assertTrue(principal.getAuthorities().stream()
                .anyMatch(authority -> "PROPERTY_OWNER".equals(authority.getAuthority())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        assertEquals(java.util.Set.of(seed.propertyId()), propertyAccessService.assignedHotelIds());
        assertTrue(propertyAccessService.accessibleHotelIds().contains(seed.propertyId()));
        assertFalse(propertyAccessService.assignedHotelIds().contains(seed.unrelatedPropertyId()));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void auditFailureRollsBackClaimPropertyMappingRoleAndNotifications() {
        SeedData seed = seedImportedClaim("claim-audit-rollback@example.test");
        doThrow(new IllegalStateException("audit unavailable"))
                .when(auditRepository).save(any());

        assertThrows(IllegalStateException.class,
                () -> claimService.approveClaim(seed.claimId(), seed.adminId()));

        assertRolledBack(seed);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void outboxFailureRollsBackClaimPropertyMappingRoleAuditAndNotification() {
        SeedData seed = seedImportedClaim("claim-outbox-rollback@example.test");
        doThrow(new IllegalStateException("outbox unavailable"))
                .when(outboxRepository).saveAndFlush(any(PropertyReviewEmailOutbox.class));

        assertThrows(IllegalStateException.class,
                () -> claimService.approveClaim(seed.claimId(), seed.adminId()));

        assertRolledBack(seed);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void claimPersistenceFailureRollsBackCanonicalApprovalSideEffects() {
        SeedData seed = seedImportedClaim("claim-store-rollback@example.test");
        doThrow(new IllegalStateException("claim store unavailable"))
                .when(claimRepository).saveAndFlush(any(PropertyClaimRequest.class));

        assertThrows(IllegalStateException.class,
                () -> claimService.approveClaim(seed.claimId(), seed.adminId()));

        assertRolledBack(seed);
    }

    private void assertRolledBack(SeedData seed) {
        PropertyClaimRequest claim = claimRepository.findById(seed.claimId()).orElseThrow();
        Hotel property = hotelRepository.findById(seed.propertyId()).orElseThrow();
        UserProperty mapping = userPropertyRepository
                .findByUserIdAndHotelIdAndRelationshipType(seed.claimantId(), seed.propertyId(), "OWNER")
                .orElseThrow();
        User claimant = userRepository.findById(seed.claimantId()).orElseThrow();
        assertEquals("PENDING", claim.getStatus());
        assertEquals("DRAFT", property.getStatus());
        assertEquals("IMPORTED_PENDING_REVIEW", property.getApprovalStatus());
        assertEquals("ACTIVE", property.getOperationStatus());
        assertEquals("PENDING", mapping.getStatus());
        assertFalse(Boolean.TRUE.equals(mapping.getIsPrimaryOwner()));
        assertTrue(claimant.getRoles().stream().noneMatch(role -> "PROPERTY_OWNER".equals(role.getCode())));
        assertTrue(auditRepository.findAll().stream().noneMatch(event -> seed.propertyId().equals(event.getHotelId())));
        assertTrue(notificationRepository.findAll().stream()
                .noneMatch(item -> seed.claimantId().equals(item.getUserId())));
        assertTrue(outboxRepository.findAll().stream()
                .noneMatch(item -> seed.propertyId().equals(item.getHotelId())));
    }

    private SeedData seedImportedClaim(String email) {
        seedOwnerRole();
        User admin = user("admin-" + email, "admin-" + email, new HashSet<>());
        User claimant = user(email, email, new HashSet<>());
        User unrelated = user("other-" + email, "other-" + email, new HashSet<>());
        Hotel property = property("CLAIM-" + Math.abs(email.hashCode()), "DRAFT", "IMPORTED_PENDING_REVIEW", "ACTIVE");
        Hotel unrelatedProperty = property(
                "OTHER-" + Math.abs(email.hashCode()), "ACTIVE", "APPROVED", "ACTIVE");
        ownership(claimant, property, "PENDING", false);
        ownership(unrelated, unrelatedProperty, "ACTIVE", true);

        PropertyClaimRequest claim = new PropertyClaimRequest();
        claim.setProperty(property);
        claim.setRequesterUser(claimant);
        claim.setVerificationMethod("EMAIL");
        claim.setVerificationData(email);
        claim.setStatus("PENDING");
        claim = claimRepository.saveAndFlush(claim);
        return new SeedData(
                admin.getId(), claimant.getId(), property.getId(), unrelatedProperty.getId(), claim.getId());
    }

    private User user(String username, String email, HashSet<Role> roles) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setFullName(username);
        user.setStatus("ACTIVE");
        user.setRoles(roles);
        return userRepository.saveAndFlush(user);
    }

    private Hotel property(String code, String status, String approvalStatus, String operationStatus) {
        Hotel property = new Hotel();
        property.setName(code);
        property.setNameVi(code);
        property.setCode(code);
        property.setSlug(code.toLowerCase(java.util.Locale.ROOT));
        property.setAddressLine("12 Test Street");
        property.setCity("Da Nang");
        property.setCountry("Vietnam");
        property.setStatus(status);
        property.setApprovalStatus(approvalStatus);
        property.setOperationStatus(operationStatus);
        property.setIsDemo(false);
        return hotelRepository.saveAndFlush(property);
    }

    private void ownership(User user, Hotel property, String status, boolean primary) {
        UserProperty mapping = new UserProperty();
        mapping.setUser(user);
        mapping.setHotel(property);
        mapping.setRelationshipType("OWNER");
        mapping.setStatus(status);
        mapping.setIsPrimaryOwner(primary);
        userPropertyRepository.saveAndFlush(mapping);
    }

    private void seedOwnerRole() {
        if (roleRepository.findByCode("PROPERTY_OWNER").isPresent()) {
            return;
        }
        Role role = new Role();
        role.setCode("PROPERTY_OWNER");
        role.setName("Property Owner");
        role.setStatus("ACTIVE");
        role.setSystemRole(true);
        roleRepository.saveAndFlush(role);
    }

    private record SeedData(
            Long adminId,
            Long claimantId,
            Long propertyId,
            Long unrelatedPropertyId,
            Long claimId) {
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
