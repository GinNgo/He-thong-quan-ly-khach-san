# T275 Legacy Public Search Compatibility Evidence

Date: 2026-08-04
Branch: `codex/public-booking`
Base: `origin/codex/ui-functional-audit-polish` at `88f7da5`
Capability: `PUB-007`
Validation status: `COMPLETE_VERIFIED`
Production credentials, production data, tenant mutation, destructive migration or real-money operation: N/A

## Compatibility Contract

- Reachable legacy `GET /api/v1/hotels/public/search` remains available for compatible clients, but now delegates to `PropertySearchService.searchProperties(PropertySearchRequestDTO)` instead of calling the legacy hotel and room-type services.
- The endpoint returns `Page<PropertySearchResponseDTO>`, matching the canonical public-search response envelope and preventing serialization of `Hotel` entity internals such as approval state, operation state or owner relationships.
- Legacy `city` retains the old case-insensitive address-line substring meaning through an internal canonical-service filter; it does not broaden into property name/code/slug/province/ward keyword matching. SQL `LIKE` metacharacters are escaped as literals, and the canonical controller disallows binding the internal field. `provinceId`, `wardId`, `checkIn`, `checkOut` and `guests` map to `provinceId`, `wardId`, `checkInDate`, `checkOutDate` and `adultCount` respectively.
- `pageNumber` and `pageSize` are accepted and delegated so the canonical paged response remains navigable beyond its first page. Canonical clamping remains unchanged and its complete pagination matrix remains T279.
- Obsolete `districtId` is rejected with HTTP 400 and directs callers to `provinceId` plus `wardId`; nonpositive `guests` is rejected with HTTP 400 instead of becoming an ambiguous capacity request.
- Eligibility, date validation, availability, price projection and pagination are delegated to the canonical search service. The legacy route therefore inherits the approved/operational/profile-aware demo policy established by T274 rather than maintaining a divergent `status=ACTIVE` entity query.
- Responses are explicitly uncached with `Cache-Control: no-store` and carry `X-LuxeStay-Freshness: LIVE_SEARCH`.

## Focused Validation

```powershell
Set-Location backend
.\mvnw.cmd "-Dtest=HotelControllerIntegrationTest,PropertySearchControllerIntegrationTest" -DforkCount=0 test

Set-Location ../frontend
$env:PLAYWRIGHT_EXTERNAL_BACKEND='true'
$env:PLAYWRIGHT_API_ONLY='true'
npx playwright test --config playwright.api.config.ts --grep "legacy search"
```

Results:

- Backend focused validation: 17/17 PASS, zero failures and zero errors (`HotelControllerIntegrationTest` 6/6 plus `PropertySearchControllerIntegrationTest` 11/11 canonical-query regression).
- Parameter-delegation coverage verifies all supported legacy fields, including page number/size, map to the expected canonical DTO fields, with child and room counts left unset rather than inferred.
- Response-contract coverage verifies the canonical paged DTO, `no-store`/freshness headers and absence of entity-only approval, operation and owner fields.
- Service-boundary coverage verifies legacy `HotelManagementService.searchHotels()` and `RoomTypeService` are not used by the public search path.
- Rejection coverage verifies `districtId` and `guests=0` return truthful `INVALID_REQUEST` HTTP 400 responses without invoking any search service.
- Real-backend API-only Playwright: 3/3 PASS. The journey compares the legacy response to canonical search, proves address-only `city` semantics (a property code and `%/_` wildcard probes do not broaden results), verifies the internal filter is rejected on the canonical endpoint, exercises page-two access and the paged DTO/entity-field minimization, confirms approved demo visibility plus draft/suspended exclusion, checks `no-store`/`LIVE_SEARCH`, and rejects obsolete `districtId` plus `guests=0/-1` with truthful HTTP 400 responses.
- Playwright discovery lists all 12 API-backed public-booking tests, including the three T275 cases. `PLAYWRIGHT_API_ONLY=true` skips the unnecessary Angular server because this legacy route has no current UI consumer.

## Permissions And Isolation

- The endpoint is an anonymous public read and requires no customer, staff or property role.
- Caller-controlled tenant authority and tenant mutation are N/A. The route cannot select a tenant mutation scope and only reads inventory allowed by the canonical public eligibility predicate.
- Tests use mocked service boundaries in an isolated MVC context. Production credentials, production data and real-money operations are not used.

## Schema And Recovery

No schema or migration change exists. Rollback is the eventual T275 task commit revert; there is no data conversion or destructive cleanup.

## Remaining Boundaries

- T276-T281 retain ownership of full filter, price, sort/pagination, availability and capacity semantics inside canonical search.
- T282 owns search/detail quote consistency, and T283 owns public property-detail eligibility.
- T294 owns search projection and nationwide performance optimization.
- The legacy route intentionally does not invent `districtId`, child-count or room-count compatibility semantics that were not present in its public contract.
