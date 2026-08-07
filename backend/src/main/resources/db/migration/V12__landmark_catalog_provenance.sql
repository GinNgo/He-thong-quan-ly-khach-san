IF COL_LENGTH('locations', 'source_provider') IS NULL
    ALTER TABLE locations ADD source_provider VARCHAR(50) NULL;

IF COL_LENGTH('locations', 'source_object_type') IS NULL
    ALTER TABLE locations ADD source_object_type VARCHAR(50) NULL;

IF COL_LENGTH('locations', 'source_object_id') IS NULL
    ALTER TABLE locations ADD source_object_id VARCHAR(255) NULL;

IF COL_LENGTH('locations', 'source_updated_at') IS NULL
    ALTER TABLE locations ADD source_updated_at DATETIME2 NULL;

IF COL_LENGTH('locations', 'last_seen_at') IS NULL
    ALTER TABLE locations ADD last_seen_at DATETIME2 NULL;

IF COL_LENGTH('locations', 'data_quality_status') IS NULL
    ALTER TABLE locations ADD data_quality_status VARCHAR(30) NULL;

IF COL_LENGTH('locations', 'manual_override') IS NULL
    ALTER TABLE locations ADD manual_override BIT NOT NULL CONSTRAINT DF_locations_manual_override DEFAULT 0;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('locations')
      AND name = 'UX_locations_landmark_source'
)
    CREATE UNIQUE INDEX UX_locations_landmark_source
        ON locations(source_provider, source_object_type, source_object_id)
        WHERE location_type = 'LANDMARK'
          AND source_provider IS NOT NULL
          AND source_object_type IS NOT NULL
          AND source_object_id IS NOT NULL;
