# T322 Booking-confirmation email evidence

Date: 2026-08-04

## Scope

- Booking-confirmation messages use the transactional email outbox introduced by T324.
- The booking transaction registers an `afterCommit` callback; a rolled-back transaction does not queue an email.
- Queue or delivery failure is isolated from the already committed booking state.
- Template key `booking_confirmation` now uses locale-specific versions `v2.vi` and `v2.en` with HTML and plain-text bodies.
- No production SMTP credentials, live mailbox, invoice email, or registration email flow was changed by T322.

## Focused automated coverage

Test class:

`backend/src/test/java/com/hotel/services/BookingConfirmationEmailTest.java`

Covered cases:

1. Vietnamese HTML/text rendering, escaping, version metadata, and enqueue only after commit.
2. Rolled-back booking transactions do not enqueue a confirmation.
3. English rendering and locale-specific template/idempotency identity.
4. Post-commit enqueue failure is contained and records a failure metric without reversing booking state.
5. A queued booking email moves `PENDING -> FAILED -> SENT` through bounded retry while booking state remains `CONFIRMED`.

## Validation command

Run sequentially with the shared backend build slot:

```powershell
Set-Location backend
./mvnw.cmd -q -Dtest=BookingConfirmationEmailTest,EmailOutboxServiceTest,EmailOutboxWorkerTest test
```

Status: PASS. `BookingConfirmationEmailTest` 5/5, `EmailOutboxServiceTest` 5/5 and `EmailOutboxWorkerTest` 1/1; 11/11 total.

## External evidence

Sandbox provider/mailbox delivery is not required for this source-level task and was not attempted. Provider-side disposable inbox evidence remains governed by the existing CROSS-010 external credential stop gate.
