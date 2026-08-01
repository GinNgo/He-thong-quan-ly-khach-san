# T110 Platform Subscription Negative Journeys

## Scope

- Uses a fresh owner/property fixture and the real Platform Billing APIs through Playwright.
- Rejects a callback with a tampered amount and confirms the order/attempt remain pending.
- Rejects a callback signed with the wrong system merchant and confirms no state mutation.
- Replays one accepted callback and verifies the second response is marked `replayed` with one `APPLIED` order.
- Cancels an unpaid order and rejects a late callback with `INVALID_STATE_TRANSITION`.
- Rejects downgrade order creation with `POLICY_NOT_CONFIGURED` before any order is persisted.
- Includes an expiry journey that runs only with the one-minute E2E expiry profile.

## Automated Validation

Commands from `frontend/`:

```powershell
npx playwright test platform-subscription-negative.spec.ts --list
npx tsc --noEmit -p tsconfig.json
```

Results on 2026-08-02:

- Playwright listing: 5 negative journeys discovered.
- TypeScript compile: SUCCESS.
- The suite is environment-gated and skipped without admin/simulator variables; no provider or real-money calls are made by default.

## Required Variables

- `LUXESTAY_E2E_ADMIN_USERNAME`
- `LUXESTAY_E2E_ADMIN_PASSWORD`
- `LUXESTAY_E2E_PLATFORM_MERCHANT_ID`
- `LUXESTAY_E2E_PLATFORM_SIGNING_SECRET`
- Optional `LUXESTAY_E2E_API_URL` for a non-default API host.
- Set `LUXESTAY_E2E_PLATFORM_ORDER_EXPIRY_MINUTES=1` in the backend E2E profile to enable the expiry test.

## Safety Assertions

- Callback signatures are generated only from the synthetic simulator secret.
- Client-supplied price, merchant and entitlement effects never become authoritative.
- Cancellation and policy blockers leave no successful platform ledger or entitlement transition.
