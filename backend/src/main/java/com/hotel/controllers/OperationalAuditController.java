package com.hotel.controllers;

import com.hotel.dtos.OperationalAuditEventDTO;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.OperationalAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/audit-events")
public class OperationalAuditController {

    private final OperationalAuditService auditService;

    @GetMapping
    @Permission(function = FunctionCode.AUDIT_LOG, action = ActionCode.VIEW)
    public Page<OperationalAuditEventDTO> search(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Long hotelId,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String aggregateType,
            @RequestParam(required = false) String aggregateId,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return auditService.search(query(scope, hotelId, domain, eventType, aggregateType, aggregateId, actorId, correlationId, from, to),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Permission(function = FunctionCode.AUDIT_LOG, action = ActionCode.EXPORT)
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Long hotelId,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String aggregateType,
            @RequestParam(required = false) String aggregateId,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        byte[] body = auditService.exportCsv(query(scope, hotelId, domain, eventType, aggregateType, aggregateId, actorId, correlationId, from, to));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("operational-audit.csv").build());
        headers.setContentLength(body.length);
        return ResponseEntity.ok().headers(headers).body(body);
    }

    private OperationalAuditService.AuditQuery query(String scope, Long hotelId, String domain, String eventType,
                                                     String aggregateType, String aggregateId, Long actorId,
                                                     String correlationId, LocalDateTime from, LocalDateTime to) {
        return new OperationalAuditService.AuditQuery(scope, hotelId, domain, eventType, aggregateType,
                aggregateId, actorId, correlationId, from, to);
    }
}
