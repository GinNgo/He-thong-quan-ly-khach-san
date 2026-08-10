IF OBJECT_ID('dbo.app_function', 'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.app_function', 'supported_action_mask') IS NULL
        ALTER TABLE dbo.app_function ADD supported_action_mask INT NOT NULL
            CONSTRAINT DF_app_function_supported_action_mask DEFAULT 127;

    IF COL_LENGTH('dbo.app_function', 'scope_type') IS NULL
        ALTER TABLE dbo.app_function ADD scope_type VARCHAR(20) NOT NULL
            CONSTRAINT DF_app_function_scope_type DEFAULT 'PROPERTY';

    IF COL_LENGTH('dbo.app_function', 'active') IS NULL
        ALTER TABLE dbo.app_function ADD active BIT NOT NULL
            CONSTRAINT DF_app_function_active DEFAULT 1;

    IF COL_LENGTH('dbo.app_function', 'version') IS NULL
        ALTER TABLE dbo.app_function ADD version BIGINT NOT NULL
            CONSTRAINT DF_app_function_version DEFAULT 0;

    UPDATE dbo.app_function
       SET scope_type = CASE
           WHEN code IN ('SYSTEM', 'USER', 'ROLE', 'ROLE_PERMISSION', 'PLATFORM_BILLING',
                         'PLATFORM_REFUND', 'PLATFORM_REVENUE', 'PAYMENT_READINESS', 'AUDIT_LOG')
               THEN 'PLATFORM'
           WHEN code IN ('CUSTOMER', 'AI_CHAT') THEN 'SELF'
           ELSE 'PROPERTY'
       END;

    UPDATE dbo.app_function
       SET supported_action_mask = CASE
           WHEN code IN ('REPORT', 'PLATFORM_REVENUE', 'AUDIT_LOG') THEN 17
           WHEN code IN ('CHECKIN', 'CHECKOUT', 'HOUSEKEEPING', 'RESERVATION_ASSIGNMENT',
                         'RESERVATION_CANCEL', 'RESERVATION_NO_SHOW', 'PROPERTY_PAYMENT_CONFIRM_MANUAL') THEN 101
           WHEN code IN ('PROPERTY_REFUND', 'PLATFORM_REFUND') THEN 113
           ELSE 127
       END;

    IF NOT EXISTS (
        SELECT 1 FROM sys.check_constraints
        WHERE name = 'CK_app_function_supported_action_mask'
    )
        ALTER TABLE dbo.app_function ADD CONSTRAINT CK_app_function_supported_action_mask
            CHECK (supported_action_mask >= 0 AND (supported_action_mask & ~127) = 0);

    IF NOT EXISTS (
        SELECT 1 FROM sys.check_constraints
        WHERE name = 'CK_app_function_scope_type'
    )
        ALTER TABLE dbo.app_function ADD CONSTRAINT CK_app_function_scope_type
            CHECK (scope_type IN ('PUBLIC', 'SELF', 'PROPERTY', 'PLATFORM'));
END;

