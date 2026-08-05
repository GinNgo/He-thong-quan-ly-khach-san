package com.hotel.integration;

import com.hotel.BackendApplication;
import com.hotel.dtos.RoleUpdateRequest;
import com.hotel.entities.OperationalAuditEvent;
import com.hotel.entities.Role;
import com.hotel.repositories.OperationalAuditEventRepository;
import com.hotel.repositories.RoleRepository;
import com.hotel.services.RoleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = BackendApplication.class,
        properties = "payment.property.encryption-key=test-property-payment-encryption-key")
@ActiveProfiles("test")
class RoleMutationConcurrencyIntegrationTest {

    @Autowired private RoleService roleService;
    @Autowired private RoleRepository roleRepository;
    @Autowired private OperationalAuditEventRepository auditRepository;

    private Role role;

    @BeforeEach
    void setUp() {
        String suffix = Long.toUnsignedString(System.nanoTime(), 36).toUpperCase();
        role = new Role();
        role.setCode("CONCURRENT_" + suffix);
        role.setName("Concurrent role");
        role.setDescription("Concurrency fixture");
        role.setStatus("ACTIVE");
        role.setSystemRole(false);
        role = roleRepository.saveAndFlush(role);
    }

    @AfterEach
    void cleanUp() {
        if (role != null && role.getId() != null && roleRepository.existsById(role.getId())) {
            roleRepository.deleteById(role.getId());
        }
    }

    @Test
    void concurrentMetadataUpdatesAcceptExactlyOneVersionAndWriteOneAuditEvent() throws Exception {
        long expectedVersion = role.getVersion();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Throwable first;
        Throwable second;
        try {
            Future<Throwable> firstFuture = executor.submit(() -> updateAfter(
                    start, expectedVersion, "Night operations", "Concurrent role update A"));
            Future<Throwable> secondFuture = executor.submit(() -> updateAfter(
                    start, expectedVersion, "Day operations", "Concurrent role update B"));
            start.countDown();
            first = firstFuture.get(20, TimeUnit.SECONDS);
            second = secondFuture.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(Arrays.asList(first, second))
                .filteredOn(Objects::nonNull)
                .singleElement()
                .isInstanceOf(OptimisticLockingFailureException.class);

        Role reloaded = roleRepository.findById(role.getId()).orElseThrow();
        assertThat(reloaded.getVersion()).isEqualTo(expectedVersion + 1);
        assertThat(reloaded.getName()).isIn("Night operations", "Day operations");

        assertThat(auditRepository.findAll().stream()
                .filter(event -> "ROLE_UPDATED".equals(event.getEventType()))
                .filter(event -> String.valueOf(role.getId()).equals(event.getAggregateId()))
                .toList())
                .singleElement()
                .satisfies(this::assertVersionedAuditEvent);
    }

    private Throwable updateAfter(
            CountDownLatch start,
            long expectedVersion,
            String name,
            String reason) {
        try {
            if (!start.await(5, TimeUnit.SECONDS)) return new IllegalStateException("Start latch timed out");
            RoleUpdateRequest request = new RoleUpdateRequest();
            request.setCode(role.getCode());
            request.setName(name);
            request.setDescription("Updated concurrently");
            request.setExpectedVersion(expectedVersion);
            request.setReason(reason);
            roleService.updateRole(role.getId(), request);
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return exception;
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private void assertVersionedAuditEvent(OperationalAuditEvent event) {
        assertThat(event.getActorType()).isEqualTo("SYSTEM");
        assertThat(event.getReason()).isIn("Concurrent role update A", "Concurrent role update B");
        assertThat(event.getBeforeStateJson()).contains("\"version\":" + role.getVersion());
        assertThat(event.getAfterStateJson()).contains("\"version\":" + (role.getVersion() + 1));
    }
}
