-- Additive credential-login lock metadata and pseudonymized append-only audit evidence.
IF OBJECT_ID('dbo.users', 'U') IS NULL
    THROW 51000, 'Required users table is missing.', 1;

IF COL_LENGTH('dbo.users', 'failed_login_count') IS NULL
    ALTER TABLE dbo.users ADD failed_login_count INT NOT NULL
        CONSTRAINT DF_users_failed_login_count DEFAULT 0;

IF COL_LENGTH('dbo.users', 'failed_login_window_started_at') IS NULL
    ALTER TABLE dbo.users ADD failed_login_window_started_at DATETIME2 NULL;

IF COL_LENGTH('dbo.users', 'login_locked_until') IS NULL
    ALTER TABLE dbo.users ADD login_locked_until DATETIME2 NULL;

IF COL_LENGTH('dbo.users', 'last_login_at') IS NULL
    ALTER TABLE dbo.users ADD last_login_at DATETIME2 NULL;

IF OBJECT_ID('dbo.auth_login_attempts', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.auth_login_attempts (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_auth_login_attempts PRIMARY KEY,
        user_id BIGINT NULL,
        account_fingerprint CHAR(64) NOT NULL,
        ip_fingerprint CHAR(64) NOT NULL,
        outcome VARCHAR(20) NOT NULL,
        reason_code VARCHAR(40) NOT NULL,
        correlation_id VARCHAR(100) NOT NULL,
        occurred_at DATETIME2 NOT NULL CONSTRAINT DF_auth_login_attempt_occurred DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_auth_login_attempt_user FOREIGN KEY (user_id) REFERENCES dbo.users(id),
        CONSTRAINT CK_auth_login_attempt_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'BLOCKED'))
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.auth_login_attempts') AND name = 'IX_auth_login_account_time')
    CREATE INDEX IX_auth_login_account_time ON dbo.auth_login_attempts(account_fingerprint, occurred_at DESC);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.auth_login_attempts') AND name = 'IX_auth_login_ip_time')
    CREATE INDEX IX_auth_login_ip_time ON dbo.auth_login_attempts(ip_fingerprint, occurred_at DESC);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.auth_login_attempts') AND name = 'IX_auth_login_correlation')
    CREATE INDEX IX_auth_login_correlation ON dbo.auth_login_attempts(correlation_id, occurred_at DESC);

IF OBJECT_ID('dbo.TR_auth_login_attempts_append_only', 'TR') IS NULL
BEGIN
    EXEC(N'
        CREATE TRIGGER dbo.TR_auth_login_attempts_append_only
        ON dbo.auth_login_attempts
        INSTEAD OF UPDATE, DELETE
        AS
        BEGIN
            SET NOCOUNT ON;
            THROW 51000, ''Authentication login attempts are append-only.'', 1;
        END
    ');
END;
