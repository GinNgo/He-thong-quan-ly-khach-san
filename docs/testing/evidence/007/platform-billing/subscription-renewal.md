# T097 Subscription Renewal Order and Application

## Scope

- Added renewal-order creation for the current active entitlement plan through the backend catalog snapshot pipeline.
- Added one-open-lifecycle-order protection while preserving owner-scoped idempotent replay.
- Added paid-renewal application that creates a new contract version, supersedes the previous contract, updates the entitlement and appends `RENEWED` history.
- Connected verified renewal callbacks to the renewal application boundary.

## Enforced Invariants

- Only an active, non-lifetime entitlement and contract can create a renewal order.
- Renewal price, features, billing period and duration come from the locked backend catalog; the client cannot submit them.
- Monthly orders extend by months, yearly orders by years and day-based snapshots by days; no one-year duration is hard-coded.
- Lifetime conversion through renewal is blocked before payment with `POLICY_NOT_CONFIGURED`.
- A renewal must retain the current plan; plan changes require the T098 upgrade workflow.
- The successful renewal transaction must match the locked order, amount, VND currency and successful attempt.
- Equivalent callback replay returns the stored contract/history without extending duration or creating revenue twice.

## Automated Validation

Commands from `backend/`:

```powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd '-Dtest=PlatformBillingModelTest,SubscriptionOrderServiceTest,PlatformPaymentConfigurationServiceTest,PlatformPaymentAttemptServiceTest,PlatformPaymentCallbackServiceTest,SubscriptionApplicationServiceTest,SubscriptionRenewalServiceTest' -DforkCount=0 test
$env:JWT_SECRET='feature007-platform-billing-test-key-at-least-32-bytes'
.\mvnw.cmd '-Dtest=BackendApplicationTests' -DforkCount=0 test
```

Result on 2026-08-01:

- Platform Billing focused tests: 24 passed, 0 failed, 0 errors, 0 skipped.
- Renewal coverage includes monthly catalog terms, lifetime denial, current-plan order creation, two-month snapshot extension, contract supersession, callback routing and equivalent replay.
- Spring context: 1 passed; 65 JPA repository interfaces discovered.
- Compile and test builds: SUCCESS.
- The JWT value was an ephemeral test-only key and was not persisted.

## Permissions and Environment

- Property access is resolved by `PropertyAccessService`; endpoint permission mapping remains T100.
- Callback application is reached only after T095 provider verification and immutable platform transaction creation.
- Tests use synthetic simulator evidence and H2 context startup only; no production credentials, external provider call or real money was used.

## Schema and Recovery

- New migration: N/A. Renewal uses the additive V24/V25 order, transaction, contract, entitlement and history tables.
- Forward recovery: disable new renewal order/callback intake, preserve paid order and transaction evidence, deploy a corrected renewal service and replay the verified callback.
- Existing contracts and history must not be deleted during rollback; a later additive lifecycle transition is required for corrections.
