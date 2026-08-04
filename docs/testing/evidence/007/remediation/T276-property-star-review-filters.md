# T276 Property Type, Star And Review Filter Evidence

Date: 2026-08-04
Branch: `codex/public-booking`
Base: `origin/codex/ui-functional-audit-polish` at `88f7da5`
Capability: `PUB-008`
Validation status: `COMPLETE_VERIFIED`
Production credentials, production data, tenant mutation, destructive migration or real-money operation: N/A

## Implemented Contract

- Public search accepts `HOTEL`, `RESORT`, `APARTMENT`, `VILLA`, `HOMESTAY`, `MOTEL`, `GUEST_HOUSE` and `HOSTEL`; values are trimmed, uppercased and deduplicated. Unknown types are rejected instead of widening results.
- Star filters accept only integer values from 1 through 5 and deduplicate repeated values. Invalid values return HTTP 400.
- `minReviewScore` accepts finite values in the inclusive range 0 through 10. A property is reviewed only with a positive review count and non-null average; a stored score of zero with a positive count remains reviewed.
- Applying `minReviewScore`, including zero, excludes null/zero-count unrated properties. Search projection and result cards use the same reviewed/unrated rule.
- Route state canonicalizes type/star/review filters, renders one removable chip per value, keeps the active count aligned and resets `pageNumber` to 1 after apply, chip removal, clear or sort changes.
- The mobile drawer opens with focus on close, contains Tab/Shift+Tab, closes on Escape and restores focus to its trigger.

## Focused Validation

```powershell
Set-Location backend
.\mvnw.cmd "-Dtest=PropertySearchControllerIntegrationTest" -DforkCount=0 test

Set-Location ../frontend
npm test -- --watch=false `
  --include=src/app/features/property-search/pages/property-search-page/property-search-query.spec.ts `
  --include=src/app/features/property-search/pages/property-search-page/property-search-page.spec.ts `
  --include=src/app/features/property-search/components/search-filter-sidebar/search-filter-sidebar.spec.ts `
  --include=src/app/features/property-search/components/property-result-card/property-result-card.spec.ts
npm run build -- --configuration development

# The final browser rerun used already-listening local backend/frontend processes.
$env:PLAYWRIGHT_EXTERNAL_BACKEND='true'
$env:PLAYWRIGHT_API_ONLY='true'
npx playwright test --config playwright.api.config.ts --grep "property filters|desktop filters|mobile filter drawer"
```

Results:

- Backend `PropertySearchControllerIntegrationTest`: 14/14 PASS. Coverage includes exact results, normalization/deduplication, invalid values, positive-count/null-score handling, reviewed score zero and unrated exclusion at threshold zero.
- Frontend focused query/sidebar/card/page tests: 16/16 PASS; the final page-focused rerun after the focus-race fix passed 4/4. Coverage includes route hydration, canonical emission, zero-score display, per-value chips/count, page reset and mobile focus lifecycle.
- Angular development build: PASS, exit code 0. The two temporary parallel-i18n overlay files used only to compile this branch were removed before handoff.
- API-backed Playwright: 3/3 PASS against the real E2E backend. `RESORT` + 5 stars + score 8 returns only `demo-dn-03`; unrated `demo-hn-01` remains visible without a threshold, is excluded at threshold zero, and a reviewed property remains visible at threshold zero.
- The desktop journey verifies canonical URL values, reset from page 3 to page 1, exact result content, three chips/count and per-type chip removal with another page reset.
- The mobile journey verifies close-button focus, reverse/forward focus wrapping, Escape dismissal and trigger-focus restoration. The first run exposed focus scheduling before the `*ngIf` view existed; scheduling was corrected and both the focused rerun and final 3/3 run passed.

## Permissions And Isolation

- These filters are anonymous public reads and do not accept caller-controlled tenant authority or mutation scope.
- Every query remains inside the canonical approved/active/profile-aware demo eligibility predicate from T274; filters only narrow eligible inventory.
- E2E uses deterministic demo data and test-only secrets. No production credential, production data or financial action is used.

## Schema And Recovery

No schema or migration change exists. Rollback is the eventual T276 task commit revert; no data conversion or destructive cleanup is required.

## Remaining Boundaries

- T277 owns amenity and booking-policy filters/badges.
- T278 owns minimum/maximum price predicate correctness.
- T279 owns the complete sort and pagination matrix beyond the page-reset regressions verified here.
- T280-T281 own availability and capacity semantics.
- T294 owns nationwide search projection and performance optimization.
