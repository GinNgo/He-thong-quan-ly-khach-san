DECLARE @hotelModuleId BIGINT = (
    SELECT TOP 1 id
    FROM dbo.app_module
    WHERE code = 'HOTEL' OR name = N'Khach san'
    ORDER BY id
);

IF @hotelModuleId IS NOT NULL
BEGIN
    INSERT INTO dbo.app_function(code, name, url, icon, sort_order, module_id)
    SELECT 'RESERVATION_AMEND', N'Thay doi dat phong', NULL, 'pi pi-calendar-plus', 44, @hotelModuleId
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.app_function existing WHERE existing.code = 'RESERVATION_AMEND'
    );
END;

;WITH defaults AS (
    SELECT role.id AS role_id,
           function_row.id AS function_id,
           CASE
               WHEN role.code IN ('SUPER_ADMIN', 'ADMIN') THEN 63
               ELSE 5
           END AS action_mask
    FROM dbo.app_role role
    CROSS JOIN dbo.app_function function_row
    WHERE role.code IN ('SUPER_ADMIN', 'ADMIN', 'PROPERTY_OWNER', 'HOTEL_ADMIN', 'HOTEL_MANAGER', 'RECEPTIONIST')
      AND function_row.code = 'RESERVATION_AMEND'
)
MERGE dbo.app_role_permission AS target
USING defaults AS source
ON target.role_id = source.role_id AND target.function_id = source.function_id
WHEN MATCHED THEN
    UPDATE SET action_mask = target.action_mask | source.action_mask
WHEN NOT MATCHED THEN
    INSERT(role_id, function_id, action_mask)
    VALUES(source.role_id, source.function_id, source.action_mask);
