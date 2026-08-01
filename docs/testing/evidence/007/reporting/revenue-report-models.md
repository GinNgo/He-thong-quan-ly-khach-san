# T125 Shared Revenue Report Models

## Implemented Contract

- Property Commerce and Platform Billing use explicit, mutually exclusive report contexts.
- Every normalized filter carries a recognition basis, inclusive/exclusive UTC bounds, source time zone and context-safe scope.
- Property filters and rows require a positive server-resolved property ID; platform filters and rows reject property scope.
- Report totals, breakdowns and transaction rows use exact scale-zero VND and enforce `gross - refunds - credits = net`.
- Results carry immutable rows, breakdowns, reconciliation issues, source watermark, optional checksum and generated time for API/export parity.
- Breakdown dimensions support property room/service metrics and platform plan/status/provider metrics without mixing ledgers.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=RevenueReportModelsTest' -DforkCount=0 test
```

Result on 2026-08-02: 5 passed, 0 failed, 0 skipped; build succeeded.

Permission/API validation is N/A for this shared model-only task. No production credential, provider call, database migration or real-money operation was used.
