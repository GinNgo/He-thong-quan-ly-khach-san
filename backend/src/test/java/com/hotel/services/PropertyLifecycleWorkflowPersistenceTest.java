package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.OperationalAuditEvent;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.NotificationRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.propertyreview.PropertyReviewEmailOutboxService;
import com.hotel.propertyreview.PropertyReviewInAppNotificationService;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@Import({
        PropertyLifecycleWorkflowService.class,
        PropertyReviewInAppNotificationService.class
})
class PropertyLifecycleWorkflowPersistenceTest {

    @Autowired private PropertyLifecycleWorkflowService workflowService;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserPropertyRepository userPropertyRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private NotificationRepository notificationRepository;

    @MockBean private OperationalAuditService operationalAuditService;
    @MockBean private PropertyReviewEmailOutboxService emailOutboxService;
    @MockBean private SimpMessagingTemplate messagingTemplate;
    @SpyBean private PropertyReviewInAppNotificationService notificationService;

    @BeforeEach
    void providePersistedAuditIdentity() {
        OperationalAuditEvent event = mock(OperationalAuditEvent.class);
        when(event.getId()).thenReturn(901L);
        when(operationalAuditService.append(any())).thenReturn(event);
    }

    @Test
    void suspensionPreservesMappingsRolesAndBookingWhileNotifyingAssignedActors() {
        SeedData seed = seedActiveProperty("suspend");

        var result = workflowService.suspend(
                99L, seed.propertyId(), "Safety inspection is required.");

        Hotel property = hotelRepository.findById(seed.propertyId()).orElseThrow();
        Reservation reservation = reservationRepository.findById(seed.reservationId()).orElseThrow();
        var mappings = userPropertyRepository.findByHotelId(seed.propertyId());
        Set<Long> recipients = notificationRepository.findAll().stream()
                .map(notification -> notification.getUserId())
                .collect(Collectors.toSet());

        assertEquals("SUSPENDED", property.getStatus());
        assertEquals("APPROVED", property.getApprovalStatus());
        assertEquals("SUSPENDED", property.getOperationStatus());
        assertEquals("SUSPEND", property.getLifecycleAction());
        assertEquals(99L, property.getLifecycleChangedByUserId());
        assertNotNull(property.getLifecycleChangedAt());
        assertTrue(result.changed());
        assertEquals(2, mappings.size());
        assertTrue(mappings.stream().allMatch(mapping -> "ACTIVE".equals(mapping.getStatus())));
        assertEquals("CONFIRMED", reservation.getStatus());
        assertEquals(seed.propertyId(), reservation.getHotel().getId());
        assertEquals(Set.of(seed.ownerId(), seed.staffId()), recipients);
        assertTrue(userRepository.findById(seed.ownerId()).orElseThrow().getRoles().isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void auditFailureRollsBackLifecycleMetadataAndStatus() {
        SeedData seed = seedActiveProperty("audit");
        doThrow(new IllegalStateException("audit unavailable"))
                .when(operationalAuditService).append(any());

        assertThrows(IllegalStateException.class, () -> workflowService.close(
                99L, seed.propertyId(), "Property operations ended permanently."));

        Hotel property = hotelRepository.findById(seed.propertyId()).orElseThrow();
        assertEquals("ACTIVE", property.getStatus());
        assertEquals("APPROVED", property.getApprovalStatus());
        assertEquals("ACTIVE", property.getOperationStatus());
        assertEquals(null, property.getLifecycleAction());
        assertTrue(notificationRepository.findAll().isEmpty());
        assertTrue(reservationRepository.findById(seed.reservationId()).isPresent());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void notificationPersistenceFailureRollsBackReactivation() {
        SeedData seed = seedSuspendedProperty("notification");
        doThrow(new IllegalStateException("notification store unavailable"))
                .when(notificationService).send(any(), any(), any(), any(), any());

        assertThrows(IllegalStateException.class, () -> workflowService.reactivate(
                99L, seed.propertyId(), "Inspection issues were resolved."));

        Hotel property = hotelRepository.findById(seed.propertyId()).orElseThrow();
        assertEquals("SUSPENDED", property.getStatus());
        assertEquals("APPROVED", property.getApprovalStatus());
        assertEquals("SUSPENDED", property.getOperationStatus());
        assertEquals("SUSPEND", property.getLifecycleAction());
        assertTrue(notificationRepository.findAll().isEmpty());
        assertTrue(reservationRepository.findById(seed.reservationId()).isPresent());
    }

    private SeedData seedActiveProperty(String suffix) {
        return seed(suffix, "ACTIVE", "ACTIVE", null);
    }

    private SeedData seedSuspendedProperty(String suffix) {
        return seed(suffix, "SUSPENDED", "SUSPENDED", "SUSPEND");
    }

    private SeedData seed(
            String suffix,
            String status,
            String operationStatus,
            String lifecycleAction) {
        User owner = user("owner-" + suffix + "@example.test");
        User staff = user("staff-" + suffix + "@example.test");
        User customer = user("customer-" + suffix + "@example.test");

        Hotel property = new Hotel();
        property.setName("Lifecycle Hotel " + suffix);
        property.setNameVi("Lifecycle Hotel " + suffix);
        property.setCode("LIFE-" + suffix.toUpperCase());
        property.setSlug("life-" + suffix);
        property.setAddressLine("12 Test Street");
        property.setCity("Da Nang");
        property.setCountry("Vietnam");
        property.setStatus(status);
        property.setApprovalStatus("APPROVED");
        property.setOperationStatus(operationStatus);
        property.setLifecycleAction(lifecycleAction);
        property.setLifecycleReason(lifecycleAction == null ? null : "Safety inspection is required.");
        property.setLifecycleChangedByUserId(lifecycleAction == null ? null : 99L);
        property.setLifecycleChangedAt(lifecycleAction == null
                ? null
                : java.time.LocalDateTime.of(2026, 8, 3, 9, 0));
        property.setIsDemo(false);
        property = hotelRepository.saveAndFlush(property);

        mapping(owner, property, "OWNER");
        mapping(staff, property, "STAFF");

        Reservation reservation = new Reservation();
        reservation.setUser(customer);
        reservation.setHotel(property);
        reservation.setCheckInDate(LocalDate.of(2026, 8, 10));
        reservation.setCheckOutDate(LocalDate.of(2026, 8, 12));
        reservation.setGuests(2);
        reservation.setTotalAmount(BigDecimal.valueOf(1_000_000));
        reservation.setStatus("CONFIRMED");
        reservation = reservationRepository.saveAndFlush(reservation);

        return new SeedData(owner.getId(), staff.getId(), property.getId(), reservation.getId());
    }

    private User user(String email) {
        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setFullName(email);
        user.setStatus("ACTIVE");
        user.setRoles(new HashSet<>());
        return userRepository.saveAndFlush(user);
    }

    private void mapping(User user, Hotel property, String relationshipType) {
        UserProperty mapping = new UserProperty();
        mapping.setUser(user);
        mapping.setHotel(property);
        mapping.setRelationshipType(relationshipType);
        mapping.setStatus("ACTIVE");
        mapping.setStartDate(java.time.LocalDateTime.of(2026, 8, 1, 9, 0));
        userPropertyRepository.saveAndFlush(mapping);
    }

    private record SeedData(Long ownerId, Long staffId, Long propertyId, Long reservationId) {
    }
}
