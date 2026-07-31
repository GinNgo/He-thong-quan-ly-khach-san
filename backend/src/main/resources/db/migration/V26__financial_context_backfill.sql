SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

IF OBJECT_ID('dbo.financial_migration_exceptions', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.financial_migration_exceptions (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_financial_migration_exceptions PRIMARY KEY,
        source_table VARCHAR(80) NOT NULL,
        source_id BIGINT NULL,
        issue_code VARCHAR(80) NOT NULL,
        details NVARCHAR(1000) NOT NULL,
        detected_at DATETIME2 NOT NULL CONSTRAINT DF_financial_migration_exception_detected DEFAULT SYSUTCDATETIME(),
        resolved_at DATETIME2 NULL,
        resolution_note NVARCHAR(1000) NULL,
        CONSTRAINT UQ_financial_migration_exception UNIQUE (source_table, source_id, issue_code)
    );
END;

IF OBJECT_ID('dbo.payments', 'U') IS NOT NULL
   AND OBJECT_ID('dbo.reservations', 'U') IS NOT NULL
BEGIN
    INSERT INTO dbo.financial_migration_exceptions(source_table, source_id, issue_code, details)
    SELECT 'payments', p.id, 'PROPERTY_OWNER_UNRESOLVED', N'Payment cannot be mapped to a reservation with a property.'
    FROM dbo.payments p
    LEFT JOIN dbo.reservations r ON r.id = p.reservation_id
    WHERE (r.id IS NULL OR r.hotel_id IS NULL)
      AND NOT EXISTS (
          SELECT 1 FROM dbo.financial_migration_exceptions e
          WHERE e.source_table = 'payments' AND e.source_id = p.id AND e.issue_code = 'PROPERTY_OWNER_UNRESOLVED'
      );

    INSERT INTO dbo.property_financial_transactions(
        public_id, hotel_id, reservation_id, transaction_type, direction, amount, currency,
        method, provider, idempotency_identity, actor_type, reason, occurred_at, recorded_at
    )
    SELECT
        CONCAT('LEGACY-PAYMENT-', p.id), r.hotel_id, p.reservation_id,
        CASE WHEN p.amount < 0 THEN 'REFUND' ELSE 'ROOM_PAYMENT' END,
        CASE WHEN p.amount < 0 THEN 'CREDIT' ELSE 'DEBIT' END,
        ABS(ROUND(p.amount, 0)), 'VND', p.payment_method, p.payment_method,
        CONCAT('LEGACY:payments:', p.id), 'MIGRATION', N'Backfilled from legacy payments',
        COALESCE(p.payment_date, p.created_at, SYSUTCDATETIME()), SYSUTCDATETIME()
    FROM dbo.payments p
    JOIN dbo.reservations r ON r.id = p.reservation_id AND r.hotel_id IS NOT NULL
    WHERE UPPER(p.status) IN ('SUCCESS','SUCCEEDED','PAID','COMPLETED','REFUNDED')
      AND p.amount <> 0
      AND NOT EXISTS (
          SELECT 1 FROM dbo.property_financial_transactions t
          WHERE t.idempotency_identity = CONCAT('LEGACY:payments:', p.id)
      );
END;

IF OBJECT_ID('dbo.invoices', 'U') IS NOT NULL
   AND OBJECT_ID('dbo.reservations', 'U') IS NOT NULL
BEGIN
    INSERT INTO dbo.property_invoices(
        hotel_id, reservation_id, invoice_number, customer_snapshot_json, property_snapshot_json,
        subtotal, tax_amount, fee_amount, discount_amount, total_amount, paid_amount,
        refunded_amount, balance_amount, currency, status, finalized_at, created_at
    )
    SELECT
        r.hotel_id, i.reservation_id, CONCAT('LEGACY-', i.invoice_code), N'{}', N'{}',
        ROUND(i.total_amount, 0), 0, 0, 0, ROUND(i.total_amount, 0),
        CASE WHEN UPPER(i.status) = 'PAID' THEN ROUND(i.total_amount, 0) ELSE 0 END,
        0,
        CASE WHEN UPPER(i.status) = 'PAID' THEN 0 ELSE ROUND(i.total_amount, 0) END,
        'VND', CASE WHEN UPPER(i.status) = 'PAID' THEN 'FINALIZED' ELSE 'DRAFT' END,
        CASE WHEN UPPER(i.status) = 'PAID' THEN COALESCE(i.updated_at, i.created_at, SYSUTCDATETIME()) ELSE NULL END,
        COALESCE(i.created_at, SYSUTCDATETIME())
    FROM dbo.invoices i
    JOIN dbo.reservations r ON r.id = i.reservation_id AND r.hotel_id IS NOT NULL
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.property_invoices pi WHERE pi.invoice_number = CONCAT('LEGACY-', i.invoice_code)
    );
END;

IF OBJECT_ID('dbo.subscription_orders', 'U') IS NOT NULL
BEGIN
    INSERT INTO dbo.financial_migration_exceptions(source_table, source_id, issue_code, details)
    SELECT 'subscription_orders', so.id, 'PLATFORM_TARGET_PROPERTY_UNRESOLVED', N'Legacy subscription order has no deterministic target property snapshot.'
    FROM dbo.subscription_orders so
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.financial_migration_exceptions e
        WHERE e.source_table = 'subscription_orders' AND e.source_id = so.id AND e.issue_code = 'PLATFORM_TARGET_PROPERTY_UNRESOLVED'
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.views WHERE name = 'v_feature007_migration_exceptions' AND schema_id = SCHEMA_ID('dbo'))
    EXEC('CREATE VIEW dbo.v_feature007_migration_exceptions AS SELECT * FROM dbo.financial_migration_exceptions WHERE resolved_at IS NULL');
