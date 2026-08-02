-- Establish a server-owned scope for property services and immutable system templates.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

IF OBJECT_ID('dbo.services', 'U') IS NULL
    THROW 51020, 'Required services table is missing.', 1;

IF COL_LENGTH('dbo.services', 'hotel_id') IS NULL
    ALTER TABLE dbo.services ADD hotel_id BIGINT NULL;
IF COL_LENGTH('dbo.services', 'is_system') IS NULL
    ALTER TABLE dbo.services ADD is_system BIT NOT NULL CONSTRAINT DF_services_is_system_v39 DEFAULT 0;

UPDATE dbo.services
SET is_system = 0
WHERE is_system IS NULL;

-- Legacy global rows have no tenant owner, so they become shared system templates.
UPDATE dbo.services
SET is_system = 1
WHERE hotel_id IS NULL AND is_system = 0;

IF EXISTS (
    SELECT 1
    FROM dbo.services
    WHERE (is_system = 1 AND hotel_id IS NOT NULL)
       OR (is_system = 0 AND hotel_id IS NULL)
)
    THROW 51021, 'Services contain an invalid ownership scope.', 1;

IF EXISTS (
    SELECT hotel_id, code
    FROM dbo.services
    WHERE is_system = 0
    GROUP BY hotel_id, code
    HAVING COUNT(*) > 1
)
    THROW 51022, 'Tenant service codes are duplicated.', 1;

IF EXISTS (
    SELECT code
    FROM dbo.services
    WHERE is_system = 1
    GROUP BY code
    HAVING COUNT(*) > 1
)
    THROW 51023, 'System service template codes are duplicated.', 1;

-- Replace the legacy global code uniqueness with scope-aware filtered indexes.
DECLARE @indexName sysname;
DECLARE @isUniqueConstraint bit;
DECLARE @dropSql nvarchar(1000);
DECLARE legacy_unique_indexes CURSOR LOCAL FAST_FORWARD FOR
    SELECT i.name, i.is_unique_constraint
    FROM sys.indexes i
    WHERE i.object_id = OBJECT_ID('dbo.services')
      AND i.is_unique = 1
      AND i.is_primary_key = 0
      AND i.name NOT IN ('UX_services_tenant_code', 'UX_services_system_code')
      AND EXISTS (
          SELECT 1
          FROM sys.index_columns ic
          JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
          WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND c.name = 'code'
      )
      AND NOT EXISTS (
          SELECT 1
          FROM sys.index_columns ic
          JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
          WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND c.name <> 'code'
      );

OPEN legacy_unique_indexes;
FETCH NEXT FROM legacy_unique_indexes INTO @indexName, @isUniqueConstraint;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @dropSql = CASE WHEN @isUniqueConstraint = 1
        THEN N'ALTER TABLE dbo.services DROP CONSTRAINT ' + QUOTENAME(@indexName)
        ELSE N'DROP INDEX ' + QUOTENAME(@indexName) + N' ON dbo.services' END;
    EXEC sp_executesql @dropSql;
    FETCH NEXT FROM legacy_unique_indexes INTO @indexName, @isUniqueConstraint;
END;
CLOSE legacy_unique_indexes;
DEALLOCATE legacy_unique_indexes;

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_services_scope')
BEGIN
    ALTER TABLE dbo.services ADD CONSTRAINT CK_services_scope CHECK (
        (is_system = 1 AND hotel_id IS NULL) OR
        (is_system = 0 AND hotel_id IS NOT NULL)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_services_tenant_code' AND object_id = OBJECT_ID('dbo.services'))
    CREATE UNIQUE INDEX UX_services_tenant_code ON dbo.services(hotel_id, code) WHERE is_system = 0;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_services_system_code' AND object_id = OBJECT_ID('dbo.services'))
    CREATE UNIQUE INDEX UX_services_system_code ON dbo.services(code) WHERE is_system = 1;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_services_hotel_status' AND object_id = OBJECT_ID('dbo.services'))
    CREATE INDEX IX_services_hotel_status ON dbo.services(hotel_id, status);
