SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

IF OBJECT_ID('dbo.reservation_charge_lines', 'U') IS NOT NULL
   AND COL_LENGTH('dbo.reservation_charge_lines', 'legacy_service_item_id') IS NULL
BEGIN
    ALTER TABLE dbo.reservation_charge_lines ADD legacy_service_item_id BIGINT NULL;
END;
GO

IF OBJECT_ID('dbo.reservation_charge_lines', 'U') IS NOT NULL
   AND OBJECT_ID('dbo.reservation_services', 'U') IS NOT NULL
   AND NOT EXISTS (
       SELECT 1
       FROM sys.foreign_keys
       WHERE parent_object_id = OBJECT_ID('dbo.reservation_charge_lines')
         AND name = 'FK_charge_line_legacy_service_item'
   )
BEGIN
    ALTER TABLE dbo.reservation_charge_lines ADD CONSTRAINT FK_charge_line_legacy_service_item
        FOREIGN KEY (legacy_service_item_id) REFERENCES dbo.reservation_services(id);
END;

IF OBJECT_ID('dbo.reservation_charge_lines', 'U') IS NOT NULL
   AND OBJECT_ID('dbo.reservation_services', 'U') IS NOT NULL
BEGIN
    IF EXISTS (
        SELECT 1
        FROM dbo.reservation_services legacy
        LEFT JOIN dbo.reservations reservation ON reservation.id = legacy.reservation_id
        LEFT JOIN dbo.services service ON service.id = legacy.service_id
        WHERE UPPER(LTRIM(RTRIM(COALESCE(legacy.status, '')))) = 'ACTIVE'
          AND (
              reservation.id IS NULL
              OR reservation.hotel_id IS NULL
              OR service.id IS NULL
              OR legacy.quantity IS NULL
              OR legacy.quantity <= 0
              OR legacy.price IS NULL
              OR legacy.price < 0
              OR legacy.total_amount IS NULL
              OR legacy.total_amount < 0
              OR legacy.total_amount <> legacy.price * legacy.quantity
              OR NOT (
                  (service.is_system = 1 AND service.hotel_id IS NULL)
                  OR (service.is_system = 0 AND service.hotel_id = reservation.hotel_id)
              )
          )
    )
    BEGIN
        THROW 51043, 'Legacy reservation service rows require manual reconciliation before charge-line backfill.', 1;
    END;

    INSERT INTO dbo.reservation_charge_lines (
        hotel_id,
        reservation_id,
        charge_type,
        source_id,
        source_version,
        code,
        name,
        description,
        unit_price,
        quantity,
        tax_amount,
        discount_amount,
        total_amount,
        service_used_at,
        actor_id,
        reverses_line_id,
        legacy_service_item_id,
        created_at
    )
    SELECT
        reservation.hotel_id,
        legacy.reservation_id,
        'SERVICE',
        legacy.service_id,
        CONCAT('LEGACY-SERVICE-ITEM:', legacy.id),
        LEFT(service.code, 80),
        LEFT(COALESCE(NULLIF(LTRIM(RTRIM(service.name_vi)), ''), NULLIF(LTRIM(RTRIM(service.name_en)), ''), N'Legacy service'), 255),
        LEFT(COALESCE(NULLIF(LTRIM(RTRIM(service.description_vi)), ''), NULLIF(LTRIM(RTRIM(service.description_en)), '')), 1000),
        legacy.price,
        CAST(legacy.quantity AS DECIMAL(19,3)),
        0,
        0,
        legacy.total_amount,
        COALESCE(legacy.used_at, legacy.created_at, SYSUTCDATETIME()),
        legacy.added_by_user_id,
        NULL,
        legacy.id,
        COALESCE(legacy.created_at, SYSUTCDATETIME())
    FROM dbo.reservation_services legacy
    JOIN dbo.reservations reservation ON reservation.id = legacy.reservation_id
    JOIN dbo.services service ON service.id = legacy.service_id
    WHERE UPPER(LTRIM(RTRIM(COALESCE(legacy.status, '')))) = 'ACTIVE'
      AND NOT EXISTS (
          SELECT 1
          FROM dbo.reservation_charge_lines charge_line
          WHERE charge_line.legacy_service_item_id = legacy.id
      );
END;

IF OBJECT_ID('dbo.reservation_charge_lines', 'U') IS NOT NULL
   AND NOT EXISTS (
       SELECT 1
       FROM sys.indexes
       WHERE object_id = OBJECT_ID('dbo.reservation_charge_lines')
         AND name = 'UX_charge_lines_legacy_service_item'
   )
BEGIN
    CREATE UNIQUE INDEX UX_charge_lines_legacy_service_item
        ON dbo.reservation_charge_lines(legacy_service_item_id)
        WHERE legacy_service_item_id IS NOT NULL;
END;
