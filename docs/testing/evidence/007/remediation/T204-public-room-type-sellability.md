# T204 - Public Room-Type Sellability

## Scope

Remediation of PUB-016: public room-type listing must not expose inventory for an unavailable property or an inactive room type, and booking must re-check sellability after taking the pessimistic room-type lock.

## Implementation

- Added `PublicInventoryEligibilityPolicy` as the shared public eligibility rule: property approval/operation state, production demo visibility and room-type `ACTIVE` status.
- Applied the policy to both the canonical `/api/public/properties/{hotelId}/room-types` endpoint and the existing `/api/room-types/public/hotel/{hotelId}` compatibility route.
- Applied the same policy immediately after `RoomTypeRepository.findByIdForUpdate(...)` in `ReservationService`, so a room type deactivated before the booking lock is acquired cannot be sold from a stale detail URL.
- Added a 404 recovery path in the public hotel-detail component when the room catalog becomes unavailable.
- Updated the test data initializer so T203 tenant-owned service rows are seeded with their property; this is required for Spring integration contexts after the V39 ownership constraint.

## Verification

| Layer | Command / fixture | Result |
|---|---|---|
| Backend unit/service | `./mvnw.cmd "-Dtest=PublicInventoryEligibilityPolicyTest,RoomTypeServiceImplTest,ReservationServiceTest" test` | 19/19 passed |
| Backend HTTP | `PublicDiscoveryControllerIntegrationTest` with approved property, active/inactive room types, suspended-property stale URL and both routes | 7/7 passed |
| Backend concurrency | `ReservationConcurrencyIntegrationTest` with a deactivation transaction holding `findByIdForUpdate` before booking | 2/2 passed |
| Frontend | `npm test -- --watch=false --include=src/app/features/client/hotel-detail/hotel-detail.component.spec.ts` | 3/3 passed |
| Migration | N/A; no schema change in T204 | N/A |
| Real provider/browser | N/A; no external provider or production inventory mutation used | N/A |

## Residual Scope

T283/PUB-015 still owns the separate public property-detail endpoint eligibility contract. T205/PUB-023 still owns persisted booking idempotency and ambiguous retry handling. Production demo visibility remains configuration-controlled and production payment remains disabled.
