# T096 Idempotent Subscription Application

## Scope

- Added a locked application service that accepts only a paid Platform Billing purchase order and its matching successful transaction.
- Creates one immutable software contract from the snapshotted plan, price, duration and feature evidence.
- Creates or repairs the current entitlement and appends one `PURCHASED` history row before moving the order to `APPLIED`.
- Connected verified purchase callbacks to the application service; renewal and upgrade effects remain isolated for T097 and T098.

## Enforced Invariants

- A failed, cancelled, expired, unpaid or non-purchase order cannot activate an entitlement.
- The transaction must belong to the locked order, be a purchase debit for the exact VND order price and originate from a successful attempt.
- Contract dates use the snapshotted `durationValue` and `durationUnit`; no one-year duration is hard-coded.
- An existing active contract blocks a second purchase until an approved renewal or upgrade policy is used.
- Equivalent callback replay returns the stored contract, entitlement and history without creating a duplicate effect.
- Incomplete or conflicting stored evidence fails closed with a stable financial error.

## Automated Validation

Commands from `backend/`:

```powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd '-Dtest=PlatformBillingModelTest,SubscriptionOrderServiceTest,PlatformPaymentConfigurationServiceTest,PlatformPaymentAttemptServiceTest,PlatformPaymentCallbackServiceTest,SubscriptionApplicationServiceTest' -DforkCount=0 test
$env:JWT_SECRET='feature007-platform-billing-test-key-at-least-32-bytes'
.\mvnw.cmd '-Dtest=BackendApplicationTests' -DforkCount=0 test
```

Result on 2026-08-01:

- Platform Billing focused tests: 18 passed, 0 failed, 0 errors, 0 skipped.
- `SubscriptionApplicationServiceTest`: 3 passed, covering creation, equivalent replay and mismatched transaction/order rejection.
- Spring context: 1 passed; 65 JPA repository interfaces discovered.
- Compile and test builds: SUCCESS.
- The JWT value was an ephemeral test-only key and was not persisted.

## Permissions and Environment

- Endpoint permission mapping is deferred to T100; T096 is an internal application boundary invoked only after verified provider evidence.
- Tests use mocked repositories, synthetic provider evidence and H2 context startup only.
- No production credentials, production database, network provider call or real-money operation was used.

## Schema and Recovery

- New migration: N/A. The implementation writes the additive V24/V25 order, transaction, contract, entitlement and history tables.
- Forward recovery: disable purchase callback intake, retain the immutable payment/order evidence, deploy a corrected application service and replay the verified callback.
- Rollback must not delete contracts, ledger rows or history already applied; corrections require a later additive lifecycle transition.
