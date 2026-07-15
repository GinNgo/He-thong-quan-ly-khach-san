IF OBJECT_ID('dbo.backup_app_function_v9', 'U') IS NULL
    SELECT * INTO dbo.backup_app_function_v9 FROM dbo.app_function;

IF OBJECT_ID('dbo.backup_app_role_permission_v9', 'U') IS NULL
    SELECT * INTO dbo.backup_app_role_permission_v9 FROM dbo.app_role_permission;

IF COL_LENGTH('app_role', 'status') IS NULL
    ALTER TABLE app_role ADD status NVARCHAR(20) NOT NULL CONSTRAINT DF_app_role_status DEFAULT 'ACTIVE';

IF COL_LENGTH('app_role', 'system_role') IS NULL
    ALTER TABLE app_role ADD system_role BIT NOT NULL CONSTRAINT DF_app_role_system DEFAULT 0;

EXEC(N'UPDATE app_role SET system_role = 1 WHERE code IN (''SUPER_ADMIN'',''ADMIN'',''CUSTOMER'',''PROPERTY_OWNER'',''HOTEL_ADMIN'',''HOTEL_MANAGER'',''RECEPTIONIST'',''ACCOUNTANT'')');

IF COL_LENGTH('rooms', 'note') IS NULL
    ALTER TABLE rooms ADD note NVARCHAR(1000) NULL;

IF EXISTS (SELECT 1 FROM app_function WHERE code = 'HOTEL')
   AND NOT EXISTS (SELECT 1 FROM app_function WHERE code = 'HOTEL_SERVICE')
    UPDATE app_function SET code = 'HOTEL_SERVICE', url = '/admin/services' WHERE code = 'HOTEL';

DECLARE @aliases TABLE(alias_code NVARCHAR(100), canonical_code NVARCHAR(100));
INSERT INTO @aliases VALUES
('CHECKIN_OPERATION','RESERVATION'),
('IN_HOUSE_GUESTS','RESERVATION'),
('CHECKOUT_OPERATION','RESERVATION'),
('HOUSEKEEPING_OPERATION','PROPERTY_ROOMS'),
('MAINTENANCE_OPERATION','PROPERTY_ROOMS'),
('ACTIVE_SUBSCRIPTIONS','PROPERTY_OWNERS');

;WITH masks AS (
    SELECT rp.role_id, canonical.id AS function_id,
           MAX(rp.action_mask & 1) + MAX(rp.action_mask & 2) + MAX(rp.action_mask & 4)
           + MAX(rp.action_mask & 8) + MAX(rp.action_mask & 16) + MAX(rp.action_mask & 32) AS action_mask
    FROM app_role_permission rp
    JOIN app_function source_function ON source_function.id = rp.function_id
    JOIN @aliases mapping ON mapping.alias_code = source_function.code
    JOIN app_function canonical ON canonical.code = mapping.canonical_code
    GROUP BY rp.role_id, canonical.id
)
MERGE app_role_permission AS target
USING masks AS source
ON target.role_id = source.role_id AND target.function_id = source.function_id
WHEN MATCHED THEN UPDATE SET action_mask = target.action_mask | source.action_mask
WHEN NOT MATCHED THEN INSERT(role_id, function_id, action_mask)
VALUES(source.role_id, source.function_id, source.action_mask);

DELETE rp FROM app_role_permission rp
JOIN app_function f ON f.id = rp.function_id
JOIN @aliases mapping ON mapping.alias_code = f.code;

DELETE f FROM app_function f JOIN @aliases mapping ON mapping.alias_code = f.code;

-- Payment reconciliation has no page/API yet. Keep it out of navigation instead
-- of sending users to a placeholder or 404 route.
DELETE rp FROM app_role_permission rp
JOIN app_function f ON f.id = rp.function_id
WHERE f.code IN ('FINANCE', 'RESERVATION_PAYMENT');
DELETE FROM app_function WHERE code IN ('FINANCE', 'RESERVATION_PAYMENT');
