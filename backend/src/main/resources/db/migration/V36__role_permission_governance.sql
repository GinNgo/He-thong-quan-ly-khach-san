-- Additive governance metadata for role permission concurrency and auditability.
IF OBJECT_ID('dbo.app_role', 'U') IS NULL OR OBJECT_ID('dbo.app_role_permission', 'U') IS NULL
    THROW 51000, 'Required role tables are missing.', 1;

IF COL_LENGTH('dbo.app_role', 'version') IS NULL
BEGIN
    ALTER TABLE dbo.app_role ADD version BIGINT NOT NULL CONSTRAINT DF_app_role_version DEFAULT 0;
END;

IF COL_LENGTH('dbo.app_role_permission', 'version') IS NULL
BEGIN
    ALTER TABLE dbo.app_role_permission ADD version BIGINT NOT NULL CONSTRAINT DF_app_role_permission_version DEFAULT 0;
END;

IF OBJECT_ID('dbo.app_role_permission_audit', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.app_role_permission_audit (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_app_role_permission_audit PRIMARY KEY,
        role_id BIGINT NOT NULL,
        actor_user_id BIGINT NULL,
        expected_version BIGINT NOT NULL,
        resulting_version BIGINT NOT NULL,
        previous_state_json NVARCHAR(MAX) NOT NULL,
        new_state_json NVARCHAR(MAX) NOT NULL,
        occurred_at DATETIME2 NOT NULL CONSTRAINT DF_app_role_permission_audit_occurred DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_app_role_permission_audit_role FOREIGN KEY (role_id) REFERENCES dbo.app_role(id),
        CONSTRAINT FK_app_role_permission_audit_actor FOREIGN KEY (actor_user_id) REFERENCES dbo.users(id)
    );

    CREATE INDEX IX_role_permission_audit_role
        ON dbo.app_role_permission_audit(role_id, occurred_at);
END;
