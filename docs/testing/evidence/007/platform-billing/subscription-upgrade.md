# T098 Approved Subscription Upgrade

## Scope

- Added backend validation for a strictly higher active catalog plan and current entitlement ownership.
- Added current-usage checks for properties, room types, rooms, images and staff before creating an upgrade order.
- Applied the approved full-price upgrade policy through one new contract, entitlement update and `UPGRADED` history event.
- Connected verified upgrade callbacks to the upgrade application boundary.

## Approved Policy

- Policy version: `FULL_PRICE_PRESERVE_REMAINING_TERM_V1`.
- The target plan activates immediately after authoritative payment.
- The full snapshotted target catalog price is charged; there is no credit or proration.
- The current remaining term is preserved and the target duration is added after `max(current expiry, payment time)`.
- A target lifetime plan produces a lifetime contract; a lifetime contract cannot move to a finite plan.
- Target limits cannot reduce current limits, must improve at least one limit and must support current usage.

## Automated Validation

Commands from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=SubscriptionUpgradeServiceTest' -DforkCount=0 test
.\mvnw.cmd '-Dtest=PlatformBillingModelTest,SubscriptionOrderServiceTest,PlatformPaymentConfigurationServiceTest,PlatformPaymentAttemptServiceTest,PlatformPaymentCallbackServiceTest,SubscriptionApplicationServiceTest,SubscriptionRenewalServiceTest,SubscriptionUpgradeServiceTest' -DforkCount=0 test
$env:JWT_SECRET='feature007-platform-billing-test-key-at-least-32-bytes'
.\mvnw.cmd '-Dtest=BackendApplicationTests' -DforkCount=0 test
```

Result on 2026-08-01:

- Upgrade and callback-focused tests: 9 passed, 0 failed, 0 errors, 0 skipped.
- Platform Billing focused tests: 29 passed, 0 failed, 0 errors, 0 skipped.
- Upgrade coverage includes strict feature progression, current-usage rejection, full-price order delegation, immediate activation, preserved remaining term, contract supersession, equivalent replay and verified callback routing.
- Spring context: 1 passed; 66 JPA repository interfaces discovered, including the usage repository queries.
- Compile and test builds: SUCCESS.
- The JWT value was an ephemeral test-only key and was not persisted.

## Permissions and Environment

- Property access is resolved through `PropertyAccessService`; endpoint permission mapping remains T100.
- Application occurs only after T095 provider verification and immutable Platform Billing transaction creation.
- Tests use mocked repositories, synthetic simulator evidence and H2 context startup only.
- No production credential, provider network call, production database or real-money operation is used.

## Schema and Recovery

- New migration: N/A. Upgrade uses the additive V24/V25 order, transaction, contract, entitlement and history tables.
- Forward recovery: disable upgrade order/callback intake, preserve order and payment evidence, deploy the corrected policy implementation and replay the verified callback.
- Rollback must not delete or rewrite contracts, ledger rows or history; corrective entitlement changes require a later additive lifecycle transition.
