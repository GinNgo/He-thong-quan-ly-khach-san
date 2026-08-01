# T095 Verified Platform Callback and Exactly-Once Ledger Effect

## Scope

- Added provider callback normalization and verification using server-resolved system merchant credentials.
- Added locked platform-attempt and order orchestration for verified success/failure callbacks.
- Added exactly-once immutable platform ledger effects keyed by provider, environment and event identity.
- Added append-only audit evidence for attempt and order transitions plus rejected/unknown callbacks.

## Enforced Invariants

- Callback requests cannot submit expected merchant identity, credentials, amount, currency or environment.
- The callback provider/reference must resolve to a locked platform attempt and its exact system configuration.
- Signature, merchant, amount, VND currency, reference, expiry and provider event identity are verified by the adapter.
- A successful purchase/renewal/upgrade records one debit transaction and moves the order to `PAID`.
- Equivalent replay returns the existing transaction; conflicting event ownership or evidence is rejected.
- Failed/expired/tampered callbacks activate no subscription and create no platform revenue.
- Production remains governed by the fail-closed T094 readiness gate.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=PlatformPaymentCallbackServiceTest,PlatformPaymentConfigurationServiceTest,PlatformPaymentAttemptServiceTest,PlatformBillingModelTest' -DforkCount=0 test
$env:JWT_SECRET='feature007-platform-billing-test-key-at-least-32-bytes'
.\mvnw.cmd '-Dtest=BackendApplicationTests' -DforkCount=0 test
```

Coverage includes successful exactly-once ledger creation, replay, binding rejection and verified failure without revenue.

Result on 2026-08-01:

- Focused tests: 12 passed, 0 failed, 0 skipped.
- Spring context: 1 passed; 65 JPA repository interfaces discovered.
- Compile and test builds: SUCCESS.
- The JWT value was an ephemeral test-only key required by the existing test profile and was not persisted.

## Permissions and Environment

- Provider callbacks authenticate through provider evidence rather than customer JWT.
- No client-supplied merchant secret is accepted or logged.
- Tests use mocked adapters and synthetic credentials only; provider-specific signature contracts remain T102.

## Schema and Recovery

- New migration: N/A. The callback writes only V24 attempts/orders/ledger and foundational audit rows.
- Forward recovery: disable the provider configuration, retain attempt/order/ledger/audit evidence and safely replay the original verified callback after a code fix.
