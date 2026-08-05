# T259 - Physical room CRUD and bulk creation

## Scope and result

- Branch/worktree: `codex/property-operations` in the dedicated external worktree.
- Starting commit: `ce5291e`.
- Result: physical-room CRUD and bulk creation now enforce one tenant-safe, validated and transactionally atomic lifecycle across direct and management routes.

## Behavior evidence

- Request validation bounds room number, property/type identifiers, floor, capacity, note, bulk prefix/range and the 200-room command cap.
- Room numbers are trimmed and uppercased before lookup/write. The room type must belong to the selected property and cross-property identifiers retain not-found semantics.
- Mutations lock the property before the room type/room. Property-scoped `MAX_ROOMS` usage is evaluated under that lock before count-plus-insert.
- Bulk creation precomputes and checks the complete normalized range before the first insert; any duplicate or validation failure rejects the entire command instead of returning a partially created floor.
- Room update may not move an actively assigned room to another type, and soft deactivation is rejected while a nonterminal reservation assignment exists.
- Management room routes now use the same `ROOM:VIEW/CREATE/UPDATE/DELETE` permissions as direct routes and include the previously missing DELETE endpoint.
- Admin and owner inventory screens provide tested create/edit/bulk/deactivate behavior, validation and permission-aware actions.

## Automated verification

Backend focused suite:

```text
backend\mvnw.cmd "-Dtest=RoomServiceImplTest,RoomStatePolicyTest,TenantFilterArchitectureTest,RoomAuthorizationParityTest" test
```

- PASS: 17 tests, 0 failures, 0 errors, 0 skipped.
- Covers validation, lock/quota ordering, tenant scope, atomic duplicate preflight, booking conflict, authoritative state protection and permission parity.

Frontend focused suite:

```text
npm test -- --watch=false --include=src/app/features/admin/room-management/room-management.spec.ts --include=src/app/features/management/inventory/management-inventory.component.spec.ts
```

- PASS: 6 tests across admin and owner inventory surfaces.

Build:

```text
npm run build -- --configuration development
```

- PASS: Angular development bundle generated with temporary public-i18n compatibility sources; those sources were removed before staging.

SQL Server:

```text
backend\tools\physical-room-sqlserver-validation.ps1
```

- PASS: V85 applied twice to a disposable SQL Server 2022 database.
- Verified legacy normalization (` a101 ` to `A101`), unique `(hotel_id, room_number)` enforcement and invalid floor rejection.

## Parallel-work compatibility

The base branch references subscription catalog and public i18n sources available only in parallel task worktrees. Focused verification used temporary compatibility sources/compiler includes; all were removed before staging and are not part of T259.

## Migration and recovery

- V85 normalizes current room numbers and stops with an explicit error if normalized duplicates must be resolved before the unique index can be added.
- Forward recovery: rename conflicting legacy rooms deterministically, rerun V85, then keep all writes behind the property lock and database unique index.
- Rollback for an unpromoted disposable environment: drop the V85 unique index/check constraints; normalized room numbers remain valid application data.
- Production migration execution and destructive rollback were not performed.
