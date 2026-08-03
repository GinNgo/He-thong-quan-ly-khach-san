# T271 Grouped Autocomplete Evidence

Date: 2026-08-04
Branch: `codex/public-booking`
Base: `origin/codex/ui-functional-audit-polish` at `88f7da5`
Capability: `PUB-003`
Production credentials, production data, destructive migration, tenant mutation or real-money operation: N/A

## Implemented Contract

- `GET /api/public/search/suggestions` returns separate province, ward, property and landmark groups.
- Vietnamese names match with or without accents through normalized search fields.
- Each group uses deterministic exact, prefix and stable name/ID ordering, is deduplicated by type plus ID, and clamps its limit to `1..10`.
- Landmark suggestions require `ACTIVE` status and stored coordinates, include province context, category, coordinates and a bounded default radius, and support optional province scoping.
- The Angular autocomplete exposes group metadata, keeps keyboard navigation across all groups and selects the active item with Enter.
- A landmark selection persists `landmarkId`, coordinates and radius in home state; search navigation emits `sortBy=NEAREST`, and search-page URL hydration preserves that selection for sticky-bar resubmission.
- Property search resolves an active landmark by ID, rejects unusable landmarks, overrides caller-supplied coordinates with stored coordinates and applies a bounded radius before distance filtering.

## Focused Frontend Tests

```powershell
Set-Location frontend
npx vitest run src/app/features/client/home/services/home-search-state.service.spec.ts src/app/features/client/home/components/location-autocomplete/location-autocomplete.component.spec.ts --config vitest-base.config.ts --globals
```

Result: 8/8 passed.

The tests cover landmark serialization, URL hydration and keyboard traversal/selection across grouped results.

## Focused Backend Tests

The base branch cannot run an unmodified full Maven compile because unrelated `UserService.java` and `UserController.java` files contain UTF-8 BOMs, and the partial platform-billing source set references classes absent from this branch. A temporary, uncommitted Maven compiler overlay excluded only those unrelated sources and limited test compilation to the public discovery integration class. The overlay was removed after validation.

```powershell
Set-Location backend
.\mvnw.cmd -Dtest=PublicDiscoveryControllerIntegrationTest#groupedSuggestions_* test
```

Result: 6/6 passed after the remaining application sources compiled successfully.

Covered accent/no-accent province and ward matching, property name/address matching, the two-character floor, active coordinate-backed duplicate landmark names, deterministic priority, per-group limit, province-scoped landmark suggestions, authoritative landmark coordinate resolution and invalid-landmark rejection. A full-class diagnostic also identified the independent PUB-004 legacy-province projection assertion, which remains assigned to T272.

## Angular Build

```powershell
Set-Location frontend
npx ng build --configuration development
```

Result: PASS after temporarily supplying the two i18n service files owned by the parallel i18n scope. The files were removed after validation and are not part of this branch.

## API-Backed Browser Test

The browser used the real Angular application and Spring/H2 E2E API on isolated ports. Spring ran from the focused compiled classes and deterministic local demo seed; API responses were reverse-proxied only to avoid shared local port conflicts.

```powershell
Set-Location frontend
$env:PLAYWRIGHT_EXTERNAL_BACKEND='true'
npx playwright test --config playwright.api.config.ts --grep 'grouped accent'
```

Result: 1/1 passed.

The journey verifies accent/no-accent province lookup, property discovery and keyboard selection, landmark selection, `landmarkId` plus `sortBy=NEAREST` serialization, HTTP 200 search, rendered property results and sticky-bar resubmission without losing the landmark.

## Schema and Isolation

- `V59__public_landmark_autocomplete.sql` is additive: it creates nullable landmark metadata columns, a defaulted popularity column and filtered discovery indexes only when absent.
- The migration was not executed against production or persistent user data. H2 test schemas were isolated and discarded with the backend process.
- Suggestions remain anonymous public reads. No property-owned or tenant-owned record is mutated.

## Recovery

Revert the T271 task commit to remove the endpoint/UI behavior. If the SQL Server migration has already been applied, the added nullable columns and indexes can remain safely unused; no destructive rollback is required.
