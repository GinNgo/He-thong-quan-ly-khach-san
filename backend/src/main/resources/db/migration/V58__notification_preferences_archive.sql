IF COL_LENGTH('dbo.notifications', 'archived_at') IS NULL
BEGIN
    ALTER TABLE dbo.notifications ADD archived_at DATETIME2(3) NULL;
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.notifications')
      AND name = 'IX_notifications_user_archive_created'
)
BEGIN
    CREATE INDEX IX_notifications_user_archive_created
        ON dbo.notifications(user_id, archived_at, created_at DESC, id DESC);
END;

IF OBJECT_ID('dbo.notification_preferences', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.notification_preferences (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        user_id BIGINT NOT NULL,
        event_class VARCHAR(40) NOT NULL,
        channel VARCHAR(20) NOT NULL,
        enabled BIT NOT NULL,
        updated_at DATETIME2(3) NOT NULL,
        version BIGINT NOT NULL CONSTRAINT DF_notification_preference_version DEFAULT 0,
        CONSTRAINT FK_notification_preference_user
            FOREIGN KEY (user_id) REFERENCES dbo.users(id),
        CONSTRAINT CK_notification_preference_event_class
            CHECK (event_class IN ('ACCOUNT_SECURITY','BOOKING','PAYMENT','REFUND','INVOICE','SUPPORT','MARKETING')),
        CONSTRAINT CK_notification_preference_channel
            CHECK (channel IN ('IN_APP','EMAIL'))
    );

    CREATE UNIQUE INDEX UX_notification_preference_user_class_channel
        ON dbo.notification_preferences(user_id, event_class, channel);
END;
