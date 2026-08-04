-- Additive lifecycle metadata; no property, booking or ownership rows are rewritten.
IF OBJECT_ID('dbo.hotels', 'U') IS NULL OR OBJECT_ID('dbo.users', 'U') IS NULL
    THROW 51000, 'Required hotels or users table is missing.', 1;

IF COL_LENGTH('dbo.hotels', 'lifecycle_action') IS NULL
    ALTER TABLE dbo.hotels ADD lifecycle_action NVARCHAR(30) NULL;

IF COL_LENGTH('dbo.hotels', 'lifecycle_reason') IS NULL
    ALTER TABLE dbo.hotels ADD lifecycle_reason NVARCHAR(500) NULL;

IF COL_LENGTH('dbo.hotels', 'lifecycle_changed_by_user_id') IS NULL
    ALTER TABLE dbo.hotels ADD lifecycle_changed_by_user_id BIGINT NULL;

IF COL_LENGTH('dbo.hotels', 'lifecycle_changed_at') IS NULL
    ALTER TABLE dbo.hotels ADD lifecycle_changed_at DATETIME2 NULL;

IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys
    WHERE parent_object_id = OBJECT_ID('dbo.hotels')
      AND name = 'FK_hotels_lifecycle_changed_by_user'
)
    ALTER TABLE dbo.hotels
        ADD CONSTRAINT FK_hotels_lifecycle_changed_by_user
        FOREIGN KEY (lifecycle_changed_by_user_id) REFERENCES dbo.users(id);

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.hotels')
      AND name = 'IX_hotels_property_lifecycle'
)
    CREATE INDEX IX_hotels_property_lifecycle
        ON dbo.hotels(approval_status, operation_status, status, lifecycle_changed_at DESC, id DESC);

IF OBJECT_ID('dbo.app_module', 'U') IS NOT NULL
   AND OBJECT_ID('dbo.app_function', 'U') IS NOT NULL
BEGIN
    DECLARE @moduleId BIGINT = COALESCE(
        (SELECT TOP 1 id FROM dbo.app_module WHERE code = 'PARTNER' ORDER BY id),
        (SELECT TOP 1 id FROM dbo.app_module WHERE code = 'SYSTEM' ORDER BY id),
        (SELECT TOP 1 id FROM dbo.app_module ORDER BY id));

    IF @moduleId IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM dbo.app_function WHERE code = 'PROPERTY_LIFECYCLE')
        INSERT INTO dbo.app_function(code, name, url, icon, sort_order, module_id)
        VALUES ('PROPERTY_LIFECYCLE', N'Property lifecycle', '/admin/properties', 'pi pi-power-off', 31, @moduleId);
END;

IF OBJECT_ID('dbo.app_role', 'U') IS NOT NULL
   AND OBJECT_ID('dbo.app_function', 'U') IS NOT NULL
   AND OBJECT_ID('dbo.app_role_permission', 'U') IS NOT NULL
BEGIN
    DECLARE @functionId BIGINT = (
        SELECT TOP 1 id FROM dbo.app_function WHERE code = 'PROPERTY_LIFECYCLE');

    IF @functionId IS NOT NULL
    BEGIN
        MERGE dbo.app_role_permission AS target
        USING (
            SELECT role.id AS role_id,
                   @functionId AS function_id,
                   CASE WHEN role.code = 'SUPER_ADMIN' THEN 63 ELSE 33 END AS action_mask
            FROM dbo.app_role role
            WHERE role.code IN ('SUPER_ADMIN', 'ADMIN')
        ) AS source
        ON target.role_id = source.role_id AND target.function_id = source.function_id
        WHEN MATCHED THEN
            UPDATE SET action_mask = target.action_mask | source.action_mask
        WHEN NOT MATCHED THEN
            INSERT(role_id, function_id, action_mask)
            VALUES(source.role_id, source.function_id, source.action_mask);
    END;
END;
