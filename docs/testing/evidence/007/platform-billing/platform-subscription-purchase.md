# T109 Platform Subscription Purchase and Activation Journey

## Scope

- Registers a unique owner and property through the public partner flow.
- Verifies the new property is pending, approves it through the admin API, and opens management billing.
- Creates a backend-priced Platform Billing purchase order and simulator payment attempt.
- Sends a signed simulator callback through the provider boundary and refreshes the UI from server state.
- Verifies the order reaches `APPLIED` and the UI reports server-owned subscription activation evidence.

## Automated Validation

Commands from `frontend/` and `backend/`:

```powershell
npx playwright test platform-subscription-purchase.spec.ts --list
.\mvnw.cmd -Dtest=PlatformPaymentCallbackControllerTest test
```

Results on 2026-08-02:

- Playwright listing: 1 journey discovered; execution is intentionally skipped unless all simulator variables are present.
- Platform callback controller test: 2 passed, 0 failed, 0 skipped.
- Backend compile: SUCCESS after adding the callback boundary.

## Required E2E Variables

Set these only to non-production test values before running the journey:

- `LUXESTAY_E2E_ADMIN_USERNAME`
- `LUXESTAY_E2E_ADMIN_PASSWORD`
- `LUXESTAY_E2E_PLATFORM_MERCHANT_ID`
- `LUXESTAY_E2E_PLATFORM_SIGNING_SECRET`
- Optional: `LUXESTAY_E2E_API_URL`

The test uses a synthetic owner/property and simulator callback data. It does not enable production payment or use real money.

## Boundary Controls

- Platform callbacks are public provider endpoints and do not require a customer JWT; signature, merchant, amount, reference, expiry and event identity remain server-verified.
- `X-Payment-Signature` is explicitly accepted by the API CORS policy.
- No client-side entitlement activation action is exposed.
