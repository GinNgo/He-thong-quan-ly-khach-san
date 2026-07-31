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

# T071 Server-Priced Service and Minibar Charges

## Scope

- Added a transactional `ReservationChargeService` that locks the reservation before creating or correcting folio charges.
- Accepts only the catalog service identity, SERVICE/MINIBAR classification, quantity and usage time; unit price and total are always calculated from the active server catalog.
- Snapshots bilingual catalog identity/description, catalog version, actor, quantity, usage time and exact VND amounts.
- Treats the existing catalog price as tax-inclusive because the current service catalog has no separate tax rule; the persisted tax snapshot is therefore server-owned zero rather than caller supplied.
- Requires an authenticated property actor with `RESERVATION_SERVICE/CREATE`, active property access and a `CHECKED_IN` reservation.

## Append-Only Corrections

- A correction locks the original line and appends an `ADJUSTMENT` reversal linked through `reverses_line_id`.
- The original charge remains unchanged and a line cannot be corrected twice.
- An optional replacement is created from the current server catalog price in the same transaction.
- Cross-property catalog items, inactive services, non-service lines, future usage times and invalid VND/quantity values are rejected before persistence.

## Automated Validation

Focused command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=ReservationChargeServiceTest,ReservationChargeLineTest,TenantFilterArchitectureTest' -DforkCount=0 test
```

Result:

- Service tests: 5 passed
- Entity tests: 4 passed
- Tenant-filter architecture tests: 3 passed
- Total: 12 passed
- Failures: 0
- Errors: 0
- Skipped: 0

Fresh application-context validation, with a test-only secret supplied to the command process:

```powershell
$env:JWT_SECRET='test_secret_for_context_validation_only_32_chars'
.\mvnw.cmd '-Dtest=BackendApplicationTests' -DforkCount=0 test
```

Result: 1 context test passed; all 51 JPA repositories, including the charge-line repository, were scanned and the Spring Boot application context started successfully.

## Safety and Recovery

- No endpoint, provider request, production credential, real-money action or production database mutation is part of T071.
- Recovery disables new charge creation while retaining all existing charge/reversal evidence; corrections are never rolled back by deleting or rewriting persisted lines.
