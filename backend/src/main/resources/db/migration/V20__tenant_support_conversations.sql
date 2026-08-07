IF OBJECT_ID('dbo.support_conversations', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.support_conversations (
        id BIGINT IDENTITY(1,1) NOT NULL,
        public_id VARCHAR(64) NOT NULL,
        customer_id BIGINT NOT NULL,
        hotel_id BIGINT NOT NULL,
        reservation_id BIGINT NULL,
        assigned_agent_id BIGINT NULL,
        channel VARCHAR(20) NOT NULL,
        status VARCHAR(20) NOT NULL,
        last_activity_at DATETIME2 NOT NULL,
        assigned_at DATETIME2 NULL,
        escalated_at DATETIME2 NULL,
        closed_at DATETIME2 NULL,
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(255) NULL,
        updated_by NVARCHAR(255) NULL,
        version BIGINT NOT NULL CONSTRAINT DF_support_conversations_version DEFAULT 0,
        CONSTRAINT PK_support_conversations PRIMARY KEY (id),
        CONSTRAINT UQ_support_conversations_public_id UNIQUE (public_id),
        CONSTRAINT CK_support_conversations_channel CHECK (channel IN ('IN_APP','FACEBOOK','ZALO')),
        CONSTRAINT CK_support_conversations_status CHECK (status IN ('OPEN','ASSIGNED','ESCALATED','CLOSED')),
        CONSTRAINT FK_support_conversations_customer FOREIGN KEY (customer_id) REFERENCES dbo.users(id),
        CONSTRAINT FK_support_conversations_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_support_conversations_reservation FOREIGN KEY (reservation_id) REFERENCES dbo.reservations(id),
        CONSTRAINT FK_support_conversations_agent FOREIGN KEY (assigned_agent_id) REFERENCES dbo.users(id)
    );
END;

IF COL_LENGTH('dbo.chat_messages', 'conversation_id') IS NULL
BEGIN
    ALTER TABLE dbo.chat_messages ADD conversation_id BIGINT NULL;
END;

IF COL_LENGTH('dbo.chat_messages', 'hotel_id') IS NULL
BEGIN
    ALTER TABLE dbo.chat_messages ADD hotel_id BIGINT NULL;
END;

IF COL_LENGTH('dbo.chat_messages', 'legacy_unscoped') IS NULL
BEGIN
    ALTER TABLE dbo.chat_messages ADD legacy_unscoped BIT NULL;
    UPDATE dbo.chat_messages SET legacy_unscoped = 1 WHERE legacy_unscoped IS NULL;
    ALTER TABLE dbo.chat_messages ALTER COLUMN legacy_unscoped BIT NOT NULL;
    ALTER TABLE dbo.chat_messages ADD CONSTRAINT DF_chat_messages_legacy_unscoped DEFAULT 0 FOR legacy_unscoped;
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys
    WHERE parent_object_id = OBJECT_ID('dbo.chat_messages')
      AND name = 'FK_chat_messages_conversation'
)
BEGIN
    ALTER TABLE dbo.chat_messages ADD CONSTRAINT FK_chat_messages_conversation
        FOREIGN KEY (conversation_id) REFERENCES dbo.support_conversations(id);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys
    WHERE parent_object_id = OBJECT_ID('dbo.chat_messages')
      AND name = 'FK_chat_messages_hotel'
)
BEGIN
    ALTER TABLE dbo.chat_messages ADD CONSTRAINT FK_chat_messages_hotel
        FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.chat_messages')
      AND name = 'CK_chat_messages_scope'
)
BEGIN
    ALTER TABLE dbo.chat_messages ADD CONSTRAINT CK_chat_messages_scope CHECK (
        (legacy_unscoped = 1 AND conversation_id IS NULL AND hotel_id IS NULL)
        OR (legacy_unscoped = 0 AND conversation_id IS NOT NULL AND hotel_id IS NOT NULL)
    );
END;

IF OBJECT_ID('dbo.support_conversation_events', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.support_conversation_events (
        id BIGINT IDENTITY(1,1) NOT NULL,
        conversation_id BIGINT NOT NULL,
        hotel_id BIGINT NOT NULL,
        actor_user_id BIGINT NULL,
        event_type VARCHAR(40) NOT NULL,
        details NVARCHAR(500) NULL,
        occurred_at DATETIME2 NOT NULL,
        CONSTRAINT PK_support_conversation_events PRIMARY KEY (id),
        CONSTRAINT FK_support_events_conversation FOREIGN KEY (conversation_id) REFERENCES dbo.support_conversations(id),
        CONSTRAINT FK_support_events_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_support_events_actor FOREIGN KEY (actor_user_id) REFERENCES dbo.users(id)
    );
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.support_conversations')
      AND name = 'IX_support_conversations_hotel_status_activity'
)
BEGIN
    CREATE INDEX IX_support_conversations_hotel_status_activity
        ON dbo.support_conversations(hotel_id, status, last_activity_at DESC);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.support_conversations')
      AND name = 'IX_support_conversations_customer_activity'
)
BEGIN
    CREATE INDEX IX_support_conversations_customer_activity
        ON dbo.support_conversations(customer_id, last_activity_at DESC);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.support_conversations')
      AND name = 'IX_support_conversations_agent_status'
)
BEGIN
    CREATE INDEX IX_support_conversations_agent_status
        ON dbo.support_conversations(assigned_agent_id, status);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.chat_messages')
      AND name = 'IX_chat_messages_conversation_timestamp'
)
BEGIN
    CREATE INDEX IX_chat_messages_conversation_timestamp
        ON dbo.chat_messages(conversation_id, timestamp);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.chat_messages')
      AND name = 'IX_chat_messages_hotel_receiver_read'
)
BEGIN
    CREATE INDEX IX_chat_messages_hotel_receiver_read
        ON dbo.chat_messages(hotel_id, receiver_id, is_read);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.support_conversation_events')
      AND name = 'IX_support_events_conversation_time'
)
BEGIN
    CREATE INDEX IX_support_events_conversation_time
        ON dbo.support_conversation_events(conversation_id, occurred_at DESC);
END;

-- Rollback: remove the new foreign keys/indexes, then drop support_conversation_events and
-- support_conversations. Keep chat_messages scope columns until legacy rows are reviewed.
