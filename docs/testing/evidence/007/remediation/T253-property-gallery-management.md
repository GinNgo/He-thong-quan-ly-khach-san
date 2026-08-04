# T253 - Property gallery management

Date: 2026-08-04
Branch: `codex/property-operations`

## Implemented behavior

- Added one property-scoped gallery API at
  `/api/v1/properties/{propertyId}/gallery` with list, external HTTP(S) link,
  validated multipart upload, complete reorder, set-primary and delete commands.
- Added a reusable Angular gallery component and client. The same component is
  reachable from the administrative property editor and the management dashboard,
  with loading/error/empty states, localized alternative-text fields, upload/link
  controls, ordering controls, primary selection and deletion.
- Gallery writes normalize contiguous `sortOrder` values and preserve exactly one
  primary image whenever the gallery is non-empty. The selected primary URL is also
  synchronized to the legacy `hotels.main_image` projection used by existing public
  search/detail fallbacks.
- The first image becomes primary automatically. Deleting the primary promotes the
  next ordered image; deleting the last image clears the legacy projection.

## Authorization and tenant isolation

- Controller access is restricted to the existing property-management roles.
  The service independently resolves `SUPER_ADMIN` authority or an authenticated
  active property assignment before returning or mutating gallery data.
- Property and image identifiers outside the caller's property scope are reported
  as not-found. Reorder requires the exact current property-local image id set, with
  no missing, duplicate or injected cross-property ids.
- Closed properties are immutable and properties under approval cannot be changed,
  matching the profile lifecycle boundary established by T251-T252.
- The link endpoint accepts only absolute HTTP(S) URLs and rejects managed upload
  paths, preventing callers from claiming and later deleting an avatar or another
  managed file through a linked gallery row.

## Atomic quota and storage lifecycle

- Every addition pessimistically locks the `hotels` row before reading current
  gallery usage and before applying the property entitlement. The `MAX_IMAGES`
  calculation includes property, room-type and physical-room images, so concurrent
  gallery additions for the same property serialize around count-plus-insert.
- Uploaded JPG/PNG/WebP files reuse the existing signature, declared content-type,
  dimension, pixel and path-boundary validation, with a property-scoped random
  filename under `/api/public/uploads/`.
- A failed database association deletes the newly written file immediately. A later
  transaction rollback also deletes it through transaction synchronization. Managed
  files removed from a gallery are deleted only after database commit, so rollback
  retains the still-referenced file.

## Verification

Backend focused command:

```text
backend\mvnw.cmd "-Dtest=PropertyGalleryServiceTest,FileUploadServiceTest" test
```

Result: 17/17 passed with zero failures, errors or skipped tests. Coverage includes
property-lock-before-quota order, aggregate quota denial, cross-property property and
image IDOR, managed-link rejection, exact reorder membership, single-primary repair,
database failure cleanup, transaction rollback cleanup, post-commit deletion and
real property-scoped filesystem storage/deletion.

Frontend focused command:

```text
npm test -- --watch=false --include=src/app/features/admin/property-management/property-management.spec.ts --include=src/app/features/management/dashboard/management-dashboard.component.spec.ts --include=src/app/shared/components/property-gallery/property-gallery.component.spec.ts --include=src/app/core/services/property-gallery.service.spec.ts
```

Result: 15/15 passed across four files. Coverage includes the canonical HTTP paths,
multipart metadata, exact reorder payload, optimistic reorder rollback, primary and
delete state, upload/link commands, and gallery reachability in both admin and owner
management surfaces.

Frontend compile command:

```text
npm run build -- --configuration development
```

Result: development bundle completed successfully.

## Migration and recovery

No schema migration, destructive backfill, production credential or external storage
operation is required. T253 reuses `property_images` and the configured local upload
directory. Rollback requires reverting the gallery controller/service/client/UI and
file-storage extension together; existing image rows and uploaded files remain
compatible.

Temporary subscription source snapshots, repository compatibility methods, Maven
test includes and copied Angular i18n sources used only to work around parallel-base
compile gaps were removed before staging.
