IF COL_LENGTH('dbo.payment_sessions', 'checkout_url') IS NULL
BEGIN
    ALTER TABLE dbo.payment_sessions
        ADD checkout_url VARCHAR(2048) NULL;
END;
