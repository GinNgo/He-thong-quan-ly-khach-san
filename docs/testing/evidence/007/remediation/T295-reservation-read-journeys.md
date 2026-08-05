# T295 Reservation Read Journeys

Task commit: `6788dbe`

## Scope

- Adds bounded server-side reservation pages with deterministic
  `checkInDate DESC, id DESC` ordering and optional status/customer/booking-id
  filters.
- Keeps customer reads owner-bound and staff reads limited to assigned,
  operational properties. Cross-property and cross-customer details fail as 404.
- Returns append-only reservation history from `operational_audit_events` only
  after the reservation ownership/property authorization check succeeds.
- Adds Vietnamese admin/customer list-detail journeys with explicit loading,
  retryable error, empty and event-history states.

## Automated Validation

Backend focused run:

```powershell
.\mvnw.cmd -q "-Dtest=ReservationReadJourneyIntegrationTest,ReservationControllerIdempotencyTest,ReservationLifecyclePermissionMatrixTest" test
```

| Suite | Tests | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| `ReservationReadJourneyIntegrationTest` | 3 | 3 | 0 | 0 | 0 |
| `ReservationControllerIdempotencyTest` | 3 | 3 | 0 | 0 | 0 |
| `ReservationLifecyclePermissionMatrixTest` | 5 | 5 | 0 | 0 | 0 |

Frontend focused run:

```powershell
npm test -- --watch=false --ts-config tsconfig.t295.spec.json --include src/app/features/admin/reservation-management/reservation-lifecycle-permissions.spec.ts --include src/app/features/admin/reservation-management/reservation-read-journey.spec.ts --include src/app/features/client/profile/profile-booking-read.component.spec.ts --include src/app/features/client/profile/profile-current-read.component.spec.ts
```

Result: 4 files, 13 tests, 13 passed, 0 failed.

## Verified Boundaries

- A customer page contains only reservations owned by the signed-in username;
  the same customer receives 404 for another customer's detail.
- A receptionist can filter the assigned property's reservations by status and
  username, read event history, and receives 404 for another property.
- A manager sees only the assigned property and stable pagination metadata.
- The admin page sends the query, status, page and size to the backend rather
  than filtering an unbounded client-side list.
- Customer and admin details render immutable event history after an authorized
  detail read; list DTOs do not perform per-row audit-history queries.

## Baseline Isolation Note

The base branch contains incomplete parallel-work artifacts that prevent a plain
full Maven compile: UTF-8 BOMs in two auth sources, a platform-billing controller
whose DTO/service files are not present in the base commit, and test sources whose
main classes are still uncommitted in other worktrees. The successful focused run
temporarily normalized/excluded only those unrelated files and limited test
compilation to the three suites above; no temporary change is included in this
task commit. The changed T295 main sources also passed focused Maven compilation
under that isolation. The Angular full test graph has an analogous missing i18n
parallel-work dependency, so `tsconfig.t295.spec.json` provides a committed,
reproducible focused test boundary.

## Migration And Financial Safety

- Migration: N/A. T295 reuses the append-only `operational_audit_events` table
  introduced by V47.
- Financial mutation: N/A. Pagination, detail and audit visibility do not derive
  or alter payment, refund, folio or invoice amounts.
- Production credentials and real-money providers were not used.
