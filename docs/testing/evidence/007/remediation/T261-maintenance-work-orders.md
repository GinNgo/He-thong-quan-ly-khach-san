# T261 - Maintenance work-order lifecycle

## Scope and result

- Branch/worktree: `codex/property-operations` in the dedicated external worktree.
- Starting commit: `2ae4fa0`.
- Result: generic maintenance toggles are replaced by tenant-safe, reasoned work orders in both admin and owner inventory surfaces.

## Behavior evidence

- `/api/v1/maintenance-work-orders` lists and creates property/room-scoped work orders; item endpoints expose start, complete, reopen and cancel transitions.
- Creation requires a room, reason and supported `LOW/NORMAL/HIGH/URGENT` priority; optional assignees must have an active assignment at the same property and scheduled end must follow scheduled start.
- Only one `OPEN` or `IN_PROGRESS` order can exist per room. The service and V87 filtered unique index enforce the invariant.
- Start locks the work order and room, rejects active booking assignments, then applies `RoomStatePolicy.startMaintenance`; complete or in-progress cancellation releases the room through the same authoritative policy.
- Cross-property work-order and room identifiers are hidden through assigned-property checks. Both work-order tables carry `hotel_id` and participate in the request tenant filter; action endpoints use `ROOM` view/create/update/delete permissions.
- Both room inventory surfaces show reason, priority, schedule, booking impact and append-only status history with explicit start/complete/reopen/cancel controls.

## Automated verification

Backend focused suite:

```text
backend\mvnw.cmd "-Dtest=MaintenanceWorkOrderServiceTest" test
```

- PASS: 7 tests, 0 failures, 0 errors, 0 skipped.
- Covers tenant creation, work-order/room transition locking, active-booking rejection, completion, cross-property denial, invalid assignee denial and in-progress cancellation.

Frontend focused suites:

```text
npm test -- --watch=false --include=src/app/core/services/maintenance-work-order.service.spec.ts --include=src/app/shared/components/maintenance-work-orders/maintenance-work-orders.component.spec.ts --include=src/app/features/admin/room-management/room-management.spec.ts --include=src/app/features/management/inventory/management-inventory.component.spec.ts
```

- PASS: 12 tests, including API payloads, reason requirements, lifecycle controls, booking-impact rendering and replacement of the legacy toggle.

Build:

```text
npm run build -- --configuration development
```

- PASS: Angular development bundle generated with temporary public-i18n compatibility sources; those sources were removed before staging.

SQL Server:

```text
backend\tools\maintenance-work-order-sqlserver-validation.ps1 -LocalServer '.\MSSQLSERVER01'
```

- PASS: V87 applied twice to a disposable local SQL Server database.
- Verified lifecycle and schedule constraints plus filtered uniqueness for one active work order per room.
- The disposable database was dropped by the harness.

## Parallel-work compatibility

Focused verification used temporary compatibility sources required by parallel subscription/public-i18n work. All temporary sources were removed before staging. No shared task list or aggregate inventory was modified.

## Migration and recovery

- V87 is additive and idempotent: it creates work-order/history tables and indexes only when absent.
- Forward recovery: correct invalid seed data before rerunning V87; application writes already conform to the lifecycle, priority and schedule constraints.
- Rollback for an unpromoted disposable environment: drop the V87 indexes, history table and work-order table in dependency order.
- Production migration execution and destructive rollback were not performed.
