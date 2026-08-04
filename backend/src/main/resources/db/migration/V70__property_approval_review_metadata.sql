-- Forward-only approval evidence. Legacy pending rows intentionally keep nullable metadata.
IF OBJECT_ID('dbo.hotels', 'U') IS NULL OR OBJECT_ID('dbo.users', 'U') IS NULL
    THROW 51000, 'Required hotels or users table is missing.', 1;

IF COL_LENGTH('dbo.hotels', 'submitted_by_user_id') IS NULL
    ALTER TABLE dbo.hotels ADD submitted_by_user_id BIGINT NULL;

IF COL_LENGTH('dbo.hotels', 'submitted_at') IS NULL
    ALTER TABLE dbo.hotels ADD submitted_at DATETIME2 NULL;

IF COL_LENGTH('dbo.hotels', 'reviewed_by_user_id') IS NULL
    ALTER TABLE dbo.hotels ADD reviewed_by_user_id BIGINT NULL;

IF COL_LENGTH('dbo.hotels', 'reviewed_at') IS NULL
    ALTER TABLE dbo.hotels ADD reviewed_at DATETIME2 NULL;

IF COL_LENGTH('dbo.hotels', 'review_reason') IS NULL
    ALTER TABLE dbo.hotels ADD review_reason NVARCHAR(500) NULL;

IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys
    WHERE parent_object_id = OBJECT_ID('dbo.hotels')
      AND name = 'FK_hotels_submitted_by_user'
)
    ALTER TABLE dbo.hotels
        ADD CONSTRAINT FK_hotels_submitted_by_user
        FOREIGN KEY (submitted_by_user_id) REFERENCES dbo.users(id);

IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys
    WHERE parent_object_id = OBJECT_ID('dbo.hotels')
      AND name = 'FK_hotels_reviewed_by_user'
)
    ALTER TABLE dbo.hotels
        ADD CONSTRAINT FK_hotels_reviewed_by_user
        FOREIGN KEY (reviewed_by_user_id) REFERENCES dbo.users(id);

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.hotels')
      AND name = 'IX_hotels_property_approval_queue'
)
    CREATE INDEX IX_hotels_property_approval_queue
        ON dbo.hotels(approval_status, status, operation_status, submitted_at DESC, id DESC);
