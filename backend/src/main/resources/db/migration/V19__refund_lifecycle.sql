IF OBJECT_ID('dbo.refund_requests', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.refund_requests (
        id BIGINT IDENTITY(1,1) NOT NULL,
        public_id VARCHAR(64) NOT NULL,
        reservation_id BIGINT NOT NULL,
        original_payment_id BIGINT NOT NULL,
        hotel_id BIGINT NOT NULL,
        requested_amount DECIMAL(18,2) NOT NULL,
        currency VARCHAR(3) NOT NULL,
        provider VARCHAR(30) NOT NULL,
        status VARCHAR(30) NOT NULL,
        idempotency_key VARCHAR(160) NOT NULL,
        reason NVARCHAR(500) NULL,
        provider_refund_reference VARCHAR(160) NULL,
        requested_at DATETIME2 NOT NULL,
        completed_at DATETIME2 NULL,
        points_reversed_at DATETIME2 NULL,
        request_notified_at DATETIME2 NULL,
        terminal_notified_at DATETIME2 NULL,
        failure_code VARCHAR(80) NULL,
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(255) NULL,
        updated_by NVARCHAR(255) NULL,
        CONSTRAINT PK_refund_requests PRIMARY KEY (id),
        CONSTRAINT UQ_refund_requests_public_id UNIQUE (public_id),
        CONSTRAINT UQ_refund_requests_original_payment UNIQUE (original_payment_id),
        CONSTRAINT UQ_refund_requests_idempotency UNIQUE (idempotency_key),
        CONSTRAINT CK_refund_requests_amount CHECK (requested_amount > 0),
        CONSTRAINT CK_refund_requests_currency CHECK (currency = 'VND'),
        CONSTRAINT CK_refund_requests_status CHECK (status IN ('REQUESTED','PENDING_PROVIDER','SUCCEEDED','FAILED')),
        CONSTRAINT FK_refund_requests_reservation FOREIGN KEY (reservation_id) REFERENCES dbo.reservations(id),
        CONSTRAINT FK_refund_requests_original_payment FOREIGN KEY (original_payment_id) REFERENCES dbo.payments(id),
        CONSTRAINT FK_refund_requests_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id)
    );
END;

IF OBJECT_ID('dbo.refund_provider_attempts', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.refund_provider_attempts (
        id BIGINT IDENTITY(1,1) NOT NULL,
        refund_request_id BIGINT NOT NULL,
        hotel_id BIGINT NOT NULL,
        provider VARCHAR(30) NOT NULL,
        attempt_number INT NOT NULL,
        idempotency_key VARCHAR(180) NOT NULL,
        requested_amount DECIMAL(18,2) NOT NULL,
        status VARCHAR(30) NOT NULL,
        provider_reference VARCHAR(160) NULL,
        requested_at DATETIME2 NOT NULL,
        completed_at DATETIME2 NULL,
        failure_code VARCHAR(80) NULL,
        response_code VARCHAR(80) NULL,
        details_json NVARCHAR(MAX) NULL,
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(255) NULL,
        updated_by NVARCHAR(255) NULL,
        CONSTRAINT PK_refund_provider_attempts PRIMARY KEY (id),
        CONSTRAINT UQ_refund_attempts_request_number UNIQUE (refund_request_id, attempt_number),
        CONSTRAINT UQ_refund_attempts_idempotency UNIQUE (idempotency_key),
        CONSTRAINT CK_refund_attempts_number CHECK (attempt_number > 0),
        CONSTRAINT CK_refund_attempts_amount CHECK (requested_amount > 0),
        CONSTRAINT CK_refund_attempts_status CHECK (status IN ('REQUESTED','PENDING_PROVIDER','SUCCEEDED','FAILED')),
        CONSTRAINT FK_refund_attempts_request FOREIGN KEY (refund_request_id) REFERENCES dbo.refund_requests(id),
        CONSTRAINT FK_refund_attempts_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id)
    );
END;

IF COL_LENGTH('dbo.notifications', 'event_key') IS NULL
BEGIN
    ALTER TABLE dbo.notifications ADD event_key VARCHAR(160) NULL;
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.refund_requests')
      AND name = 'IX_refund_requests_reservation_status'
)
BEGIN
    CREATE INDEX IX_refund_requests_reservation_status
        ON dbo.refund_requests(reservation_id, status);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.refund_requests')
      AND name = 'IX_refund_requests_hotel_status'
)
BEGIN
    CREATE INDEX IX_refund_requests_hotel_status
        ON dbo.refund_requests(hotel_id, status);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.refund_provider_attempts')
      AND name = 'IX_refund_attempts_request_status'
)
BEGIN
    CREATE INDEX IX_refund_attempts_request_status
        ON dbo.refund_provider_attempts(refund_request_id, status);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.refund_provider_attempts')
      AND name = 'IX_refund_attempts_hotel_status'
)
BEGIN
    CREATE INDEX IX_refund_attempts_hotel_status
        ON dbo.refund_provider_attempts(hotel_id, status);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.notifications')
      AND name = 'UX_notifications_event_key'
)
BEGIN
    CREATE UNIQUE INDEX UX_notifications_event_key
        ON dbo.notifications(event_key)
        WHERE event_key IS NOT NULL;
END;
