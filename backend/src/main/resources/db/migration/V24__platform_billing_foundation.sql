SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

IF OBJECT_ID('dbo.platform_payment_configurations', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.platform_payment_configurations (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_platform_payment_configurations PRIMARY KEY,
        provider VARCHAR(40) NOT NULL,
        environment VARCHAR(20) NOT NULL,
        enabled BIT NOT NULL CONSTRAINT DF_platform_config_enabled DEFAULT 0,
        merchant_reference_masked NVARCHAR(160) NULL,
        secret_reference NVARCHAR(500) NULL,
        bank_name NVARCHAR(160) NULL,
        bank_account_masked NVARCHAR(80) NULL,
        callback_url NVARCHAR(1000) NULL,
        production_approved_at DATETIME2 NULL,
        production_approved_by BIGINT NULL,
        version BIGINT NOT NULL CONSTRAINT DF_platform_config_version DEFAULT 0,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_platform_config_created DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2 NOT NULL CONSTRAINT DF_platform_config_updated DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_platform_config_approver FOREIGN KEY (production_approved_by) REFERENCES dbo.users(id),
        CONSTRAINT UQ_platform_config_provider_environment UNIQUE (provider, environment),
        CONSTRAINT CK_platform_config_environment CHECK (environment IN ('SIMULATOR','SANDBOX','PRODUCTION')),
        CONSTRAINT CK_platform_config_approval CHECK (environment <> 'PRODUCTION' OR production_approved_at IS NOT NULL)
    );
END;

IF OBJECT_ID('dbo.platform_subscription_orders', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.platform_subscription_orders (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_platform_subscription_orders PRIMARY KEY,
        public_id VARCHAR(64) NOT NULL,
        order_code VARCHAR(80) NOT NULL,
        owner_user_id BIGINT NOT NULL,
        target_hotel_id BIGINT NOT NULL,
        operation VARCHAR(20) NOT NULL,
        plan_id BIGINT NOT NULL,
        plan_version VARCHAR(80) NOT NULL,
        plan_code VARCHAR(80) NOT NULL,
        plan_name NVARCHAR(255) NOT NULL,
        price DECIMAL(19,0) NOT NULL,
        currency VARCHAR(3) NOT NULL CONSTRAINT DF_platform_order_currency DEFAULT 'VND',
        billing_period VARCHAR(30) NOT NULL,
        duration_value INT NOT NULL,
        duration_unit VARCHAR(20) NOT NULL,
        feature_snapshot_json NVARCHAR(MAX) NOT NULL,
        status VARCHAR(30) NOT NULL,
        idempotency_key VARCHAR(160) NOT NULL,
        request_hash VARCHAR(128) NOT NULL,
        expires_at DATETIME2 NOT NULL,
        applied_at DATETIME2 NULL,
        version BIGINT NOT NULL CONSTRAINT DF_platform_order_version DEFAULT 0,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_platform_order_created DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2 NOT NULL CONSTRAINT DF_platform_order_updated DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_platform_order_owner FOREIGN KEY (owner_user_id) REFERENCES dbo.users(id),
        CONSTRAINT FK_platform_order_hotel FOREIGN KEY (target_hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_platform_order_plan FOREIGN KEY (plan_id) REFERENCES dbo.subscription_plans(id),
        CONSTRAINT UQ_platform_order_public UNIQUE (public_id),
        CONSTRAINT UQ_platform_order_code UNIQUE (order_code),
        CONSTRAINT UQ_platform_order_idempotency UNIQUE (owner_user_id, idempotency_key),
        CONSTRAINT CK_platform_order_operation CHECK (operation IN ('PURCHASE','RENEW','UPGRADE','DOWNGRADE','REFUND')),
        CONSTRAINT CK_platform_order_amount CHECK (price >= 0),
        CONSTRAINT CK_platform_order_currency CHECK (currency = 'VND'),
        CONSTRAINT CK_platform_order_duration CHECK (duration_value > 0 AND duration_unit IN ('DAY','MONTH','YEAR','LIFETIME')),
        CONSTRAINT CK_platform_order_status CHECK (status IN ('CREATED','PENDING_PAYMENT','PAID','APPLIED','FAILED','CANCELLED','EXPIRED','REFUNDED'))
    );
END;

IF OBJECT_ID('dbo.platform_payment_attempts', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.platform_payment_attempts (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_platform_payment_attempts PRIMARY KEY,
        public_id VARCHAR(64) NOT NULL,
        order_id BIGINT NOT NULL,
        configuration_id BIGINT NOT NULL,
        provider VARCHAR(40) NOT NULL,
        method VARCHAR(40) NOT NULL,
        environment VARCHAR(20) NOT NULL,
        expected_amount DECIMAL(19,0) NOT NULL,
        currency VARCHAR(3) NOT NULL CONSTRAINT DF_platform_attempt_currency DEFAULT 'VND',
        status VARCHAR(30) NOT NULL,
        idempotency_key VARCHAR(160) NOT NULL,
        request_hash VARCHAR(128) NOT NULL,
        provider_order_ref VARCHAR(160) NULL,
        provider_transaction_ref VARCHAR(200) NULL,
        provider_event_id VARCHAR(200) NULL,
        expires_at DATETIME2 NOT NULL,
        completed_at DATETIME2 NULL,
        failure_code VARCHAR(100) NULL,
        version BIGINT NOT NULL CONSTRAINT DF_platform_attempt_version DEFAULT 0,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_platform_attempt_created DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2 NOT NULL CONSTRAINT DF_platform_attempt_updated DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_platform_attempt_order FOREIGN KEY (order_id) REFERENCES dbo.platform_subscription_orders(id),
        CONSTRAINT FK_platform_attempt_config FOREIGN KEY (configuration_id) REFERENCES dbo.platform_payment_configurations(id),
        CONSTRAINT UQ_platform_attempt_public UNIQUE (public_id),
        CONSTRAINT UQ_platform_attempt_idempotency UNIQUE (order_id, idempotency_key),
        CONSTRAINT CK_platform_attempt_amount CHECK (expected_amount >= 0),
        CONSTRAINT CK_platform_attempt_currency CHECK (currency = 'VND'),
        CONSTRAINT CK_platform_attempt_environment CHECK (environment IN ('SIMULATOR','SANDBOX','PRODUCTION')),
        CONSTRAINT CK_platform_attempt_status CHECK (status IN ('CREATED','PENDING','PROCESSING','SUCCESS','FAILED','CANCELLED','PARTIALLY_REFUNDED','REFUNDED','EXPIRED'))
    );
END;

IF OBJECT_ID('dbo.platform_financial_transactions', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.platform_financial_transactions (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_platform_financial_transactions PRIMARY KEY,
        public_id VARCHAR(64) NOT NULL,
        order_id BIGINT NOT NULL,
        attempt_id BIGINT NULL,
        original_transaction_id BIGINT NULL,
        transaction_type VARCHAR(40) NOT NULL,
        direction VARCHAR(10) NOT NULL,
        amount DECIMAL(19,0) NOT NULL,
        currency VARCHAR(3) NOT NULL CONSTRAINT DF_platform_transaction_currency DEFAULT 'VND',
        method VARCHAR(40) NULL,
        provider VARCHAR(40) NULL,
        environment VARCHAR(20) NULL,
        provider_transaction_ref VARCHAR(200) NULL,
        idempotency_identity VARCHAR(200) NOT NULL,
        actor_type VARCHAR(30) NOT NULL,
        actor_id BIGINT NULL,
        reason NVARCHAR(1000) NULL,
        occurred_at DATETIME2 NOT NULL,
        recorded_at DATETIME2 NOT NULL CONSTRAINT DF_platform_transaction_recorded DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_platform_transaction_order FOREIGN KEY (order_id) REFERENCES dbo.platform_subscription_orders(id),
        CONSTRAINT FK_platform_transaction_attempt FOREIGN KEY (attempt_id) REFERENCES dbo.platform_payment_attempts(id),
        CONSTRAINT FK_platform_transaction_original FOREIGN KEY (original_transaction_id) REFERENCES dbo.platform_financial_transactions(id),
        CONSTRAINT UQ_platform_transaction_public UNIQUE (public_id),
        CONSTRAINT UQ_platform_transaction_effect UNIQUE (idempotency_identity),
        CONSTRAINT CK_platform_transaction_amount CHECK (amount > 0),
        CONSTRAINT CK_platform_transaction_currency CHECK (currency = 'VND'),
        CONSTRAINT CK_platform_transaction_direction CHECK (direction IN ('DEBIT','CREDIT')),
        CONSTRAINT CK_platform_transaction_type CHECK (transaction_type IN ('SUBSCRIPTION_PURCHASE','SUBSCRIPTION_RENEWAL','SUBSCRIPTION_UPGRADE','DOWNGRADE_CREDIT','SUBSCRIPTION_REFUND'))
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.platform_payment_attempts') AND name = 'UX_platform_attempt_provider_event')
    CREATE UNIQUE INDEX UX_platform_attempt_provider_event ON dbo.platform_payment_attempts(provider, environment, provider_event_id) WHERE provider_event_id IS NOT NULL;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.platform_financial_transactions') AND name = 'IX_platform_transactions_occurred')
    CREATE INDEX IX_platform_transactions_occurred ON dbo.platform_financial_transactions(occurred_at, transaction_type);
