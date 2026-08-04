# T303 Staff-created Reservation

Status: `PARTIAL_BLOCKED_EXTERNAL`

## Implemented

- Replaced the broken legacy payload and physical-room selector with a dedicated staff booking workflow.
- Added a minimal customer search contract that returns only active `CUSTOMER` accounts and masks email addresses.
- Added a persisted two-minute server quote containing the selected customer, property, room type, dates, occupancy, quantity, authoritative base price, total, deposit, availability and payment-configuration version.
- Quote and create endpoints require `RESERVATION:CREATE`, `Idempotency-Key` and an operational property assigned to the current staff actor.
- Create locks and revalidates the room type, price, deposit configuration and current availability. It creates the reservation for the selected customer rather than the authenticated employee.
- The reservation and detail deliberately keep the physical room null; assignment remains exclusively in the T301 locked workflow.
- UI invalidates stale quotes on any field change, prevents duplicate quote/create requests, shows authoritative totals and handles `409` by requiring a fresh quote.

## Verification

| Check | Result |
|---|---|
| Isolated backend compile | PASS |
| `StaffBookingServiceTest` | 2/2 PASS |
| `StaffBookingServiceTest,ReservationServiceTest,ReservationLifecyclePropertyIdorTest` | 6/6 PASS |
| `reservation-create.spec.ts` | 5/5 PASS |
| `reservation-lifecycle.service.spec.ts` | 5/5 PASS |
| Angular development build | PASS |
| `git diff --check` | PASS |

## Remaining Gate

The local frontend and backend can be built, but no working receptionist/manager credential is configured. The in-app Browser could not attach, Chrome reached the local admin login, and the documented legacy `admin/admin` credential was rejected during T302 verification. Therefore a real authenticated quote/create/unassigned-booking journey cannot be claimed in this task either. No production credential, payment provider credential or real-money operation was used.
