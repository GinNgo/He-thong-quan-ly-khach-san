SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

IF EXISTS (
    SELECT hotel_id, unique_transfer_content
    FROM dbo.property_payment_attempts
    WHERE unique_transfer_content IS NOT NULL
    GROUP BY hotel_id, unique_transfer_content
    HAVING COUNT_BIG(*) > 1
)
BEGIN
    THROW 51031, 'Duplicate property payment transfer content must be resolved before V31.', 1;
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.property_payment_attempts')
      AND name = 'UX_property_attempt_transfer_content'
)
BEGIN
    CREATE UNIQUE INDEX UX_property_attempt_transfer_content
        ON dbo.property_payment_attempts(hotel_id, unique_transfer_content)
        WHERE unique_transfer_content IS NOT NULL;
END;
