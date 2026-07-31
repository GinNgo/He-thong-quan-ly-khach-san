# T053 Booking Financial Summary

## Scope

- Added a server-derived `BookingFinancialSummaryService` independent of reservation operational status.
- Added the tenant-owned `BookingFinancialSummary` projection and repository mapped to the V21 `booking_financial_summaries` table.
- Added projection refresh with a locked reservation, immutable ledger reads and a ledger source watermark.
- Activated the summary Hibernate filter in the request tenant-filter interceptor.

## Calculation Rules

- Gross charges use the snapshotted booking total, with the server-owned reservation total as legacy fallback.
- Deposit required uses the immutable reservation deposit snapshot and cannot exceed gross charges.
- Successful payments sum all incoming non-refund ledger effects, preserving multiple payments and methods.
- Successful refunds sum immutable `REFUND` ledger effects; outgoing manual adjustments reduce net settlement without being mislabeled as refunds.
- Remaining balance is `gross charges - net settled`; a negative value reports overpayment explicitly.
- Financial state is derived as `UNPAID`, `PARTIALLY_PAID`, `DEPOSIT_PAID`, `PAID`, `OVERPAID`, `PARTIALLY_REFUNDED` or `REFUNDED` without changing reservation status.
- Cross-property or cross-reservation ledger evidence is rejected.

## Automated Validation

Commands from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=BookingFinancialSummaryServiceTest,PropertyPaymentModelTest,TenantFilterArchitectureTest' -DforkCount=0 test
.\mvnw.cmd '-Dtest=PropertyPaymentPersistenceIntegrationTest' -DforkCount=0 test
```

Final results on 2026-07-31:

- Unit/architecture tests: 13 passed, 0 failed, 0 errors, 0 skipped.
- Persistence integration tests: 1 passed, 0 failed, 0 errors, 0 skipped.
- Builds: SUCCESS.

The persistence test verifies projection insert/update behavior, repository calculation sources and tenant-filter isolation for attempts, ledger rows and booking summaries.

## Permissions and Environment

- Permission validation: N/A for this calculation/persistence task; the owner/property authorization contract is exposed in T058.
- Provider execution: N/A; calculations use deterministic in-memory and H2 ledger fixtures only.
- Production configuration/database: not accessed or changed.

## Schema and Recovery

- New migration: N/A. The entity maps the additive V21 projection table.
- Forward recovery: stop refreshing the projection and calculate directly from reservation plus immutable ledger records while a corrected projection deployment is prepared.
- Code rollback: remove the summary service/entity/repository and tenant filter registration. Existing immutable transactions remain the authoritative recovery source; do not delete them.
