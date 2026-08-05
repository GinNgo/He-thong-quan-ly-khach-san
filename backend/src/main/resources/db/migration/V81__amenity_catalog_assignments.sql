SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

IF OBJECT_ID('dbo.hotels', 'U') IS NULL OR OBJECT_ID('dbo.room_types', 'U') IS NULL
    THROW 51087, 'Required property inventory tables are missing.', 1;

IF OBJECT_ID('dbo.amenities', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.amenities (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_amenities PRIMARY KEY,
        code VARCHAR(50) NOT NULL,
        name_vi NVARCHAR(255) NOT NULL,
        name_en NVARCHAR(255) NULL,
        category VARCHAR(30) NOT NULL,
        icon VARCHAR(100) NULL,
        sort_order INT NOT NULL CONSTRAINT DF_amenities_sort_order DEFAULT 0,
        status VARCHAR(20) NOT NULL CONSTRAINT DF_amenities_status DEFAULT 'ACTIVE',
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(255) NULL,
        updated_by NVARCHAR(255) NULL,
        CONSTRAINT CK_amenities_category CHECK (category IN ('GENERAL','INTERNET','PARKING','FOOD','WELLNESS','ROOM','ACCESSIBILITY')),
        CONSTRAINT CK_amenities_status CHECK (status IN ('ACTIVE','INACTIVE')),
        CONSTRAINT CK_amenities_sort_order CHECK (sort_order >= 0)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.amenities') AND name = 'UX_amenities_code')
    CREATE UNIQUE INDEX UX_amenities_code ON dbo.amenities(code);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.amenities') AND name = 'IX_amenities_status_category_sort')
    CREATE INDEX IX_amenities_status_category_sort ON dbo.amenities(status, category, sort_order, id);

IF OBJECT_ID('dbo.property_amenities', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.property_amenities (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_property_amenities PRIMARY KEY,
        hotel_id BIGINT NOT NULL,
        amenity_id BIGINT NOT NULL,
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(255) NULL,
        updated_by NVARCHAR(255) NULL,
        CONSTRAINT FK_property_amenities_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_property_amenities_amenity FOREIGN KEY (amenity_id) REFERENCES dbo.amenities(id)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.property_amenities') AND name = 'UX_property_amenities_hotel_amenity')
    CREATE UNIQUE INDEX UX_property_amenities_hotel_amenity ON dbo.property_amenities(hotel_id, amenity_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.property_amenities') AND name = 'IX_property_amenities_amenity_hotel')
    CREATE INDEX IX_property_amenities_amenity_hotel ON dbo.property_amenities(amenity_id, hotel_id);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.room_types') AND name = 'UX_room_types_id_hotel_v81')
    CREATE UNIQUE INDEX UX_room_types_id_hotel_v81 ON dbo.room_types(id, hotel_id);

IF OBJECT_ID('dbo.room_type_amenities', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.room_type_amenities (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_room_type_amenities PRIMARY KEY,
        hotel_id BIGINT NOT NULL,
        room_type_id BIGINT NOT NULL,
        amenity_id BIGINT NOT NULL,
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(255) NULL,
        updated_by NVARCHAR(255) NULL,
        CONSTRAINT FK_room_type_amenities_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_room_type_amenities_room_type_hotel FOREIGN KEY (room_type_id, hotel_id) REFERENCES dbo.room_types(id, hotel_id),
        CONSTRAINT FK_room_type_amenities_amenity FOREIGN KEY (amenity_id) REFERENCES dbo.amenities(id)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.room_type_amenities') AND name = 'UX_room_type_amenities_hotel_room_type_amenity')
    CREATE UNIQUE INDEX UX_room_type_amenities_hotel_room_type_amenity ON dbo.room_type_amenities(hotel_id, room_type_id, amenity_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.room_type_amenities') AND name = 'IX_room_type_amenities_amenity_hotel_room_type')
    CREATE INDEX IX_room_type_amenities_amenity_hotel_room_type ON dbo.room_type_amenities(amenity_id, hotel_id, room_type_id);

;WITH seed(code, name_vi, name_en, category, icon, sort_order) AS (
    SELECT 'WIFI', N'Wi-Fi miễn phí', N'Free Wi-Fi', 'INTERNET', 'pi pi-wifi', 10 UNION ALL
    SELECT 'PARKING', N'Bãi đỗ xe', N'Parking', 'PARKING', 'pi pi-car', 20 UNION ALL
    SELECT 'BREAKFAST', N'Bữa sáng', N'Breakfast', 'FOOD', 'pi pi-sun', 30 UNION ALL
    SELECT 'POOL', N'Hồ bơi', N'Swimming pool', 'WELLNESS', 'pi pi-wave-pulse', 40 UNION ALL
    SELECT 'GYM', N'Phòng tập', N'Fitness center', 'WELLNESS', 'pi pi-heart', 50 UNION ALL
    SELECT 'AIR_CONDITIONING', N'Điều hòa', N'Air conditioning', 'ROOM', 'pi pi-snowflake', 60 UNION ALL
    SELECT 'NON_SMOKING', N'Không hút thuốc', N'Non-smoking', 'ROOM', 'pi pi-ban', 70 UNION ALL
    SELECT 'ACCESSIBLE', N'Hỗ trợ tiếp cận', N'Accessible facilities', 'ACCESSIBILITY', 'pi pi-universal-access', 80
)
INSERT dbo.amenities(code, name_vi, name_en, category, icon, sort_order, status, created_at, updated_at)
SELECT seed.code, seed.name_vi, seed.name_en, seed.category, seed.icon, seed.sort_order,
       'ACTIVE', SYSUTCDATETIME(), SYSUTCDATETIME()
FROM seed
WHERE NOT EXISTS (SELECT 1 FROM dbo.amenities amenity WHERE amenity.code = seed.code);
