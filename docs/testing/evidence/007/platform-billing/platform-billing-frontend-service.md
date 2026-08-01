# T105 Typed Platform Billing API Client

## Scope

- Added `PlatformBillingService` with typed catalog, backend-snapshotted order, payment-attempt, cancellation, renewal, upgrade, downgrade, history and policy methods.
- Added typed system-admin merchant configuration listing, lookup, update and masked readiness validation methods.
- All lifecycle mutations carry an explicit `Idempotency-Key`; cancellation additionally supports `X-Correlation-ID`.
- Public identifiers are URI encoded and request bodies contain only identifiers/provider method data; client price, duration, merchant and entitlement values are not accepted.

## Automated Validation

Commands from `frontend/`:

```powershell
npx ng build --configuration development --no-progress
npx ng test --watch=false --no-progress --include='src/app/core/services/platform-billing.service.spec.ts'
```

Result on 2026-08-01:

- Development Angular build: SUCCESS; strict TypeScript/template compilation completed.
- The service spec was added with HttpClient request assertions for catalog, purchase, attempt, history, cancellation and masked readiness contracts.
- Direct Vitest invocation is not a supported project runner and fails before collection because Angular's JIT compiler is not loaded; use the Angular CLI runner above.

## Safety and Recovery

- The client is a typed transport only; backend Platform Billing remains authoritative for price, expiry, merchant readiness and subscription effects.
- No payment provider call, credential, production configuration or real-money operation is performed by the service or tests.
- If an endpoint contract changes, update this client and its request assertions while preserving server-owned financial fields.
