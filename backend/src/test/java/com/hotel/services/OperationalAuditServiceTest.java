package com.hotel.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.OperationalAuditEvent;
import com.hotel.repositories.OperationalAuditEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationalAuditServiceTest {

    private final OperationalAuditEventRepository repository = mock(OperationalAuditEventRepository.class);
    private final OperationalAuditService service = new OperationalAuditService(
            repository, new ObjectMapper(), null,
            Clock.fixed(Instant.parse("2026-08-03T04:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void appendsTenantEventWithRedactedStateAndCorrelation() {
        when(repository.save(any(OperationalAuditEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OperationalAuditEvent event = service.append(new OperationalAuditService.AuditCommand(
                "TENANT", 12L, "staff", "staff_updated", "user", "55", "USER", 7L,
                "Changed role", Map.of("email", "staff@example.test", "status", "ACTIVE"),
                Map.of("password", "hash", "status", "INACTIVE"), "audit/correlation"));

        assertThat(event.getScope()).isEqualTo("TENANT");
        assertThat(event.getHotelId()).isEqualTo(12L);
        assertThat(event.getDomain()).isEqualTo("STAFF");
        assertThat(event.getEventType()).isEqualTo("STAFF_UPDATED");
        assertThat(event.getBeforeStateJson()).contains("[REDACTED]").doesNotContain("staff@example.test");
        assertThat(event.getAfterStateJson()).contains("[REDACTED]").doesNotContain("hash");
        assertThat(event.getCorrelationId()).isEqualTo("audit-correlation");
        assertThat(event.getOccurredAt()).isEqualTo(java.time.LocalDateTime.of(2026, 8, 3, 4, 0));
    }

    @Test
    void rejectsMismatchedScopeAndOwnership() {
        assertThatThrownBy(() -> service.append(new OperationalAuditService.AuditCommand(
                "SYSTEM", 12L, "ROOM", "ROOM_UPDATED", "ROOM", "1", null, null,
                "Reason", null, Map.of("status", "AVAILABLE"), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exportsStableCsvAndEscapesFormulaCells() {
        OperationalAuditEvent event = new OperationalAuditEvent(
                "SYSTEM", null, "ROLE", "ROLE_UPDATED", "ROLE", "1", "SYSTEM", null,
                "=unsafe", null, "{\"status\":\"ACTIVE\"}", "corr-1",
                java.time.LocalDateTime.of(2026, 8, 3, 4, 0));
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));

        String csv = new String(service.exportCsv(new OperationalAuditService.AuditQuery(
                "SYSTEM", null, null, null, null, null, null, null, null, null)), java.nio.charset.StandardCharsets.UTF_8);

        assertThat(csv).startsWith("\uFEFFid,scope,hotelId");
        assertThat(csv).contains("'=unsafe").contains("corr-1");
    }

    @Test
    void deniesExplicitCrossTenantSearch() {
        PropertyAccessService access = mock(PropertyAccessService.class);
        when(access.isSystemAdministrator()).thenReturn(false);
        when(access.assignedHotelIds()).thenReturn(java.util.Set.of(12L));
        OperationalAuditService tenantService = new OperationalAuditService(repository, new ObjectMapper(), access,
                Clock.systemUTC());

        assertThatThrownBy(() -> tenantService.search(new OperationalAuditService.AuditQuery(
                "TENANT", 99L, null, null, null, null, null, null, null, null), PageRequest.of(0, 25)))
                .isInstanceOf(com.hotel.exceptions.ResourceNotFoundException.class);
    }
}
