# T093 Backend-Owned Subscription Order Creation

## Scope

- Added a Platform Billing catalog repository that pessimistically reads the selected plan and its feature limits.
- Added purchase-order creation that derives owner/property access from the authenticated backend context.
- Snapshotted plan identity, content version, price, VND currency, billing period, duration and sorted feature limits.
- Added owner-scoped persisted idempotency and a configurable 30-minute default order expiry.

## Enforced Invariants

- The request contains only target property, plan ID and idempotency key; it cannot submit price, duration or features.
- Only active, positive-price catalog plans with an approved duration mapping can create a billing order.
- Equivalent retries return the original order snapshot without rereading a changed catalog.
- Reusing an idempotency key for a different property/plan payload returns `IDEMPOTENCY_KEY_REUSED`.
- The authenticated owner row is locked before order lookup/creation to serialize concurrent owner-scoped retries.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=SubscriptionOrderServiceTest,PlatformBillingModelTest' -DforkCount=0 test
$env:JWT_SECRET='feature007-platform-billing-test-key-at-least-32-bytes'
.\mvnw.cmd '-Dtest=BackendApplicationTests' -DforkCount=0 test
```

Result on 2026-08-01:

- Focused tests run: 6
- Failures: 0
- Errors: 0
- Skipped: 0
- Build: SUCCESS
- Spring context test: 1 passed; 65 JPA repository interfaces discovered.
- The JWT value was an ephemeral test-only key required by the existing test profile and was not persisted.

Coverage includes backend snapshot creation, exact expiry, deterministic feature snapshot, equivalent replay and conflicting-key rejection.

## Permissions and Environment

- Property access is resolved by `PropertyAccessService`; no caller-owned owner ID is accepted.
- Controller permission mapping is deferred to T100.
- No provider, merchant credential, callback, production database or real-money operation is used.

## Schema and Recovery

- New migration: N/A. Orders persist to the additive V24 table.
- The expiry duration is fail-fast validated between 1 and 1440 minutes and defaults to 30 minutes.
- Forward recovery: disable order endpoints and retain existing immutable snapshots; adjust catalog mapping in a later code deployment.
