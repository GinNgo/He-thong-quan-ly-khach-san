DECLARE @hotelModuleId BIGINT = (
    SELECT TOP 1 id
    FROM dbo.app_module
    WHERE code = 'HOTEL' OR name = N'Khách sạn'
    ORDER BY id
);

IF @hotelModuleId IS NOT NULL
BEGIN
    INSERT INTO dbo.app_function(code, name, url, icon, sort_order, module_id)
    SELECT source.code, source.name, NULL, source.icon, source.sort_order, @hotelModuleId
    FROM (VALUES
        ('RESERVATION_ASSIGNMENT', N'Gán phòng lưu trú', 'pi pi-link', 40),
        ('CHECKIN', N'Nhận phòng', 'pi pi-sign-in', 41),
        ('RESERVATION_CANCEL', N'Hủy đặt phòng vận hành', 'pi pi-times-circle', 42),
        ('RESERVATION_NO_SHOW', N'Đánh dấu khách không đến', 'pi pi-user-minus', 43)
    ) source(code, name, icon, sort_order)
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.app_function existing WHERE existing.code = source.code
    );
END;

;WITH defaults AS (
    SELECT role.id AS role_id,
           function_row.id AS function_id,
           CASE
               WHEN role.code IN ('SUPER_ADMIN', 'ADMIN') THEN 63
               WHEN function_row.code = 'RESERVATION_ASSIGNMENT' THEN 5
               ELSE 4
           END AS action_mask
    FROM dbo.app_role role
    CROSS JOIN dbo.app_function function_row
    WHERE role.code IN ('SUPER_ADMIN', 'ADMIN', 'PROPERTY_OWNER', 'HOTEL_ADMIN', 'HOTEL_MANAGER', 'RECEPTIONIST')
      AND function_row.code IN ('RESERVATION_ASSIGNMENT', 'CHECKIN', 'RESERVATION_CANCEL', 'RESERVATION_NO_SHOW')
)
MERGE dbo.app_role_permission AS target
USING defaults AS source
ON target.role_id = source.role_id AND target.function_id = source.function_id
WHEN MATCHED THEN
    UPDATE SET action_mask = target.action_mask | source.action_mask
WHEN NOT MATCHED THEN
    INSERT(role_id, function_id, action_mask)
    VALUES(source.role_id, source.function_id, source.action_mask);
