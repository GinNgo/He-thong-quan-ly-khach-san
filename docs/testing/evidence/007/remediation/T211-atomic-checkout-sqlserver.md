# T211 Atomic Checkout And Rollback

## Scope

- Executes the real `ReservationService`, `InvoiceFinalizationService` and
  `CheckoutOperationsService` against SQL Server rather than mocking the invoice
  aggregate.
- Recreates the production filtered unique indexes for one finalized invoice per
  reservation and one checkout housekeeping effect per property/effect key.
- Injects a SQL Server check constraint that rejects the final reservation status
  write after invoice, room, assignment and housekeeping persistence has run.
- Replays checkout concurrently and once more from a cleared persistence context to
  prove restart-like retry behavior.

## Automated Validation

Backend test:

```powershell
.\mvnw.cmd -q '-Dtest=CheckoutAggregateSqlServerIT' test
```

The final run used an isolated `T211_Checkout_<pid>_<timestamp>` database on the
already-running disposable SQL Server test container. No production database or
merchant credential was used. The exact temporary database was dropped after the
run.

Surefire result from
`backend/target/surefire-reports/com.hotel.integration.CheckoutAggregateSqlServerIT.txt`:

| Suite | Tests | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| `CheckoutAggregateSqlServerIT` | 2 | 2 | 0 | 0 | 0 |

## Verified Boundaries

- A database constraint failure on the final `CHECKED_OUT` status flush rolls back
  the finalized invoice, invoice line, released assignment, dirty room and
  housekeeping task together; the booking remains `CHECKED_IN`.
- Two simultaneous checkout calls serialize on the reservation lock and return
  successfully without duplicate financial or operational effects.
- A later restart-like replay returns the existing finalized evidence.
- SQL Server contains exactly one finalized invoice, one invoice line and one
  tenant-scoped housekeeping checkout effect for the reservation.
- The test uses the same filtered uniqueness contracts shipped by
  `V22__property_checkout_invoice.sql` and
  `V33__housekeeping_checkout_idempotency.sql`.

## Environment Note

The first attempt to start a second SQL Server container timed out before test
collection because an earlier disposable validation container was still running.
The final evidence reused that test-only instance with a new isolated database. The
Maven wrapper later exceeded the command-output timeout during shutdown, but the
Surefire report had already completed with 2/2 passing tests and the temporary
database was removed explicitly.
