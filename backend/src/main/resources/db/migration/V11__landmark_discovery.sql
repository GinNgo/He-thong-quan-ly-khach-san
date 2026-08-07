IF COL_LENGTH('locations', 'category') IS NULL
    ALTER TABLE locations ADD category VARCHAR(50) NULL;

IF COL_LENGTH('locations', 'default_radius_km') IS NULL
    ALTER TABLE locations ADD default_radius_km DECIMAL(8,2) NULL;

IF COL_LENGTH('locations', 'popularity_score') IS NULL
    ALTER TABLE locations ADD popularity_score INT NOT NULL CONSTRAINT DF_locations_popularity_score DEFAULT 0;

IF COL_LENGTH('locations', 'description_vi') IS NULL
    ALTER TABLE locations ADD description_vi NVARCHAR(1000) NULL;

IF COL_LENGTH('locations', 'description_en') IS NULL
    ALTER TABLE locations ADD description_en NVARCHAR(1000) NULL;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('locations')
      AND name = 'IX_locations_landmark_discovery'
)
    CREATE INDEX IX_locations_landmark_discovery
        ON locations(location_type, status, parent_id, popularity_score);

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('locations')
      AND name = 'IX_locations_landmark_coordinates'
)
    CREATE INDEX IX_locations_landmark_coordinates
        ON locations(latitude, longitude)
        WHERE location_type = 'LANDMARK' AND status = 'ACTIVE';
