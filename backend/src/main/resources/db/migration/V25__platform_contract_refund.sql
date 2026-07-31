SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

IF OBJECT_ID('dbo.platform_software_contracts', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.platform_software_contracts (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_platform_software_contracts PRIMARY KEY,
        public_id VARCHAR(64) NOT NULL,
        target_hotel_id BIGINT NOT NULL,
        owner_user_id BIGINT NOT NULL,
        order_id BIGINT NOT NULL,
        originating_transaction_id BIGINT NOT NULL,
        supersedes_contract_id BIGINT NULL,
        plan_id BIGINT NOT NULL,
        plan_snapshot_json NVARCHAR(MAX) NOT NULL,
        feature_snapshot_json NVARCHAR(MAX) NOT NULL,
        effective_from DATETIME2 NOT NULL,
        effective_until DATETIME2 NULL,
        is_lifetime BIT NOT NULL CONSTRAINT DF_platform_contract_lifetime DEFAULT 0,
        contract_value DECIMAL(19,0) NOT NULL,
        currency VARCHAR(3) NOT NULL CONSTRAINT DF_platform_contract_currency DEFAULT 'VND',
        status VARCHAR(20) NOT NULL,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_platform_contract_created DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_platform_contract_hotel FOREIGN KEY (target_hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_platform_contract_owner FOREIGN KEY (owner_user_id) REFERENCES dbo.users(id),
        CONSTRAINT FK_platform_contract_order FOREIGN KEY (order_id) REFERENCES dbo.platform_subscription_orders(id),
        CONSTRAINT FK_platform_contract_transaction FOREIGN KEY (originating_transaction_id) REFERENCES dbo.platform_financial_transactions(id),
        CONSTRAINT FK_platform_contract_superseded FOREIGN KEY (supersedes_contract_id) REFERENCES dbo.platform_software_contracts(id),
        CONSTRAINT FK_platform_contract_plan FOREIGN KEY (plan_id) REFERENCES dbo.subscription_plans(id),
        CONSTRAINT UQ_platform_contract_public UNIQUE (public_id),
        CONSTRAINT UQ_platform_contract_order UNIQUE (order_id),
        CONSTRAINT CK_platform_contract_currency CHECK (currency = 'VND'),
        CONSTRAINT CK_platform_contract_status CHECK (status IN ('ACTIVE','SUPERSEDED','EXPIRED','REVOKED','REFUNDED')),
        CONSTRAINT CK_platform_contract_dates CHECK (is_lifetime = 1 OR effective_until > effective_from)
    );
END;

IF OBJECT_ID('dbo.platform_subscription_entitlements', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.platform_subscription_entitlements (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_platform_subscription_entitlements PRIMARY KEY,
        target_hotel_id BIGINT NOT NULL,
        contract_id BIGINT NOT NULL,
        plan_id BIGINT NOT NULL,
        feature_snapshot_json NVARCHAR(MAX) NOT NULL,
        effective_from DATETIME2 NOT NULL,
        effective_until DATETIME2 NULL,
        is_lifetime BIT NOT NULL,
        status VARCHAR(20) NOT NULL,
        version BIGINT NOT NULL CONSTRAINT DF_platform_entitlement_version DEFAULT 0,
        updated_at DATETIME2 NOT NULL CONSTRAINT DF_platform_entitlement_updated DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_platform_entitlement_hotel FOREIGN KEY (target_hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_platform_entitlement_contract FOREIGN KEY (contract_id) REFERENCES dbo.platform_software_contracts(id),
        CONSTRAINT FK_platform_entitlement_plan FOREIGN KEY (plan_id) REFERENCES dbo.subscription_plans(id),
        CONSTRAINT UQ_platform_entitlement_hotel UNIQUE (target_hotel_id),
        CONSTRAINT CK_platform_entitlement_status CHECK (status IN ('ACTIVE','EXPIRED','REVOKED','REFUNDED'))
    );
END;

IF OBJECT_ID('dbo.platform_subscription_histories', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.platform_subscription_histories (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_platform_subscription_histories PRIMARY KEY,
        target_hotel_id BIGINT NOT NULL,
        order_id BIGINT NOT NULL,
        contract_id BIGINT NULL,
        transaction_id BIGINT NULL,
        action_type VARCHAR(30) NOT NULL,
        previous_state_json NVARCHAR(MAX) NULL,
        new_state_json NVARCHAR(MAX) NOT NULL,
        actor_type VARCHAR(30) NOT NULL,
        actor_id BIGINT NULL,
        reason NVARCHAR(1000) NULL,
        occurred_at DATETIME2 NOT NULL CONSTRAINT DF_platform_history_occurred DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_platform_history_hotel FOREIGN KEY (target_hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_platform_history_order FOREIGN KEY (order_id) REFERENCES dbo.platform_subscription_orders(id),
        CONSTRAINT FK_platform_history_contract FOREIGN KEY (contract_id) REFERENCES dbo.platform_software_contracts(id),
        CONSTRAINT FK_platform_history_transaction FOREIGN KEY (transaction_id) REFERENCES dbo.platform_financial_transactions(id),
        CONSTRAINT CK_platform_history_action CHECK (action_type IN ('PURCHASED','RENEWED','UPGRADED','DOWNGRADE_BLOCKED','REVOKED','EXPIRED','REFUNDED'))
    );
END;

IF OBJECT_ID('dbo.platform_refund_requests', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.platform_refund_requests (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_platform_refund_requests PRIMARY KEY,
        public_id VARCHAR(64) NOT NULL,
        original_transaction_id BIGINT NOT NULL,
        order_id BIGINT NOT NULL,
        requested_amount DECIMAL(19,0) NOT NULL,
        succeeded_amount DECIMAL(19,0) NULL,
        currency VARCHAR(3) NOT NULL CONSTRAINT DF_platform_refund_currency DEFAULT 'VND',
        reason NVARCHAR(1000) NOT NULL,
        requested_by BIGINT NOT NULL,
        approved_by BIGINT NULL,
        status VARCHAR(30) NOT NULL,
        policy_version VARCHAR(80) NULL,
        idempotency_key VARCHAR(160) NOT NULL,
        request_hash VARCHAR(128) NOT NULL,
        requested_at DATETIME2 NOT NULL CONSTRAINT DF_platform_refund_requested DEFAULT SYSUTCDATETIME(),
        completed_at DATETIME2 NULL,
        version BIGINT NOT NULL CONSTRAINT DF_platform_refund_version DEFAULT 0,
        CONSTRAINT FK_platform_refund_original FOREIGN KEY (original_transaction_id) REFERENCES dbo.platform_financial_transactions(id),
        CONSTRAINT FK_platform_refund_order FOREIGN KEY (order_id) REFERENCES dbo.platform_subscription_orders(id),
        CONSTRAINT FK_platform_refund_requester FOREIGN KEY (requested_by) REFERENCES dbo.users(id),
        CONSTRAINT FK_platform_refund_approver FOREIGN KEY (approved_by) REFERENCES dbo.users(id),
        CONSTRAINT UQ_platform_refund_public UNIQUE (public_id),
        CONSTRAINT UQ_platform_refund_idempotency UNIQUE (requested_by, idempotency_key),
        CONSTRAINT CK_platform_refund_amount CHECK (requested_amount > 0),
        CONSTRAINT CK_platform_refund_currency CHECK (currency = 'VND'),
        CONSTRAINT CK_platform_refund_status CHECK (status IN ('REQUESTED','POLICY_BLOCKED','PENDING_APPROVAL','PENDING_PROVIDER','SUCCEEDED','FAILED','CANCELLED'))
    );
END;

IF OBJECT_ID('dbo.platform_refund_attempts', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.platform_refund_attempts (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_platform_refund_attempts PRIMARY KEY,
        refund_request_id BIGINT NOT NULL,
        attempt_number INT NOT NULL,
        provider VARCHAR(40) NOT NULL,
        environment VARCHAR(20) NOT NULL,
        provider_reference VARCHAR(200) NULL,
        provider_event_id VARCHAR(200) NULL,
        status VARCHAR(30) NOT NULL,
        failure_code VARCHAR(100) NULL,
        retryable BIT NOT NULL CONSTRAINT DF_platform_refund_retryable DEFAULT 0,
        requested_at DATETIME2 NOT NULL CONSTRAINT DF_platform_refund_attempt_requested DEFAULT SYSUTCDATETIME(),
        completed_at DATETIME2 NULL,
        CONSTRAINT FK_platform_refund_attempt_request FOREIGN KEY (refund_request_id) REFERENCES dbo.platform_refund_requests(id),
        CONSTRAINT UQ_platform_refund_attempt UNIQUE (refund_request_id, attempt_number),
        CONSTRAINT CK_platform_refund_attempt_number CHECK (attempt_number > 0)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.platform_subscription_histories') AND name = 'IX_platform_history_hotel_occurred')
    CREATE INDEX IX_platform_history_hotel_occurred ON dbo.platform_subscription_histories(target_hotel_id, occurred_at);
