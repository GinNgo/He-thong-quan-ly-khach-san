IF OBJECT_ID('dbo.reservations', 'U') IS NOT NULL
   AND COL_LENGTH('dbo.reservations', 'status') IS NOT NULL
BEGIN
    UPDATE dbo.reservations
    SET status = 'PENDING_PAYMENT'
    WHERE UPPER(LTRIM(RTRIM(status))) = 'PENDING';
END;

IF OBJECT_ID('dbo.payments', 'U') IS NOT NULL
   AND COL_LENGTH('dbo.payments', 'status') IS NOT NULL
BEGIN
    UPDATE dbo.payments
    SET status = 'SUCCEEDED'
    WHERE UPPER(LTRIM(RTRIM(status))) IN ('SUCCESS', 'PAID', 'COMPLETED', 'REFUNDED');

    UPDATE dbo.payments
    SET status = 'PENDING'
    WHERE UPPER(LTRIM(RTRIM(status))) IN ('PENDING_PAYMENT', 'PROCESSING');
END;
