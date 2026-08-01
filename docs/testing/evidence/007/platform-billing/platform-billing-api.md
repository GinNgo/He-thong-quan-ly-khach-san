# T100 Platform Billing API

## Scope

- Added authenticated plan catalog, purchase order, order detail, payment-attempt and unpaid-order cancellation endpoints under `/api/platform`.
- Added renewal, approved upgrade, blocked downgrade, policy availability and subscription history endpoints.
- Added system-admin platform merchant configuration listing/update/validation endpoints using the existing masked response projections.
- Added owner/system-safe order and history query mapping without returning credentials, idempotency hashes or internal secret references.

## Authorization and Safety

- Owner lifecycle endpoints require `PLATFORM_BILLING` action masks and still enforce order ownership/property access in application services.
- Merchant readiness endpoints require `PAYMENT_READINESS`; super admin continues through the centralized permission bypass.
- Readiness validation returns only `PaymentEnvironmentGuard.Readiness`; resolved merchant credentials and secrets never leave the service boundary.
- Cancellation locks an unpaid order, rejects processing attempts, cancels created/pending attempts in the same transaction and appends a redacted audit event.
- Downgrade delegates to T099 and returns `POLICY_NOT_CONFIGURED` without creating an order.
- Production mode remains disabled and fail-closed through T094 configuration validation.

## Automated Validation

Commands from `backend/`:

```powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd '-Dtest=PlatformBillingControllerTest,PlatformBillingQueryServiceTest,PlatformBillingModelTest' -DforkCount=0 test
.\mvnw.cmd '-Dtest=PlatformBillingControllerTest,PlatformBillingQueryServiceTest,PlatformBillingModelTest,SubscriptionOrderServiceTest,PlatformPaymentConfigurationServiceTest,PlatformPaymentAttemptServiceTest,PlatformPaymentCallbackServiceTest,SubscriptionApplicationServiceTest,SubscriptionRenewalServiceTest,SubscriptionUpgradeServiceTest,SubscriptionPolicyServiceTest' -DforkCount=0 test
$env:JWT_SECRET='feature007-platform-billing-test-key-at-least-32-bytes'
.\mvnw.cmd '-Dtest=BackendApplicationTests' -DforkCount=0 test
```

Result on 2026-08-01:

- Controller/query/model-focused tests: 6 passed, 0 failed, 0 errors, 0 skipped.
- Platform Billing focused tests: 36 passed, 0 failed, 0 errors, 0 skipped.
- Coverage includes identifier-only purchase delegation, secret-safe readiness projection and atomic pending-attempt cancellation.
- Spring context: 1 passed; 66 JPA repository interfaces discovered and the new controller/query service wired successfully.
- Compile and test builds: SUCCESS.
- The JWT value was an ephemeral test-only key and was not persisted.

## Schema and Recovery

- New migration: N/A. The API uses V24/V25 Platform Billing tables and V28 permissions.
- Forward recovery: disable the new `/api/platform` routes, preserve all order/payment/contract/history evidence, deploy corrected mapping and safely retry idempotent operations.
- Cancellation rollback is transactional; an exception before commit leaves both order and attempts unchanged.
