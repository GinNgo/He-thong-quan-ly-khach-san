SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

IF OBJECT_ID('dbo.promotion_campaigns', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.promotion_campaigns (
        id BIGINT IDENTITY(1,1) NOT NULL,
        code VARCHAR(80) NOT NULL,
        owner_type VARCHAR(20) NOT NULL,
        hotel_id BIGINT NULL,
        application_type VARCHAR(20) NOT NULL,
        name_vi NVARCHAR(255) NOT NULL,
        name_en NVARCHAR(255) NULL,
        discount_type VARCHAR(20) NOT NULL,
        discount_value DECIMAL(19,2) NOT NULL,
        max_discount DECIMAL(19,2) NULL,
        starts_at DATETIME2 NOT NULL,
        ends_at DATETIME2 NOT NULL,
        timezone VARCHAR(64) NOT NULL,
        eligibility_json NVARCHAR(MAX) NULL,
        budget DECIMAL(19,2) NULL,
        redemption_limit BIGINT NULL,
        per_customer_limit BIGINT NULL,
        stacking_policy VARCHAR(30) NOT NULL,
        priority INT NOT NULL CONSTRAINT DF_promotion_campaigns_priority DEFAULT 0,
        status VARCHAR(20) NOT NULL,
        version BIGINT NOT NULL CONSTRAINT DF_promotion_campaigns_version DEFAULT 0,
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(255) NULL,
        updated_by NVARCHAR(255) NULL,
        CONSTRAINT PK_promotion_campaigns PRIMARY KEY (id),
        CONSTRAINT FK_promotion_campaigns_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT CK_promotion_campaigns_owner CHECK (
            (owner_type = 'SYSTEM' AND hotel_id IS NULL) OR
            (owner_type = 'TENANT' AND hotel_id IS NOT NULL)
        ),
        CONSTRAINT CK_promotion_campaigns_application CHECK (application_type IN ('AUTOMATIC','COUPON')),
        CONSTRAINT CK_promotion_campaigns_discount_type CHECK (discount_type IN ('PERCENT','FIXED')),
        CONSTRAINT CK_promotion_campaigns_discount_value CHECK (discount_value > 0),
        CONSTRAINT CK_promotion_campaigns_max_discount CHECK (max_discount IS NULL OR max_discount >= 0),
        CONSTRAINT CK_promotion_campaigns_schedule CHECK (ends_at > starts_at),
        CONSTRAINT CK_promotion_campaigns_limits CHECK (
            (budget IS NULL OR budget >= 0) AND
            (redemption_limit IS NULL OR redemption_limit >= 0) AND
            (per_customer_limit IS NULL OR per_customer_limit >= 0)
        ),
        CONSTRAINT CK_promotion_campaigns_stacking CHECK (stacking_policy IN ('NO_COUPON','ALLOW_ONE_COUPON')),
        CONSTRAINT CK_promotion_campaigns_status CHECK (status IN ('DRAFT','SCHEDULED','ACTIVE','PAUSED','EXPIRED','REJECTED'))
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_promotion_campaigns_scope_code' AND object_id = OBJECT_ID('dbo.promotion_campaigns'))
    CREATE UNIQUE INDEX UX_promotion_campaigns_scope_code ON dbo.promotion_campaigns(owner_type, hotel_id, code);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_promotion_campaigns_hotel_status_time' AND object_id = OBJECT_ID('dbo.promotion_campaigns'))
    CREATE INDEX IX_promotion_campaigns_hotel_status_time ON dbo.promotion_campaigns(hotel_id, status, starts_at, ends_at);

IF OBJECT_ID('dbo.membership_tiers', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.membership_tiers (
        id BIGINT IDENTITY(1,1) NOT NULL,
        owner_type VARCHAR(20) NOT NULL,
        hotel_id BIGINT NULL,
        code VARCHAR(60) NOT NULL,
        name_vi NVARCHAR(255) NOT NULL,
        name_en NVARCHAR(255) NULL,
        tier_rank INT NOT NULL,
        eligibility_json NVARCHAR(MAX) NULL,
        benefits_json NVARCHAR(MAX) NULL,
        status VARCHAR(20) NOT NULL,
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(255) NULL,
        updated_by NVARCHAR(255) NULL,
        CONSTRAINT PK_membership_tiers PRIMARY KEY (id),
        CONSTRAINT FK_membership_tiers_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT CK_membership_tiers_owner CHECK (
            (owner_type = 'SYSTEM' AND hotel_id IS NULL) OR
            (owner_type = 'TENANT' AND hotel_id IS NOT NULL)
        ),
        CONSTRAINT CK_membership_tiers_rank CHECK (tier_rank >= 0),
        CONSTRAINT CK_membership_tiers_status CHECK (status IN ('DRAFT','ACTIVE','INACTIVE'))
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_membership_tiers_scope_code' AND object_id = OBJECT_ID('dbo.membership_tiers'))
    CREATE UNIQUE INDEX UX_membership_tiers_scope_code ON dbo.membership_tiers(owner_type, hotel_id, code);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_membership_tiers_hotel_status_rank' AND object_id = OBJECT_ID('dbo.membership_tiers'))
    CREATE INDEX IX_membership_tiers_hotel_status_rank ON dbo.membership_tiers(hotel_id, status, tier_rank);

IF OBJECT_ID('dbo.customer_memberships', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.customer_memberships (
        id BIGINT IDENTITY(1,1) NOT NULL,
        customer_id BIGINT NOT NULL,
        tier_id BIGINT NOT NULL,
        hotel_id BIGINT NULL,
        starts_at DATETIME2 NOT NULL,
        ends_at DATETIME2 NULL,
        status VARCHAR(20) NOT NULL,
        assignment_reason NVARCHAR(500) NULL,
        assigned_by_user_id BIGINT NULL,
        version BIGINT NOT NULL CONSTRAINT DF_customer_memberships_version DEFAULT 0,
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(255) NULL,
        updated_by NVARCHAR(255) NULL,
        CONSTRAINT PK_customer_memberships PRIMARY KEY (id),
        CONSTRAINT FK_customer_memberships_customer FOREIGN KEY (customer_id) REFERENCES dbo.users(id),
        CONSTRAINT FK_customer_memberships_tier FOREIGN KEY (tier_id) REFERENCES dbo.membership_tiers(id),
        CONSTRAINT FK_customer_memberships_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_customer_memberships_assigned_by FOREIGN KEY (assigned_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT CK_customer_memberships_schedule CHECK (ends_at IS NULL OR ends_at > starts_at),
        CONSTRAINT CK_customer_memberships_status CHECK (status IN ('ACTIVE','EXPIRED','REVOKED'))
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_customer_memberships_customer_status_time' AND object_id = OBJECT_ID('dbo.customer_memberships'))
    CREATE INDEX IX_customer_memberships_customer_status_time ON dbo.customer_memberships(customer_id, status, starts_at, ends_at);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_customer_memberships_hotel_customer' AND object_id = OBJECT_ID('dbo.customer_memberships'))
    CREATE INDEX IX_customer_memberships_hotel_customer ON dbo.customer_memberships(hotel_id, customer_id);

IF OBJECT_ID('dbo.promotion_redemptions', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.promotion_redemptions (
        id BIGINT IDENTITY(1,1) NOT NULL,
        campaign_id BIGINT NOT NULL,
        customer_id BIGINT NOT NULL,
        reservation_id BIGINT NOT NULL,
        hotel_id BIGINT NOT NULL,
        quote_key VARCHAR(120) NOT NULL,
        discount_amount DECIMAL(19,2) NOT NULL,
        status VARCHAR(20) NOT NULL,
        idempotency_key VARCHAR(160) NOT NULL,
        redeemed_at DATETIME2 NOT NULL,
        reversed_at DATETIME2 NULL,
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(255) NULL,
        updated_by NVARCHAR(255) NULL,
        CONSTRAINT PK_promotion_redemptions PRIMARY KEY (id),
        CONSTRAINT UQ_promotion_redemptions_idempotency UNIQUE (idempotency_key),
        CONSTRAINT FK_promotion_redemptions_campaign FOREIGN KEY (campaign_id) REFERENCES dbo.promotion_campaigns(id),
        CONSTRAINT FK_promotion_redemptions_customer FOREIGN KEY (customer_id) REFERENCES dbo.users(id),
        CONSTRAINT FK_promotion_redemptions_reservation FOREIGN KEY (reservation_id) REFERENCES dbo.reservations(id),
        CONSTRAINT FK_promotion_redemptions_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT CK_promotion_redemptions_amount CHECK (discount_amount >= 0),
        CONSTRAINT CK_promotion_redemptions_status CHECK (status IN ('RESERVED','APPLIED','REVERSED'))
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_promotion_redemptions_campaign_status' AND object_id = OBJECT_ID('dbo.promotion_redemptions'))
    CREATE INDEX IX_promotion_redemptions_campaign_status ON dbo.promotion_redemptions(campaign_id, status);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_promotion_redemptions_hotel_customer' AND object_id = OBJECT_ID('dbo.promotion_redemptions'))
    CREATE INDEX IX_promotion_redemptions_hotel_customer ON dbo.promotion_redemptions(hotel_id, customer_id);

IF OBJECT_ID('dbo.sponsored_placements', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.sponsored_placements (
        id BIGINT IDENTITY(1,1) NOT NULL,
        hotel_id BIGINT NULL,
        placement_surface VARCHAR(40) NOT NULL,
        placement_kind VARCHAR(20) NOT NULL,
        status VARCHAR(20) NOT NULL,
        title_vi NVARCHAR(255) NOT NULL,
        title_en NVARCHAR(255) NOT NULL,
        description_vi NVARCHAR(1000) NULL,
        description_en NVARCHAR(1000) NULL,
        image_url NVARCHAR(1000) NOT NULL,
        image_alt_vi NVARCHAR(500) NOT NULL,
        image_alt_en NVARCHAR(500) NOT NULL,
        target_type VARCHAR(30) NOT NULL,
        target_hotel_id BIGINT NULL,
        target_query_json NVARCHAR(MAX) NULL,
        target_province_id BIGINT NULL,
        target_landmark_id BIGINT NULL,
        starts_at DATETIME2 NOT NULL,
        ends_at DATETIME2 NOT NULL,
        sort_priority INT NOT NULL CONSTRAINT DF_sponsored_placements_priority DEFAULT 0,
        budget DECIMAL(19,2) NULL,
        spent_amount DECIMAL(19,2) NOT NULL CONSTRAINT DF_sponsored_placements_spent DEFAULT 0,
        impression_limit BIGINT NULL,
        impression_count BIGINT NOT NULL CONSTRAINT DF_sponsored_placements_impressions DEFAULT 0,
        click_limit BIGINT NULL,
        click_count BIGINT NOT NULL CONSTRAINT DF_sponsored_placements_clicks DEFAULT 0,
        approved_by_user_id BIGINT NULL,
        approved_at DATETIME2 NULL,
        rejected_reason NVARCHAR(500) NULL,
        version BIGINT NOT NULL CONSTRAINT DF_sponsored_placements_version DEFAULT 0,
        created_at DATETIME2 NULL,
        updated_at DATETIME2 NULL,
        created_by NVARCHAR(255) NULL,
        updated_by NVARCHAR(255) NULL,
        CONSTRAINT PK_sponsored_placements PRIMARY KEY (id),
        CONSTRAINT FK_sponsored_placements_hotel FOREIGN KEY (hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_sponsored_placements_target_hotel FOREIGN KEY (target_hotel_id) REFERENCES dbo.hotels(id),
        CONSTRAINT FK_sponsored_placements_approved_by FOREIGN KEY (approved_by_user_id) REFERENCES dbo.users(id),
        CONSTRAINT CK_sponsored_placements_owner CHECK (
            (placement_kind = 'EDITORIAL' AND hotel_id IS NULL) OR
            (placement_kind = 'SPONSORED' AND hotel_id IS NOT NULL)
        ),
        CONSTRAINT CK_sponsored_placements_surface CHECK (placement_surface IN ('HOME_PARTNER_SPOTLIGHT','SEARCH_RESULTS')),
        CONSTRAINT CK_sponsored_placements_kind CHECK (placement_kind IN ('EDITORIAL','SPONSORED')),
        CONSTRAINT CK_sponsored_placements_status CHECK (status IN ('DRAFT','SCHEDULED','ACTIVE','PAUSED','EXPIRED','REJECTED')),
        CONSTRAINT CK_sponsored_placements_target CHECK (
            (target_type = 'PROPERTY' AND target_hotel_id IS NOT NULL AND target_query_json IS NULL) OR
            (target_type = 'SEARCH_COLLECTION' AND target_hotel_id IS NULL AND target_query_json IS NOT NULL)
        ),
        CONSTRAINT CK_sponsored_placements_schedule CHECK (ends_at > starts_at),
        CONSTRAINT CK_sponsored_placements_quota CHECK (
            (budget IS NULL OR budget >= 0) AND spent_amount >= 0 AND
            (impression_limit IS NULL OR impression_limit >= 0) AND impression_count >= 0 AND
            (click_limit IS NULL OR click_limit >= 0) AND click_count >= 0
        )
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_sponsored_placements_public' AND object_id = OBJECT_ID('dbo.sponsored_placements'))
    CREATE INDEX IX_sponsored_placements_public ON dbo.sponsored_placements(placement_surface, status, starts_at, ends_at, sort_priority DESC);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_sponsored_placements_hotel_status' AND object_id = OBJECT_ID('dbo.sponsored_placements'))
    CREATE INDEX IX_sponsored_placements_hotel_status ON dbo.sponsored_placements(hotel_id, status);

