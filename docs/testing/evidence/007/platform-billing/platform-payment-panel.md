# T107 Platform Payment Simulator and Sandbox Panel

## Scope

- Added a responsive Platform Billing payment panel for simulator and sandbox provider selection.
- Creates payment attempts through the typed API with an idempotency key and no client amount, merchant or entitlement fields.
- Displays the backend environment, masked merchant, provider reference, exact expected amount, expiry and normalized attempt status.
- Refreshes authoritative order/attempt status from the backend and explicitly states that subscription activation is server-owned.
- Provides no client activation, entitlement-editing or production-approval control.

## Status Handling

- `CREATED`, `PENDING` and `PROCESSING`: clearly state that no entitlement change has occurred.
- `SUCCESS`: states that verified server evidence applies the subscription exactly once.
- `FAILED`, `CANCELLED` and `EXPIRED`: show terminal non-activation outcomes and safe retry guidance.
- `APPLIED` order state: displays read-only confirmation that the server applied the subscription.

## Automated Validation

Commands from `frontend/`:

```powershell
npx ng build --configuration development --no-progress
npx ng test --watch=false --no-progress --include=src/app/features/management/subscription-billing/platform-payment-panel.component.spec.ts
```

Result on 2026-08-02:

- Angular development build: SUCCESS.
- Targeted Angular CLI test run: SUCCESS.
- The component test verifies the server-owned attempt payload/idempotency header and confirms no `Activate subscription` control is rendered.

## Safety and Recovery

- The panel does not call provider networks directly and never receives resolved credentials.
- Production mode remains governed by backend readiness and approval gates.
- Removing the panel does not alter or delete persisted orders, attempts, transactions, contracts or entitlements.
