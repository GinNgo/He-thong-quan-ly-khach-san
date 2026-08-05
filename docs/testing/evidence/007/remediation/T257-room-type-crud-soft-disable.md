# T257 - Room-type CRUD and soft disable

## Scope and result

- Branch/worktree: `codex/property-operations` in the dedicated external worktree.
- Starting commit: `3c0be18`.
- Result: admin and owner room-type CRUD now share validated lifecycle behavior, matching action permissions, tenant authority and a safe soft-disable rule.

## Behavior evidence

- `RoomTypeDTO` and service validation require property, normalized code, localized name, positive base price, consistent adult/child/total capacity, valid optional bed/area/hourly-price values and `ACTIVE`/`INACTIVE` status.
- Creation and update retain property-scoped entitlement and access checks. A room type cannot be moved to another property through a crafted update payload.
- Update and delete acquire a pessimistic row lock. Both an update from `ACTIVE` to `INACTIVE` and DELETE soft-disable reject room types referenced by nonterminal reservations.
- Repeated DELETE of an already inactive room type is idempotent and never physically removes booking history.
- Management routes use the same `ROOM_TYPE:VIEW/CREATE/UPDATE/DELETE` action permissions as direct routes; property access remains independently enforced in the service.
- Admin and management screens expose create/edit/deactivate actions with client-side validation, loading/error handling and permission-aware controls.

## Automated verification

Backend focused suite:

```text
backend\mvnw.cmd "-Dtest=RoomTypeServiceImplTest,RoomTypeAuthorizationParityTest,TenantFilterArchitectureTest" test
```

- PASS: 11 tests, 0 failures, 0 errors, 0 skipped.
- Covers validation, tenant/property scope, duplicate code, locked update/delete, active-booking denial, idempotent inactive deletion, route permission parity and tenant-filter architecture.

Frontend focused suite:

```text
npm test -- --watch=false --include=src/app/features/admin/room-type-management/room-type-management.spec.ts --include=src/app/features/management/inventory/management-inventory.component.spec.ts
```

- PASS: 5 tests across both management surfaces.

Build:

```text
npm run build -- --configuration development
```

- PASS: Angular development bundle generated.

SQL Server:

```text
backend\tools\room-type-sqlserver-validation.ps1
```

- PASS: V83 applied twice to a disposable SQL Server 2022 database.
- Verified normalized `(hotel_id, code)` uniqueness and lifecycle/price/capacity constraints by proving invalid inserts are rejected.
- The disposable database and container were removed by the harness.

## Parallel-work compatibility

The base branch references subscription catalog and public i18n sources that currently exist only in other task worktrees. Focused backend tests and the frontend build used temporary compatibility copies solely for verification. All temporary DTOs/services/repository methods, compiler test includes and i18n files were removed before staging; none is part of T257.

## Migration and recovery

- V83 is additive and idempotent. It normalizes existing codes/status values before adding indexes/check constraints and fails safely if unresolved duplicate property-local codes exist.
- Forward recovery: resolve reported duplicate codes or invalid legacy values, then rerun the migration.
- Rollback for an unpromoted disposable environment: drop the V83 constraints/indexes; application soft-disable behavior remains forward compatible.
- Production migration execution and destructive rollback were not performed.
