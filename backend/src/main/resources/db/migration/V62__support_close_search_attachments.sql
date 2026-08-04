SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

IF COL_LENGTH('dbo.support_conversations', 'first_response_at') IS NULL
    EXEC(N'ALTER TABLE dbo.support_conversations ADD first_response_at DATETIME2 NULL');
IF COL_LENGTH('dbo.support_conversations', 'last_customer_message_at') IS NULL
    EXEC(N'ALTER TABLE dbo.support_conversations ADD last_customer_message_at DATETIME2 NULL');
IF COL_LENGTH('dbo.support_conversations', 'last_support_reply_at') IS NULL
    EXEC(N'ALTER TABLE dbo.support_conversations ADD last_support_reply_at DATETIME2 NULL');
IF COL_LENGTH('dbo.support_conversations', 'closed_reason') IS NULL
    EXEC(N'ALTER TABLE dbo.support_conversations ADD closed_reason NVARCHAR(500) NULL');
IF COL_LENGTH('dbo.support_conversations', 'reopened_at') IS NULL
    EXEC(N'ALTER TABLE dbo.support_conversations ADD reopened_at DATETIME2 NULL');
IF COL_LENGTH('dbo.support_conversations', 'reopen_reason') IS NULL
    EXEC(N'ALTER TABLE dbo.support_conversations ADD reopen_reason NVARCHAR(500) NULL');

IF OBJECT_ID('dbo.support_conversation_attachments', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.support_conversation_attachments (
        id BIGINT IDENTITY(1,1) NOT NULL,
        conversation_id BIGINT NOT NULL,
        hotel_id BIGINT NULL,
        uploaded_by_user_id BIGINT NOT NULL,
        original_filename NVARCHAR(180) NOT NULL,
        content_type VARCHAR(80) NOT NULL,
        size_bytes BIGINT NOT NULL,
        checksum_sha256 VARCHAR(64) NOT NULL,
        content_bytes VARBINARY(MAX) NOT NULL,
        uploaded_at DATETIME2 NOT NULL,
        CONSTRAINT PK_support_conversation_attachments PRIMARY KEY (id),
        CONSTRAINT FK_support_attachments_conversation FOREIGN KEY (conversation_id)
            REFERENCES dbo.support_conversations(id),
        CONSTRAINT FK_support_attachments_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_support_attachments_uploader FOREIGN KEY (uploaded_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT CK_support_attachments_size CHECK (size_bytes > 0 AND size_bytes <= 10485760),
        CONSTRAINT CK_support_attachments_type CHECK (
            content_type IN ('application/pdf','image/png','image/jpeg','text/plain'))
    );
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.support_conversation_attachments')
      AND name = 'IX_support_attachments_conversation_time'
)
    EXEC(N'CREATE INDEX IX_support_attachments_conversation_time
        ON dbo.support_conversation_attachments(conversation_id, uploaded_at ASC)');

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.support_conversation_attachments')
      AND name = 'IX_support_attachments_checksum'
)
    EXEC(N'CREATE INDEX IX_support_attachments_checksum
        ON dbo.support_conversation_attachments(checksum_sha256)');

-- Forward recovery is preferred. Attachment metadata/content and lifecycle reasons are
-- additive audit evidence; dropping them after use would destroy support records.
