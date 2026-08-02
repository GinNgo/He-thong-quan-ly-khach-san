# T206 Overbooking and Hold Expiry Evidence

Date: 2026-08-03
Branch: `codex/ui-functional-audit-polish`
Scope: PUB-024 overbooking protection and reservation-hold expiry

## Implementation

- Reservation creation takes the room-type pessimistic lock before recounting availability, then creates the reservation detail and expiring inventory hold in the same transaction.
- Persisted holds use explicit `ACTIVE`, `CONSUMED`, `RELEASED` and `EXPIRED` states. The SQL Server migration adds a filtered unique index so one reservation cannot own two active holds.
- Expiry and payment both lock the reservation before the hold. A payment that wins consumes the hold and confirms the reservation; an expiry that wins releases inventory and any later successful payment is recorded for reconciliation without reviving the reservation.
- The scheduler queries persisted due holds, so recovery does not depend on in-memory timers and remains idempotent across repeated scans or application restart.

## Verification

```powershell
Set-Location backend
.\mvnw.cmd -q '-Dtest=ReservationHoldServiceTest,ReservationHoldExpirySchedulerTest,ReservationConcurrencyIntegrationTest,ReservationHoldIntegrationTest,ReservationHoldSqlServerIT,PaymentServiceImplTest' test
.\tools\booking-hold-sqlserver-validation.ps1
```

## Results

| Suite | Result |
|---|---:|
| `ReservationHoldServiceTest` | 4/4 passed |
| `ReservationHoldExpirySchedulerTest` | 1/1 passed |
| `ReservationConcurrencyIntegrationTest` | 2/2 passed |
| `ReservationHoldIntegrationTest` | 3/3 passed |
| `PaymentServiceImplTest` | 6/6 passed |
| `ReservationHoldSqlServerIT` | 2/2 passed |

The H2 journeys cover simultaneous last-room booking, repeated/concurrent persisted expiry, restart-style scheduler discovery and payment-versus-expiry outcomes. The isolated SQL Server 2022 sandbox repeats the last-room and payment/expiry races against the production database lock semantics; no real merchant or production database is used.
