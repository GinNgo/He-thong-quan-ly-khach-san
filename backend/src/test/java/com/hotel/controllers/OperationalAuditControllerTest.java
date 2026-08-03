package com.hotel.controllers;

import com.hotel.dtos.OperationalAuditEventDTO;
import com.hotel.services.OperationalAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationalAuditControllerTest {

    @Test
    void clampsPageSizeAndReturnsSecureCsvDownload() {
        OperationalAuditService service = mock(OperationalAuditService.class);
        OperationalAuditController controller = new OperationalAuditController(service);
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(new OperationalAuditEventDTO(
                1L, "SYSTEM", null, "ROLE", "ROLE_UPDATED", "ROLE", "1", "USER", 9L,
                "Updated", "{}", "{}", "corr-1", LocalDateTime.now()))));
        when(service.exportCsv(any())).thenReturn("id\r\n1\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        controller.search(null, null, null, null, null, null, null, null, null, null, 0, 1000);
        org.mockito.Mockito.verify(service).search(any(), org.mockito.ArgumentMatchers.argThat(pageable -> pageable.getPageSize() == 100));

        ResponseEntity<byte[]> response = controller.export(null, null, null, null, null, null, null, null, null, null);
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("operational-audit.csv");
        assertThat(response.getBody()).isNotEmpty();
    }
}
