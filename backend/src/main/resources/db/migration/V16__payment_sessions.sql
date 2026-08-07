IF OBJECT_ID('dbo.payment_sessions', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.payment_sessions (
        id BIGINT IDENTITY(1,1) NOT NULL,
        public_id VARCHAR(64) NOT NULL,
        reservation_id BIGINT NOT NULL,
        hotel_id BIGINT NOT NULL,
        owner_user_id BIGINT NOT NULL,
        provider VARCHAR(30) NOT NULL,
        method VARCHAR(40) NOT NULL,
        expected_amount DECIMAL(18,2) NOT NULL,
        currency VARCHAR(3) NOT NULL,
        provider_reference VARCHAR(120) NOT NULL,
        provider_transaction_id VARCHAR(160) NULL,
        idempotency_key VARCHAR(120) NOT NULL,
        status VARCHAR(30) NOT NULL,
        expires_at DATETIME2 NOT NULL,
        completed_at DATETIME2 NULL,
        reconciliation_required BIT NOT NULL CONSTRAINT DF_payment_sessions_reconciliation DEFAULT 0,
        failure_code VARCHAR(80) NULL,
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(255) NULL,
        updated_by NVARCHAR(255) NULL,
        CONSTRAINT PK_payment_sessions PRIMARY KEY (id),
        CONSTRAINT UQ_payment_sessions_public_id UNIQUE (public_id),
        CONSTRAINT UQ_payment_sessions_provider_reference UNIQUE (provider_reference),
        CONSTRAINT UQ_payment_sessions_owner_idempotency UNIQUE (owner_user_id, idempotency_key),
        CONSTRAINT CK_payment_sessions_provider CHECK (provider IN ('VNPAY','MOMO','ZALOPAY')),
        CONSTRAINT CK_payment_sessions_currency CHECK (currency = 'VND'),
        CONSTRAINT CK_payment_sessions_amount CHECK (expected_amount > 0),
        CONSTRAINT CK_payment_sessions_status CHECK (status IN ('CREATED','PENDING','SUCCEEDED','FAILED','EXPIRED')),
        CONSTRAINT FK_payment_sessions_reservation FOREIGN KEY (reservation_id) REFERENCES dbo.reservations(id),
        CONSTRAINT FK_payment_sessions_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_payment_sessions_owner FOREIGN KEY (owner_user_id) REFERENCES dbo.users(id)
    );
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.payment_sessions')
      AND name = 'IX_payment_sessions_reservation_status'
)
BEGIN
    CREATE INDEX IX_payment_sessions_reservation_status
        ON dbo.payment_sessions(reservation_id, status);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.payment_sessions')
      AND name = 'UX_payment_sessions_provider_transaction'
)
BEGIN
    CREATE UNIQUE INDEX UX_payment_sessions_provider_transaction
        ON dbo.payment_sessions(provider, provider_transaction_id)
        WHERE provider_transaction_id IS NOT NULL;
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.payment_sessions')
      AND name = 'IX_payment_sessions_hotel_status'
)
BEGIN
    CREATE INDEX IX_payment_sessions_hotel_status
        ON dbo.payment_sessions(hotel_id, status);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.payment_sessions')
      AND name = 'IX_payment_sessions_expiry'
)
BEGIN
    CREATE INDEX IX_payment_sessions_expiry
        ON dbo.payment_sessions(status, expires_at);
END;
