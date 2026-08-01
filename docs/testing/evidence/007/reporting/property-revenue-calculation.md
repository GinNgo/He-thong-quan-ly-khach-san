# T127 Property Revenue Calculation and Reconciliation

## Calculation Rules

- Successful property ledger debits form cash gross; property ledger credits form refunds, so `gross - refunds = net` exactly to one VND.
- Finalized invoice totals form invoiced gross; immutable credit notes reduce invoiced revenue without rewriting invoice snapshots.
- Prior-period invoice credits may produce negative net invoiced revenue in the selected period and are reported explicitly.
- Invoice payment allocations are reconciliation evidence only and are never added to collected revenue a second time.
- Unpaid balance is reduced by in-period credit notes and never falls below zero.
- Held deposits subtract invoice allocations and refunds from successful booking-deposit transactions.
- Room, service, payment-method, provider and transaction-type breakdowns derive from the same source snapshot as detail rows.

## Reconciliation Queue

- Detects cumulative allocation above the immutable source transaction.
- Detects invoice paid amount versus allocation mismatch.
- Detects finalized invoice header versus invoice-line mismatch.
- Detects credit-note header versus credit-note-line mismatch.
- Marks affected rows `MISMATCH` and returns stable issue codes without mutating financial evidence.

## Automated Validation

Commands from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=PropertyRevenueServiceTest,PropertyRevenueRepositoryTest' -DforkCount=0 test
.\mvnw.cmd '-Dtest=RevenueReportModelsTest,PropertyRevenueServiceTest' -DforkCount=0 test
```

Final result on 2026-08-02:

- `PropertyRevenueRepositoryTest`: 4 passed.
- `PropertyRevenueServiceTest`: 4 passed.
- `RevenueReportModelsTest`: 6 passed.
- 0 failed, 0 skipped; both Maven runs built successfully.

No external provider, production credential, migration or real-money operation was used. Database-wide reconciliation remains assigned to T132.
