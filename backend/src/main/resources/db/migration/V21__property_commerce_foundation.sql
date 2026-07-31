SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

IF OBJECT_ID('dbo.property_payment_configurations', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.property_payment_configurations (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_property_payment_configurations PRIMARY KEY,
        hotel_id BIGINT NOT NULL,
        enabled BIT NOT NULL CONSTRAINT DF_property_payment_config_enabled DEFAULT 0,
        environment VARCHAR(20) NOT NULL CONSTRAINT DF_property_payment_config_environment DEFAULT 'SIMULATOR',
        bank_name NVARCHAR(160) NULL,
        bank_code VARCHAR(40) NULL,
        account_name NVARCHAR(160) NULL,
        account_number_encrypted NVARCHAR(1000) NULL,
        account_number_masked NVARCHAR(80) NULL,
        deposit_policy_type VARCHAR(20) NOT NULL CONSTRAINT DF_property_deposit_policy DEFAULT 'NONE',
        deposit_value DECIMAL(19,0) NULL,
        payment_expiry_minutes INT NOT NULL CONSTRAINT DF_property_payment_expiry DEFAULT 15,
        transfer_template NVARCHAR(500) NULL,
        qr_provider VARCHAR(40) NULL,
        instructions_vi NVARCHAR(2000) NULL,
        instructions_en NVARCHAR(2000) NULL,
        production_approved_at DATETIME2 NULL,
        production_approved_by BIGINT NULL,
        version BIGINT NOT NULL CONSTRAINT DF_property_payment_config_version DEFAULT 0,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_property_payment_config_created DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2 NOT NULL CONSTRAINT DF_property_payment_config_updated DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_property_payment_config_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_property_payment_config_approver FOREIGN KEY (production_approved_by) REFERENCES dbo.users(id),
        CONSTRAINT CK_property_payment_config_environment CHECK (environment IN ('SIMULATOR','SANDBOX','PRODUCTION')),
        CONSTRAINT CK_property_deposit_policy CHECK (deposit_policy_type IN ('NONE','FIXED','PERCENTAGE')),
        CONSTRAINT CK_property_deposit_value CHECK (
            (deposit_policy_type = 'NONE' AND (deposit_value IS NULL OR deposit_value = 0)) OR
            (deposit_policy_type = 'FIXED' AND deposit_value > 0) OR
            (deposit_policy_type = 'PERCENTAGE' AND deposit_value > 0 AND deposit_value <= 100)
        ),
        CONSTRAINT CK_property_payment_expiry CHECK (payment_expiry_minutes BETWEEN 1 AND 10080),
        CONSTRAINT CK_property_production_approval CHECK (environment <> 'PRODUCTION' OR production_approved_at IS NOT NULL)
    );
END;

IF OBJECT_ID('dbo.property_payment_configuration_methods', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.property_payment_configuration_methods (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_property_payment_config_methods PRIMARY KEY,
        configuration_id BIGINT NOT NULL,
        hotel_id BIGINT NOT NULL,
        method VARCHAR(40) NOT NULL,
        enabled BIT NOT NULL CONSTRAINT DF_property_method_enabled DEFAULT 0,
        provider VARCHAR(40) NULL,
        merchant_reference_masked NVARCHAR(160) NULL,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_property_method_created DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2 NOT NULL CONSTRAINT DF_property_method_updated DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_property_method_config FOREIGN KEY (configuration_id) REFERENCES dbo.property_payment_configurations(id),
        CONSTRAINT FK_property_method_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT UQ_property_method UNIQUE (configuration_id, method),
        CONSTRAINT CK_property_method CHECK (method IN ('MANUAL_TRANSFER','QR_TRANSFER','VNPAY','MOMO','ZALOPAY','CASH','CARD_TERMINAL','OTHER'))
    );
END;

IF OBJECT_ID('dbo.property_payment_attempts', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.property_payment_attempts (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_property_payment_attempts PRIMARY KEY,
        public_id VARCHAR(64) NOT NULL,
        hotel_id BIGINT NOT NULL,
        reservation_id BIGINT NOT NULL,
        configuration_id BIGINT NULL,
        owner_user_id BIGINT NULL,
        purpose VARCHAR(30) NOT NULL,
        method VARCHAR(40) NOT NULL,
        provider VARCHAR(40) NOT NULL,
        environment VARCHAR(20) NOT NULL,
        expected_amount DECIMAL(19,0) NOT NULL,
        currency VARCHAR(3) NOT NULL CONSTRAINT DF_property_attempt_currency DEFAULT 'VND',
        unique_transfer_content NVARCHAR(160) NULL,
        receiver_snapshot_json NVARCHAR(MAX) NULL,
        status VARCHAR(30) NOT NULL,
        idempotency_key VARCHAR(160) NOT NULL,
        request_hash VARCHAR(128) NOT NULL,
        provider_order_ref VARCHAR(160) NULL,
        provider_transaction_ref VARCHAR(200) NULL,
        provider_event_id VARCHAR(200) NULL,
        expires_at DATETIME2 NOT NULL,
        verified_at DATETIME2 NULL,
        verified_by BIGINT NULL,
        failure_code VARCHAR(100) NULL,
        version BIGINT NOT NULL CONSTRAINT DF_property_attempt_version DEFAULT 0,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_property_attempt_created DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2 NOT NULL CONSTRAINT DF_property_attempt_updated DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_property_attempt_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_property_attempt_reservation FOREIGN KEY (reservation_id) REFERENCES dbo.reservations(id),
        CONSTRAINT FK_property_attempt_config FOREIGN KEY (configuration_id) REFERENCES dbo.property_payment_configurations(id),
        CONSTRAINT FK_property_attempt_owner FOREIGN KEY (owner_user_id) REFERENCES dbo.users(id),
        CONSTRAINT FK_property_attempt_verifier FOREIGN KEY (verified_by) REFERENCES dbo.users(id),
        CONSTRAINT UQ_property_attempt_public UNIQUE (public_id),
        CONSTRAINT UQ_property_attempt_idempotency UNIQUE (hotel_id, idempotency_key),
        CONSTRAINT CK_property_attempt_amount CHECK (expected_amount > 0),
        CONSTRAINT CK_property_attempt_currency CHECK (currency = 'VND'),
        CONSTRAINT CK_property_attempt_environment CHECK (environment IN ('SIMULATOR','SANDBOX','PRODUCTION')),
        CONSTRAINT CK_property_attempt_status CHECK (status IN ('CREATED','PENDING','PENDING_VERIFICATION','PROCESSING','SUCCESS','FAILED','CANCELLED','PARTIALLY_REFUNDED','REFUNDED','EXPIRED'))
    );
END;

IF OBJECT_ID('dbo.property_financial_transactions', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.property_financial_transactions (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_property_financial_transactions PRIMARY KEY,
        public_id VARCHAR(64) NOT NULL,
        hotel_id BIGINT NOT NULL,
        reservation_id BIGINT NULL,
        invoice_id BIGINT NULL,
        attempt_id BIGINT NULL,
        original_transaction_id BIGINT NULL,
        transaction_type VARCHAR(40) NOT NULL,
        direction VARCHAR(10) NOT NULL,
        amount DECIMAL(19,0) NOT NULL,
        currency VARCHAR(3) NOT NULL CONSTRAINT DF_property_transaction_currency DEFAULT 'VND',
        method VARCHAR(40) NULL,
        provider VARCHAR(40) NULL,
        environment VARCHAR(20) NULL,
        provider_transaction_ref VARCHAR(200) NULL,
        idempotency_identity VARCHAR(200) NOT NULL,
        actor_type VARCHAR(30) NOT NULL,
        actor_id BIGINT NULL,
        reason NVARCHAR(1000) NULL,
        occurred_at DATETIME2 NOT NULL,
        recorded_at DATETIME2 NOT NULL CONSTRAINT DF_property_transaction_recorded DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_property_transaction_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_property_transaction_reservation FOREIGN KEY (reservation_id) REFERENCES dbo.reservations(id),
        CONSTRAINT FK_property_transaction_attempt FOREIGN KEY (attempt_id) REFERENCES dbo.property_payment_attempts(id),
        CONSTRAINT FK_property_transaction_original FOREIGN KEY (original_transaction_id) REFERENCES dbo.property_financial_transactions(id),
        CONSTRAINT UQ_property_transaction_public UNIQUE (public_id),
        CONSTRAINT UQ_property_transaction_effect UNIQUE (idempotency_identity),
        CONSTRAINT CK_property_transaction_amount CHECK (amount > 0),
        CONSTRAINT CK_property_transaction_currency CHECK (currency = 'VND'),
        CONSTRAINT CK_property_transaction_direction CHECK (direction IN ('DEBIT','CREDIT')),
        CONSTRAINT CK_property_transaction_type CHECK (transaction_type IN ('BOOKING_DEPOSIT','ROOM_PAYMENT','SERVICE_PAYMENT','SURCHARGE','MANUAL_ADJUSTMENT','REFUND'))
    );
END;

IF OBJECT_ID('dbo.booking_financial_summaries', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.booking_financial_summaries (
        reservation_id BIGINT NOT NULL CONSTRAINT PK_booking_financial_summaries PRIMARY KEY,
        hotel_id BIGINT NOT NULL,
        gross_charges DECIMAL(19,0) NOT NULL CONSTRAINT DF_booking_summary_gross DEFAULT 0,
        deposit_required DECIMAL(19,0) NOT NULL CONSTRAINT DF_booking_summary_deposit DEFAULT 0,
        successful_payments DECIMAL(19,0) NOT NULL CONSTRAINT DF_booking_summary_payments DEFAULT 0,
        successful_refunds DECIMAL(19,0) NOT NULL CONSTRAINT DF_booking_summary_refunds DEFAULT 0,
        remaining_balance DECIMAL(19,0) NOT NULL CONSTRAINT DF_booking_summary_balance DEFAULT 0,
        financial_state VARCHAR(30) NOT NULL CONSTRAINT DF_booking_summary_state DEFAULT 'UNPAID',
        source_version BIGINT NOT NULL CONSTRAINT DF_booking_summary_version DEFAULT 0,
        calculated_at DATETIME2 NOT NULL CONSTRAINT DF_booking_summary_calculated DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_booking_summary_reservation FOREIGN KEY (reservation_id) REFERENCES dbo.reservations(id),
        CONSTRAINT FK_booking_summary_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT CK_booking_summary_state CHECK (financial_state IN ('UNPAID','PARTIALLY_PAID','DEPOSIT_PAID','PAID','OVERPAID','PARTIALLY_REFUNDED','REFUNDED'))
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.property_payment_configurations') AND name = 'UX_property_payment_config_active')
    CREATE UNIQUE INDEX UX_property_payment_config_active ON dbo.property_payment_configurations(hotel_id);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.property_payment_attempts') AND name = 'UX_property_attempt_provider_event')
    CREATE UNIQUE INDEX UX_property_attempt_provider_event ON dbo.property_payment_attempts(provider, environment, provider_event_id) WHERE provider_event_id IS NOT NULL;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.property_financial_transactions') AND name = 'IX_property_transactions_hotel_occurred')
    CREATE INDEX IX_property_transactions_hotel_occurred ON dbo.property_financial_transactions(hotel_id, occurred_at, transaction_type);
