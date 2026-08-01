# T103 Platform Callback Replay and Concurrency

## Scope

- Added an H2-backed integration fixture for the real `PlatformPaymentCallbackService` and `SubscriptionApplicationService`.
- Verified sequential callback replay returns an accepted replay result without duplicating the platform ledger transaction, software contract, entitlement or subscription history.
- Verified two equivalent callbacks released concurrently produce one normal application and one replay while preserving one subscription effect.
- Reused the unique simulator provider/environment configuration across test methods so the fixture respects the production uniqueness constraint.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=PlatformCallbackConcurrencyIntegrationTest' -DforkCount=0 test
```

Result on 2026-08-01:

- `PlatformCallbackConcurrencyIntegrationTest`: 2 passed, 0 failed, 0 errors, 0 skipped.
- Sequential replay and concurrent callback scenarios both assert exactly one ledger transaction, contract, entitlement and history row.
- The test uses synthetic simulator HMAC evidence and an in-memory H2 database only; no external provider, production credential or real-money operation is used.

## Safety and Recovery

- Callback verification remains provider-evidence based; no customer JWT or client-supplied merchant data is trusted.
- The test exercises row-lock/idempotency behavior without changing production schema or data.
- If a future callback implementation regresses, disable callback intake, preserve existing immutable evidence and correct the application boundary before replaying verified events.
