-- Persist immutable provider subjects instead of using email as the identity key.
IF OBJECT_ID('dbo.users', 'U') IS NULL
    THROW 51000, 'Required users table is missing.', 1;

IF OBJECT_ID('dbo.social_identities', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.social_identities (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_social_identities PRIMARY KEY,
        user_id BIGINT NOT NULL,
        provider NVARCHAR(32) NOT NULL,
        provider_subject NVARCHAR(255) NOT NULL,
        provider_email NVARCHAR(320) NOT NULL,
        last_login_at DATETIME2 NOT NULL CONSTRAINT DF_social_identities_last_login DEFAULT SYSUTCDATETIME(),
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(255) NULL,
        updated_by NVARCHAR(255) NULL,
        CONSTRAINT FK_social_identities_user FOREIGN KEY (user_id) REFERENCES dbo.users(id),
        CONSTRAINT CK_social_identities_provider CHECK (provider IN ('GOOGLE', 'FACEBOOK'))
    );

    CREATE UNIQUE INDEX UX_social_identities_provider_subject
        ON dbo.social_identities(provider, provider_subject);

    CREATE UNIQUE INDEX UX_social_identities_user_provider
        ON dbo.social_identities(user_id, provider);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.social_identities')
      AND name = 'UX_social_identities_provider_subject'
)
    THROW 51001, 'Provider subject uniqueness was not created.', 1;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.social_identities')
      AND name = 'UX_social_identities_user_provider'
)
    THROW 51002, 'User provider uniqueness was not created.', 1;
