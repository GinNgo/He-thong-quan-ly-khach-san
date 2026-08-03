-- Rotating refresh-token families. Raw bearer values are never persisted.
IF OBJECT_ID('dbo.users', 'U') IS NULL
    THROW 51000, 'Required users table is missing.', 1;

IF OBJECT_ID('dbo.auth_refresh_tokens', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.auth_refresh_tokens (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_auth_refresh_tokens PRIMARY KEY,
        user_id BIGINT NOT NULL,
        family_id VARCHAR(36) NOT NULL,
        token_hash CHAR(64) NOT NULL,
        status VARCHAR(20) NOT NULL,
        issued_at DATETIME2 NOT NULL,
        expires_at DATETIME2 NOT NULL,
        rotated_at DATETIME2 NULL,
        revoked_at DATETIME2 NULL,
        reuse_detected_at DATETIME2 NULL,
        replaced_by_hash CHAR(64) NULL,
        revocation_reason VARCHAR(100) NULL,
        version BIGINT NOT NULL CONSTRAINT DF_auth_refresh_version DEFAULT 0,
        CONSTRAINT FK_auth_refresh_user FOREIGN KEY (user_id) REFERENCES dbo.users(id),
        CONSTRAINT CK_auth_refresh_status CHECK (status IN ('ACTIVE','ROTATED','REVOKED','EXPIRED')),
        CONSTRAINT CK_auth_refresh_expiry CHECK (expires_at > issued_at)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_auth_refresh_token_hash')
    CREATE UNIQUE INDEX UX_auth_refresh_token_hash ON dbo.auth_refresh_tokens(token_hash);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_auth_refresh_family')
    CREATE INDEX IX_auth_refresh_family ON dbo.auth_refresh_tokens(family_id, status);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_auth_refresh_user')
    CREATE INDEX IX_auth_refresh_user ON dbo.auth_refresh_tokens(user_id, status, expires_at);
