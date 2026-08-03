-- Shared append-only operational audit evidence for tenant and system administration.
IF OBJECT_ID('dbo.operational_audit_events', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.operational_audit_events (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_operational_audit_events PRIMARY KEY,
        scope VARCHAR(20) NOT NULL,
        hotel_id BIGINT NULL,
        domain VARCHAR(40) NOT NULL,
        event_type VARCHAR(80) NOT NULL,
        aggregate_type VARCHAR(80) NOT NULL,
        aggregate_id VARCHAR(100) NOT NULL,
        actor_type VARCHAR(30) NOT NULL,
        actor_id BIGINT NULL,
        reason NVARCHAR(500) NOT NULL,
        before_state_json NVARCHAR(MAX) NULL,
        after_state_json NVARCHAR(MAX) NULL,
        correlation_id VARCHAR(100) NOT NULL,
        occurred_at DATETIME2 NOT NULL CONSTRAINT DF_operational_audit_occurred DEFAULT SYSUTCDATETIME(),
        CONSTRAINT CK_operational_audit_scope CHECK (
            (scope = 'TENANT' AND hotel_id IS NOT NULL)
            OR (scope = 'SYSTEM' AND hotel_id IS NULL)
        )
    );
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.operational_audit_events')
      AND name = 'IX_operational_audit_tenant_time'
)
    CREATE INDEX IX_operational_audit_tenant_time
        ON dbo.operational_audit_events(scope, hotel_id, occurred_at DESC, id DESC);

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.operational_audit_events')
      AND name = 'IX_operational_audit_aggregate'
)
    CREATE INDEX IX_operational_audit_aggregate
        ON dbo.operational_audit_events(aggregate_type, aggregate_id, occurred_at DESC);

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.operational_audit_events')
      AND name = 'IX_operational_audit_correlation'
)
    CREATE INDEX IX_operational_audit_correlation
        ON dbo.operational_audit_events(correlation_id, occurred_at DESC);

IF OBJECT_ID('dbo.TR_operational_audit_events_append_only', 'TR') IS NULL
BEGIN
    EXEC(N'
        CREATE TRIGGER dbo.TR_operational_audit_events_append_only
        ON dbo.operational_audit_events
        INSTEAD OF UPDATE, DELETE
        AS
        BEGIN
            SET NOCOUNT ON;
            THROW 51000, ''Operational audit events are append-only.'', 1;
        END
    ');
END;

DECLARE @systemModuleId BIGINT = (
    SELECT TOP 1 id FROM dbo.app_module
    WHERE code = 'SYSTEM' OR name = N'Hệ thống'
    ORDER BY id
);

IF @systemModuleId IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM dbo.app_function WHERE code = 'AUDIT_LOG')
BEGIN
    INSERT INTO dbo.app_function(code, name, url, icon, sort_order, module_id)
    VALUES ('AUDIT_LOG', N'Nhật ký vận hành', '/admin/audit-log', 'pi pi-history', 31, @systemModuleId);
END;

;WITH defaults AS (
    SELECT role.id AS role_id,
           function_row.id AS function_id,
           CASE
               WHEN role.code = 'SUPER_ADMIN' THEN 63
               ELSE 17
           END AS action_mask
    FROM dbo.app_role role
    CROSS JOIN dbo.app_function function_row
    WHERE role.code IN ('SUPER_ADMIN', 'PROPERTY_OWNER', 'HOTEL_ADMIN', 'HOTEL_MANAGER')
      AND function_row.code = 'AUDIT_LOG'
)
MERGE dbo.app_role_permission AS target
USING defaults AS source
ON target.role_id = source.role_id AND target.function_id = source.function_id
WHEN MATCHED THEN
    UPDATE SET action_mask = target.action_mask | source.action_mask
WHEN NOT MATCHED THEN
    INSERT(role_id, function_id, action_mask)
    VALUES(source.role_id, source.function_id, source.action_mask);
