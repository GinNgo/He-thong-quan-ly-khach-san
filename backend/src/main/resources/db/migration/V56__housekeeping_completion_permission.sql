IF OBJECT_ID('dbo.app_role', 'U') IS NOT NULL
   AND OBJECT_ID('dbo.app_function', 'U') IS NOT NULL
   AND OBJECT_ID('dbo.app_role_permission', 'U') IS NOT NULL
BEGIN
    ;WITH completion_permission AS (
        SELECT role.id AS role_id, function_row.id AS function_id, 37 AS action_mask
        FROM dbo.app_role role
        CROSS JOIN dbo.app_function function_row
        WHERE role.code = 'HOUSEKEEPING'
          AND function_row.code = 'HOUSEKEEPING'
    )
    MERGE dbo.app_role_permission AS target
    USING completion_permission AS source
    ON target.role_id = source.role_id AND target.function_id = source.function_id
    WHEN MATCHED THEN
        UPDATE SET action_mask = target.action_mask | source.action_mask
    WHEN NOT MATCHED THEN
        INSERT(role_id, function_id, action_mask)
        VALUES(source.role_id, source.function_id, source.action_mask);
END;
GO
