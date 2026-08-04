# T242 Legacy Subscription Compatibility Evidence

## Result

- Status: `COMPLETE_VERIFIED`
- Code commit: `d403540`
- Scope: PROP-SUB-019 / FR-044 / SC-013

## Implemented Contract

- `/api/subscriptions/plans` returns explicit active catalog DTOs and never serializes JPA plan/feature graphs.
- Compatibility `/me`, `/me/features` and `/me/usage` routes require `targetHotelId`, enforce assigned-property access without hiding historical billing data for suspended properties, and return only privacy-safe DTOs.
- Current state resolves through `PropertySubscriptionEntitlementService`: platform rows take precedence, terminal platform state never falls back, and only an unambiguous `LEGACY_PROJECTION` may bridge.
- Internal legacy subscription IDs, provider references, user graphs and raw feature payloads are not exposed.
- Usage delegates to `PlatformSubscriptionUsageRepository`, the same calculator used by upgrade validation, preventing read/enforcement drift.
- Frontend consumers use selected-property context only. Feature guards fail closed for no property, unavailable state, missing feature, zero limit or invalid negative limit; only `-1` or a positive limit grants access.

## Verification

- Backend production compile: PASS.
- Backend focused tests: 18/18 PASS.
  - Subscription controller MVC: 6.
  - Compatibility catalog/current/features/usage service: 7.
  - Platform-first entitlement bridge: 5.
- Frontend focused tests: 15/15 PASS across subscription service, feature guard, admin catalog and management billing regression.
- Independent full-stack review: no blocking finding.
- `git diff --check`: PASS.
- `backend/target` and temporary frontend i18n stubs removed.

## Baseline Qualification

The ordinary repository-wide backend `testCompile` still encounters unrelated missing refund/chat/WebSocket classes from the integration base. T242 production compile and its isolated focused source/test harness pass; no compatibility stubs were committed.

## Coordinator Handoff

- Mark T242 complete in `specs/007-payment-billing-completion/tasks.md`.
- Merge PROP-SUB-019 evidence into both shared aggregate inventories.
- Keep plan administration/versioning in T244 and authoritative expiry/revoke/history in T243.
