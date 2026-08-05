SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

IF COL_LENGTH('dbo.chat_messages', 'client_message_id') IS NULL
    EXEC(N'ALTER TABLE dbo.chat_messages ADD client_message_id VARCHAR(64) NULL');

IF COL_LENGTH('dbo.chat_messages', 'delivery_status') IS NULL
    EXEC(N'ALTER TABLE dbo.chat_messages ADD delivery_status VARCHAR(20) NULL');

EXEC(N'UPDATE dbo.chat_messages
          SET delivery_status = CASE WHEN is_read = 1 THEN ''READ'' ELSE ''PERSISTED'' END
        WHERE delivery_status IS NULL');

IF EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.chat_messages')
      AND name = 'delivery_status'
      AND is_nullable = 1
)
    EXEC(N'ALTER TABLE dbo.chat_messages ALTER COLUMN delivery_status VARCHAR(20) NOT NULL');

IF COL_LENGTH('dbo.chat_messages', 'delivered_at') IS NULL
    EXEC(N'ALTER TABLE dbo.chat_messages ADD delivered_at DATETIME2 NULL');

IF COL_LENGTH('dbo.chat_messages', 'read_at') IS NULL
    EXEC(N'ALTER TABLE dbo.chat_messages ADD read_at DATETIME2 NULL');

IF NOT EXISTS (
    SELECT 1 FROM sys.default_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.chat_messages')
      AND name = 'DF_chat_messages_delivery_status'
)
    EXEC(N'ALTER TABLE dbo.chat_messages ADD CONSTRAINT DF_chat_messages_delivery_status
        DEFAULT ''PERSISTED'' FOR delivery_status');

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.chat_messages')
      AND name = 'CK_chat_messages_delivery_status'
)
    EXEC(N'ALTER TABLE dbo.chat_messages ADD CONSTRAINT CK_chat_messages_delivery_status
        CHECK (delivery_status IN (''PERSISTED'',''DELIVERED'',''READ''))');

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.chat_messages')
      AND name = 'UX_chat_messages_client_identity'
)
    EXEC(N'CREATE UNIQUE INDEX UX_chat_messages_client_identity
        ON dbo.chat_messages(conversation_id, sender_id, client_message_id)
        WHERE client_message_id IS NOT NULL');

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.chat_messages')
      AND name = 'IX_chat_messages_conversation_delivery'
)
    EXEC(N'CREATE INDEX IX_chat_messages_conversation_delivery
        ON dbo.chat_messages(conversation_id, delivery_status, timestamp DESC)');

-- Forward recovery is preferred. Retain client identities and acknowledgement timestamps;
-- dropping them would make delayed retries capable of duplicating previously accepted messages.
