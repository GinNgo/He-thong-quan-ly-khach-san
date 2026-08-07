IF OBJECT_ID('location_import_runs', 'U') IS NULL
BEGIN
    CREATE TABLE location_import_runs (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        run_id VARCHAR(64) NOT NULL,
        source_provider VARCHAR(80) NOT NULL,
        source_version VARCHAR(120) NULL,
        source_checksum VARCHAR(128) NULL,
        started_at DATETIME2 NOT NULL,
        completed_at DATETIME2 NULL,
        generated_count INT NOT NULL CONSTRAINT DF_location_import_runs_generated DEFAULT 0,
        imported_count INT NOT NULL CONSTRAINT DF_location_import_runs_imported DEFAULT 0,
        quarantined_count INT NOT NULL CONSTRAINT DF_location_import_runs_quarantined DEFAULT 0,
        coverage_json NVARCHAR(MAX) NULL,
        status VARCHAR(24) NOT NULL,
        error_message NVARCHAR(2000) NULL,
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by VARCHAR(255) NULL,
        updated_by VARCHAR(255) NULL,
        CONSTRAINT UQ_location_import_runs_run_id UNIQUE (run_id)
    );
END;

IF OBJECT_ID('landmark_import_issues', 'U') IS NULL
BEGIN
    CREATE TABLE landmark_import_issues (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        run_id BIGINT NOT NULL,
        source_provider VARCHAR(80) NULL,
        source_object_type VARCHAR(50) NULL,
        source_object_id VARCHAR(255) NULL,
        reason_code VARCHAR(80) NOT NULL,
        raw_name NVARCHAR(255) NULL,
        normalized_name NVARCHAR(255) NULL,
        proposed_province VARCHAR(120) NULL,
        proposed_latitude DECIMAL(9,6) NULL,
        proposed_longitude DECIMAL(9,6) NULL,
        match_score DECIMAL(6,5) NULL,
        review_status VARCHAR(24) NOT NULL,
        details_json NVARCHAR(MAX) NULL,
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by VARCHAR(255) NULL,
        updated_by VARCHAR(255) NULL,
        CONSTRAINT FK_landmark_import_issues_run FOREIGN KEY (run_id) REFERENCES location_import_runs(id)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_location_import_runs_status_started')
    CREATE INDEX IX_location_import_runs_status_started ON location_import_runs(status, started_at DESC);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_landmark_import_issues_run_review')
    CREATE INDEX IX_landmark_import_issues_run_review ON landmark_import_issues(run_id, review_status);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_landmark_import_issues_source_key')
    CREATE INDEX IX_landmark_import_issues_source_key
        ON landmark_import_issues(source_provider, source_object_type, source_object_id);
