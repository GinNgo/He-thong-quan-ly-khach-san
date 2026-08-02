SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

IF COL_LENGTH('dbo.reservations', 'booking_idempotency_scope') IS NULL
    ALTER TABLE dbo.reservations ADD booking_idempotency_scope VARCHAR(160) NULL;

IF COL_LENGTH('dbo.reservations', 'booking_idempotency_key') IS NULL
    ALTER TABLE dbo.reservations ADD booking_idempotency_key VARCHAR(160) NULL;

IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE object_id = OBJECT_ID('dbo.reservations')
                 AND name = 'UX_reservations_booking_idempotency')
    CREATE UNIQUE INDEX UX_reservations_booking_idempotency
        ON dbo.reservations(booking_idempotency_scope, booking_idempotency_key)
        WHERE booking_idempotency_scope IS NOT NULL
          AND booking_idempotency_key IS NOT NULL;
