-- Property transition history remains canonical in operational_audit_events.
-- This migration adds only the mutable email queue and append-only delivery evidence.
IF OBJECT_ID('dbo.operational_audit_events', 'U') IS NULL
   OR OBJECT_ID('dbo.hotels', 'U') IS NULL
   OR OBJECT_ID('dbo.users', 'U') IS NULL
    THROW 51000, 'Required audit, hotels or users table is missing.', 1;

IF OBJECT_ID('dbo.property_review_email_outbox', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.property_review_email_outbox (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_property_review_email_outbox PRIMARY KEY,
        audit_event_id BIGINT NOT NULL,
        hotel_id BIGINT NOT NULL,
        recipient_user_id BIGINT NOT NULL,
        recipient_email NVARCHAR(320) NULL,
        subject NVARCHAR(500) NOT NULL,
        body_text NVARCHAR(MAX) NOT NULL,
        status VARCHAR(24) NOT NULL CONSTRAINT DF_property_review_email_status DEFAULT 'PENDING',
        attempt_count INT NOT NULL CONSTRAINT DF_property_review_email_attempt_count DEFAULT 0,
        max_attempts INT NOT NULL CONSTRAINT DF_property_review_email_max_attempts DEFAULT 5,
        next_attempt_at DATETIME2(7) NOT NULL CONSTRAINT DF_property_review_email_next_attempt DEFAULT SYSUTCDATETIME(),
        claim_token VARCHAR(64) NULL,
        claimed_at DATETIME2(7) NULL,
        last_error_code VARCHAR(80) NULL,
        sent_at DATETIME2(7) NULL,
        created_at DATETIME2(7) NOT NULL CONSTRAINT DF_property_review_email_created DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2(7) NOT NULL CONSTRAINT DF_property_review_email_updated DEFAULT SYSUTCDATETIME(),
        record_version BIGINT NOT NULL CONSTRAINT DF_property_review_email_version DEFAULT 0,
        CONSTRAINT UQ_property_review_email_event_recipient UNIQUE (audit_event_id, recipient_user_id),
        CONSTRAINT CK_property_review_email_status CHECK (
            status IN ('PENDING','PROCESSING','SENT','FAILED','DEAD_LETTER')),
        CONSTRAINT CK_property_review_email_attempts CHECK (
            attempt_count >= 0 AND max_attempts BETWEEN 1 AND 20),
        CONSTRAINT CK_property_review_email_claim CHECK (
            (status = 'PROCESSING' AND claim_token IS NOT NULL AND claimed_at IS NOT NULL)
            OR (status <> 'PROCESSING' AND claim_token IS NULL AND claimed_at IS NULL)),
        CONSTRAINT FK_property_review_email_audit FOREIGN KEY (audit_event_id)
            REFERENCES dbo.operational_audit_events(id),
        CONSTRAINT FK_property_review_email_hotel FOREIGN KEY (hotel_id)
            REFERENCES dbo.hotels(id),
        CONSTRAINT FK_property_review_email_recipient FOREIGN KEY (recipient_user_id)
            REFERENCES dbo.users(id)
    );
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.property_review_email_outbox')
      AND name = 'IX_property_review_email_due'
)
    CREATE INDEX IX_property_review_email_due
        ON dbo.property_review_email_outbox(status, next_attempt_at, id);

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.property_review_email_outbox')
      AND name = 'IX_property_review_email_claim'
)
    CREATE INDEX IX_property_review_email_claim
        ON dbo.property_review_email_outbox(status, claimed_at, id);

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.property_review_email_outbox')
      AND name = 'IX_property_review_email_property'
)
    CREATE INDEX IX_property_review_email_property
        ON dbo.property_review_email_outbox(hotel_id, created_at DESC, id DESC);

IF OBJECT_ID('dbo.property_review_email_delivery_attempts', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.property_review_email_delivery_attempts (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_property_review_email_attempts PRIMARY KEY,
        outbox_id BIGINT NOT NULL,
        attempt_number INT NOT NULL,
        outcome VARCHAR(16) NOT NULL,
        error_code VARCHAR(80) NULL,
        duration_ms BIGINT NOT NULL,
        attempted_at DATETIME2(7) NOT NULL CONSTRAINT DF_property_review_email_attempted DEFAULT SYSUTCDATETIME(),
        CONSTRAINT UQ_property_review_email_attempt UNIQUE (outbox_id, attempt_number),
        CONSTRAINT CK_property_review_email_attempt_number CHECK (attempt_number > 0),
        CONSTRAINT CK_property_review_email_outcome CHECK (outcome IN ('SENT','FAILED')),
        CONSTRAINT CK_property_review_email_attempt_error CHECK (
            (outcome = 'SENT' AND error_code IS NULL)
            OR (outcome = 'FAILED' AND NULLIF(LTRIM(RTRIM(error_code)), '') IS NOT NULL)),
        CONSTRAINT CK_property_review_email_duration CHECK (duration_ms >= 0),
        CONSTRAINT FK_property_review_email_attempt_outbox FOREIGN KEY (outbox_id)
            REFERENCES dbo.property_review_email_outbox(id)
    );
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.property_review_email_delivery_attempts')
      AND name = 'IX_property_review_email_attempt_time'
)
    CREATE INDEX IX_property_review_email_attempt_time
        ON dbo.property_review_email_delivery_attempts(outbox_id, attempted_at DESC, id DESC);

IF OBJECT_ID('dbo.TR_property_review_email_attempts_append_only', 'TR') IS NULL
BEGIN
    EXEC(N'
        CREATE TRIGGER dbo.TR_property_review_email_attempts_append_only
        ON dbo.property_review_email_delivery_attempts
        INSTEAD OF UPDATE, DELETE
        AS
        BEGIN
            SET NOCOUNT ON;
            THROW 51000, ''Property review email attempts are append-only.'', 1;
        END
    ');
END;
