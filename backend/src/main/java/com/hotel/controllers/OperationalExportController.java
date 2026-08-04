package com.hotel.controllers;

import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.OperationalExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/management/operational-exports")
@RequiredArgsConstructor
public class OperationalExportController {
    private final OperationalExportService service;

    @GetMapping
    @Permission(function = FunctionCode.REPORT, action = ActionCode.EXPORT)
    public ResponseEntity<byte[]> export(
            @RequestParam Long propertyId,
            @RequestParam OperationalExportService.Dataset dataset,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var artifact = service.export(propertyId, dataset, status, from, to);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.filename() + "\"")
                .header("X-Export-Schema", "operational-" + dataset.name().toLowerCase(java.util.Locale.ROOT) + "-v1")
                .header("X-Export-Row-Count", Long.toString(artifact.rowCount()))
                .header("X-Export-Checksum", artifact.checksum())
                .header("Access-Control-Expose-Headers", "Content-Disposition, X-Export-Schema, X-Export-Row-Count, X-Export-Checksum")
                .body(artifact.content());
    }
}
