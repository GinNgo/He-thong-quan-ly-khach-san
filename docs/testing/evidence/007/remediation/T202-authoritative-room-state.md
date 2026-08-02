# T202 - Authoritative room-state transitions

## Implemented

- Added `RoomStatePolicy` as the single command boundary for room initialization, reservation assignment, check-in, release/cancel, checkout, maintenance, deactivation and housekeeping completion.
- Room mutations that change operational state now use pessimistic room locks; generic room updates can change metadata only and reject status-field mutations.
- Added optimistic `Room.version` and migration `V37__authoritative_room_state.sql` with state-domain, ownership and legacy-normalization checks.
- Added dedicated start/complete maintenance endpoints for both direct admin and management inventory APIs, and changed both UIs to call those commands instead of copying status strings.
- Reservation assignment, check-in, release/cancel, checkout and housekeeping completion now use the policy and locked room aggregates.

## Verification

Focused verification executed on 2026-08-03:

```text
Backend focused suite: PASS (35 tests, 0 failures, 0 errors)
Frontend build: PASS (`npm run build`)
Frontend focused suite: PASS (3 test files, 3 tests)
SQL Server StayLifecycleSqlServerIT: PASS (2/2)
V37 disposable SQL Server migration check: PASS
```

The focused backend suite covers policy transitions, invalid combinations, locked room updates, assignment/check-in/release concurrency, checkout effects and tenant-safe housekeeping completion. The migration check also proves legacy state normalization and rejection of an invalid room-state update at the database boundary.

## Residual scope

- T202 establishes authoritative state ownership only. Reasoned maintenance work orders and reopen/history workflow remain T021.
- Deterministic active-room/date uniqueness and broader assignment concurrency enhancements remain T207.
