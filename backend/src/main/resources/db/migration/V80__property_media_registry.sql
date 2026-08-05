-- Add property-owned media metadata and link every existing image row to its owning property.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

IF OBJECT_ID('dbo.hotels', 'U') IS NULL
    THROW 51080, 'Required hotels table is missing.', 1;

IF OBJECT_ID('dbo.property_media', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.property_media (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_property_media PRIMARY KEY,
        hotel_id BIGINT NOT NULL,
        source_type VARCHAR(30) NOT NULL,
        public_url NVARCHAR(1000) NOT NULL,
        storage_key VARCHAR(255) NULL,
        content_type VARCHAR(100) NULL,
        file_size_bytes BIGINT NULL,
        width INT NULL,
        height INT NULL,
        checksum_sha256 CHAR(64) NULL,
        alt_text_vi NVARCHAR(255) NULL,
        alt_text_en NVARCHAR(255) NULL,
        status VARCHAR(20) NOT NULL CONSTRAINT DF_property_media_status DEFAULT 'ACTIVE',
        is_demo BIT NOT NULL CONSTRAINT DF_property_media_is_demo DEFAULT 0,
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(255) NULL,
        updated_by NVARCHAR(255) NULL,
        CONSTRAINT FK_property_media_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT CK_property_media_source CHECK (source_type IN ('MANAGED_UPLOAD','EXTERNAL_HTTPS','LEGACY_MANAGED','LEGACY_ASSET')),
        CONSTRAINT CK_property_media_status CHECK (status IN ('ACTIVE','QUARANTINED')),
        CONSTRAINT CK_property_media_size CHECK (file_size_bytes IS NULL OR file_size_bytes > 0),
        CONSTRAINT CK_property_media_dimensions CHECK (
            (width IS NULL AND height IS NULL) OR (width > 0 AND height > 0)
        ),
        CONSTRAINT CK_property_media_checksum CHECK (checksum_sha256 IS NULL OR LEN(checksum_sha256) = 64),
        CONSTRAINT CK_property_media_managed_metadata CHECK (
            source_type <> 'MANAGED_UPLOAD'
            OR (storage_key IS NOT NULL AND content_type IS NOT NULL AND file_size_bytes > 0
                AND width > 0 AND height > 0 AND checksum_sha256 IS NOT NULL)
        )
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.property_media') AND name = 'IX_property_media_hotel_status')
    CREATE INDEX IX_property_media_hotel_status ON dbo.property_media(hotel_id, status, id DESC);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.property_media') AND name = 'UX_property_media_storage_key')
    CREATE UNIQUE INDEX UX_property_media_storage_key ON dbo.property_media(storage_key) WHERE storage_key IS NOT NULL;

IF OBJECT_ID('dbo.room_images', 'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.room_images', 'sort_order') IS NULL
        ALTER TABLE dbo.room_images ADD sort_order INT NOT NULL CONSTRAINT DF_room_images_sort_order_v80 DEFAULT 0;
    IF COL_LENGTH('dbo.room_images', 'alt_text_vi') IS NULL
        ALTER TABLE dbo.room_images ADD alt_text_vi NVARCHAR(255) NULL;
    IF COL_LENGTH('dbo.room_images', 'alt_text_en') IS NULL
        ALTER TABLE dbo.room_images ADD alt_text_en NVARCHAR(255) NULL;

    EXEC sys.sp_executesql N'
        ;WITH ordered AS (
            SELECT id, ROW_NUMBER() OVER (PARTITION BY room_id ORDER BY is_primary DESC, id) - 1 AS next_order
            FROM dbo.room_images
        )
        UPDATE image_row SET sort_order = ordered.next_order
        FROM dbo.room_images image_row
        JOIN ordered ON ordered.id = image_row.id;

        UPDATE image_row
        SET alt_text_vi = COALESCE(NULLIF(LTRIM(RTRIM(image_row.alt_text_vi)), ''''), N''Phòng '' + room_row.room_number),
            alt_text_en = COALESCE(NULLIF(LTRIM(RTRIM(image_row.alt_text_en)), ''''), N''Room '' + room_row.room_number)
        FROM dbo.room_images image_row
        JOIN dbo.rooms room_row ON room_row.id = image_row.room_id;';
END;

IF OBJECT_ID('dbo.property_images', 'U') IS NOT NULL
BEGIN
    UPDATE image_row
    SET alt_text_vi = COALESCE(NULLIF(LTRIM(RTRIM(image_row.alt_text_vi)), ''), hotel.name_vi, hotel.name, N'Ảnh cơ sở')
    FROM dbo.property_images image_row
    JOIN dbo.hotels hotel ON hotel.id = image_row.hotel_id;
END;

IF OBJECT_ID('dbo.room_type_images', 'U') IS NOT NULL
BEGIN
    UPDATE image_row
    SET alt_text_vi = COALESCE(NULLIF(LTRIM(RTRIM(image_row.alt_text_vi)), ''), room_type.name_vi, N'Ảnh loại phòng')
    FROM dbo.room_type_images image_row
    JOIN dbo.room_types room_type ON room_type.id = image_row.room_type_id;
END;

EXEC sys.sp_executesql N'
    ;WITH legacy_media AS (
        SELECT image_row.hotel_id,
               image_row.image_url AS public_url,
               MAX(image_row.alt_text_vi) AS alt_text_vi,
               MAX(image_row.alt_text_en) AS alt_text_en,
               MAX(CASE WHEN image_row.is_demo = 1 THEN 1 ELSE 0 END) AS is_demo
        FROM dbo.property_images image_row
        GROUP BY image_row.hotel_id, image_row.image_url
        UNION ALL
        SELECT room_type.hotel_id, image_row.image_url,
               MAX(image_row.alt_text_vi), MAX(image_row.alt_text_en),
               MAX(CASE WHEN image_row.is_demo = 1 THEN 1 ELSE 0 END)
        FROM dbo.room_type_images image_row
        JOIN dbo.room_types room_type ON room_type.id = image_row.room_type_id
        GROUP BY room_type.hotel_id, image_row.image_url
        UNION ALL
        SELECT room_row.hotel_id, image_row.image_url,
               MAX(image_row.alt_text_vi), MAX(image_row.alt_text_en), 0
        FROM dbo.room_images image_row
        JOIN dbo.rooms room_row ON room_row.id = image_row.room_id
        GROUP BY room_row.hotel_id, image_row.image_url
    ), collapsed AS (
        SELECT hotel_id, public_url, MAX(alt_text_vi) AS alt_text_vi,
               MAX(alt_text_en) AS alt_text_en, MAX(is_demo) AS is_demo
        FROM legacy_media
        WHERE public_url IS NOT NULL AND LTRIM(RTRIM(public_url)) <> ''''
        GROUP BY hotel_id, public_url
    )
    INSERT INTO dbo.property_media(
        hotel_id, source_type, public_url, alt_text_vi, alt_text_en,
        status, is_demo, created_at, updated_at)
    SELECT collapsed.hotel_id,
           CASE
               WHEN collapsed.public_url LIKE ''/api/public/uploads/%'' THEN ''LEGACY_MANAGED''
               WHEN collapsed.public_url LIKE ''https://%'' THEN ''EXTERNAL_HTTPS''
               ELSE ''LEGACY_ASSET''
           END,
           collapsed.public_url, collapsed.alt_text_vi, collapsed.alt_text_en,
           ''ACTIVE'', collapsed.is_demo, SYSUTCDATETIME(), SYSUTCDATETIME()
    FROM collapsed
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.property_media media
        WHERE media.hotel_id = collapsed.hotel_id AND media.public_url = collapsed.public_url
    );';

IF OBJECT_ID('dbo.property_images', 'U') IS NOT NULL AND COL_LENGTH('dbo.property_images', 'media_id') IS NULL
    ALTER TABLE dbo.property_images ADD media_id BIGINT NULL;
IF OBJECT_ID('dbo.room_type_images', 'U') IS NOT NULL AND COL_LENGTH('dbo.room_type_images', 'media_id') IS NULL
    ALTER TABLE dbo.room_type_images ADD media_id BIGINT NULL;
IF OBJECT_ID('dbo.room_images', 'U') IS NOT NULL AND COL_LENGTH('dbo.room_images', 'media_id') IS NULL
    ALTER TABLE dbo.room_images ADD media_id BIGINT NULL;

EXEC sys.sp_executesql N'
    UPDATE image_row SET media_id = media.id
    FROM dbo.property_images image_row
    CROSS APPLY (
        SELECT TOP 1 id FROM dbo.property_media
        WHERE hotel_id = image_row.hotel_id AND public_url = image_row.image_url
        ORDER BY id
    ) media
    WHERE image_row.media_id IS NULL;

    UPDATE image_row SET media_id = media.id
    FROM dbo.room_type_images image_row
    JOIN dbo.room_types room_type ON room_type.id = image_row.room_type_id
    CROSS APPLY (
        SELECT TOP 1 id FROM dbo.property_media
        WHERE hotel_id = room_type.hotel_id AND public_url = image_row.image_url
        ORDER BY id
    ) media
    WHERE image_row.media_id IS NULL;

    UPDATE image_row SET media_id = media.id
    FROM dbo.room_images image_row
    JOIN dbo.rooms room_row ON room_row.id = image_row.room_id
    CROSS APPLY (
        SELECT TOP 1 id FROM dbo.property_media
        WHERE hotel_id = room_row.hotel_id AND public_url = image_row.image_url
        ORDER BY id
    ) media
    WHERE image_row.media_id IS NULL;';

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_property_images_media')
    EXEC(N'ALTER TABLE dbo.property_images ADD CONSTRAINT FK_property_images_media FOREIGN KEY (media_id) REFERENCES dbo.property_media(id);');
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_room_type_images_media')
    EXEC(N'ALTER TABLE dbo.room_type_images ADD CONSTRAINT FK_room_type_images_media FOREIGN KEY (media_id) REFERENCES dbo.property_media(id);');
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_room_images_media')
    EXEC(N'ALTER TABLE dbo.room_images ADD CONSTRAINT FK_room_images_media FOREIGN KEY (media_id) REFERENCES dbo.property_media(id);');

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.property_images') AND name = 'IX_property_images_media')
    EXEC(N'CREATE INDEX IX_property_images_media ON dbo.property_images(media_id);');
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.room_type_images') AND name = 'IX_room_type_images_media')
    EXEC(N'CREATE INDEX IX_room_type_images_media ON dbo.room_type_images(media_id);');
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.room_images') AND name = 'IX_room_images_media')
    EXEC(N'CREATE INDEX IX_room_images_media ON dbo.room_images(media_id);');

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_property_images_sort_order_v80')
    EXEC(N'ALTER TABLE dbo.property_images ADD CONSTRAINT CK_property_images_sort_order_v80 CHECK (sort_order >= 0);');
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_room_type_images_sort_order_v80')
    EXEC(N'ALTER TABLE dbo.room_type_images ADD CONSTRAINT CK_room_type_images_sort_order_v80 CHECK (sort_order >= 0);');
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_room_images_sort_order_v80')
    EXEC(N'ALTER TABLE dbo.room_images ADD CONSTRAINT CK_room_images_sort_order_v80 CHECK (sort_order >= 0);');
