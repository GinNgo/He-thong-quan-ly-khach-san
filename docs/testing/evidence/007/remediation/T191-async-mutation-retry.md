# T191 Async Cancellation, Duplicate Submit And Safe Retry Evidence

Date: 2026-08-02
Base commit: `158d3ed` (T190)
Backend profile: `test`; isolated unit tests use Mockito/H2-compatible application code
Frontend runner: Angular test builder / Vitest
Browser runner: Playwright Chromium with intercepted application API
Production credentials or provider operations: N/A

## Commands

```powershell
Set-Location backend
.\mvnw.cmd -q '-Dtest=MutationIdempotencyServiceTest,FinancialIdempotencyServiceTest,FinancialErrorContractTest,ReservationControllerIdempotencyTest' test

Set-Location ..\frontend
.\node_modules\.bin\ng.cmd test --watch=false --no-progress `
  --include "src/app/core/services/async-action-coordinator.service.spec.ts" `
  --include "src/app/features/client/booking-checkout/booking-checkout.component.spec.ts" `
  --include "src/app/features/client/profile/profile.component.spec.ts"

npx.cmd playwright test e2e/async-mutation-retry.spec.ts --project=chromium --workers=1
```

## Result

| Layer / suite | Tests | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| `MutationIdempotencyServiceTest` | 5 | 5 | 0 | 0 | 0 |
| `FinancialIdempotencyServiceTest` | 3 | 3 | 0 | 0 | 0 |
| `ReservationControllerIdempotencyTest` | 2 | 2 | 0 | 0 | 0 |
| `FinancialErrorContractTest` | 2 | 2 | 0 | 0 | 0 |
| Angular async coordinator | 4 | 4 | 0 | 0 | 0 |
| Booking checkout/profile components | 9 | 9 | 0 | 0 | 0 |
| Playwright retry journey | 1 | 1 | 0 | 0 | 0 |
| **Total** | **26** | **26** | **0** | **0** | **0** |

## Contract Repairs

- Added `MutationIdempotencyService`, which claims, replays, completes and fails a mutation
  through the persisted idempotency ledger in independent `REQUIRES_NEW` transactions.
- Customer/public reservation creation and customer cancellation now use a stable operation and
  scope identity. Repeated equivalent requests replay the original DTO; an in-progress duplicate
  receives retryable `CONCURRENT_MODIFICATION`; a failed claim can retry with the same key.
- The Angular HTTP mutation interceptor already supplies correlation/idempotency headers. The new
  `AsyncActionCoordinatorService` standardizes `join` (double-submit), `replace` (latest-only
  reads), cancellation, busy state and retry only for API errors explicitly marked `retryable`.
- Booking creation keeps its key in `sessionStorage` until the server returns success, so a browser
  reload or retry after an unknown outcome cannot create a second reservation. Cancellation uses the
  same per-reservation key and coordinator guard.
- Payment/refund/subscription mutations retain their existing server-owned idempotency services;
  provider callbacks remain protocol-specific and no real provider was called.

## Remaining Boundary

Legacy admin CRUD endpoints still rely on their existing component busy flags and the global
mutation-header contract; they do not all expose a persisted response-replay adapter yet. Their
domain-specific remediation remains tracked by the generated property/operations tasks, so
`CROSS-033` remains `PARTIAL` rather than claiming unsupported universal completion.
