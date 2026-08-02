# T205 Booking Idempotency Evidence

Date: 2026-08-03
Branch: `codex/ui-functional-audit-polish`
Scope: PUB-023 booking-request idempotency and ambiguous client retry

## Implementation

- Reservation mutations require a caller-supplied `Idempotency-Key`; the Angular financial interceptor supplies one for API mutations and the checkout keeps the booking key in shared `localStorage` for a bounded 30-minute retry window.
- The persisted financial idempotency ledger stores a canonical SHA-256 request hash and response body. Equivalent replays return the original response; a conflicting payload returns `409 IDEMPOTENCY_KEY_REUSED`.
- Booking rows now retain the scoped booking key. If the reservation committed before the client received a response, a later `IN_PROGRESS` retry recovers that reservation and completes the ledger instead of creating another hold/guest/reservation.
- Claim races run in isolated transactions. A unique-constraint loser rolls back its failed Hibernate session before reading the winning record.
- Customer scopes use the authenticated username; public scopes use the client address and remain separate from authenticated booking scopes.

## Verification

```powershell
Set-Location backend
.\mvnw.cmd -q '-Dtest=MutationIdempotencyServiceTest,FinancialIdempotencyServiceTest,ReservationControllerIdempotencyTest,ReservationServiceTest,FinancialErrorContractTest' test
.\mvnw.cmd -q '-Dtest=BookingIdempotencyPersistenceIntegrationTest' test

Set-Location ..\frontend
.\node_modules\.bin\ng.cmd test --watch=false --no-progress `
  --include "src/app/features/client/booking-checkout/booking-checkout.component.spec.ts"
$env:LUXESTAY_E2E_WEB_URL='http://localhost:4201'
npx.cmd playwright test e2e/async-mutation-retry.spec.ts --project=chromium --workers=1
```

## Results

| Suite | Result |
|---|---:|
| `MutationIdempotencyServiceTest` | 6/6 passed |
| `FinancialIdempotencyServiceTest` | 3/3 passed |
| `ReservationControllerIdempotencyTest` | 3/3 passed |
| `ReservationServiceTest` | 29/29 passed |
| `FinancialErrorContractTest` | 2/2 passed |
| `BookingIdempotencyPersistenceIntegrationTest` | 2/2 passed |
| Angular booking checkout | 7/7 passed |
| Playwright async retry journey | 2/2 passed |

The browser suite covers retry after a retryable failure and reload after an unknown outcome; both requests carry the same key. The persistence suite covers service recreation/replay, conflicting payloads and concurrent double-submit with one acquired identity.
