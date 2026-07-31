SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

IF OBJECT_ID('dbo.financial_idempotency_records', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.financial_idempotency_records (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_financial_idempotency_records PRIMARY KEY,
        context VARCHAR(30) NOT NULL,
        operation VARCHAR(80) NOT NULL,
        scope_key VARCHAR(160) NOT NULL,
        idempotency_key VARCHAR(160) NOT NULL,
        request_hash VARCHAR(64) NOT NULL,
        state VARCHAR(20) NOT NULL CONSTRAINT DF_financial_idempotency_state DEFAULT 'IN_PROGRESS',
        response_status INT NULL,
        response_body NVARCHAR(MAX) NULL,
        hotel_id BIGINT NULL,
        owner_user_id BIGINT NULL,
        correlation_id VARCHAR(100) NOT NULL,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_financial_idempotency_created DEFAULT SYSUTCDATETIME(),
        completed_at DATETIME2 NULL,
        CONSTRAINT UQ_financial_idempotency_identity UNIQUE (context, operation, scope_key, idempotency_key),
        CONSTRAINT FK_financial_idempotency_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_financial_idempotency_owner FOREIGN KEY (owner_user_id) REFERENCES dbo.users(id),
        CONSTRAINT CK_financial_idempotency_context CHECK (context IN ('PROPERTY_COMMERCE','PLATFORM_BILLING')),
        CONSTRAINT CK_financial_idempotency_state CHECK (state IN ('IN_PROGRESS','COMPLETED','FAILED')),
        CONSTRAINT CK_financial_idempotency_hash CHECK (LEN(request_hash) = 64),
        CONSTRAINT CK_financial_idempotency_completion CHECK (state = 'IN_PROGRESS' OR completed_at IS NOT NULL)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.financial_idempotency_records') AND name = 'IX_financial_idempotency_scope')
    CREATE INDEX IX_financial_idempotency_scope
        ON dbo.financial_idempotency_records(context, scope_key, operation, created_at);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.financial_idempotency_records') AND name = 'IX_financial_idempotency_correlation')
    CREATE INDEX IX_financial_idempotency_correlation
        ON dbo.financial_idempotency_records(correlation_id, created_at);
