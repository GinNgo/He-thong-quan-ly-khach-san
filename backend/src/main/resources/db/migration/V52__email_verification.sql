IF COL_LENGTH('dbo.users', 'email_verified_at') IS NULL
    ALTER TABLE dbo.users ADD email_verified_at DATETIME2(7) NULL;

IF COL_LENGTH('dbo.users', 'pending_email') IS NULL
    ALTER TABLE dbo.users ADD pending_email NVARCHAR(320) NULL;

-- Preserve existing account behavior; only new credential registrations start unverified.
UPDATE dbo.users
SET email_verified_at = COALESCE(email_verified_at, SYSUTCDATETIME())
WHERE email_verified_at IS NULL;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_users_pending_email')
    CREATE UNIQUE INDEX UX_users_pending_email ON dbo.users(pending_email)
        WHERE pending_email IS NOT NULL;

IF OBJECT_ID('dbo.email_verification_tokens', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.email_verification_tokens (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_email_verification_tokens PRIMARY KEY,
        user_id BIGINT NOT NULL,
        purpose VARCHAR(32) NOT NULL,
        target_email NVARCHAR(320) NOT NULL,
        token_hash VARCHAR(64) NOT NULL,
        requested_at DATETIME2(7) NOT NULL,
        expires_at DATETIME2(7) NOT NULL,
        request_ip NVARCHAR(64) NOT NULL,
        used_at DATETIME2(7) NULL,
        revoked_at DATETIME2(7) NULL,
        CONSTRAINT UQ_email_verification_token_hash UNIQUE (token_hash),
        CONSTRAINT CK_email_verification_purpose CHECK (purpose IN ('INITIAL_VERIFICATION', 'EMAIL_CHANGE')),
        CONSTRAINT FK_email_verification_tokens_user FOREIGN KEY (user_id) REFERENCES dbo.users(id)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_email_verification_user_requested')
    CREATE INDEX IX_email_verification_user_requested
        ON dbo.email_verification_tokens(user_id, requested_at);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_email_verification_ip_requested')
    CREATE INDEX IX_email_verification_ip_requested
        ON dbo.email_verification_tokens(request_ip, requested_at);
