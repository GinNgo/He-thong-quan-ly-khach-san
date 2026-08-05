# T292 Disable Anonymous Booking Evidence

Task: `T292`
Capability: `PUB-027`
Status: `COMPLETE_VERIFIED`

## Implemented Contract

- `POST /api/reservations/public/book` is fail-closed with stable HTTP 410 and code `ANONYMOUS_BOOKING_DISABLED`.
- The endpoint ignores the payload and cannot enter Bean Validation, mutation idempotency, reservation service or synthetic guest-user creation.
- The response includes the standard error path/correlation contract, `retryable=false` and `Deprecation: true`.
- Security `permitAll` is narrowed from the broad public reservation subtree to the exact disabled POST route.
- Authenticated CUSTOMER booking remains unchanged.

## Focused Verification

| Suite | Result |
|---|---|
| `ReservationControllerIdempotencyTest` | 4/4 PASS |
| `ReservationBookingValidationTest` | 2/2 PASS |
| Aggregate | 6/6 PASS |
| `git diff --check` | PASS |

The disabled-route test verifies zero calls to `MutationIdempotencyService` and `ReservationService`, so no user, reservation, detail, hold or idempotency row can be created through this controller path.

`EndpointSecurityArchitectureTest` was also executed and reported only two pre-existing unrelated findings (`EmailVerificationController.confirm`, `HotelController.getMyHotels`); it did not flag the narrowed T292 route.

## Privacy And Safety

No anonymous identity, consent, retention, lookup token, rate limit or account-claim policy is invented. No migration, PII conversion, credential or financial transaction is involved.

## Rollback

Rollback is the T292 task commit revert, but re-enabling anonymous mutation requires an approved verified identity/retrieval, consent/retention and anti-abuse design first.
