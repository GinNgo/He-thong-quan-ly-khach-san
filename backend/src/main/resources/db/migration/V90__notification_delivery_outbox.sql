IF COL_LENGTH('dbo.notifications', 'event_key') IS NULL
BEGIN
    ALTER TABLE dbo.notifications ADD event_key VARCHAR(160) NULL;
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.notifications')
      AND name = 'UX_notifications_event_key'
)
BEGIN
    CREATE UNIQUE INDEX UX_notifications_event_key
        ON dbo.notifications(event_key)
        WHERE event_key IS NOT NULL;
END;

IF OBJECT_ID('dbo.notification_delivery_outbox', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.notification_delivery_outbox (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        notification_id BIGINT NOT NULL,
        recipient_username NVARCHAR(190) NULL,
        destination VARCHAR(160) NOT NULL,
        status VARCHAR(20) NOT NULL,
        attempt_count INT NOT NULL CONSTRAINT DF_notification_outbox_attempt_count DEFAULT 0,
        next_attempt_at DATETIME2(3) NOT NULL,
        created_at DATETIME2(3) NOT NULL,
        delivered_at DATETIME2(3) NULL,
        last_error_type VARCHAR(120) NULL,
        version BIGINT NOT NULL CONSTRAINT DF_notification_outbox_version DEFAULT 0,
        CONSTRAINT FK_notification_outbox_notification
            FOREIGN KEY (notification_id) REFERENCES dbo.notifications(id),
        CONSTRAINT CK_notification_outbox_status
            CHECK (status IN ('PENDING', 'RETRY', 'DELIVERED', 'DEAD'))
    );

    CREATE UNIQUE INDEX UX_notification_outbox_notification
        ON dbo.notification_delivery_outbox(notification_id);
    CREATE INDEX IX_notification_outbox_due
        ON dbo.notification_delivery_outbox(status, next_attempt_at, id);
END;

IF OBJECT_ID('dbo.notification_delivery_attempts', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.notification_delivery_attempts (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        outbox_id BIGINT NOT NULL,
        attempt_number INT NOT NULL,
        outcome VARCHAR(20) NOT NULL,
        error_type VARCHAR(120) NULL,
        attempted_at DATETIME2(3) NOT NULL,
        CONSTRAINT FK_notification_attempt_outbox
            FOREIGN KEY (outbox_id) REFERENCES dbo.notification_delivery_outbox(id),
        CONSTRAINT CK_notification_attempt_outcome
            CHECK (outcome IN ('DELIVERED', 'FAILED', 'DEAD'))
    );

    CREATE UNIQUE INDEX UX_notification_attempt_number
        ON dbo.notification_delivery_attempts(outbox_id, attempt_number);
END;
