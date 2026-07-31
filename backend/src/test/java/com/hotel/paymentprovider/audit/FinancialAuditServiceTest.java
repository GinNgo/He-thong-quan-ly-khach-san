package com.hotel.paymentprovider.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinancialAuditServiceTest {

    @Test
    void appendRedactsProviderSecretsAndPreservesEvidence() {
        FinancialAuditEventRepository repository = mock(FinancialAuditEventRepository.class);
        when(repository.save(any(FinancialAuditEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        FinancialAuditService service = new FinancialAuditService(repository, new ObjectMapper());

        FinancialAuditEvent event = service.append(new FinancialAuditService.AuditCommand(
                "PROPERTY_COMMERCE", 7L, "PAYMENT_ATTEMPT", "attempt-1", "USER", 9L,
                "CALLBACK", "PENDING", "SUCCESS", "verified", "idem-1", "provider-event-1",
                "corr-1", Map.of("amount", 125000, "accessToken", "do-not-store", "nested", Map.of("secret", "hidden"))));

        assertTrue(event.getMetadataJson().contains("[REDACTED]"));
        assertFalse(event.getMetadataJson().contains("do-not-store"));
        assertFalse(event.getMetadataJson().contains("hidden"));
        assertThrows(IllegalStateException.class, event::rejectMutation);
    }
}
