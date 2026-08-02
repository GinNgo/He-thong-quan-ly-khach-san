# T198 - Per-property plan limits and feature gates

Date: 2026-08-02
Task: `PROP-SUB-021`

## Implemented

- `SubscriptionFeatureService` now exposes property-scoped feature and quota checks backed by the locked platform entitlement read model.
- Room, room-type, bulk-room and image quotas use the target property's counts and `hotelId`; they no longer sum every property assigned to the user.
- Management context resolves plan/status/limits and usage for the selected property and exposes the entitlement source.
- Legacy `MAX_PROPERTIES` remains account-level only for draft property creation/claim, where no target property entitlement exists yet.
- Read-only `GET /api/hotels/my-hotels` is no longer blocked by a subscription feature annotation.

## Validation

| Layer | Command | Result |
|---|---|---|
| Backend property/gate suite | `./mvnw.cmd -q "-Dtest=SubscriptionFeatureServiceTest,PropertySubscriptionEntitlementServiceTest,RoomServiceImplTest,RoomTypeServiceImplTest,ManagementPortalServiceTest" test` | PASS (22/22) |
| Frontend management dashboard | `npm test -- --watch=false --include=src/app/features/management/dashboard/management-dashboard.component.spec.ts` | PASS (2/2) |
| Backend compile | `./mvnw.cmd -q -DskipTests compile` | PASS |

## Isolation evidence

- A basic property at room limit is rejected while a premium property in the same user session is accepted.
- Parallel property checks resolve independent locked property entitlements and do not call the legacy user-wide repository.
- Mutation paths call `findByTargetHotelIdForUpdate` before evaluating capacity; concurrent writes therefore serialize on the target entitlement row when one exists.
