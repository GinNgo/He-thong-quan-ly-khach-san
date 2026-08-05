# T272 Current Province Compatibility Evidence

Date: 2026-08-04
Branch: `codex/public-booking`
Base: `origin/codex/ui-functional-audit-polish` at `88f7da5`
Capability: `PUB-004`
Production credentials, production database mutation, destructive migration, tenant-owned mutation or real-money operation: N/A

## Implemented Contract

- The packaged administrative baseline contains all 63 legacy provinces and 10,051 wards. A separately validated catalog projects those rows into exactly 34 current provinces while retaining every legacy province ID as a compatible search scope.
- `GET /api/public/locations/provinces` and autocomplete return only the exact 34 source codes from the packaged compatibility catalog; stale/custom `VN34-*` rows are excluded. Ward lookup, property counts, property suggestions and property search expand a current or legacy province ID into the same compatibility scope.
- Province, ward, property and landmark suggestions preserve the deterministic exact/prefix/name/ID ordering, per-group limits and ID-based deduplication established by T271.
- Property and landmark responses project legacy storage rows to the current province display identity. A current-province selection accepts properties and landmarks stored under any mapped legacy province.
- Landmark discovery rejects inactive, missing-coordinate, non-finite and out-of-range-coordinate rows. Search rejects incomplete, non-finite or out-of-range coordinate pairs, non-finite or out-of-range radius values and landmark/province mismatches outside the compatibility scope.
- The packaged landmark catalog retains separate IDs for duplicate names, including `Chợ Đêm` across provinces and two `Hồ Xuân Hương` rows in the same current province.

## Packaged Data Integrity

The packaged JSON catalogs were parsed independently before import:

| Catalog | SHA-256 | Verified content |
|---|---|---|
| `locations.json` | `87C64C32262F3D0ADE57954668CDF1771C7718CA2C38CDCB3250C246B3F65C9B` | 63 legacy provinces, 696 legacy districts, 10,051 wards |
| `provinces-current-34.json` | `4182EAE7588D3C90778454EF0B499E92E497D3EEB543F0CC8F494D342F810647` | 34 current provinces, 63 unique legacy aliases, no missing or overlapping mapping |
| `landmarks.json` | `54092B3FCB7F4D4F0583165BAAFA41D31C63E8E6289DA243640CCF537DA2B078` | 122 landmarks, 121 active, all 34 current provinces covered, zero invalid active coordinates/radii/province references |

The packaged import integration runs the complete import twice against isolated H2 storage and proves the second run adds no rows. Final persisted counts are 63 legacy provinces, 34 current provinces, 10,051 wards and 122 landmarks; every one of the 63 legacy aliases resolves bidirectionally to its catalog current province. All 121 active landmarks retain valid coordinates, bounded radii and current-province parents. A fixture with one valid landmark followed by an invalid row proves the whole import transaction rolls back and persists neither row.

## Focused Backend Tests

The base branch cannot run an unmodified full Maven compile because unrelated `UserService.java` and `UserController.java` contain UTF-8 BOMs, while the partial platform-billing controller references source absent from this branch. A temporary uncommitted compiler overlay excluded only those three unrelated sources and limited test compilation to the four T272 suites. The overlay was removed; `backend/pom.xml` matches `HEAD`.

```powershell
Set-Location backend
.\mvnw.cmd "-Dtest=PackagedLocationImportIntegrationTest" test
.\mvnw.cmd "-Dtest=ProvinceCompatibilityServiceTest" test
.\mvnw.cmd "-Dtest=PublicDiscoveryControllerIntegrationTest" test
.\mvnw.cmd "-Dtest=PropertySearchControllerIntegrationTest" test
```

Result: 18/18 passed.

- Packaged import: 1/1.
- Province compatibility service: 3/3.
- Public discovery HTTP: 8/8.
- Property search HTTP: 6/6.

Coverage includes exact 34-code filtering, all 63 legacy alias mappings, current/legacy scope equivalence, ward unions, aggregate property visibility, current display projection, duplicate landmark identity, atomic invalid-catalog rollback, invalid-coordinate filtering, authoritative landmark coordinates, zero/oversized/NaN radius rejection, province mismatch and direct coordinate validation.

## API-Backed Browser Tests

The real Angular application and compiled Spring/H2 E2E backend ran on isolated local ports. The backend imported the packaged nationwide catalogs before deterministic demo seeding.

```powershell
Set-Location frontend
$env:PLAYWRIGHT_EXTERNAL_BACKEND='true'
npx playwright test --config playwright.api.config.ts
npx playwright test --config playwright.api.config.ts --grep "supports current province compatibility"
```

Result: full regression 5/5 passed; the final T272-focused rerun after exact catalog-code filtering passed 1/1.

The T272 journey verifies exactly 34 current provinces, current-ID ward expansion over legacy parents, browser selection and URL serialization of a legacy ward, two separate duplicate-name landmark options and a successful landmark-backed HTTP search. The full file also re-runs the T269-T271 search/detail/back, overnight-only, grouped keyboard autocomplete, sticky landmark and mobile validation journeys.

## Schema, Permissions And Recovery

- The landmark schema migration remains additive and retains `V59__public_landmark_autocomplete.sql`, the version already published by T271. Renaming an already-pushed Flyway migration would invalidate any database that applied it. A parallel branch later introduced its own V59 migration, so the coordinator must renumber that not-yet-integrated migration during branch convergence. No migration was executed against production or persistent user data in T272.
- Location import is a system startup operation using packaged/configured resources. Public APIs remain anonymous reads and do not mutate property-owned or tenant-owned records.
- To disable forward import without data loss, set `app.location-import.enabled=false`. Reverting the task commit removes compatibility behavior and packaged catalogs; already-added nullable landmark columns/indexes may remain safely unused. Removing imported rows is intentionally not automated because destructive cleanup requires separate approval.
