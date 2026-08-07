package com.hotel.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "location_import_runs",
        uniqueConstraints = @UniqueConstraint(name = "UQ_location_import_runs_run_id", columnNames = "run_id"),
        indexes = @Index(name = "IX_location_import_runs_status_started", columnList = "status,started_at"))
public class LocationImportRun extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "source_provider", nullable = false, length = 80)
    private String sourceProvider;

    @Column(name = "source_version", length = 120)
    private String sourceVersion;

    @Column(name = "source_checksum", length = 128)
    private String sourceChecksum;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "generated_count", nullable = false)
    private int generatedCount;

    @Column(name = "imported_count", nullable = false)
    private int importedCount;

    @Column(name = "quarantined_count", nullable = false)
    private int quarantinedCount;

    @Column(name = "coverage_json", columnDefinition = "nvarchar(max)")
    private String coverageJson;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "error_message", columnDefinition = "nvarchar(2000)")
    private String errorMessage;
}
