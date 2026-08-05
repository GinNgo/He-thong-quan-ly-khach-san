# T258 - Room-type image replacement

## Scope and result

- Branch/worktree: `codex/property-operations` in the dedicated external worktree.
- Starting commit: `d5da3c8`.
- Result: room-type images now use a dedicated property-owned gallery contract instead of destructive URL replacement during ordinary room-type edits.

## Behavior evidence

- `/api/v1/room-types/{roomTypeId}/gallery` provides list, HTTPS link, managed upload, exact reorder, primary selection and delete operations.
- Reads require `ROOM_TYPE:VIEW`; every mutation requires `ROOM_TYPE:UPDATE` and independently hides cross-property identifiers as not found.
- Mutations lock the property before the room type, then evaluate aggregate `MAX_IMAGES` usage while the property lock is held so concurrent galleries cannot bypass quota.
- External links use the shared credential-free absolute HTTPS policy. Uploads use signature/content-type/dimension checks and both paths require Vietnamese alt text with bounded optional English text.
- Reorder and delete use temporary sort positions before compacting to contiguous zero-based order, avoiding unique-index collisions. Exactly one image is primary whenever the gallery is non-empty.
- New owned media is discarded when association fails; transaction rollback preserves existing rows and managed upload bytes. Removed managed media is reclaimed only after it is unreferenced and the transaction commits.
- Admin and owner inventory surfaces reuse the gallery editor. Standard room-type updates omit `imageUrls`, so profile edits cannot silently replace gallery metadata.

## Automated verification

Backend focused suite:

```text
backend\mvnw.cmd "-Dtest=RoomTypeGalleryServiceTest,PropertyMediaServiceTest,TenantFilterArchitectureTest" test
```

- PASS: 14 tests, 0 failures, 0 errors, 0 skipped.
- Covers lock order, aggregate quota, tenant IDOR, association rollback with old-gallery preservation, owned-media cleanup, reorder, primary, delete and tenant-filter architecture.

Frontend focused suite:

```text
npm test -- --watch=false --include=src/app/core/services/room-type-gallery.service.spec.ts --include=src/app/shared/components/property-gallery/property-gallery.component.spec.ts --include=src/app/features/admin/room-type-management/room-type-management.spec.ts --include=src/app/features/management/inventory/management-inventory.component.spec.ts
```

- PASS: 13 tests across four files.

Build:

```text
npm run build -- --configuration development
```

- PASS: Angular development bundle generated with temporary public-i18n compatibility sources; those sources were removed before staging.

SQL Server:

```text
backend\tools\room-type-gallery-sqlserver-validation.ps1
```

- PASS: V84 normalized a malformed legacy gallery and applied twice to a disposable SQL Server 2022 database.
- Verified contiguous unique `(room_type_id, sort_order)` and a filtered unique single-primary index by proving duplicate inserts are rejected.

## Parallel-work compatibility

The base branch still references subscription catalog and public i18n sources that exist only in parallel task worktrees. Focused verification used temporary compatibility sources/compiler includes; all were removed before staging and are not part of T258.

## Migration and recovery

- V84 is additive and idempotent. It deterministically normalizes order and selects the earliest existing/ordered image as primary before adding unique indexes.
- Forward recovery: repair a gallery through the dedicated API and rerun V84 if a deployment was interrupted before index creation.
- Rollback for an unpromoted disposable environment: drop `UX_room_type_images_type_order_v84` and `UX_room_type_images_one_primary_v84`; no image/media rows need deletion.
- Production migration execution and destructive rollback were not performed.
