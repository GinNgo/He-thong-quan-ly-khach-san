# T260 - Physical-room image lifecycle

## Scope and result

- Branch/worktree: `codex/property-operations` in the dedicated external worktree.
- Starting commit: `233219a`.
- Result: every physical room now has a tenant-safe property-owned gallery lifecycle reachable from both room management surfaces.

## Behavior evidence

- `/api/v1/rooms/{roomId}/gallery` provides list, HTTPS link, managed upload, exact reorder, primary selection and delete operations.
- Reads require `ROOM:VIEW`; mutations require `ROOM:UPDATE`, with independent property assignment checks and cross-tenant not-found behavior.
- Mutations lock the property before the room and evaluate aggregate property/room-type/room image usage under that lock.
- Shared media policy enforces credential-free HTTPS links, file signature/content type/dimension limits, required Vietnamese alt text and bounded optional English text.
- Reorder and delete use temporary sort positions before zero-based compaction. A non-empty gallery retains exactly one primary image.
- Failed associations discard newly created media while preserving prior room images; removed managed files are reclaimed only when unreferenced and after transaction commit.
- The reusable gallery editor now supports property, room-type and physical-room scopes. Admin and owner room edit flows expose upload/link/order/primary/delete without overloading the room metadata DTO.

## Automated verification

Backend focused suite:

```text
backend\mvnw.cmd "-Dtest=RoomGalleryServiceTest" test
```

- PASS: 6 tests, 0 failures, 0 errors, 0 skipped.
- Covers property/room lock order, quota, tenant IDOR, failed-association cleanup, collision-safe reorder, primary promotion and delete cleanup.

Frontend focused suites:

```text
npm test -- --watch=false --include=src/app/core/services/room-gallery.service.spec.ts --include=src/app/shared/components/property-gallery/property-gallery.component.spec.ts --include=src/app/features/admin/room-management/room-management.spec.ts
npm test -- --watch=false --include=src/app/features/management/inventory/management-inventory.component.spec.ts
```

- PASS: 12 tests in the rerun group plus 3 owner-inventory tests; total 15.

Build:

```text
npm run build -- --configuration development
```

- PASS: Angular development bundle generated with temporary public-i18n compatibility sources; those sources were removed before staging.

SQL Server:

```text
backend\tools\room-gallery-sqlserver-validation.ps1 -LocalServer '.\MSSQLSERVER01'
```

- PASS: V86 normalized a malformed legacy gallery and applied twice to a disposable database on the local SQL Server developer instance.
- Verified unique `(room_id, sort_order)` and filtered single-primary indexes by proving duplicate inserts are rejected.
- The disposable database was dropped by the harness. Docker mode remains available when its daemon is healthy.

## Parallel-work compatibility

Focused backend/frontend verification used temporary compatibility sources required by parallel subscription/public-i18n work. All temporary sources and compiler includes were removed before staging.

## Migration and recovery

- V86 is additive and idempotent. It deterministically normalizes order and primary selection before adding unique indexes.
- Forward recovery: repair a gallery through the dedicated API and rerun V86 if deployment stops before index creation.
- Rollback for an unpromoted disposable environment: drop `UX_room_images_room_order_v86` and `UX_room_images_one_primary_v86`; no media rows need deletion.
- Production migration execution and destructive rollback were not performed.
