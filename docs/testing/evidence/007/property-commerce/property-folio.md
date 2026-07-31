# T070 Reservation Charge-Line Persistence

## Scope

- Added the tenant-owned `ReservationChargeLine` entity for room, service, minibar, surcharge, tax, fee, discount and adjustment snapshots.
- Added append-only correction linkage through `reverses_line_id`; persisted charge rows reject updates.
- Captured catalog source/version, code, name, description, unit price, quantity, tax, discount, total, service usage time and actor evidence.
- Added tenant-safe read methods plus a pessimistic-lock lookup for correction workflows.
- Registered `reservationChargeLineTenantFilter` in the request interceptor and its architecture coverage.

## Validation Rules

- Every line belongs to the same property as its reservation.
- Service and minibar lines require a usage timestamp.
- VND snapshots are non-negative scale-zero values; quantities are positive with at most three decimal places.
- A reversal can only reference another line from the same property and reservation.
- The entity maps the additive V22 `reservation_charge_lines` schema; this task adds no migration.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=ReservationChargeLineTest,TenantFilterArchitectureTest' -DforkCount=0 test
```

Final result:

- Reservation charge-line tests: 4 passed
- Tenant-filter architecture tests: 3 passed
- Total: 7 passed
- Failures: 0
- Errors: 0
- Skipped: 0

The command wrapper timed out while Maven was still running, but the same Maven process completed normally and generated successful Surefire reports for both suites.

## Permissions and Safety

- N/A for endpoint permissions: T070 introduces persistence only; mutation authorization is enforced by the services/controllers in later US3 tasks.
- No production credentials, provider request, real-money operation or production database mutation was used.
- Tenant filtering is active for authenticated property requests; later mutation services must also validate aggregate ownership.

## Recovery

- The V22 schema is additive and was already delivered by T015.
- Application recovery is to stop creating new charge lines while preserving existing append-only evidence; persisted financial rows must not be deleted or rewritten.
