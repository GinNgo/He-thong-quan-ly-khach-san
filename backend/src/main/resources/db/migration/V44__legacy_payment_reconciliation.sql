SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

IF OBJECT_ID('dbo.property_financial_transactions', 'U') IS NOT NULL
   AND COL_LENGTH('dbo.property_financial_transactions', 'legacy_reconciliation_required') IS NULL
BEGIN
    ALTER TABLE dbo.property_financial_transactions
        ADD legacy_reconciliation_required BIT NOT NULL
            CONSTRAINT DF_property_transaction_legacy_reconcile DEFAULT 0 WITH VALUES;
END;
GO

IF OBJECT_ID('dbo.payments', 'U') IS NOT NULL
   AND OBJECT_ID('dbo.reservations', 'U') IS NOT NULL
   AND OBJECT_ID('dbo.payment_sessions', 'U') IS NOT NULL
   AND OBJECT_ID('dbo.property_financial_transactions', 'U') IS NOT NULL
   AND OBJECT_ID('dbo.financial_migration_exceptions', 'U') IS NOT NULL
BEGIN
    INSERT INTO dbo.financial_migration_exceptions(source_table, source_id, issue_code, details)
    SELECT
        'payments',
        payment.id,
        'LEGACY_PAYMENT_INVALID',
        N'Legacy payment has invalid ownership, amount, status, or method and was not accepted as settlement.'
    FROM dbo.payments payment
    LEFT JOIN dbo.reservations reservation ON reservation.id = payment.reservation_id
    WHERE (
            reservation.id IS NULL
            OR reservation.hotel_id IS NULL
            OR payment.amount IS NULL
            OR payment.amount = 0
            OR payment.amount <> ROUND(payment.amount, 0)
            OR NULLIF(LTRIM(RTRIM(payment.payment_method)), '') IS NULL
            OR UPPER(LTRIM(RTRIM(COALESCE(payment.status, ''))))
                NOT IN ('SUCCESS', 'SUCCEEDED', 'PAID', 'COMPLETED', 'REFUNDED')
          )
      AND NOT EXISTS (
          SELECT 1
          FROM dbo.financial_migration_exceptions exception_row
          WHERE exception_row.source_table = 'payments'
            AND exception_row.source_id = payment.id
            AND exception_row.issue_code = 'LEGACY_PAYMENT_INVALID'
      );

    INSERT INTO dbo.financial_migration_exceptions(source_table, source_id, issue_code, details)
    SELECT
        'payments',
        payment.id,
        'LEGACY_REFUND_UNLINKED',
        N'Legacy negative payment has no authoritative original-transaction link and requires manual reconciliation.'
    FROM dbo.payments payment
    JOIN dbo.reservations reservation ON reservation.id = payment.reservation_id
    WHERE reservation.hotel_id IS NOT NULL
      AND payment.amount < 0
      AND payment.amount = ROUND(payment.amount, 0)
      AND UPPER(LTRIM(RTRIM(COALESCE(payment.status, ''))))
            IN ('SUCCESS', 'SUCCEEDED', 'PAID', 'COMPLETED', 'REFUNDED')
      AND NOT EXISTS (
          SELECT 1
          FROM dbo.financial_migration_exceptions exception_row
          WHERE exception_row.source_table = 'payments'
            AND exception_row.source_id = payment.id
            AND exception_row.issue_code = 'LEGACY_REFUND_UNLINKED'
      );

    INSERT INTO dbo.financial_migration_exceptions(source_table, source_id, issue_code, details)
    SELECT
        'payments',
        payment.id,
        'LEGACY_SETTLEMENT_UNVERIFIED',
        N'Legacy successful payment has no exact successful payment-session evidence for reservation, amount, method, and transaction reference.'
    FROM dbo.payments payment
    JOIN dbo.reservations reservation ON reservation.id = payment.reservation_id
    WHERE reservation.hotel_id IS NOT NULL
      AND payment.amount > 0
      AND payment.amount = ROUND(payment.amount, 0)
      AND NULLIF(LTRIM(RTRIM(payment.payment_method)), '') IS NOT NULL
      AND UPPER(LTRIM(RTRIM(COALESCE(payment.status, ''))))
            IN ('SUCCESS', 'SUCCEEDED', 'PAID', 'COMPLETED')
      AND NOT EXISTS (
          SELECT 1
          FROM dbo.payment_sessions session_row
          WHERE session_row.reservation_id = payment.reservation_id
            AND session_row.expected_amount = payment.amount
            AND UPPER(LTRIM(RTRIM(session_row.method))) =
                UPPER(LTRIM(RTRIM(payment.payment_method)))
            AND UPPER(LTRIM(RTRIM(session_row.status))) = 'SUCCEEDED'
            AND NULLIF(LTRIM(RTRIM(payment.transaction_id)), '') IS NOT NULL
            AND session_row.provider_transaction_id = LTRIM(RTRIM(payment.transaction_id))
      )
      AND NOT EXISTS (
          SELECT 1
          FROM dbo.financial_migration_exceptions exception_row
          WHERE exception_row.source_table = 'payments'
            AND exception_row.source_id = payment.id
            AND exception_row.issue_code = 'LEGACY_SETTLEMENT_UNVERIFIED'
      );

    UPDATE ledger
    SET
        ledger.provider_transaction_ref = LEFT(NULLIF(LTRIM(RTRIM(payment.transaction_id)), ''), 200),
        ledger.reason = LEFT(CONCAT(
                N'Legacy payment evidence; source_status=',
                UPPER(LTRIM(RTRIM(COALESCE(payment.status, 'NULL')))),
                N'; source_method=',
                UPPER(LTRIM(RTRIM(COALESCE(payment.payment_method, 'NULL')))),
                N'; source_reference=',
                COALESCE(NULLIF(LTRIM(RTRIM(payment.transaction_id)), ''), N'NULL')
            ), 1000),
        ledger.legacy_reconciliation_required = CASE
            WHEN payment.amount > 0
             AND payment.amount = ROUND(payment.amount, 0)
             AND NULLIF(LTRIM(RTRIM(payment.payment_method)), '') IS NOT NULL
             AND UPPER(LTRIM(RTRIM(COALESCE(payment.status, ''))))
                    IN ('SUCCESS', 'SUCCEEDED', 'PAID', 'COMPLETED')
             AND EXISTS (
                 SELECT 1
                 FROM dbo.payment_sessions session_row
                 WHERE session_row.reservation_id = payment.reservation_id
                   AND session_row.expected_amount = payment.amount
                   AND UPPER(LTRIM(RTRIM(session_row.method))) =
                       UPPER(LTRIM(RTRIM(payment.payment_method)))
                   AND UPPER(LTRIM(RTRIM(session_row.status))) = 'SUCCEEDED'
                   AND NULLIF(LTRIM(RTRIM(payment.transaction_id)), '') IS NOT NULL
                   AND session_row.provider_transaction_id = LTRIM(RTRIM(payment.transaction_id))
             )
                THEN 0
            ELSE 1
        END
    FROM dbo.property_financial_transactions ledger
    JOIN dbo.payments payment
      ON ledger.idempotency_identity = CONCAT('LEGACY:payments:', payment.id);
END;

IF OBJECT_ID('dbo.property_financial_transactions', 'U') IS NOT NULL
   AND NOT EXISTS (
       SELECT 1
       FROM sys.indexes
       WHERE object_id = OBJECT_ID('dbo.property_financial_transactions')
         AND name = 'IX_property_transactions_legacy_reconciliation'
   )
BEGIN
    CREATE INDEX IX_property_transactions_legacy_reconciliation
        ON dbo.property_financial_transactions(reservation_id, occurred_at)
        WHERE legacy_reconciliation_required = 1;
END;
