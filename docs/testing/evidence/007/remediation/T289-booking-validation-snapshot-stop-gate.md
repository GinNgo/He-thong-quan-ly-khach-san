# T289 Booking Validation And Snapshot Evidence

Task: `T289`
Capability: `PUB-022`
Status: `BLOCKED_FINANCIAL_POLICY`

## Implemented Safe Slice

- Authenticated management and CUSTOMER booking routes apply Bean Validation before mutation; the anonymous route is intentionally unchanged for T292 ownership.
- Request validation covers required/positive identifiers, dates, quantity/adults/guests, nonnegative children, bounded strings and checkout-after-checkin.
- The booking response exposes the already-persisted deposit policy snapshot: configuration ID/version, policy/value, booking total, required deposit and currency.
- Reservation service tests prove the response total and snapshot/version come from the locked server booking path, not caller price fields.

## Focused Verification

| Suite | Result |
|---|---|
| `ReservationBookingValidationTest` | 2/2 PASS; 18.287s |
| `ReservationServiceTest` | 14/14 PASS; 12.21s final rerun |
| Aggregate | 16/16 PASS; 0 failures/errors/skips |
| `git diff --check` | PASS |

Validation evidence proves malformed payload is rejected with HTTP 400 before reservation/idempotency mutation and the CUSTOMER route retains explicit method authority. Strict Mockito remains enabled.

## Remaining Financial Stop Gate

T282 has no approved quote components, identity/version, TTL or stale-price behavior. The persisted deposit configuration version is exposed truthfully but is not mislabeled as a public quote version. Full T289 quote/version evidence cannot be completed until that policy lands.

## Safety

- No anonymous-booking policy change, payment method policy, production credential, real transaction or migration is included.
- Temporary Maven compiler overlay was removed.

## Resume Condition

After T282 approval and T287 context implementation, bind the approved quote identity/version to booking, prove stale/tampered rejection through HTTP/database tests and return the final authoritative quote evidence without trusting client totals.
