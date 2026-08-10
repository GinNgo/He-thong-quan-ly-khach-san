;WITH lifecycle_permissions AS (
    SELECT role.id AS role_id, function_row.id AS function_id
    FROM dbo.app_role role
    CROSS JOIN dbo.app_function function_row
    WHERE role.code IN (
        'SUPER_ADMIN', 'ADMIN', 'PROPERTY_OWNER', 'HOTEL_ADMIN',
        'HOTEL_MANAGER', 'RECEPTIONIST'
    )
      AND function_row.code IN (
        'RESERVATION_ASSIGNMENT', 'CHECKIN', 'CHECKOUT',
        'RESERVATION_CANCEL', 'RESERVATION_NO_SHOW'
    )
)
MERGE dbo.app_role_permission AS target
USING lifecycle_permissions AS source
ON target.role_id = source.role_id AND target.function_id = source.function_id
WHEN MATCHED THEN
    UPDATE SET action_mask = target.action_mask | 65
WHEN NOT MATCHED THEN
    INSERT(role_id, function_id, action_mask)
    VALUES(source.role_id, source.function_id, 65);
