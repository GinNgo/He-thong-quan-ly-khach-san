package com.hotel.controllers;

import com.hotel.dtos.FinancialAuditEventDTO;
import com.hotel.paymentprovider.audit.FinancialAuditQueryService;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
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
@RequestMapping("/api/admin/financial-audit-events")
public class FinancialAuditController {
    private final FinancialAuditQueryService service;

    @GetMapping
    @Permission(function = FunctionCode.AUDIT_LOG, action = ActionCode.VIEW)
    public Page<FinancialAuditEventDTO> search(@RequestParam(required = false) String context,
            @RequestParam(required = false) Long hotelId, @RequestParam(required = false) String aggregateType,
            @RequestParam(required = false) String aggregateId, @RequestParam(required = false) String source,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return service.search(query(context, hotelId, aggregateType, aggregateId, source, correlationId, from, to),
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));
    }

    @GetMapping("/policy")
    @Permission(function = FunctionCode.AUDIT_LOG, action = ActionCode.VIEW)
    public FinancialAuditQueryService.Policy policy() { return service.policy(); }

    @GetMapping(value = "/export", produces = "text/csv")
    @Permission(function = FunctionCode.AUDIT_LOG, action = ActionCode.EXPORT)
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String context,
            @RequestParam(required = false) Long hotelId, @RequestParam(required = false) String aggregateType,
            @RequestParam(required = false) String aggregateId, @RequestParam(required = false) String source,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        byte[] body = service.export(query(context, hotelId, aggregateType, aggregateId, source, correlationId, from, to));
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("financial-audit.csv").build());
        headers.set("X-Audit-Retention-Days", Integer.toString(FinancialAuditQueryService.RETENTION_DAYS));
        headers.set("X-Audit-Export-Max-Rows", Integer.toString(FinancialAuditQueryService.EXPORT_MAX_ROWS));
        return ResponseEntity.ok().headers(headers).body(body);
    }

    private FinancialAuditQueryService.Query query(String context, Long hotelId, String aggregateType, String aggregateId,
            String source, String correlationId, LocalDateTime from, LocalDateTime to) {
        return new FinancialAuditQueryService.Query(context, hotelId, aggregateType, aggregateId, source, correlationId, from, to);
    }
}
