# T266 - Service catalog validation and lifecycle

## Scope and result

- Branch/worktree: `codex/property-operations` in the dedicated external worktree.
- Starting commit: `d237947`.
- Result: property services now have validated VND pricing, optimistic concurrency, reasoned soft deactivation and append-only catalog history while historical financial snapshots remain stable.

## Behavior evidence

- Codes and bilingual names are required and normalized; price must be a positive integer VND amount; status is limited to `ACTIVE` or `INACTIVE`.
- Update/deactivate locks the tenant service and checks its optimistic version.
- `DELETE /api/services/{id}?reason=...` is a controlled lifecycle command: it requires a reason, marks the service inactive and records a before/after history snapshot instead of deleting the row.
- The frontend passes an explicit operator reason after confirmation.
- Inactive services cannot create new reservation charges. Existing charge/folio/invoice lines retain their captured code, bilingual name, unit price and catalog version.
- System templates remain immutable and tenant access checks precede mutation.

## Automated verification

```text
backend\mvnw.cmd "-Dtest=HotelServiceLogicImplTest,ReservationChargeServiceTest,HotelServiceAuthorizationParityTest" test
```

- PASS: 18 tests, 0 failures, 0 errors, 0 skipped.
- Covers field/price/status validation, locking/versioning, reasoned soft deactivation/history, permission signatures, inactive-service charge rejection and historical snapshot stability.

SQL Server:

```text
backend\tools\service-catalog-sqlserver-validation.ps1 -LocalServer '.\MSSQLSERVER01'
```

- PASS: V88 applied twice to a disposable local SQL Server database.
- Invalid price and status writes were rejected; lifecycle/history schema remained idempotent.

Frontend integration:

```text
npm test -- --watch=false --include=src/app/features/admin/service-management/service-management.spec.ts --include=src/app/core/services/hotel-service.service.spec.ts
```

- PASS: 7 tests, including the required deactivation reason query parameter.
- Temporary public-i18n compatibility sources were removed before staging.

## Migration and recovery

- V88 is additive but intentionally fails when legacy service rows have blank code/name, non-positive or fractional price, or unsupported status.
- Forward recovery: correct the reported legacy rows in a reviewed data-fix migration, then rerun V88. The migration does not delete or silently coerce financial catalog data.
- Rollback for an unpromoted disposable environment: drop the V88 constraints/history table/version column after verifying no new history depends on them.
- Production migration execution and destructive rollback were not performed.
