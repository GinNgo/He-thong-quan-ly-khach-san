# T277 Public Offer Policy Stop Gate

Date: 2026-08-04
Branch: `codex/public-booking`
Capability: `PUB-009`
Validation status: `BLOCKED_POLICY_AND_PARALLEL_SCOPE`

## Stop-Gate Finding

T277 cannot be truthfully completed without an approved cancellation and refund policy. The current cancellation path refunds successful payments for every eligible cancellation; there is no authoritative deadline, fee, refund percentage, no-show rule or immutable cancellation-policy snapshot. Advertising or filtering on `freeCancellation=true` would therefore make a financial promise that the server cannot enforce.

No cancellation/refund behavior was changed and no policy value was invented.

## Parallel Scope Finding

The amenity portion is already owned and implemented by T255 on `codex/property-operations` at commit `69d16c0` (`feat(T255): add amenity catalog and assignments`). That commit owns the localized catalog, property/room-type assignments, tenant-safe management, indexed all-selected public filtering and public badges. Reimplementing those models or migrations on this branch would duplicate an active parallel scope and create migration/integration conflicts.

The property-operations branch also has later uncommitted operational-policy work. This branch did not read it as authority, copy it, modify it or merge it.

## Additional Unapproved Semantics

- A priced `SVC_BREAKFAST` hotel service does not prove that breakfast is included in a public room offer. Inclusion scope by property, room type, rate and date is unspecified.
- Property payment configuration supports local methods, but it does not define the public `PAY_AT_HOTEL` promise or online-provider independence. T288 owns that readiness and booking-method contract.
- T298 owns cancellation terms and refund-policy snapshot behavior. T277 must consume an approved versioned policy rather than define one independently.

## Read-Only Evidence

```powershell
git -C C:\Users\ngovo\.codex\worktrees\luxestay-property-operations log -5 --oneline
rg -n "T255|PROP-OPS-014|amenit" C:\Users\ngovo\.codex\worktrees\luxestay-property-operations\docs -g "*.md"
rg -n "cancelLockedReservation|refundSuccessfulPayments|freeCancellation|breakfastIncluded|payAtProperty" backend/src/main/java frontend/src/app
```

Observed results:

- T255 commit `69d16c0` and its executable evidence are present on the property-operations branch.
- Current public-search request/response fields exist, but unsupported policy filters are rejected and response flags are not backed by a versioned offer-policy model.
- Current reservation cancellation invokes successful-payment refund handling without a public-offer cancellation eligibility engine.

## Required Owner Decisions

Before T277 can resume, approve and version at least:

1. Free-cancellation deadline basis and timezone.
2. Refund amount/fee rules before and after the deadline, including pending and completed payments.
3. No-show and same-day behavior.
4. Breakfast-included assignment scope and snapshot rules.
5. Pay-at-property eligibility, supported local methods and independence from online-provider readiness.
6. Convergence order for T255 amenity artifacts before public-booking policy integration.

## Safe Resume Contract

After approval and convergence, T277 should filter and badge the same authoritative room-type offer later selected for detail/quote/booking, pass a server-owned policy-version token, and snapshot that exact version on the reservation detail. Tenant-owned assignments must derive property authority on the server and reject cross-property IDs before mutation.

## Schema And Recovery

No schema, data or source change was made for T277. Rollback is N/A. No production credential, production data, real-money action or destructive migration was used.
