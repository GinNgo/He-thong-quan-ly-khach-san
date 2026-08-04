# T280 Search And Detail Availability Evidence

Date: 2026-08-04
Branch: `codex/public-booking`
Capability: `PUB-012`
Validation status: `BLOCKED_RUNTIME`
Production credentials, production data, destructive migration or real-money operation: N/A

## Implemented Contract

- `RoomAvailabilityPolicy` is the single room-state projection used by public search SQL, room-type detail availability and locked reservation creation.
- Current/no-date availability requires physical room status `AVAILABLE`; dated availability permits the valid stay pool `AVAILABLE`, `RESERVED` and `OCCUPIED` before subtracting overlapping reservation quantities.
- Both projections require housekeeping `CLEAN` or `INSPECTED` and maintenance `NONE`. Null, dirty, cleaning, maintenance, out-of-service and unsupported state combinations fail closed.
- Overlapping `PENDING_PAYMENT`, `CONFIRMED` and `CHECKED_IN` reservations consume quantity. `CANCELLED`, `REJECTED`, `EXPIRED`, `NO_SHOW`, `CHECKED_OUT` and `COMPLETED` reservations release it.
- Search `availableRoomCount`, lowest available room type and starting price use the same room-type counts returned by public detail. Locked booking rechecks that count before creating a quantity hold, preventing a stale search/detail count from overbooking.
- Search and detail UIs distinguish positive, sold-out and missing availability. Detail quantity controls are enabled only for a positive authoritative count and remain bounded by that count.

## Focused Validation

```powershell
Set-Location backend
.\mvnw.cmd -q '-Dtest=RoomAvailabilityPolicyTest,RoomAvailabilityConsistencyIntegrationTest,PropertySearchControllerIntegrationTest' test

Set-Location ../frontend
npm test -- --watch=false --no-progress `
  --include=src/app/features/client/hotel-detail/hotel-detail.component.spec.ts `
  --include=src/app/features/property-search/components/property-result-card/property-result-card.spec.ts
npm run build -- --configuration development --no-progress
```

Results:

- Backend focused aggregate: 27/27 PASS, zero failures/errors/skips: policy 3/3, persisted consistency matrix 2/2 and public-search regression 22/22.
- The `T280 State Matrix Hotel` fixture verifies current count 2, dated count 4, all nine blocking/released reservation statuses, search/detail parity and a locked quantity-4 booking reducing authoritative availability to zero. A further quantity request is rejected without overbooking.
- Additional backend regressions observed in the final combined run: `PublicDiscoveryControllerIntegrationTest` 9/9 and `ReservationConcurrencyIntegrationTest` 2/2 PASS.
- Frontend detail/card focused suite: 2 files / 14 tests PASS. Angular development build: PASS in 80.508 seconds.
- Playwright collection discovers 2 T280 API/browser cases covering demo search/detail count parity and the visible search-to-detail journey.

## Runtime Stop Gate

The one authorized bounded Playwright attempt did not reach either product assertion. With the corrected quoted Maven arguments and an exact temporary compiler overlay, Playwright exited after 198.9 seconds with:

```text
Error: Timed out waiting 180000ms from config.webServer.
```

No backend or frontend listener remained on ports 28743/42769. Per the inherited T279 runtime stop gate, no retry was made. The temporary POM overlay and task-owned processes were removed. The registered T280 cases must pass after the shared backend E2E compile/runtime boundary is repaired.

## Permissions And Isolation

- Search and detail are anonymous public reads and accept no caller-controlled tenant scope.
- Reservation validation locks the selected room type and uses the same eligibility and availability policy; persisted tests prove quantity exhaustion without cross-property mutation or overbooking.
- Tests use deterministic H2 fixtures and test-only secrets. No production credential, production data or real-money action is used.

## Schema And Recovery

No schema or migration change exists. Rollback is the eventual T280 task commit revert; no data conversion or destructive cleanup is required.
