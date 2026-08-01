# T099 Subscription Policy Blockers

## Scope

- Added a read-only policy boundary for subscription downgrade and proration requests.
- Enforced authenticated property access before returning policy details.
- Returned stable `POLICY_NOT_CONFIGURED` errors with machine-readable current-state markers.
- Exposed truthful policy availability for the future Platform Billing API and UI in T100/T106.

## Enforced Invariants

- No downgrade order is created until a versioned downgrade/credit policy is approved.
- No automatic proration or client-supplied credit amount is accepted.
- Unauthorized property access keeps its tenant denial and does not leak policy details.
- The blocker service has no repository or order-writer dependency, so blocked requests cannot mutate orders, contracts, entitlements, history or ledger rows.
- Approved full-price upgrade policy `FULL_PRICE_PRESERVE_REMAINING_TERM_V1` remains separate and does not imply a general proration policy.

## Automated Validation

Commands from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=SubscriptionPolicyServiceTest' -DforkCount=0 test
.\mvnw.cmd '-Dtest=PlatformBillingModelTest,SubscriptionOrderServiceTest,PlatformPaymentConfigurationServiceTest,PlatformPaymentAttemptServiceTest,PlatformPaymentCallbackServiceTest,SubscriptionApplicationServiceTest,SubscriptionRenewalServiceTest,SubscriptionUpgradeServiceTest,SubscriptionPolicyServiceTest' -DforkCount=0 test
$env:JWT_SECRET='feature007-platform-billing-test-key-at-least-32-bytes'
.\mvnw.cmd '-Dtest=BackendApplicationTests' -DforkCount=0 test
```

Result on 2026-08-01:

- `SubscriptionPolicyServiceTest`: 4 passed, 0 failed, 0 errors, 0 skipped.
- Platform Billing focused tests: 33 passed, 0 failed, 0 errors, 0 skipped.
- Coverage includes downgrade blocking, upgrade/downgrade proration blocking, truthful availability and tenant-denial precedence.
- Spring context: 1 passed; 66 JPA repository interfaces discovered.
- Compile and test builds: SUCCESS.
- The JWT value was an ephemeral test-only key and was not persisted.

## Permissions and Environment

- `PropertyAccessService` resolves tenant access before the blocker response; endpoint permission mapping remains T100.
- No provider adapter, merchant configuration, production credential, network call, database mutation or real-money operation is used.

## Schema and Recovery

- New migration: N/A. The blocker is application policy and writes no data.
- Forward recovery: after an owner-approved versioned policy exists, replace the blocker with a separately tested order/application implementation while keeping old blocked responses compatible.
- Rollback: remove endpoint wiring to the blocker; no financial data recovery is required because T099 creates no financial records.
