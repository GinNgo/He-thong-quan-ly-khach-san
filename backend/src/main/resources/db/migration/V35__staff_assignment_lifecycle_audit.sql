-- Forward-only additive migration. Recovery deploys the prior application version;
-- columns and the audit index remain in place to preserve lifecycle evidence.
IF OBJECT_ID('users', 'U') IS NULL OR OBJECT_ID('user_properties', 'U') IS NULL
    THROW 51000, 'Required users or user_properties table is missing.', 1;

IF COL_LENGTH('user_properties', 'status_reason') IS NULL
    ALTER TABLE user_properties ADD status_reason NVARCHAR(500) NULL;

IF COL_LENGTH('user_properties', 'status_changed_at') IS NULL
    ALTER TABLE user_properties ADD status_changed_at DATETIME2 NULL;

IF COL_LENGTH('user_properties', 'status_changed_by_user_id') IS NULL
    ALTER TABLE user_properties ADD status_changed_by_user_id BIGINT NULL;

IF NOT EXISTS (
    SELECT 1
    FROM sys.foreign_keys
    WHERE name = 'FK_user_properties_status_changed_by_user'
)
    ALTER TABLE user_properties
        ADD CONSTRAINT FK_user_properties_status_changed_by_user
        FOREIGN KEY (status_changed_by_user_id) REFERENCES users(id);

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID('user_properties')
      AND name = 'IX_user_properties_staff_lifecycle'
)
    CREATE INDEX IX_user_properties_staff_lifecycle
        ON user_properties(user_id, relationship_type, status, hotel_id, start_date);
