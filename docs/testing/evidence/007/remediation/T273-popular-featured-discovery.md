# T273 Popular And Featured Discovery Evidence

Date: 2026-08-04
Branch: `codex/public-booking`
Base: `origin/codex/ui-functional-audit-polish` at `88f7da5`
Capability: `PUB-005`
Validation status: `COMPLETE_VERIFIED`
Production credentials, production data, tenant mutation, destructive migration or real-money operation: N/A

## Implemented Contract

- Popular destinations are limited to the current province catalog, require at least one publicly eligible property and rank by property count descending, display name ascending and province ID ascending. The public API and frontend both clamp requests to 1..8 results.
- Destination media is selected from the eight bundled assets by stable province source code, not the destination's current rank. Responses include explicit alt text and provenance in the form `BUNDLED_DESTINATION:destination-NN.webp`.
- The popular response declares a public 60-second freshness window with revalidation. The Angular client shares concurrent requests, reuses a result for at most 60 seconds, evicts errors and supports explicit invalidation/forced retry.
- Featured properties use the canonical public search with `sortBy=RATING`: reviewed properties first, then rating descending, review count descending and property ID ascending. Home initialization and retry execute a live uncached search; the API declares `Cache-Control: no-store`.
- Property media priority is deterministic: primary ordered property media, first ordered property media, catalog main image, then the frontend property-type fallback. Blank media URLs are ignored, gallery order is stable, and each response carries image alt text plus `PROPERTY_MEDIA`, `PROPERTY_CATALOG_MAIN` or `NONE` provenance.
- Popular and featured sections distinguish loading, request failure and valid-empty states. Each failure has its own retry action, and broken or absent image URLs fall back once to a bundled local asset without a retry loop.

## API-Backed Browser Journey

The T273 journey was added to the existing API-backed public-booking Playwright suite. It uses the real Angular application and Spring/H2 `e2e` profile with deterministic current-location and property seed data. Only the failed image request is simulated: the test returns HTTP 404 for the first seeded property asset so the browser must render the type-specific local fallback.

```powershell
Set-Location frontend
npx playwright test --config playwright.api.config.ts --list
$env:PLAYWRIGHT_EXTERNAL_BACKEND='true'
npx playwright test --config playwright.api.config.ts --grep "deterministic seeded popular"
```

Result: Playwright discovery PASS with 6 API-backed tests listed; the focused T273 journey passed 1/1 against the real Angular application and Spring/H2 E2E API.

The journey verifies:

- Popular and featured response order exactly matches the rendered DOM ID order.
- Popular ranking follows property count/name/ID and featured ranking follows reviewed/rating/review-count/ID rules.
- Popular responses expose the 60-second public freshness contract, while featured responses are `no-store` live searches.
- Every rendered discovery image has the non-empty API alt text and matching provenance data attribute.
- A seeded property image forced to 404 is replaced by a successfully loaded bundled fallback.

## Focused Backend And Frontend Tests

```powershell
Set-Location backend
.\mvnw.cmd "-Dtest=PublicDiscoveryControllerIntegrationTest,PropertySearchControllerIntegrationTest" test

Set-Location ../frontend
npm test -- --watch=false `
  --include=src/app/core/services/client-api.service.spec.ts `
  --include=src/app/features/client/home/components/popular-destinations/popular-destinations.component.spec.ts `
  --include=src/app/features/client/home/components/featured-properties/featured-properties.component.spec.ts `
  --include=src/app/features/property-search/components/property-result-card/property-result-card.spec.ts
npx ng build --configuration development
```

Results:

- Backend HTTP/integration: 16/16 PASS (`PublicDiscoveryControllerIntegrationTest` 9/9 and `PropertySearchControllerIntegrationTest` 7/7). The Maven wrapper reached the harness timeout immediately after both Surefire reports completed; both reports contain zero failures and zero errors.
- Angular cache and component suites: 13/13 PASS across four focused spec files, including a late stale-request failure that cannot evict a newer forced-refresh result and blank API image normalization on both Home and search cards.
- Angular development build: PASS. The build and browser run used temporary local implementations of the two i18n services owned by a parallel branch; both files were removed and are not part of this task.
- The normal unmodified Maven compile remains blocked before relevant compilation by the pre-existing UTF-8 BOMs in `UserService.java` and `UserController.java`, plus an incomplete parallel platform-billing controller. A temporary compiler overlay excluded only those unrelated sources and was removed before staging.

Coverage includes deterministic ties and repeat calls, limit clamping, stable destination asset selection after a rank change, reviewed/unreviewed property ordering, trimmed/blank image normalization, image priority/provenance, cache concurrency/TTL/force-refresh/error eviction, independent error/empty/retry states and broken-image fallback without a loop.

## Asset Provenance

`docs/ASSET_LICENSES.md` now maps every bundled property, destination and room filename to its Unsplash photo ID. Type-specific and destination fallbacks are byte-identical local copies and retain the source ID of the copied file.

```powershell
Get-FileHash frontend/public/assets/properties/*.webp,frontend/public/assets/destinations/*.webp,frontend/public/assets/room-types/*.webp,frontend/public/assets/fallbacks/*.webp -Algorithm SHA256 | Sort-Object Hash,Path
```

Result: 14/14 documented fallback/source pairs have identical SHA-256 hashes.

## Permissions And Isolation

- Both endpoints remain anonymous public reads. No customer, staff or property role is required.
- Tenant-owned mutation and authenticated tenant selection are N/A; this task does not write property data and does not accept a caller-controlled property ID as authority.
- The E2E database is isolated in-memory H2 and is discarded when the backend process stops.

## Schema And Recovery

No schema or migration change exists. Rollback is the eventual T273 task commit revert. The local media remains licensed demo content; cache invalidation can be forced in the client, and the featured collection deliberately avoids reusable HTTP/client cache state.

## Follow-On Boundaries

- The complete public eligibility matrix remains T274/PUB-006.
- The full sort and pagination matrix remains T279/PUB-011.
- Search/detail availability consistency remains T280/PUB-012.
- Complete gallery and property content governance remains T284/PUB-017.
- Nationwide search query/N+1 optimization remains T294/PUB-029.
