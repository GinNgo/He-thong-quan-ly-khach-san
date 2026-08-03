-- Forward-only credential identity hardening. Do not auto-merge ambiguous legacy identities.
IF OBJECT_ID('dbo.users', 'U') IS NULL
    THROW 51000, 'Required users table is missing.', 1;

IF OBJECT_ID('dbo.app_role', 'U') IS NULL OR OBJECT_ID('dbo.app_user_role', 'U') IS NULL
    THROW 51000, 'Required role tables are missing.', 1;

IF EXISTS (
    SELECT 1
    FROM dbo.users
    GROUP BY LOWER(LTRIM(RTRIM(username)))
    HAVING COUNT(*) > 1
)
    THROW 51000, 'Duplicate normalized usernames require manual remediation.', 1;

IF EXISTS (
    SELECT 1
    FROM dbo.users
    GROUP BY LOWER(LTRIM(RTRIM(email)))
    HAVING COUNT(*) > 1
)
    THROW 51000, 'Duplicate normalized emails require manual remediation.', 1;

IF EXISTS (
    SELECT 1
    FROM dbo.app_role
    GROUP BY LOWER(LTRIM(RTRIM(code)))
    HAVING COUNT(*) > 1
)
    THROW 51000, 'Duplicate normalized role codes require manual remediation.', 1;

IF EXISTS (
    SELECT user_id, role_id
    FROM dbo.app_user_role
    GROUP BY user_id, role_id
    HAVING COUNT(*) > 1
)
    THROW 51000, 'Duplicate user-role assignments require manual remediation.', 1;

IF COL_LENGTH('dbo.users', 'version') IS NULL
BEGIN
    ALTER TABLE dbo.users
        ADD version BIGINT NOT NULL
            CONSTRAINT DF_users_version DEFAULT 0;
END
ELSE
BEGIN
    UPDATE dbo.users SET version = 0 WHERE version IS NULL;
    ALTER TABLE dbo.users ALTER COLUMN version BIGINT NOT NULL;
END;

IF COL_LENGTH('dbo.users', 'username_normalized') IS NULL
    ALTER TABLE dbo.users ADD username_normalized AS LOWER(LTRIM(RTRIM(username))) PERSISTED;

IF COL_LENGTH('dbo.users', 'email_normalized') IS NULL
    ALTER TABLE dbo.users ADD email_normalized AS LOWER(LTRIM(RTRIM(email))) PERSISTED;

IF COL_LENGTH('dbo.app_role', 'code_normalized') IS NULL
    ALTER TABLE dbo.app_role ADD code_normalized AS LOWER(LTRIM(RTRIM(code))) PERSISTED;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_users_username_normalized')
    CREATE UNIQUE INDEX UX_users_username_normalized ON dbo.users(username_normalized);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_users_email_normalized')
    CREATE UNIQUE INDEX UX_users_email_normalized ON dbo.users(email_normalized);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_app_role_code_normalized')
    CREATE UNIQUE INDEX UX_app_role_code_normalized ON dbo.app_role(code_normalized);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_app_user_role_user_role')
    CREATE UNIQUE INDEX UX_app_user_role_user_role ON dbo.app_user_role(user_id, role_id);

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_users_username_nonblank')
    ALTER TABLE dbo.users ADD CONSTRAINT CK_users_username_nonblank
        CHECK (LEN(LTRIM(RTRIM(username))) BETWEEN 4 AND 100);

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_users_email_nonblank')
    ALTER TABLE dbo.users ADD CONSTRAINT CK_users_email_nonblank
        CHECK (LEN(LTRIM(RTRIM(email))) BETWEEN 3 AND 320);

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_app_role_code_nonblank')
    ALTER TABLE dbo.app_role ADD CONSTRAINT CK_app_role_code_nonblank
        CHECK (LEN(LTRIM(RTRIM(code))) BETWEEN 1 AND 100);
