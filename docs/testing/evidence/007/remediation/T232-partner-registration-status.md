# T232 Partner Registration Status

Date: 2026-08-04

## Scope

- Replaces the account-wide NONE/PENDING/APPROVED guess with a typed per-property response.
- Returns `overallStatus`, `propertyCount` and every distinct owned property with canonical state, raw approval/operation/ownership states and an optional rejection reason.
- Supports DRAFT, PENDING, APPROVED, REJECTED, SUSPENDED, CANCELLED and mixed multi-property accounts.
- Rebuilds the status page as responsive property cards with loading, error, retry and truthful empty states.
- Shows the management action only when canonical status is APPROVED and both operation and ownership are ACTIVE.

## Authorization and isolation

- The controller resolves the account strictly from `CustomUserDetails.userId`; anonymous and generic principals are denied.
- Only OWNER mappings for the authenticated user are loaded, with their properties fetched in one query.
- Duplicate legacy owner mappings collapse by property id, preferring the newest row until T240 adds database invariants.
- Rejection reasons use the mapping reason first, then only a rejected claim matching the same requester user id and property id.
- Claim property/requester relations are bulk-fetched and rechecked in the service, preventing N+1 reads and cross-account reason leakage.

## Canonical classification

- REJECTED and SUSPENDED take precedence over non-operational combinations.
- Explicit cancelled/closed or inactive ownership becomes CANCELLED.
- APPROVED requires approval, operation and ownership to be authoritative and active together.
- Pending ownership/review stays PENDING, draft approval stays DRAFT and inconsistent legacy combinations fail closed as CANCELLED.

## Verification

1. Backend isolated focused harness:

   - `PropertyRegistrationControllerTest`: 10 passed.
   - `PropertyRegistrationServiceTest`: 5 passed.
   - `PropertyRegistrationRollbackIntegrationTest`: 2 passed.
   - `PropertyRegistrationStatusServiceTest`: 16 passed.

   Result: 33 tests passed, 0 failed. Coverage includes authoritative principal isolation, empty/single/mixed status, all canonical states, duplicate mapping collapse, safe rejection reasons and T231 registration/conversion rollback regression.

2. Frontend focused Angular harness:

   - `partner-registration-status.component.spec.ts`: 5 passed.
   - `partner-registration-status.service.spec.ts`: 1 passed.

   Result: 6 tests passed, 0 failed. Coverage includes mixed property cards, rejection reason visibility, suspended/cancelled guidance, empty state, retry and exact management gating.

3. Repository check:

   `git diff --check`

   Result: passed.

## Baseline build constraint

The normal Maven lifecycle remains blocked by the unrelated base gap where `PlatformBillingController` references absent `SubscriptionPlanDTO` and `SubscriptionCatalogService`. The backend harness used target-only compatibility stubs, compiled the production and focused test slices, ran all 33 tests and cleaned generated artifacts. The normal Angular builder remains blocked by unrelated missing base i18n services; the focused Angular harness executed only the T232 component and service tests.

## Rollback

The change is schema-neutral. Reverting the task commit restores the old coarse response and page. No persisted state needs reversal.
