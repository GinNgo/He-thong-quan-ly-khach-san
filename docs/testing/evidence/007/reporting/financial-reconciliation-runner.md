# T131 Financial Reconciliation Runner

`FinancialReconciliationService` dispatches normalized filters to exactly one bounded-context report service, then verifies report row totals and all CSV/Excel/PDF artifacts from that same immutable result.

The read-only mismatch queue separates:

- authoritative source issues already detected by property/platform report services;
- report row count and one-VND gross/refund/credit/net differences;
- export checksum, row count and missing-content differences.

The runner returns export metadata rather than embedding file bytes and never updates ledger, invoice, refund, order, contract or entitlement evidence.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=FinancialReconciliationServiceTest' -DforkCount=0 test
```

Result on 2026-08-02: 2 tests passed, 0 failed, 0 skipped. Tests cover a reconciled three-format platform run and combined source/report/export mismatch queue behavior.

No production credentials, external provider, migration or real-money operation was used.
