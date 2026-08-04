IF EXISTS (
    SELECT hotel_id, UPPER(LTRIM(RTRIM(code)))
    FROM dbo.room_types
    GROUP BY hotel_id, UPPER(LTRIM(RTRIM(code)))
    HAVING COUNT(*) > 1
)
    THROW 51000, 'Duplicate property-local room type codes must be resolved before V83.', 1;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.room_types') AND name = 'UK_room_types_hotel_code')
    CREATE UNIQUE INDEX UK_room_types_hotel_code ON dbo.room_types(hotel_id, code);

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_room_types_status_v83')
    ALTER TABLE dbo.room_types ADD CONSTRAINT CK_room_types_status_v83 CHECK (status IN ('ACTIVE', 'INACTIVE'));

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_room_types_capacity_v83')
    ALTER TABLE dbo.room_types ADD CONSTRAINT CK_room_types_capacity_v83 CHECK (
        max_guests IS NULL OR (
            max_guests >= 1
            AND (max_adults IS NULL OR max_adults >= 1)
            AND (max_children IS NULL OR max_children >= 0)
            AND (max_adults IS NULL OR max_guests >= max_adults)
            AND (max_adults IS NULL OR max_children IS NULL OR max_guests >= max_adults + max_children)
        )
    );

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_room_types_price_v83')
    ALTER TABLE dbo.room_types ADD CONSTRAINT CK_room_types_price_v83 CHECK (
        base_price >= 0 AND (hourly_price IS NULL OR hourly_price >= 0)
    );
