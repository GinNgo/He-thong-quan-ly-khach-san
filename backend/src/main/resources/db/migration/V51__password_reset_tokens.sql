IF OBJECT_ID('dbo.password_reset_tokens', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.password_reset_tokens (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_password_reset_tokens PRIMARY KEY,
        user_id BIGINT NULL,
        email_fingerprint VARCHAR(64) NOT NULL,
        token_hash VARCHAR(64) NOT NULL,
        requested_at DATETIME2(7) NOT NULL,
        expires_at DATETIME2(7) NOT NULL,
        request_ip NVARCHAR(64) NOT NULL,
        used_at DATETIME2(7) NULL,
        revoked_at DATETIME2(7) NULL,
        CONSTRAINT UQ_password_reset_token_hash UNIQUE (token_hash),
        CONSTRAINT FK_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES dbo.users(id)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_password_reset_user_requested')
    CREATE INDEX IX_password_reset_user_requested
        ON dbo.password_reset_tokens(user_id, requested_at);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_password_reset_ip_requested')
    CREATE INDEX IX_password_reset_ip_requested
        ON dbo.password_reset_tokens(request_ip, requested_at);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_password_reset_email_requested')
    CREATE INDEX IX_password_reset_email_requested
        ON dbo.password_reset_tokens(email_fingerprint, requested_at);
