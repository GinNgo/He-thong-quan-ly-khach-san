-- Transactional email queue and append-only provider delivery evidence.
-- Delivery is fail-closed until an explicitly configured sandbox adapter is enabled.
IF OBJECT_ID('dbo.email_outbox', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.email_outbox (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_email_outbox PRIMARY KEY,
        hotel_id BIGINT NULL,
        idempotency_key VARCHAR(180) NOT NULL,
        request_hash VARCHAR(64) NOT NULL,
        template_key VARCHAR(80) NOT NULL,
        template_version VARCHAR(40) NOT NULL,
        recipient_email NVARCHAR(320) NOT NULL,
        subject NVARCHAR(500) NOT NULL,
        body_html NVARCHAR(MAX) NULL,
        body_text NVARCHAR(MAX) NULL,
        attachment_name NVARCHAR(255) NULL,
        attachment_content_type VARCHAR(120) NULL,
        attachment_bytes VARBINARY(MAX) NULL,
        status VARCHAR(24) NOT NULL CONSTRAINT DF_email_outbox_status DEFAULT 'PENDING',
        attempt_count INT NOT NULL CONSTRAINT DF_email_outbox_attempt_count DEFAULT 0,
        max_attempts INT NOT NULL CONSTRAINT DF_email_outbox_max_attempts DEFAULT 5,
        manual_retry_count INT NOT NULL CONSTRAINT DF_email_outbox_manual_retry_count DEFAULT 0,
        next_attempt_at DATETIME2(7) NOT NULL CONSTRAINT DF_email_outbox_next_attempt DEFAULT SYSUTCDATETIME(),
        last_error_code VARCHAR(80) NULL,
        provider_message_id VARCHAR(180) NULL,
        sent_at DATETIME2(7) NULL,
        failed_at DATETIME2(7) NULL,
        created_at DATETIME2(7) NOT NULL CONSTRAINT DF_email_outbox_created DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2(7) NOT NULL CONSTRAINT DF_email_outbox_updated DEFAULT SYSUTCDATETIME(),
        record_version BIGINT NOT NULL CONSTRAINT DF_email_outbox_version DEFAULT 0,
        CONSTRAINT UQ_email_outbox_idempotency UNIQUE (idempotency_key),
        CONSTRAINT CK_email_outbox_status CHECK (status IN ('PENDING','PROCESSING','SENT','FAILED','BOUNCED','DEAD_LETTER')),
        CONSTRAINT CK_email_outbox_attempts CHECK (attempt_count >= 0 AND max_attempts BETWEEN 1 AND 20 AND manual_retry_count >= 0),
        CONSTRAINT FK_email_outbox_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.email_outbox') AND name = 'IX_email_outbox_due')
    CREATE INDEX IX_email_outbox_due ON dbo.email_outbox(status, next_attempt_at, id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.email_outbox') AND name = 'IX_email_outbox_failures')
    CREATE INDEX IX_email_outbox_failures ON dbo.email_outbox(status, failed_at DESC, id DESC);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.email_outbox') AND name = 'IX_email_outbox_hotel_status')
    CREATE INDEX IX_email_outbox_hotel_status ON dbo.email_outbox(hotel_id, status, created_at DESC);

IF OBJECT_ID('dbo.email_delivery_attempts', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.email_delivery_attempts (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_email_delivery_attempts PRIMARY KEY,
        outbox_id BIGINT NOT NULL,
        attempt_number INT NOT NULL,
        outcome VARCHAR(24) NOT NULL,
        error_code VARCHAR(80) NULL,
        provider_message_id VARCHAR(180) NULL,
        duration_ms BIGINT NOT NULL,
        attempted_at DATETIME2(7) NOT NULL CONSTRAINT DF_email_attempted_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT UQ_email_delivery_attempt UNIQUE (outbox_id, attempt_number),
        CONSTRAINT CK_email_delivery_outcome CHECK (outcome IN ('SENT','FAILED','BOUNCED')),
        CONSTRAINT FK_email_delivery_outbox FOREIGN KEY (outbox_id) REFERENCES dbo.email_outbox(id)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.email_delivery_attempts') AND name = 'IX_email_delivery_outbox_time')
    CREATE INDEX IX_email_delivery_outbox_time ON dbo.email_delivery_attempts(outbox_id, attempted_at DESC, id DESC);

IF OBJECT_ID('dbo.TR_email_delivery_attempts_append_only', 'TR') IS NULL
BEGIN
    EXEC(N'
        CREATE TRIGGER dbo.TR_email_delivery_attempts_append_only
        ON dbo.email_delivery_attempts
        INSTEAD OF UPDATE, DELETE
        AS
        BEGIN
            SET NOCOUNT ON;
            THROW 51000, ''Email delivery attempts are append-only.'', 1;
        END
    ');
END;
