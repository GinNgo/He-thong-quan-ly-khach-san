# T207 Physical-room Assignment Evidence

Date: 2026-08-03
Branch: `codex/ui-functional-audit-polish`
Scope: STAY-007 concurrent-safe physical-room assignment

## Implementation

- `ReservationService.assignRooms()` locks the reservation first, then active assignment rows and candidate physical rooms in sorted ID order before validating room ownership/state and writing the assignment.
- Repeating an identical assignment request returns the existing assignment without another room or assignment write; a conflicting room set is rejected while the reservation remains locked.
- `ReservationRoom` stores the reservation check-in/check-out dates. Flyway migration `V41__reservation_room_date_guard.sql` backfills existing rows, adds the `(room_id, stay_start_date, stay_end_date, status)` index and adds a SQL Server filtered unique index for active exact-room/date duplicates.
- The SQL Server lifecycle harness includes a mock for `PublicInventoryEligibilityPolicy`, keeping the test context aligned with the current `ReservationService` constructor without changing production behavior.

## Verification

```powershell
Set-Location backend
.\mvnw.cmd -q '-Dtest=ReservationAssignmentConcurrencyIntegrationTest,ReservationLifecycleLockingTest' test
.\tools\stay-lifecycle-sqlserver-validation.ps1
```

## Results

| Suite | Result |
|---|---:|
| `ReservationAssignmentConcurrencyIntegrationTest` | 1/1 passed |
| `ReservationLifecycleLockingTest` | 4/4 passed |
| `StayLifecycleSqlServerIT` | 2/2 passed |

The H2 integration journey starts two reservations for one physical room and observes exactly one successful assignment, with persisted stay dates. The locking unit suite verifies deterministic reservation -> assignment -> room ordering and idempotent replay. The isolated SQL Server 2022 container validates the assignment race together with concurrent check-in, checkout replay and outer-transaction rollback using production lock semantics; no real merchant or production database is used.
