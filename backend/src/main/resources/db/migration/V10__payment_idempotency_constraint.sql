IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'UQ_payments_transaction_id'
      AND object_id = OBJECT_ID('dbo.payments')
)
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX UQ_payments_transaction_id
        ON dbo.payments(transaction_id)
        WHERE transaction_id IS NOT NULL;
END