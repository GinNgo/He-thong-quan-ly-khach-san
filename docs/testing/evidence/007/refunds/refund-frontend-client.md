# T121 Typed Refund API Client

## Coverage

- `RefundService` exposes separate Property Commerce and Platform Billing request, status, approval and provider-attempt methods.
- Transaction and refund identifiers are URI encoded before they are sent to the API.
- Refund bodies contain only amount/reason or provider fields; server-owned balances, merchant data and policy effects remain backend-owned.
- Optional `Idempotency-Key` and `X-Correlation-ID` headers are preserved for every mutating refund operation.

## Validation

Command from `frontend/`:

```powershell
npm test -- --watch=false --include=src/app/core/services/refund.service.spec.ts
```

Result on 2026-08-02: 3 passed, 0 failed. Angular emitted existing unrelated NG8107 optional-chain warnings.
