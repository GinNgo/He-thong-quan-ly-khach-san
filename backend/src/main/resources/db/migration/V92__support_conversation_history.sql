IF OBJECT_ID('dbo.support_conversations', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.support_conversations (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        customer_id BIGINT NOT NULL,
        subject NVARCHAR(120) NOT NULL,
        created_at DATETIME2(3) NOT NULL,
        updated_at DATETIME2(3) NOT NULL,
        CONSTRAINT FK_support_conversation_customer FOREIGN KEY (customer_id) REFERENCES dbo.users(id)
    );
    CREATE INDEX IX_support_conversation_customer_updated
        ON dbo.support_conversations(customer_id, updated_at DESC, id DESC);
END;

IF OBJECT_ID('dbo.chat_messages', 'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.chat_messages', 'conversation_id') IS NULL
    BEGIN
        ALTER TABLE dbo.chat_messages ADD conversation_id BIGINT NULL;
    END;

    ;WITH queued_customers AS (
        SELECT
            sender_id AS customer_id,
            COALESCE(MIN([timestamp]), SYSUTCDATETIME()) AS created_at,
            COALESCE(MAX([timestamp]), SYSUTCDATETIME()) AS updated_at
        FROM dbo.chat_messages
        WHERE receiver_id = 0
        GROUP BY sender_id
    )
    INSERT INTO dbo.support_conversations (customer_id, subject, created_at, updated_at)
    SELECT customer_id, N'Ho tro chung', created_at, updated_at
    FROM queued_customers queued
    WHERE NOT EXISTS (
        SELECT 1
        FROM dbo.support_conversations conversation
        WHERE conversation.customer_id = queued.customer_id
    );

    UPDATE message
    SET conversation_id = conversation.id
    FROM dbo.chat_messages message
    INNER JOIN dbo.support_conversations conversation
        ON conversation.customer_id = CASE
            WHEN message.receiver_id = 0 THEN message.sender_id
            ELSE message.receiver_id
        END
    WHERE message.conversation_id IS NULL
      AND EXISTS (
          SELECT 1
          FROM dbo.chat_messages queued
          WHERE queued.sender_id = conversation.customer_id
            AND queued.receiver_id = 0
      );

    IF NOT EXISTS (
        SELECT 1
        FROM sys.foreign_keys
        WHERE parent_object_id = OBJECT_ID('dbo.chat_messages')
          AND name = 'FK_chat_message_conversation')
    BEGIN
        ALTER TABLE dbo.chat_messages ADD CONSTRAINT FK_chat_message_conversation
            FOREIGN KEY (conversation_id) REFERENCES dbo.support_conversations(id);
    END;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE object_id = OBJECT_ID('dbo.chat_messages')
          AND name = 'IX_chat_message_conversation_timestamp')
    BEGIN
        CREATE INDEX IX_chat_message_conversation_timestamp
            ON dbo.chat_messages(conversation_id, [timestamp] DESC, id DESC);
    END;
END;
