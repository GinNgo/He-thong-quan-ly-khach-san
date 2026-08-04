package com.hotel.paymentprovider.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialAuditQueryServiceTest {
    @Mock FinancialAuditEventRepository repository;
    @Mock PropertyAccessService propertyAccessService;
    FinancialAuditQueryService service;

    @BeforeEach void setUp() { service = new FinancialAuditQueryService(repository, propertyAccessService, new ObjectMapper()); }

    @Test
    void tenantViewerReceivesRedactedPropertyEventsAndHasNoRawProviderIdentity() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.assignedHotelIds()).thenReturn(Set.of(7L));
        FinancialAuditEvent event = new FinancialAuditEvent("PROPERTY_COMMERCE", 7L, "PAYMENT", "PAY-1",
                "USER", 3L, "CALLBACK", "PENDING", "PAID", "Provider accepted",
                "idem-secret", "provider-transaction-raw", "corr-1",
                "{\"email\":\"guest@example.com\",\"safe\":\"ok\",\"nested\":{\"phone\":\"0901\"}}",
                LocalDateTime.of(2026, 8, 4, 10, 0));
        when(repository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));

        var dto = service.search(new FinancialAuditQueryService.Query("PROPERTY_COMMERCE", 7L, null, null, null, null, null, null), PageRequest.of(0, 25)).getContent().getFirst();
        assertTrue(dto.idempotencyReference().startsWith("sha256:"));
        assertTrue(dto.providerReference().startsWith("sha256:"));
        assertFalse(dto.providerReference().contains("provider-transaction-raw"));
        assertTrue(dto.metadataJson().contains("[REDACTED]"));
        assertTrue(dto.metadataJson().contains("\"safe\":\"ok\""));
        assertFalse(dto.metadataJson().contains("guest@example.com"));
    }

    @Test
    void tenantCannotEnumerateForeignOrPlatformAuditScope() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.assignedHotelIds()).thenReturn(Set.of(7L));
        assertThrows(RuntimeException.class, () -> service.search(new FinancialAuditQueryService.Query(null, 99L, null, null, null, null, null, null), PageRequest.of(0, 25)));
        assertThrows(RuntimeException.class, () -> service.search(new FinancialAuditQueryService.Query("PLATFORM_BILLING", null, null, null, null, null, null, null), PageRequest.of(0, 25)));
        verifyNoInteractions(repository);
    }

    @Test
    void policyIsAppendOnlySevenYearsAndExportIsBounded() {
        var policy = service.policy();
        assertTrue(policy.appendOnly());
        assertEquals(2555, policy.retentionDays());
        assertEquals(10_000, policy.exportMaxRows());
    }
}
