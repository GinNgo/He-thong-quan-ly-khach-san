# T119 Refund Concurrency Integration Tests

## Coverage

- Two concurrent partial requests against one original property debit are serialized by the locked original transaction; one request succeeds and the other is rejected with `REFUND_EXCEEDS_BALANCE` when their combined amount exceeds the available balance.
- Equivalent sequential refund requests with the same idempotency key return the original request and do not create a duplicate request or ledger effect.
- Cumulative refunds reject amounts above the remaining balance after a successful provider effect, while an exact remaining-balance request is accepted.
- The original debit amount is unchanged after refund processing, and the sum of successful refund ledger credits never exceeds the original amount.

## Validation

Command from `backend/`:

```powershell
.\mvnw.cmd -q '-Dtest=FinancialRefundConcurrencyIntegrationTest' test
```

Result on 2026-08-02: 3 passed, 0 failed, 0 errors, 0 skipped.
