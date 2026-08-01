# T106 Platform Billing UI and Merchant Readiness

## Scope

- Replaced the management billing catalog transport with the typed Platform Billing client.
- Added truthful downgrade/proration blockers, current usage, backend-owned plan data and a server-created order snapshot with price, version, status and expiry.
- Removed the contact-only upgrade placeholder and added secure order creation using an idempotency key and the selected managed property.
- Added a separate system-admin platform merchant control room with masked merchant/secret state, readiness validation and a write-only secret-reference field.
- Added the `PLATFORM_BILLING` and `PAYMENT_READINESS` frontend permission codes plus a guarded admin route, keeping platform merchant configuration separate from property payment configuration.

## Automated Validation

Commands from `frontend/`:

```powershell
npx ng build --configuration development --no-progress
npx ng test --watch=false --no-progress --include=src/app/features/management/subscription-billing/subscription-billing.component.spec.ts --include=src/app/features/admin/platform-payment-configuration/platform-payment-configuration.component.spec.ts
```

Result on 2026-08-02:

- Angular development build: SUCCESS; both new lazy route chunks compiled under strict template checking.
- Targeted Angular CLI test run: SUCCESS.
- Coverage verifies backend catalog/policy blocker rendering and confirms the admin screen displays masked merchant evidence without rendering the secret reference.
- The build emits the existing PrimeNG browser-target regex compatibility debug warning; it does not fail compilation.

## Safety and Recovery

- Production remains visibly fail-closed and the UI does not enable production approval or real-money execution.
- Browser payloads cannot set order price, duration, entitlement or resolved merchant credentials.
- If the UI must be disabled, remove the guarded route and management order action while preserving backend orders and immutable financial evidence.
