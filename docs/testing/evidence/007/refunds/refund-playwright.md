# T124 Refund Lifecycle Playwright Journey

## Coverage

- Customer partial-refund submission displays the truthful requested state and an equivalent retry is marked `replayed`.
- Provider attempt creation, fake/invalid callback signature and provider failure are exercised without creating a successful ledger effect.
- Two equivalent callback requests run concurrently; exactly one creates the simulated effect and one returns a replay response.
- A successful replayed status is rendered with zero remaining refundable balance.

## Validation

Command from `frontend/`:

```powershell
npx playwright test e2e/refund-lifecycle.spec.ts --project=chromium
```

Result on 2026-08-02: 3 passed, 0 failed, 0 skipped.

The refund components changed during the browser test fix were also compiled with:

```powershell
npm run build -- --configuration development
```

Result on 2026-08-02: build passed.
