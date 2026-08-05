# T278 Price Filter Consistency Evidence

Date: 2026-08-04
Branch: `codex/public-booking`
Base: `origin/codex/ui-functional-audit-polish` at `88f7da5`
Capability: `PUB-010`
Validation status: `COMPLETE_VERIFIED`
Production credentials, production data, tenant mutation, destructive migration or real-money operation: N/A

## Implemented Contract

- Minimum and maximum nightly-price bounds are inclusive and must be satisfied by the same active, capacity-compatible, physically available room type.
- Null-priced room types never qualify. When dates are supplied, the room type used for filtering and displayed pricing must also have sufficient inventory after overlapping reservations.
- `startingPrice`, the lowest-room summary and the public pricing projection are selected from the same bounded eligible room set; the UI nightly price therefore cannot fall outside the requested range.
- `PRICE_ASC` and `PRICE_DESC` order by that same bounded displayed-price projection. Stable tie ordering remains owned by T279.
- Price inputs must be finite and non-negative, and `minPrice` cannot exceed `maxPrice`; invalid requests return HTTP 400.
- The frontend preserves raw invalid route order when sending the authoritative API request, canonicalizes only slider display until Apply, renders a removable price chip and resets `pageNumber` to 1 while preserving other query state.

## Focused Validation

```powershell
Set-Location backend
.\mvnw.cmd "-Dtest=PropertySearchControllerIntegrationTest" -DforkCount=0 test

Set-Location ../frontend
npm test -- --watch=false `
  --include=src/app/core/services/client-api.service.spec.ts `
  --include=src/app/features/property-search/pages/property-search-page/property-search-query.spec.ts `
  --include=src/app/features/property-search/pages/property-search-page/property-search-page.spec.ts `
  --include=src/app/features/property-search/components/search-filter-sidebar/search-filter-sidebar.spec.ts `
  --include=src/app/features/property-search/components/property-result-card/property-result-card.spec.ts
npm run build -- --configuration development

# Final browser rerun used already-listening local backend/frontend processes.
$env:PLAYWRIGHT_EXTERNAL_BACKEND='true'
$env:PLAYWRIGHT_API_ONLY='true'
npx playwright test --config playwright.api.config.ts --grep "price bounds|PRICE_ASC|price chip"
```

Results:

- Backend `PropertySearchControllerIntegrationTest`: 19/19 PASS, zero failures/errors/skips. Focused T278 cases cover invalid bounds, split-room bounds, exact inclusivity, unavailable in-range rooms, null price, one-sided bounds and 550k/650k/750k ascending/descending projections.
- Frontend focused price/query/sidebar/card/page/client tests: 5 files / 28 tests PASS. Angular development build: PASS in 48.535 seconds.
- API-backed Playwright: 3/3 PASS against the real E2E backend and Angular UI.
- `demo-hn-01`, with room prices below/inside/above the tested ranges, is excluded for split-only 600k-700k bounds and is the exact inclusive result for 500k-500k; API `startingPrice`, pricing nightly price and DOM `data-price-value` all equal 500000.
- Bounded `PRICE_ASC` produces 500k, 500k, 500k, 500k, 550k, 550k, 575k, 575k and 650k for the deterministic demo set; `PRICE_DESC` produces the reverse values over the same properties, with every displayed price inside 500k-800k.
- The browser journey hydrates the inclusive price chip at page 3, applies it at page 1 while preserving keyword/display location/type/star/sort/page size, then removes it from page 2 and again resets to page 1 without overwriting the preserved search state.
- Temporary compiler/i18n overlays and locally started test processes/logs were removed before handoff.

## Permissions And Isolation

- Price filtering is an anonymous public read and accepts no caller-controlled tenant or mutation scope.
- The filter only narrows properties and rooms already permitted by canonical public eligibility, capacity and inventory predicates.
- Tests use deterministic integration/E2E data and test-only secrets. No production credential, production data or real-money action is used.

## Schema And Recovery

No schema or migration change exists. Rollback is the eventual T278 task commit revert; no data conversion or destructive cleanup is required.

## Remaining Boundaries

- T279 owns stable tie-breakers, the full sort matrix, count parity and out-of-range pagination behavior.
- T280-T281 own the complete availability and capacity matrices.
- T282 owns search/detail quote consistency and T294 owns nationwide performance optimization.
