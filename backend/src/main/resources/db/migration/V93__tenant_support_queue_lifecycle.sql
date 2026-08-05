IF COL_LENGTH('dbo.support_conversations', 'public_id') IS NULL
BEGIN
    EXEC(N'ALTER TABLE dbo.support_conversations ADD public_id VARCHAR(64) NULL');
    EXEC(N'UPDATE dbo.support_conversations SET public_id = CONCAT(''legacy-'', id) WHERE public_id IS NULL');
    EXEC(N'ALTER TABLE dbo.support_conversations ALTER COLUMN public_id VARCHAR(64) NOT NULL');
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.key_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.support_conversations')
      AND name = 'UQ_support_conversations_public_id'
)
BEGIN
    EXEC(N'ALTER TABLE dbo.support_conversations ADD CONSTRAINT UQ_support_conversations_public_id UNIQUE (public_id)');
END;

IF COL_LENGTH('dbo.support_conversations', 'hotel_id') IS NULL
    EXEC(N'ALTER TABLE dbo.support_conversations ADD hotel_id BIGINT NULL');
IF COL_LENGTH('dbo.support_conversations', 'reservation_id') IS NULL
    EXEC(N'ALTER TABLE dbo.support_conversations ADD reservation_id BIGINT NULL');
IF COL_LENGTH('dbo.support_conversations', 'assigned_agent_id') IS NULL
    EXEC(N'ALTER TABLE dbo.support_conversations ADD assigned_agent_id BIGINT NULL');
IF COL_LENGTH('dbo.support_conversations', 'sla_deadline_at') IS NULL
    EXEC(N'ALTER TABLE dbo.support_conversations ADD sla_deadline_at DATETIME2 NULL');
IF COL_LENGTH('dbo.support_conversations', 'assigned_at') IS NULL
    EXEC(N'ALTER TABLE dbo.support_conversations ADD assigned_at DATETIME2 NULL');
IF COL_LENGTH('dbo.support_conversations', 'escalated_at') IS NULL
    EXEC(N'ALTER TABLE dbo.support_conversations ADD escalated_at DATETIME2 NULL');
IF COL_LENGTH('dbo.support_conversations', 'closed_at') IS NULL
    EXEC(N'ALTER TABLE dbo.support_conversations ADD closed_at DATETIME2 NULL');

IF COL_LENGTH('dbo.support_conversations', 'channel') IS NULL
BEGIN
    EXEC(N'ALTER TABLE dbo.support_conversations ADD channel VARCHAR(20) NULL');
    EXEC(N'UPDATE dbo.support_conversations SET channel = ''IN_APP'' WHERE channel IS NULL');
    EXEC(N'ALTER TABLE dbo.support_conversations ALTER COLUMN channel VARCHAR(20) NOT NULL');
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.default_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.support_conversations')
      AND name = 'DF_support_conversations_channel'
)
    EXEC(N'ALTER TABLE dbo.support_conversations ADD CONSTRAINT DF_support_conversations_channel DEFAULT ''IN_APP'' FOR channel');

IF COL_LENGTH('dbo.support_conversations', 'status') IS NULL
BEGIN
    EXEC(N'ALTER TABLE dbo.support_conversations ADD status VARCHAR(20) NULL');
    EXEC(N'UPDATE dbo.support_conversations SET status = ''OPEN'' WHERE status IS NULL');
    EXEC(N'ALTER TABLE dbo.support_conversations ALTER COLUMN status VARCHAR(20) NOT NULL');
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.default_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.support_conversations')
      AND name = 'DF_support_conversations_status'
)
    EXEC(N'ALTER TABLE dbo.support_conversations ADD CONSTRAINT DF_support_conversations_status DEFAULT ''OPEN'' FOR status');

IF COL_LENGTH('dbo.support_conversations', 'last_activity_at') IS NULL
BEGIN
    EXEC(N'ALTER TABLE dbo.support_conversations ADD last_activity_at DATETIME2 NULL');
    EXEC(N'UPDATE dbo.support_conversations
        SET last_activity_at = COALESCE(updated_at, created_at, SYSUTCDATETIME())
        WHERE last_activity_at IS NULL');
    EXEC(N'ALTER TABLE dbo.support_conversations ALTER COLUMN last_activity_at DATETIME2 NOT NULL');
END;

IF COL_LENGTH('dbo.support_conversations', 'version') IS NULL
BEGIN
    EXEC(N'ALTER TABLE dbo.support_conversations ADD version BIGINT NULL');
    EXEC(N'UPDATE dbo.support_conversations SET version = 0 WHERE version IS NULL');
    EXEC(N'ALTER TABLE dbo.support_conversations ALTER COLUMN version BIGINT NOT NULL');
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.default_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.support_conversations')
      AND name = 'DF_support_conversations_version'
)
    EXEC(N'ALTER TABLE dbo.support_conversations ADD CONSTRAINT DF_support_conversations_version DEFAULT 0 FOR version');

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.support_conversations')
      AND name = 'CK_support_conversations_channel'
)
    EXEC(N'ALTER TABLE dbo.support_conversations ADD CONSTRAINT CK_support_conversations_channel
        CHECK (channel IN (''IN_APP'',''FACEBOOK'',''ZALO''))');

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.support_conversations')
      AND name = 'CK_support_conversations_status'
)
    EXEC(N'ALTER TABLE dbo.support_conversations ADD CONSTRAINT CK_support_conversations_status
        CHECK (status IN (''OPEN'',''ASSIGNED'',''ESCALATED'',''CLOSED''))');

IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys
    WHERE parent_object_id = OBJECT_ID('dbo.support_conversations')
      AND name = 'FK_support_conversations_hotel'
)
    EXEC(N'ALTER TABLE dbo.support_conversations ADD CONSTRAINT FK_support_conversations_hotel
        FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id)');

IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys
    WHERE parent_object_id = OBJECT_ID('dbo.support_conversations')
      AND name = 'FK_support_conversations_reservation'
)
    EXEC(N'ALTER TABLE dbo.support_conversations ADD CONSTRAINT FK_support_conversations_reservation
        FOREIGN KEY (reservation_id) REFERENCES dbo.reservations(id)');

IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys
    WHERE parent_object_id = OBJECT_ID('dbo.support_conversations')
      AND name = 'FK_support_conversations_agent'
)
    EXEC(N'ALTER TABLE dbo.support_conversations ADD CONSTRAINT FK_support_conversations_agent
        FOREIGN KEY (assigned_agent_id) REFERENCES dbo.users(id)');

IF COL_LENGTH('dbo.chat_messages', 'hotel_id') IS NULL
    EXEC(N'ALTER TABLE dbo.chat_messages ADD hotel_id BIGINT NULL');

IF COL_LENGTH('dbo.chat_messages', 'legacy_unscoped') IS NULL
    EXEC(N'ALTER TABLE dbo.chat_messages ADD legacy_unscoped BIT NULL');

EXEC(N'UPDATE message
    SET hotel_id = conversation.hotel_id
    FROM dbo.chat_messages message
    JOIN dbo.support_conversations conversation ON conversation.id = message.conversation_id
    WHERE message.hotel_id IS NULL AND conversation.hotel_id IS NOT NULL');

EXEC(N'UPDATE dbo.chat_messages
    SET legacy_unscoped = CASE WHEN hotel_id IS NULL THEN 1 ELSE 0 END
    WHERE legacy_unscoped IS NULL');

EXEC(N'ALTER TABLE dbo.chat_messages ALTER COLUMN legacy_unscoped BIT NOT NULL');

IF NOT EXISTS (
    SELECT 1 FROM sys.default_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.chat_messages')
      AND name = 'DF_chat_messages_legacy_unscoped'
)
    EXEC(N'ALTER TABLE dbo.chat_messages ADD CONSTRAINT DF_chat_messages_legacy_unscoped DEFAULT 0 FOR legacy_unscoped');

IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys
    WHERE parent_object_id = OBJECT_ID('dbo.chat_messages')
      AND name = 'FK_chat_messages_hotel'
)
    EXEC(N'ALTER TABLE dbo.chat_messages ADD CONSTRAINT FK_chat_messages_hotel
        FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id)');

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.chat_messages')
      AND name = 'CK_chat_messages_scope'
)
    EXEC(N'ALTER TABLE dbo.chat_messages ADD CONSTRAINT CK_chat_messages_scope CHECK (
        legacy_unscoped = 1 OR (conversation_id IS NOT NULL AND hotel_id IS NOT NULL)
    )');

IF OBJECT_ID('dbo.support_conversation_events', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.support_conversation_events (
        id BIGINT IDENTITY(1,1) NOT NULL,
        conversation_id BIGINT NOT NULL,
        hotel_id BIGINT NULL,
        actor_user_id BIGINT NULL,
        event_type VARCHAR(40) NOT NULL,
        details NVARCHAR(500) NULL,
        occurred_at DATETIME2 NOT NULL,
        CONSTRAINT PK_support_conversation_events PRIMARY KEY (id),
        CONSTRAINT FK_support_events_conversation FOREIGN KEY (conversation_id)
            REFERENCES dbo.support_conversations(id),
        CONSTRAINT FK_support_events_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_support_events_actor FOREIGN KEY (actor_user_id) REFERENCES dbo.users(id)
    );
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.support_conversations')
      AND name = 'IX_support_conversations_hotel_status_activity'
)
    EXEC(N'CREATE INDEX IX_support_conversations_hotel_status_activity
        ON dbo.support_conversations(hotel_id, status, last_activity_at DESC)');

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.support_conversations')
      AND name = 'IX_support_conversations_customer_activity'
)
    EXEC(N'CREATE INDEX IX_support_conversations_customer_activity
        ON dbo.support_conversations(customer_id, last_activity_at DESC)');

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.support_conversations')
      AND name = 'IX_support_conversations_agent_status'
)
    EXEC(N'CREATE INDEX IX_support_conversations_agent_status
        ON dbo.support_conversations(assigned_agent_id, status)');

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.chat_messages')
      AND name = 'IX_chat_messages_hotel_receiver_read'
)
    EXEC(N'CREATE INDEX IX_chat_messages_hotel_receiver_read
        ON dbo.chat_messages(hotel_id, receiver_id, is_read)');

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.support_conversation_events')
      AND name = 'IX_support_events_conversation_time'
)
    CREATE INDEX IX_support_events_conversation_time
        ON dbo.support_conversation_events(conversation_id, occurred_at DESC);

-- Forward recovery is preferred: retain the additive tenant/lifecycle columns and deploy
-- corrected filters or backfill logic. Dropping columns or events after use is destructive.
