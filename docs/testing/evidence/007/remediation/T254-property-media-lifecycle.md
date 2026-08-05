# T254 - Property-owned media validation and lifecycle

Date: 2026-08-04
Branch: `codex/property-operations`

## Implemented behavior

- Added `property_media` as the shared tenant-owned media registry for property,
  room-type and physical-room image rows. Each record carries its property owner,
  source type, public URL, optional storage key, detected content type, file size,
  dimensions, SHA-256 checksum, localized alternative text and lifecycle status.
- New external image links accept only absolute HTTPS URLs with a valid host and no
  embedded credentials. Managed upload paths cannot be claimed through the link
  contract. Vietnamese alternative text is mandatory and bounded; English text is
  optional and bounded.
- Property uploads reuse the hardened JPG/PNG/WebP parser and now persist detected
  signature/content type, dimensions, byte size, checksum and randomized
  property-scoped storage key. Upload errors use property-media error codes rather
  than avatar-specific API responses.
- Property galleries, room-type replacement and room creation associate image rows
  with owned media records. Physical-room images receive deterministic order,
  localized alt text and a single primary image. Room-type replacement releases old
  media only after the replacement rows have been flushed.
- Profile forms no longer mutate the legacy `hotels.main_image` field directly;
  the tenant-safe gallery primary remains its authoritative projection.

## Authorization, ownership and cleanup

- `PropertyMedia` participates in the Hibernate tenant filter, and every creation
  takes the server-resolved `Hotel` owner rather than a client-supplied media owner.
- Cross-property media cannot be attached through the public service API. Gallery
  operations retain their independent super-admin or active property-assignment
  check before media creation or release.
- Reference checks cover property, room-type and physical-room image tables. An
  unreferenced managed file is deleted only after the database transaction commits;
  rollback retains still-referenced files. A failed upload association reclaims the
  newly written file.
- T258 and T260 retain their dedicated room-type and physical-room gallery CRUD,
  concurrency and richer UI scope; T254 supplies their owned-media foundation.

## Verification

Backend focused command:

```text
backend\mvnw.cmd "-Dtest=PropertyMediaPolicyTest,PropertyMediaServiceTest,PropertyGalleryServiceTest,FileUploadServiceTest,PropertyProfileMapperTest,RoomTypeServiceImplTest,RoomServiceImplTest,TenantFilterArchitectureTest" test
```

Result: 39/39 passed with zero failures, errors or skipped tests. Coverage includes
HTTPS/user-info/managed-path rejection, localized alt validation, property-owned
external and uploaded records, checksum/dimension/storage metadata, signature error
mapping, cross-table reference-aware release, rollback/post-commit file cleanup,
tenant-filter registration, gallery invariants, room image ownership and room-type
replacement release behavior.

Frontend focused command:

```text
npm test -- --watch=false --include=src/app/core/services/property-gallery.service.spec.ts --include=src/app/features/admin/property-management/property-management.spec.ts --include=src/app/features/admin/room-type-management/room-type-management.spec.ts --include=src/app/features/management/dashboard/management-dashboard.component.spec.ts --include=src/app/shared/components/property-gallery/property-gallery.component.spec.ts
```

Result: 16/16 passed across five files. The suite covers the owned-media response
metadata, required alternative text, gallery state transitions and reachability from
both administrative and owner surfaces.

Frontend compile command:

```text
npm run build -- --configuration development
```

Result: development bundle completed successfully.

SQL Server validation command:

```text
backend\tools\property-media-sqlserver-validation.ps1
```

Result: migration/backfill/idempotence validation passed against SQL Server 2022.
The executable harness creates a disposable database/container by default; the
verified run reused an already-running disposable test server because concurrent
Docker load prevented a third SQL Server container from becoming ready. It applied
V80 twice, confirmed all three legacy image tables received owned media references,
confirmed physical-room metadata normalization and foreign keys, and proved the
managed-upload metadata constraint rejects incomplete rows. The disposable database
was dropped after verification.

## Migration and recovery

- `V80__property_media_registry.sql` is additive. It creates the media registry,
  adds nullable media references, backfills one owned record per property/URL,
  fills missing legacy alt/order metadata and adds indexes/checks/foreign keys.
- The migration is idempotent when executed twice in the validation harness. Dynamic
  SQL is used after conditional column additions so first-run SQL Server batch
  compilation succeeds on the legacy schema.
- No existing image row or file is deleted by the migration. Forward recovery can
  repair missing references and rerun V80 logic in a new additive migration. Rolling
  the application back leaves nullable media references and the registry harmless;
  destructive schema rollback is not required or authorized.
- No production credential, production database, external object store or real
  customer media was used.

Temporary subscription source snapshots, repository compatibility methods, Maven
test includes and copied Angular i18n sources used only to work around parallel-base
compile gaps were removed before staging.
