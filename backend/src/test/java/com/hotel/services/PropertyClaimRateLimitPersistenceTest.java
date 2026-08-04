package com.hotel.services;

import com.hotel.dtos.PropertyClaimRequestDTO;
import com.hotel.entities.Hotel;
import com.hotel.entities.PropertyClaimRequest;
import com.hotel.entities.User;
import com.hotel.exceptions.PropertyClaimRateLimitException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.PropertyClaimRequestRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
class PropertyClaimRateLimitPersistenceTest {

    @Autowired private PropertyClaimRequestRepository claimRepository;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserPropertyRepository userPropertyRepository;

    @Test
    void persistedAcceptedClaimsLimitNextRequestWithoutMutation() {
        User requester = persistUser();
        Hotel first = persistProperty("CLAIM-RATE-1");
        Hotel second = persistProperty("CLAIM-RATE-2");
        Hotel third = persistProperty("CLAIM-RATE-3");
        Hotel blockedTarget = persistProperty("CLAIM-RATE-4");
        Hotel expiredAtBoundary = persistProperty("CLAIM-RATE-EXPIRED");

        Clock clock = Clock.fixed(Instant.parse("2026-08-04T06:00:00Z"), ZoneOffset.UTC);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        persistClaim(requester, expiredAtBoundary, "REJECTED", now.minusMinutes(15));
        PropertyClaimRequest firstClaim = persistClaim(requester, first, "REJECTED", now.minusMinutes(3));
        persistClaim(requester, second, "CANCELLED", now.minusMinutes(2));
        persistClaim(requester, third, "PENDING", now.minusMinutes(1));

        LocalDateTime cutoff = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusMinutes(15);
        assertEquals(3L, claimRepository
                .countByRequesterUserIdAndCreatedAtGreaterThan(requester.getId(), cutoff));
        assertEquals(firstClaim.getId(), claimRepository
                .findFirstByRequesterUserIdAndCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(
                        requester.getId(), cutoff)
                .orElseThrow()
                .getId());

        PropertyOwnershipLifecycleService ownershipLifecycleService =
                mock(PropertyOwnershipLifecycleService.class);
        PropertyClaimService service = new PropertyClaimService(
                claimRepository,
                hotelRepository,
                userRepository,
                userPropertyRepository,
                mock(SubscriptionFeatureService.class),
                ownershipLifecycleService,
                new PropertyClaimRateLimiter(
                        claimRepository, clock, 3, Duration.ofMinutes(15)));

        PropertyClaimRateLimitException exception = assertThrows(
                PropertyClaimRateLimitException.class,
                () -> service.requestClaim(
                        blockedTarget.getId(),
                        requester.getId(),
                        new PropertyClaimRequestDTO("BUSINESS_LICENSE", "license-2026", null)));

        assertTrue(exception.getRetryAfterSeconds() > 0);
        assertEquals(4L, claimRepository.count());
        verify(ownershipLifecycleService, never()).createPendingOwner(any(), any());
    }

    private User persistUser() {
        User user = new User();
        user.setUsername("claim-rate-owner");
        user.setEmail("claim-rate-owner@example.test");
        user.setPasswordHash("hash");
        user.setFullName("Claim Rate Owner");
        user.setStatus("ACTIVE");
        user.setRoles(new HashSet<>());
        return userRepository.saveAndFlush(user);
    }

    private Hotel persistProperty(String code) {
        Hotel property = new Hotel();
        property.setName(code);
        property.setNameVi(code);
        property.setCode(code);
        property.setSlug(code.toLowerCase());
        property.setAddressLine("12 Test Street");
        property.setCity("Da Nang");
        property.setCountry("Vietnam");
        property.setStatus("PENDING_APPROVAL");
        property.setApprovalStatus("IMPORTED_PENDING_REVIEW");
        property.setOperationStatus("INACTIVE");
        property.setIsDemo(false);
        return hotelRepository.saveAndFlush(property);
    }

    private PropertyClaimRequest persistClaim(
            User requester,
            Hotel property,
            String status,
            LocalDateTime createdAt) {
        PropertyClaimRequest claim = new PropertyClaimRequest();
        claim.setRequesterUser(requester);
        claim.setProperty(property);
        claim.setVerificationMethod("EMAIL");
        claim.setVerificationData("owner@example.test");
        claim.setStatus(status);
        claim.setCreatedAt(createdAt);
        return claimRepository.saveAndFlush(claim);
    }
}
