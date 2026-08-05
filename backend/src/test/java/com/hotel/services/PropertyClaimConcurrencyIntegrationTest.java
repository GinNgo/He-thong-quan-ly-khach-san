package com.hotel.services;

import com.hotel.dtos.PropertyApprovalDecisionResponse;
import com.hotel.dtos.PropertyClaimRequestDTO;
import com.hotel.entities.Hotel;
import com.hotel.entities.Role;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.exceptions.PropertyClaimConflictException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.PropertyClaimRequestRepository;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = "spring.flyway.enabled=false")
@Import({PropertyClaimService.class, PropertyOwnershipLifecycleService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PropertyClaimConcurrencyIntegrationTest {

    @Autowired private PropertyClaimService claimService;
    @Autowired private PropertyOwnershipLifecycleService ownershipLifecycleService;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PropertyClaimRequestRepository claimRepository;
    @Autowired private UserPropertyRepository userPropertyRepository;
    @Autowired private TransactionTemplate transactions;

    @MockBean private SubscriptionFeatureService subscriptionFeatureService;
    @MockBean private PropertyClaimRateLimiter rateLimiter;
    @MockBean private PropertyApprovalWorkflowService approvalWorkflowService;

    @Test
    void duplicateRequestRacePersistsOneClaimAndOneOwnerMapping() throws Exception {
        Fixture fixture = fixture("DUP", 1);

        List<Attempt> attempts = concurrent(
                () -> claimService.requestClaim(fixture.hotelId(), fixture.userIds().getFirst(), request()),
                () -> claimService.requestClaim(fixture.hotelId(), fixture.userIds().getFirst(), request()));

        assertThat(attempts).filteredOn(Attempt::succeeded).hasSize(1);
        assertThat(attempts).filteredOn(attempt -> attempt.error() instanceof PropertyClaimConflictException)
                .hasSize(1);
        transactions.executeWithoutResult(status -> {
            assertThat(claimRepository.findByPropertyId(fixture.hotelId()))
                    .filteredOn(claim -> "PENDING".equals(claim.getStatus())).hasSize(1);
            assertThat(userPropertyRepository.findByUserIdAndRelationshipType(
                    fixture.userIds().getFirst(), "OWNER"))
                    .filteredOn(mapping -> "PENDING".equals(mapping.getStatus())).hasSize(1);
        });
    }

    @Test
    void differentRequestersMayRaceForTheSameImportedProperty() throws Exception {
        Fixture fixture = fixture("MULTI", 2);

        List<Attempt> attempts = concurrent(
                () -> claimService.requestClaim(fixture.hotelId(), fixture.userIds().get(0), request()),
                () -> claimService.requestClaim(fixture.hotelId(), fixture.userIds().get(1), request()));

        assertThat(attempts).allMatch(Attempt::succeeded);
        transactions.executeWithoutResult(status -> assertThat(claimRepository.findByPropertyId(fixture.hotelId()))
                .filteredOn(claim -> "PENDING".equals(claim.getStatus())).hasSize(2));
    }

    @Test
    void approvalAndCancellationSerializeWithoutDeadlockAndLeaveOneConsistentState() throws Exception {
        Fixture fixture = fixture("TERMINAL", 2);
        Long requesterId = fixture.userIds().getFirst();
        Long adminId = fixture.userIds().get(1);
        Long claimId = claimService.requestClaim(fixture.hotelId(), requesterId, request()).id();
        when(approvalWorkflowService.approveImportedClaim(eq(adminId), eq(fixture.hotelId()), eq(requesterId), eq(claimId)))
                .thenAnswer(invocation -> transactions.execute(status -> {
                    UserProperty owner = ownershipLifecycleService.activateOwner(fixture.hotelId(), requesterId);
                    Hotel hotel = hotelRepository.findById(fixture.hotelId()).orElseThrow();
                    hotel.setStatus("ACTIVE");
                    hotel.setApprovalStatus("APPROVED");
                    hotel.setOperationStatus("ACTIVE");
                    hotelRepository.saveAndFlush(hotel);
                    return new PropertyApprovalDecisionResponse(
                            hotel.getId(), hotel.getStatus(), hotel.getApprovalStatus(), hotel.getOperationStatus(),
                            owner.getStatus(), adminId, LocalDateTime.now(), null);
                }));

        List<Attempt> attempts = concurrent(
                () -> claimService.approveClaim(claimId, adminId),
                () -> claimService.cancelClaim(claimId, requesterId));

        assertThat(attempts).filteredOn(Attempt::succeeded).hasSize(1);
        assertThat(attempts).filteredOn(attempt -> attempt.error() instanceof PropertyClaimConflictException)
                .hasSize(1);
        transactions.executeWithoutResult(status -> {
            var claim = claimRepository.findById(claimId).orElseThrow();
            Hotel hotel = hotelRepository.findById(fixture.hotelId()).orElseThrow();
            UserProperty owner = userPropertyRepository.findOwnerMappingForUpdate(requesterId, fixture.hotelId())
                    .orElseThrow();
            if ("APPROVED".equals(claim.getStatus())) {
                assertThat(hotel.getApprovalStatus()).isEqualTo("APPROVED");
                assertThat(owner.getStatus()).isEqualTo("ACTIVE");
            } else {
                assertThat(claim.getStatus()).isEqualTo("CANCELLED");
                assertThat(hotel.getApprovalStatus()).isEqualTo("IMPORTED_PENDING_REVIEW");
                assertThat(owner.getStatus()).isEqualTo("INACTIVE");
            }
        });
    }

    private Fixture fixture(String suffix, int users) {
        return transactions.execute(status -> {
            Role ownerRole = new Role();
            ownerRole.setCode("PROPERTY_OWNER");
            ownerRole.setName("Property owner");
            if (roleRepository.findByCode("PROPERTY_OWNER").isEmpty()) roleRepository.saveAndFlush(ownerRole);
            Hotel hotel = new Hotel();
            hotel.setCode("T240-" + suffix + "-" + System.nanoTime());
            hotel.setName("T240 concurrency hotel");
            hotel.setAddressLine("240 Concurrency Street");
            hotel.setCity("Ho Chi Minh City");
            hotel.setCountry("Viet Nam");
            hotel.setStatus("DRAFT");
            hotel.setApprovalStatus("IMPORTED_PENDING_REVIEW");
            hotel.setOperationStatus("ACTIVE");
            hotel = hotelRepository.saveAndFlush(hotel);
            java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
            for (int index = 0; index < users; index++) {
                User user = new User();
                String unique = suffix.toLowerCase() + "-" + index + "-" + System.nanoTime();
                user.setUsername(unique);
                user.setEmail(unique + "@example.test");
                user.setPasswordHash("test");
                user.setFullName("T240 User");
                user.setStatus("ACTIVE");
                ids.add(userRepository.saveAndFlush(user).getId());
            }
            return new Fixture(hotel.getId(), ids);
        });
    }

    private PropertyClaimRequestDTO request() {
        return new PropertyClaimRequestDTO("EMAIL", "owner@example.test", null);
    }

    private List<Attempt> concurrent(Callable<?> firstAction, Callable<?> secondAction) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(attempt(firstAction, ready, start));
            var second = executor.submit(attempt(secondAction, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        }
    }

    private Callable<Attempt> attempt(Callable<?> action, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) return new Attempt(null, new IllegalStateException("start timeout"));
            try { return new Attempt(action.call(), null); }
            catch (Throwable error) { return new Attempt(null, error); }
        };
    }

    private record Fixture(Long hotelId, List<Long> userIds) {}
    private record Attempt(Object value, Throwable error) {
        boolean succeeded() { return value != null && error == null; }
    }
}
