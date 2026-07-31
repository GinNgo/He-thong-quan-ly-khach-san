SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

IF OBJECT_ID('dbo.housekeeping_tasks', 'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.housekeeping_tasks', 'checkout_effect_key') IS NULL
        ALTER TABLE dbo.housekeeping_tasks ADD checkout_effect_key VARCHAR(120) NULL;

    IF EXISTS (
        SELECT hotel_id, checkout_effect_key
        FROM dbo.housekeeping_tasks
        WHERE checkout_effect_key IS NOT NULL
        GROUP BY hotel_id, checkout_effect_key
        HAVING COUNT_BIG(*) > 1
    )
        THROW 51035, 'Duplicate housekeeping checkout effects must be resolved before V33.', 1;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE object_id = OBJECT_ID('dbo.housekeeping_tasks')
          AND name = 'UX_housekeeping_checkout_effect'
    )
        CREATE UNIQUE INDEX UX_housekeeping_checkout_effect
            ON dbo.housekeeping_tasks(hotel_id, checkout_effect_key)
            WHERE checkout_effect_key IS NOT NULL;
END;
