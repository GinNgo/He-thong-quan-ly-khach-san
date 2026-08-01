# T126 Tenant-Scoped Property Revenue Queries

## Implemented Sources

- Reads successful immutable property ledger transactions by normalized UTC interval.
- Reads finalized invoice headers and immutable room/service/surcharge/tax/fee/discount lines.
- Reads invoice-payment allocations needed to prevent deposit/payment double counting.
- Reads credit notes and credit-note lines without rewriting finalized invoice evidence.
- Applies server-resolved property scope to every query and remains intersected with active Hibernate tenant filters.
- Applies normalized provider, method, transaction-type and room-type filters consistently to ledger and invoice-owned evidence.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=PropertyRevenueRepositoryTest' -DforkCount=0 test
```

Result on 2026-08-02: 4 passed, 0 failed, 0 skipped; build succeeded.

The test executed all six JPQL paths against H2, exercised combined filters, and proved that an active first-property Hibernate filter returns no rows when a second property scope is requested.

No migration, external provider, production credential or real-money operation was used. Full database reconciliation fixtures remain assigned to T132.
