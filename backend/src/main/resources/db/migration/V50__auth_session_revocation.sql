-- Invalidate already-issued access tokens after logout without storing raw bearer tokens.
IF OBJECT_ID('dbo.users', 'U') IS NULL
    THROW 51000, 'Required users table is missing.', 1;

IF COL_LENGTH('dbo.users', 'auth_revoked_at') IS NULL
    ALTER TABLE dbo.users ADD auth_revoked_at DATETIME2 NULL;
