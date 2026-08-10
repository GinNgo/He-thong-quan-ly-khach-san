package com.hotel.services;

import com.hotel.dtos.UserDto;
import com.hotel.entities.AccountSubscription;
import com.hotel.entities.Hotel;
import com.hotel.entities.Role;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.repositories.AccountSubscriptionRepository;
import com.hotel.repositories.ChatMessageRepository;
import com.hotel.repositories.PropertyClaimRequestRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCurrentProfileServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserPropertyRepository userPropertyRepository;
    @Mock private AccountSubscriptionRepository accountSubscriptionRepository;
    @Mock private SubscriptionFeatureService subscriptionFeatureService;
    @Mock private PropertySubscriptionEntitlementService propertyEntitlementService;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private PropertyClaimRequestRepository propertyClaimRequestRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void returnsRolePropertyAndSubscriptionContextForTheRequestedAccount() {
        Long userId = 77L;
        User user = new User();
        user.setId(userId);
        user.setUsername("owner@example.com");
        user.setEmail("owner@example.com");
        user.setStatus("ACTIVE");

        Role ownerRole = new Role();
        ownerRole.setId(4L);
        ownerRole.setCode("PROPERTY_OWNER");
        ownerRole.setName("Property owner");
        user.setRoles(Set.of(ownerRole));

        Hotel hotel = new Hotel();
        hotel.setId(12L);
        hotel.setName("LuxeStay Riverside");
        UserProperty assignment = new UserProperty();
        assignment.setUser(user);
        assignment.setHotel(hotel);
        assignment.setRelationshipType("OWNER");
        assignment.setStatus("ACTIVE");

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setCode("PRO");
        AccountSubscription subscription = new AccountSubscription();
        subscription.setUser(user);
        subscription.setPlan(plan);
        subscription.setStatus("ACTIVE");
        subscription.setStartAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        subscription.setEndAt(LocalDateTime.of(2027, 8, 1, 0, 0));
        subscription.setIsLifetime(false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userPropertyRepository.findByUserIdAndRelationshipTypeOrderByStartDateDesc(userId, "STAFF"))
                .thenReturn(List.of());
        when(userPropertyRepository.findByUserId(userId)).thenReturn(List.of(assignment));
        when(chatMessageRepository.countByReceiverIdAndIsReadFalse(userId)).thenReturn(4L);
        when(reservationRepository.countByUserIdAndStatusIn(eq(userId), anyList())).thenReturn(2L);
        when(accountSubscriptionRepository.findByUserIdAndStatus(userId, "ACTIVE"))
                .thenReturn(List.of(subscription));
        when(propertyEntitlementService.getCurrent(12L))
                .thenReturn(PropertySubscriptionEntitlementService.EntitlementView.none(12L, "TEST"));
        when(subscriptionFeatureService.getActiveFeaturesForUser(userId))
                .thenReturn(Map.of("MAX_PROPERTIES", 3));

        UserDto result = userService.getUserWithSaaSContext(userId).orElseThrow();

        assertEquals(userId, result.getId());
        assertEquals("PROPERTY_OWNER", result.getRoles().get(0).getCode());
        assertEquals(12L, result.getAssignedProperties().get(0).getId());
        assertEquals("APPROVED", result.getPartnerRegistrationStatus());
        assertEquals("PRO", result.getPlan());
        assertEquals("ACTIVE", result.getSubscriptionStatus());
        assertEquals(3, result.getLimits().get("MAX_PROPERTIES"));
        assertEquals(1, result.getCurrentUsage().get("MAX_PROPERTIES"));
        assertEquals(4L, result.getUnreadMessageCount());
        assertEquals(2L, result.getPendingBookingCount());
        verify(userRepository, never()).findById(99L);
    }

    @Test
    void prefersPropertyEntitlementUsedByTheManagementPortal() {
        Long userId = 78L;
        User user = new User();
        user.setId(userId);
        user.setUsername("tenant@example.com");
        user.setStatus("ACTIVE");
        user.setRoles(Set.of());

        Hotel hotel = new Hotel();
        hotel.setId(15L);
        hotel.setName("LuxeStay Central");
        UserProperty assignment = new UserProperty();
        assignment.setId(1L);
        assignment.setUser(user);
        assignment.setHotel(hotel);
        assignment.setRelationshipType("OWNER");
        assignment.setIsPrimaryOwner(true);
        assignment.setStatus("ACTIVE");

        LocalDateTime startAt = LocalDateTime.of(2026, 8, 9, 0, 0);
        LocalDateTime endAt = LocalDateTime.of(2027, 8, 9, 0, 0);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userPropertyRepository.findByUserIdAndRelationshipTypeOrderByStartDateDesc(userId, "STAFF"))
                .thenReturn(List.of());
        when(userPropertyRepository.findByUserId(userId)).thenReturn(List.of(assignment));
        when(accountSubscriptionRepository.findByUserIdAndStatus(userId, "ACTIVE")).thenReturn(List.of());
        when(propertyEntitlementService.getCurrent(15L)).thenReturn(
                new PropertySubscriptionEntitlementService.EntitlementView(
                        15L, "PLATFORM", true, 3L, "PREMIUM", "Gói Cao cấp", "ACTIVE",
                        startAt, endAt, false, Map.of("MAX_ROOMS", 100), "contract-1", null));

        UserDto result = userService.getUserWithSaaSContext(userId).orElseThrow();

        assertEquals("Gói Cao cấp", result.getPlan());
        assertEquals("ACTIVE", result.getSubscriptionStatus());
        assertEquals(endAt, result.getEndAt());
        assertEquals(100, result.getLimits().get("MAX_ROOMS"));
    }

    @Test
    void returnsEmptyWithoutLoadingContextForAMissingAccount() {
        when(userRepository.findById(88L)).thenReturn(Optional.empty());

        assertTrue(userService.getUserWithSaaSContext(88L).isEmpty());

        verify(userPropertyRepository, never()).findByUserId(88L);
        verify(accountSubscriptionRepository, never()).findByUserIdAndStatus(88L, "ACTIVE");
    }
}
