SET NOCOUNT ON;

DECLARE @issues TABLE (
    issue_code VARCHAR(80) NOT NULL,
    severity VARCHAR(10) NOT NULL,
    affected_count BIGINT NOT NULL,
    details NVARCHAR(1000) NOT NULL
);

IF OBJECT_ID('dbo.payments', 'U') IS NULL
    INSERT INTO @issues VALUES ('BASE_PAYMENTS_MISSING', 'CRITICAL', 1, N'Legacy payments table is missing.');

IF OBJECT_ID('dbo.reservations', 'U') IS NULL
    INSERT INTO @issues VALUES ('BASE_RESERVATIONS_MISSING', 'CRITICAL', 1, N'Legacy reservations table is missing.');

IF OBJECT_ID('dbo.hotels', 'U') IS NULL
    INSERT INTO @issues VALUES ('BASE_HOTELS_MISSING', 'CRITICAL', 1, N'Hotels table is missing.');

IF OBJECT_ID('dbo.payments', 'U') IS NOT NULL
   AND OBJECT_ID('dbo.reservations', 'U') IS NOT NULL
BEGIN
    DECLARE @orphanPayments BIGINT = (
        SELECT COUNT_BIG(*)
        FROM dbo.payments p
        LEFT JOIN dbo.reservations r ON r.id = p.reservation_id
        WHERE r.id IS NULL
    );
    IF @orphanPayments > 0
        INSERT INTO @issues VALUES ('PROPERTY_PAYMENT_ORPHAN', 'CRITICAL', @orphanPayments, N'Payments cannot be mapped to a reservation/property.');

    DECLARE @duplicateTransactions BIGINT = (
        SELECT COUNT_BIG(*)
        FROM (
            SELECT transaction_id
            FROM dbo.payments
            WHERE transaction_id IS NOT NULL
            GROUP BY transaction_id
            HAVING COUNT_BIG(*) > 1
        ) duplicates
    );
    IF @duplicateTransactions > 0
        INSERT INTO @issues VALUES ('PROPERTY_TRANSACTION_DUPLICATE', 'CRITICAL', @duplicateTransactions, N'Duplicate legacy payment transaction identifiers exist.');
END;

IF OBJECT_ID('dbo.payment_sessions', 'U') IS NOT NULL
BEGIN
    DECLARE @duplicateProviderRefs BIGINT = (
        SELECT COUNT_BIG(*)
        FROM (
            SELECT provider, provider_reference
            FROM dbo.payment_sessions
            GROUP BY provider, provider_reference
            HAVING COUNT_BIG(*) > 1
        ) duplicates
    );
    IF @duplicateProviderRefs > 0
        INSERT INTO @issues VALUES ('PAYMENT_SESSION_PROVIDER_DUPLICATE', 'CRITICAL', @duplicateProviderRefs, N'Duplicate payment-session provider references exist.');

    DECLARE @invalidSessionCurrency BIGINT = (
        SELECT COUNT_BIG(*) FROM dbo.payment_sessions WHERE currency <> 'VND' OR expected_amount <= 0
    );
    IF @invalidSessionCurrency > 0
        INSERT INTO @issues VALUES ('PAYMENT_SESSION_AMOUNT_INVALID', 'CRITICAL', @invalidSessionCurrency, N'Payment sessions contain non-VND or non-positive amounts.');
END;

IF OBJECT_ID('dbo.refund_requests', 'U') IS NOT NULL
BEGIN
    DECLARE @orphanRefunds BIGINT = (
        SELECT COUNT_BIG(*)
        FROM dbo.refund_requests rr
        LEFT JOIN dbo.payments p ON p.id = rr.original_payment_id
        WHERE p.id IS NULL
    );
    IF @orphanRefunds > 0
        INSERT INTO @issues VALUES ('REFUND_ORIGINAL_PAYMENT_MISSING', 'CRITICAL', @orphanRefunds, N'Refund requests have no original payment.');
END;

IF OBJECT_ID('dbo.subscription_payments', 'U') IS NOT NULL
   AND OBJECT_ID('dbo.subscription_orders', 'U') IS NOT NULL
BEGIN
    DECLARE @orphanSubscriptionPayments BIGINT = (
        SELECT COUNT_BIG(*)
        FROM dbo.subscription_payments sp
        LEFT JOIN dbo.subscription_orders so ON so.id = sp.order_id
        WHERE so.id IS NULL
    );
    IF @orphanSubscriptionPayments > 0
        INSERT INTO @issues VALUES ('PLATFORM_PAYMENT_ORDER_MISSING', 'CRITICAL', @orphanSubscriptionPayments, N'Subscription payments have no platform order.');
END;

SELECT issue_code, severity, affected_count, details
FROM @issues
ORDER BY CASE severity WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 ELSE 2 END, issue_code;

IF EXISTS (SELECT 1 FROM @issues WHERE severity = 'CRITICAL')
    THROW 51007, 'Feature 007 financial preflight failed. Resolve critical rows before migration.', 1;
