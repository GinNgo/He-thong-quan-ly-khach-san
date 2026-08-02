SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

IF OBJECT_ID('dbo.legacy_subscription_entitlement_projections', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.legacy_subscription_entitlement_projections (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_legacy_entitlement_projection PRIMARY KEY,
        target_hotel_id BIGINT NOT NULL,
        owner_user_id BIGINT NOT NULL,
        plan_id BIGINT NOT NULL,
        feature_snapshot_json NVARCHAR(MAX) NOT NULL,
        source_subscription_ids VARCHAR(1000) NOT NULL,
        source_fingerprint VARCHAR(128) NOT NULL,
        effective_from DATETIME2 NOT NULL,
        effective_until DATETIME2 NULL,
        is_lifetime BIT NOT NULL CONSTRAINT DF_legacy_entitlement_projection_lifetime DEFAULT 0,
        status VARCHAR(20) NOT NULL CONSTRAINT DF_legacy_entitlement_projection_status DEFAULT 'ACTIVE',
        projected_at DATETIME2 NOT NULL CONSTRAINT DF_legacy_entitlement_projection_projected DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2 NOT NULL CONSTRAINT DF_legacy_entitlement_projection_updated DEFAULT SYSUTCDATETIME(),
        version BIGINT NOT NULL CONSTRAINT DF_legacy_entitlement_projection_version DEFAULT 0,
        CONSTRAINT FK_legacy_entitlement_projection_hotel FOREIGN KEY (target_hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_legacy_entitlement_projection_owner FOREIGN KEY (owner_user_id) REFERENCES dbo.users(id),
        CONSTRAINT FK_legacy_entitlement_projection_plan FOREIGN KEY (plan_id) REFERENCES dbo.subscription_plans(id),
        CONSTRAINT UQ_legacy_entitlement_projection_hotel UNIQUE (target_hotel_id),
        CONSTRAINT CK_legacy_entitlement_projection_status CHECK (status IN ('ACTIVE','EXPIRED')),
        CONSTRAINT CK_legacy_entitlement_projection_dates CHECK (is_lifetime = 1 OR effective_until > effective_from)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE object_id = OBJECT_ID('dbo.legacy_subscription_entitlement_projections')
                 AND name = 'IX_legacy_entitlement_projection_owner')
    CREATE INDEX IX_legacy_entitlement_projection_owner
        ON dbo.legacy_subscription_entitlement_projections(owner_user_id, status);
