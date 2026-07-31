SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

IF OBJECT_ID('dbo.property_refund_requests', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.property_refund_requests (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_property_refund_requests PRIMARY KEY,
        public_id VARCHAR(64) NOT NULL,
        hotel_id BIGINT NOT NULL,
        original_transaction_id BIGINT NOT NULL,
        requested_amount DECIMAL(19,0) NOT NULL,
        approved_amount DECIMAL(19,0) NULL,
        succeeded_amount DECIMAL(19,0) NULL,
        currency VARCHAR(3) NOT NULL CONSTRAINT DF_property_refund_currency DEFAULT 'VND',
        reason NVARCHAR(1000) NOT NULL,
        requested_by BIGINT NOT NULL,
        approved_by BIGINT NULL,
        status VARCHAR(30) NOT NULL,
        idempotency_key VARCHAR(160) NOT NULL,
        request_hash VARCHAR(128) NOT NULL,
        requested_at DATETIME2 NOT NULL CONSTRAINT DF_property_refund_requested DEFAULT SYSUTCDATETIME(),
        completed_at DATETIME2 NULL,
        version BIGINT NOT NULL CONSTRAINT DF_property_refund_version DEFAULT 0,
        CONSTRAINT FK_property_refund_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_property_refund_original FOREIGN KEY (original_transaction_id) REFERENCES dbo.property_financial_transactions(id),
        CONSTRAINT FK_property_refund_requester FOREIGN KEY (requested_by) REFERENCES dbo.users(id),
        CONSTRAINT FK_property_refund_approver FOREIGN KEY (approved_by) REFERENCES dbo.users(id),
        CONSTRAINT UQ_property_refund_public UNIQUE (public_id),
        CONSTRAINT UQ_property_refund_idempotency UNIQUE (hotel_id, idempotency_key),
        CONSTRAINT CK_property_refund_amount CHECK (requested_amount > 0),
        CONSTRAINT CK_property_refund_currency CHECK (currency = 'VND'),
        CONSTRAINT CK_property_refund_status CHECK (status IN ('REQUESTED','PENDING_APPROVAL','PENDING_PROVIDER','SUCCEEDED','FAILED','CANCELLED'))
    );
END;

IF OBJECT_ID('dbo.property_refund_attempts', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.property_refund_attempts (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_property_refund_attempts PRIMARY KEY,
        refund_request_id BIGINT NOT NULL,
        hotel_id BIGINT NOT NULL,
        attempt_number INT NOT NULL,
        provider VARCHAR(40) NOT NULL,
        environment VARCHAR(20) NOT NULL,
        provider_reference VARCHAR(200) NULL,
        provider_event_id VARCHAR(200) NULL,
        status VARCHAR(30) NOT NULL,
        failure_code VARCHAR(100) NULL,
        retryable BIT NOT NULL CONSTRAINT DF_property_refund_retryable DEFAULT 0,
        requested_at DATETIME2 NOT NULL CONSTRAINT DF_property_refund_attempt_requested DEFAULT SYSUTCDATETIME(),
        completed_at DATETIME2 NULL,
        CONSTRAINT FK_property_refund_attempt_request FOREIGN KEY (refund_request_id) REFERENCES dbo.property_refund_requests(id),
        CONSTRAINT FK_property_refund_attempt_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT UQ_property_refund_attempt UNIQUE (refund_request_id, attempt_number),
        CONSTRAINT CK_property_refund_attempt_number CHECK (attempt_number > 0)
    );
END;

IF OBJECT_ID('dbo.financial_audit_events', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.financial_audit_events (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_financial_audit_events PRIMARY KEY,
        context VARCHAR(30) NOT NULL,
        hotel_id BIGINT NULL,
        aggregate_type VARCHAR(80) NOT NULL,
        aggregate_id VARCHAR(100) NOT NULL,
        actor_type VARCHAR(30) NOT NULL,
        actor_id BIGINT NULL,
        source VARCHAR(40) NOT NULL,
        previous_state VARCHAR(50) NULL,
        new_state VARCHAR(50) NULL,
        reason NVARCHAR(1000) NULL,
        idempotency_identity VARCHAR(200) NULL,
        provider_identity VARCHAR(200) NULL,
        correlation_id VARCHAR(100) NOT NULL,
        metadata_json NVARCHAR(MAX) NULL,
        occurred_at DATETIME2 NOT NULL CONSTRAINT DF_financial_audit_occurred DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_financial_audit_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT CK_financial_audit_context CHECK (context IN ('PROPERTY_COMMERCE','PLATFORM_BILLING')),
        CONSTRAINT CK_financial_audit_scope CHECK ((context = 'PROPERTY_COMMERCE' AND hotel_id IS NOT NULL) OR context = 'PLATFORM_BILLING')
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.property_refund_requests') AND name = 'IX_property_refund_original_status')
    CREATE INDEX IX_property_refund_original_status ON dbo.property_refund_requests(hotel_id, original_transaction_id, status);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.financial_audit_events') AND name = 'IX_financial_audit_aggregate')
    CREATE INDEX IX_financial_audit_aggregate ON dbo.financial_audit_events(context, aggregate_type, aggregate_id, occurred_at);
