IF COL_LENGTH('locations', 'source_code') IS NOT NULL
   AND NOT EXISTS (
       SELECT 1
       FROM sys.indexes
       WHERE object_id = OBJECT_ID('locations')
         AND name = 'IX_locations_type_source_status'
   )
BEGIN
    CREATE INDEX IX_locations_type_source_status
        ON locations(location_type, source_code, status);
END;

IF COL_LENGTH('locations', 'source_code') IS NOT NULL
   AND NOT EXISTS (
       SELECT 1
       FROM sys.indexes
       WHERE object_id = OBJECT_ID('locations')
         AND name = 'IX_locations_type_source_status'
   )
BEGIN
    THROW 51000, 'Failed to create IX_locations_type_source_status.', 1;
END;
