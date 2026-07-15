IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('hotels') AND name = 'IX_hotels_public_discovery'
)
BEGIN
    CREATE INDEX IX_hotels_public_discovery
        ON hotels(approval_status, operation_status, province_id, ward_id)
        INCLUDE(normalized_name, normalized_address, name_vi, property_type, main_image, average_rating);
END;
