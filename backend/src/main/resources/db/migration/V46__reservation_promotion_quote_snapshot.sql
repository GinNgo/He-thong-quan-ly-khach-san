SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

IF COL_LENGTH('dbo.reservations', 'pricing_quote_id') IS NULL
    ALTER TABLE dbo.reservations ADD pricing_quote_id VARCHAR(64) NULL;
IF COL_LENGTH('dbo.reservations', 'pricing_quote_expires_at') IS NULL
    ALTER TABLE dbo.reservations ADD pricing_quote_expires_at DATETIME2 NULL;
IF COL_LENGTH('dbo.reservations', 'pricing_nightly_price') IS NULL
    ALTER TABLE dbo.reservations ADD pricing_nightly_price DECIMAL(19,0) NULL;
IF COL_LENGTH('dbo.reservations', 'pricing_room_type_id') IS NULL
    ALTER TABLE dbo.reservations ADD pricing_room_type_id BIGINT NULL;
IF COL_LENGTH('dbo.reservations', 'pricing_nights') IS NULL
    ALTER TABLE dbo.reservations ADD pricing_nights INT NULL;
IF COL_LENGTH('dbo.reservations', 'pricing_room_quantity') IS NULL
    ALTER TABLE dbo.reservations ADD pricing_room_quantity INT NULL;
IF COL_LENGTH('dbo.reservations', 'pricing_base_subtotal') IS NULL
    ALTER TABLE dbo.reservations ADD pricing_base_subtotal DECIMAL(19,0) NULL;
IF COL_LENGTH('dbo.reservations', 'pricing_tax_amount') IS NULL
    ALTER TABLE dbo.reservations ADD pricing_tax_amount DECIMAL(19,0) NULL;
IF COL_LENGTH('dbo.reservations', 'pricing_fee_amount') IS NULL
    ALTER TABLE dbo.reservations ADD pricing_fee_amount DECIMAL(19,0) NULL;
IF COL_LENGTH('dbo.reservations', 'pricing_discount_amount') IS NULL
    ALTER TABLE dbo.reservations ADD pricing_discount_amount DECIMAL(19,0) NULL;
IF COL_LENGTH('dbo.reservations', 'pricing_currency') IS NULL
    ALTER TABLE dbo.reservations ADD pricing_currency VARCHAR(3) NULL;
IF COL_LENGTH('dbo.reservations', 'pricing_promotions_json') IS NULL
    ALTER TABLE dbo.reservations ADD pricing_promotions_json NVARCHAR(MAX) NULL;
IF COL_LENGTH('dbo.reservations', 'pricing_member_benefit_json') IS NULL
    ALTER TABLE dbo.reservations ADD pricing_member_benefit_json NVARCHAR(MAX) NULL;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_reservations_pricing_quote_id' AND object_id = OBJECT_ID('dbo.reservations'))
    CREATE INDEX IX_reservations_pricing_quote_id ON dbo.reservations(pricing_quote_id) WHERE pricing_quote_id IS NOT NULL;
