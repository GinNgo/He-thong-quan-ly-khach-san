SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

IF OBJECT_ID('dbo.reservations', 'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.reservations', 'deposit_configuration_id') IS NULL
        ALTER TABLE dbo.reservations ADD deposit_configuration_id BIGINT NULL;

    IF COL_LENGTH('dbo.reservations', 'deposit_configuration_version') IS NULL
        ALTER TABLE dbo.reservations ADD deposit_configuration_version BIGINT NULL;

    IF COL_LENGTH('dbo.reservations', 'deposit_policy_type') IS NULL
        ALTER TABLE dbo.reservations ADD deposit_policy_type VARCHAR(20) NULL;

    IF COL_LENGTH('dbo.reservations', 'deposit_policy_value') IS NULL
        ALTER TABLE dbo.reservations ADD deposit_policy_value DECIMAL(19,0) NULL;

    IF COL_LENGTH('dbo.reservations', 'deposit_booking_total') IS NULL
        ALTER TABLE dbo.reservations ADD deposit_booking_total DECIMAL(19,0) NULL;

    IF COL_LENGTH('dbo.reservations', 'deposit_required') IS NULL
        ALTER TABLE dbo.reservations ADD deposit_required DECIMAL(19,0) NULL;

    IF COL_LENGTH('dbo.reservations', 'deposit_currency') IS NULL
        ALTER TABLE dbo.reservations ADD deposit_currency VARCHAR(3) NULL;

    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_reservation_deposit_configuration')
        ALTER TABLE dbo.reservations ADD CONSTRAINT FK_reservation_deposit_configuration
            FOREIGN KEY (deposit_configuration_id) REFERENCES dbo.property_payment_configurations(id);

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_reservation_deposit_snapshot')
        ALTER TABLE dbo.reservations ADD CONSTRAINT CK_reservation_deposit_snapshot CHECK (
            (
                deposit_configuration_id IS NULL
                AND deposit_configuration_version IS NULL
                AND deposit_policy_type IS NULL
                AND deposit_policy_value IS NULL
                AND deposit_booking_total IS NULL
                AND deposit_required IS NULL
                AND deposit_currency IS NULL
            )
            OR
            (
                deposit_configuration_id IS NOT NULL
                AND deposit_configuration_version >= 0
                AND deposit_policy_type IN ('NONE', 'FIXED', 'PERCENTAGE')
                AND deposit_policy_value IS NOT NULL
                AND deposit_booking_total > 0
                AND deposit_required >= 0
                AND deposit_required <= deposit_booking_total
                AND deposit_currency = 'VND'
                AND (
                    (deposit_policy_type = 'NONE' AND deposit_policy_value = 0 AND deposit_required = 0)
                    OR (deposit_policy_type = 'FIXED' AND deposit_policy_value > 0)
                    OR (deposit_policy_type = 'PERCENTAGE' AND deposit_policy_value BETWEEN 1 AND 100)
                )
            )
        );

    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE object_id = OBJECT_ID('dbo.reservations')
          AND name = 'IX_reservations_hotel_deposit_snapshot'
    )
        CREATE INDEX IX_reservations_hotel_deposit_snapshot
            ON dbo.reservations(hotel_id, deposit_policy_type, deposit_required)
            WHERE deposit_policy_type IS NOT NULL;
END;
