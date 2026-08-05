# T252 - Canonical property profile

Date: 2026-08-04
Branch: `codex/property-operations`

## Implemented behavior

- `PropertyProfileDTO` is the single backend create/read profile contract for both
  administrative and management APIs. Updates use `PropertyProfileUpdateRequest`
  so the complete profile round-trips with a required audit reason.
- Admin and owner APIs expose canonical list/detail/create/update shapes. The legacy
  create/update/management request DTOs were removed, and owner hotel reads no longer
  serialize the `Hotel` entity directly.
- The profile includes bilingual names/descriptions, property type, full location,
  coordinates, contact details, HTTP(S) website, check-in/out times, price range,
  star rating and representative image. Identifier, lifecycle, demo, source and
  aggregate-rating fields remain response-only.
- `PropertyProfileMapper` applies the same normalization and persistence rules to
  admin and owner creation/update. It validates province/ward ownership, property
  type, coordinate pairing, HTTP(S) host and minimum/maximum price order.
- Canonical writes derive city/country from the selected location and synchronize
  legacy `name`/`description` columns with `nameVi`/`descriptionVi`; legacy columns
  are fallback-only during reads and are never client-authoritative.
- The admin property dialog and owner dashboard editor expose the complete editable
  profile. Both clients use the same `PropertyProfile` model and update wrapper.

## Authorization and tenant isolation

- Admin list/detail/create/update remain protected by `SUPER_ADMIN`, with an
  independent service-side administrator check for mutation/read paths.
- Owner detail and update resolve the authenticated user and require an active
  property-local `OWNER` assignment. Cross-property reads and updates return
  not-found before the hotel repository can reveal or mutate the target.
- Read-only JSON fields ignore attempted lifecycle input; status, approval,
  operation, code, slug, demo/source and ratings remain server-owned.

## Validation and persistence evidence

- Bean validation rejects malformed phone/email/HTTP(S) URL/time, out-of-range
  coordinates, incomplete coordinate pairs and inverted price ranges before HTTP
  mutation.
- Mapper tests prove all editable fields persist and round-trip to the canonical DTO,
  while legacy name/description synchronization and location-derived city/country
  remain deterministic.
- Audit snapshots now include every editable profile field, so accepted updates keep
  complete before/after evidence in the existing transaction.

## Verification

Backend focused command:

```text
backend\mvnw.cmd "-Dtest=HotelManagementServiceImplTest,PropertyProfileMapperTest,PropertyProfileDTOValidationTest,ManagementPropertyControllerHttpTest,PropertyAdministrationControllerHttpTest,ManagementPortalServiceTest" test
```

Result: 20/20 passed with zero failures, errors or skipped tests.

Frontend focused command:

```text
npx ng test --watch=false --include src/app/core/services/management-api.service.spec.ts --include src/app/features/admin/property-management/property-management.spec.ts --include src/app/features/management/dashboard/management-dashboard.component.spec.ts
```

Result: 9/9 passed across three files, including location-option loading for the
owner profile editor.

Frontend compile command:

```text
npx ng build --configuration development
```

Result: development bundle completed successfully.

## Migration and recovery

No schema migration or production credential is required. The existing `hotels`
columns are reused without destructive cleanup or backfill. Rollback requires
reverting the DTO/controller/service/client/form changes together; already persisted
profile data and operational audit records remain compatible and retained.

Temporary subscription source snapshots, repository compatibility methods, Maven
test includes and copied Angular i18n sources used only to work around parallel-base
compile gaps were removed before staging.
