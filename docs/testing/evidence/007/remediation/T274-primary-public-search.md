# T274 Primary Public Search And Eligibility Evidence

Date: 2026-08-04
Branch: `codex/public-booking`
Base: `origin/codex/ui-functional-audit-polish` at `88f7da5`
Capability: `PUB-006`
Validation status: `COMPLETE_VERIFIED`
Production credentials, production data, tenant mutation, destructive migration or real-money operation: N/A

## Eligibility Contract

- Canonical search now derives its property predicate from the same `PublicInventoryEligibilityPolicy` already used by public room-type and locked booking checks. Public property-detail eligibility remains T283/PUB-015.
- Search returns only `approval_status=APPROVED` and `operation_status=ACTIVE` properties.
- Production hides `is_demo=true` inventory unless `app.demo-data.allow-public-demo=true`; test and E2E profiles can expose deterministic demo inventory.
- Search still requires an active room type with sufficient basic capacity and physical inventory. The complete room-state/reservation consistency matrix remains T280/PUB-012, while mixed adult/child capacity remains T281/PUB-013.
- Unknown query parameters are rejected with HTTP 400 instead of being silently ignored. The Angular search page constructs an explicit API allowlist so UI-only `displayLocation`, `propertyId`, `_retry` and arbitrary route state never reach the endpoint.

## Accepted Query Matrix

| Query parameter | Frontend and backend contract | T274 evidence | Follow-on boundary |
|---|---|---|---|
| `keyword` | Trimmed by the Angular route adapter; backend normalizes Vietnamese text and searches property name/address, province/ward, code and slug. | Binding test plus eligibility and seeded browser keyword searches. | Ranking optimization remains T294. |
| `provinceId` | Current or legacy province IDs expand to the same 34-province compatibility scope. | HTTP current/legacy parity and API-backed Home journeys. | Catalog integrity remains preserved from T272. |
| `wardId` | Exact ward filter, optionally combined with its compatible province scope. | Exact binding plus existing API-backed ward search. | No broader ward remapping is introduced. |
| `landmarkId` | Active coordinate-backed landmark is authoritative; it supplies coordinates/default radius and must match the selected province. | Binding plus existing landmark HTTP/browser coverage. | Full landmark catalog behavior remains T272. |
| `checkInDate`, `checkOutDate` | Both absent or both strict ISO `yyyy-MM-dd`; check-in cannot be in the past and check-out must be later. | HTTP rejects malformed, one-sided, equal, inverted and past ranges; UI shows the authoritative 400 as a non-retry validation state. | Search/detail quote consistency remains T282. |
| `adultCount`, `childCount`, `roomCount` | Bound and applied to basic active-room-type capacity; UI emits numeric values. | Exact binding and existing valid stay-pricing request. | Complete boundary/null legacy capacity matrix remains T281. |
| `latitude`, `longitude`, `radiusKm` | Latitude/longitude must be supplied together and finite/in range; radius requires coordinates and must be in `(0, 50]`. | Exact binding plus coordinate/radius rejection tests. | Nearest-sort matrix remains T279. |
| `propertyTypes`, `starRatings`, `minReviewScore` | Comma-separated frontend wire format binds to typed lists/scalar and reaches existing predicates. | Controller binding and Angular serializer tests. | Result semantics, zero/null ratings, chips and mobile filter behavior remain T276. |
| `minPrice`, `maxPrice` | Typed numeric bounds bind and reach the current search predicates. | Controller binding and Angular serializer tests. | Same-room-type price correctness remains T278. |
| `sortBy` | The UI emits `POPULAR`, `NEAREST`, `PRICE_ASC`, `PRICE_DESC` or `RATING`. The backend currently binds any string and falls back to popular ordering for unknown values; T274 proves binding only. | Controller binding and API allowlist browser request. | Strict value validation, stable ties and page transitions remain T279. |
| `pageNumber`, `pageSize` | One-based page number and size bind; backend clamps page to 1+ and size to 1..100. | Controller binding and API allowlist browser request. | Count parity and out-of-range behavior remain T279. |
| `stayType` | Missing/blank or `OVERNIGHT` is accepted for backward compatibility; `DAY_USE` and other values return HTTP 400. Home continues to omit it by default. | HTTP acceptance/rejection plus Angular allowlist/browser request. | Any future day-use policy requires the T270 stop-gate contract. |
| `amenityIds` | Recognized but non-empty values return HTTP 400 because canonical amenity filtering is not implemented. | HTTP rejection and typed Angular serialization. | Implementation remains T277/PUB-009. |
| `freeCancellation`, `payAtProperty`, `breakfastIncluded` | Missing, null or `false` is accepted as no filter; `true` returns HTTP 400 instead of a false-success no-op. | HTTP acceptance/rejection and Angular serializer tests. | Policy models and real predicates remain T277/PUB-009. |
| Any other parameter | Rejected with HTTP 400. `displayLocation`, `propertyId`, `_retry` and arbitrary route state are intentionally UI-only and removed before the API call. | Controller HTTP rejection, Angular route-adapter unit and API-backed browser request inspection. | N/A. |

## Focused Validation

```powershell
Set-Location backend
.\mvnw.cmd "-Dtest=PublicInventoryEligibilityPolicyTest,PropertySearchControllerBindingTest,PropertySearchControllerIntegrationTest" -DforkCount=0 test

Set-Location ../frontend
npm test -- --watch=false `
  --include=src/app/core/services/client-api.service.spec.ts `
  --include=src/app/features/property-search/pages/property-search-page/property-search-query.spec.ts
npx ng build --configuration development

$env:PLAYWRIGHT_EXTERNAL_BACKEND='true'
npx playwright test --config playwright.api.config.ts --grep "canonical public search allowlist|invalid-date 400|province aliases equivalent"
```

Results:

- Backend focused validation: 16/16 PASS (`PropertySearchControllerBindingTest` 1/1, `PublicInventoryEligibilityPolicyTest` 4/4 and `PropertySearchControllerIntegrationTest` 11/11), with zero failures and zero errors in Surefire reports.
- Angular query-adapter and API serializer validation: 2 files, 11/11 tests PASS.
- Angular development build: PASS; application bundle generated in 49.221 seconds.
- API-backed Playwright: 3/3 PASS against the real Angular application and Spring/H2 E2E API. The journeys verify the canonical allowlist/no `displayLocation` leak, truthful invalid-date HTTP 400 presentation, current/Tien Giang alias parity, visible approved demo inventory and exclusion of deterministic draft/suspended sentinels.
- Backend validation used a temporary compiler overlay excluding only unrelated `UserService`, `UserController` and `PlatformBillingController` blockers. Frontend validation used the parallel i18n services as a temporary integration overlay. Both overlays were removed before handoff; `backend/pom.xml` and the temporary i18n paths are clean.

## Permissions And Isolation

- The endpoint is an anonymous public read. No customer, staff or property role is required.
- Caller-controlled tenant authority is N/A: the request filters public inventory and performs no tenant-owned mutation.
- Test data uses isolated H2 contexts and deterministic E2E demo fixtures. Production credentials and production data are not used.

## Schema And Recovery

No schema or migration change exists. Rollback is the eventual T274 task commit revert. The demo visibility switch remains environment-driven and defaults to hiding demo inventory in production.

## Remaining Boundaries

- T276 owns complete property-type/star/review filter semantics and mobile/filter-state regression.
- T277 owns amenity and booking-policy models rather than silent placeholder filters.
- T278 owns same-room-type price-bound correctness.
- T279 owns strict sort validation, deterministic ties and one-based pagination/count parity.
- T280 and T281 own authoritative room-state/availability and capacity consistency.
- T283 owns the separate legacy public property-detail eligibility path.
- T294 owns search projection/N+1 and nationwide performance work.
