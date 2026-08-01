# T094 Platform Merchant Readiness and Payment Attempt Creation

## Scope

- Added environment-reference credential resolution for system-owned platform merchants.
- Added configuration commands and responses that persist/return masked merchant metadata only.
- Added explicit simulator/sandbox readiness validation through the shared environment guard and provider registry.
- Added platform payment-attempt creation from immutable order price/currency/expiry data.

## Enforced Invariants

- Platform Billing resolves credentials from `env:` references and never returns the reference or resolved secret.
- Only one enabled environment may exist per provider when an attempt is created.
- Provider adapters, HTTPS callback URL, merchant identity, secret and provider endpoint must be ready for sandbox mode.
- Production merchant mutation is explicitly blocked pending separate owner approval.
- Attempt requests cannot submit amount, currency, expiry, owner, property merchant or configuration ID.
- The locked order owner must match the authenticated actor; expired/non-payable orders produce no attempt.
- A second active attempt is blocked and equivalent idempotent retries return the original attempt.

## Automated Validation

Commands from `backend/`:

```powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd '-Dtest=PlatformPaymentConfigurationServiceTest,PlatformPaymentAttemptServiceTest,PlatformBillingModelTest' -DforkCount=0 test
$env:JWT_SECRET='feature007-platform-billing-test-key-at-least-32-bytes'
.\mvnw.cmd '-Dtest=BackendApplicationTests' -DforkCount=0 test
```

Coverage includes masked responses, secret redaction, production denial, incomplete readiness, server-owned amount/expiry, replay and expired-order blocking.

Result on 2026-08-01:

- Focused tests: 9 passed, 0 failed, 0 skipped.
- Spring context: 1 passed; 65 JPA repository interfaces discovered.
- Compile and test builds: SUCCESS.
- The context run identified and verified the explicit production constructor wiring for `PlatformPaymentAttemptService`.
- The JWT value was an ephemeral test-only key required by the existing test profile and was not persisted.

## Permissions and Environment

- Attempt ownership is derived from `PropertyAccessService.currentUser()` and the locked order owner.
- Endpoint permission mapping is deferred to T100.
- Only simulator fixtures and synthetic sandbox references are used; no network call or real merchant is used.

## Schema and Recovery

- New migration: N/A. Configuration and attempts use V24 tables.
- Forward recovery: disable the affected platform provider configuration; existing attempts/orders remain preserved for audit and safe retry.
