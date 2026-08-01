# T108 Platform Billing Angular Lifecycle Tests

## Coverage

- Purchase: a property without a current plan creates a backend-owned purchase order with property/plan identifiers and an idempotency key only.
- Renewal: selecting the current plan creates a renewal order without a client duration or price.
- Upgrade: selecting another plan creates an upgrade order without a client price, credit or proration amount.
- Failure: catalog errors render an explicit failure state and expose no stale purchase action.
- Policy: downgrade and proration blockers are rendered from the backend `POLICY_NOT_CONFIGURED` response.
- Readiness masking: the system-admin panel renders masked merchant evidence, keeps the secret reference empty/write-only and validates readiness using only the provider identity.

## Automated Validation

Commands from `frontend/`:

```powershell
npx ng build --configuration development --no-progress
npx ng test --watch=false --no-progress --include=src/app/features/management/subscription-billing/subscription-billing.component.spec.ts --include=src/app/features/admin/platform-payment-configuration/platform-payment-configuration.component.spec.ts
```

Result on 2026-08-02:

- Angular development build: SUCCESS.
- Targeted Angular CLI test run: SUCCESS.
- The test fixtures exercise purchase, renewal, upgrade, catalog failure, policy blockers and masked platform readiness without provider network calls.

## Safety

- Tests use HttpClient mocks and synthetic VND values only.
- No production credential, merchant, database mutation, provider callback or real-money operation is used.
- No test or component exposes a client entitlement activation control.
