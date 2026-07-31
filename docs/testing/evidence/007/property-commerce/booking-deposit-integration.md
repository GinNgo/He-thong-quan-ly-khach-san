# T051 Booking Deposit Integration Evidence

- Date: 2026-07-31
- Branch: `codex/ui-functional-audit-polish`
- Starting commit: `03c6a2e`
- Requirements: FR-007, FR-015
- Scope: Booking creation, reservation snapshot persistence and additive schema support

## Implemented Contract

- `ReservationService` obtains the property payment configuration by the server-resolved room-type property.
- The booking total remains authoritative from room price, nights and quantity; the request has no deposit amount input.
- Missing configuration returns `POLICY_NOT_CONFIGURED` before reservation/hold persistence.
- Disabled property payments return `PAYMENT_ENVIRONMENT_DISABLED` before reservation/hold persistence.
- Enabled `NONE`, `FIXED` and `PERCENTAGE` policies use `DepositPolicySnapshot` and are captured before the reservation is saved.
- Reservation snapshot fields include configuration ID/version, policy type/value, original booking total, required deposit and `VND` currency.
- The reservation aggregate rejects a second snapshot capture and rejects a snapshot belonging to another property.

## Migration

`V30__booking_deposit_policy_snapshot.sql` adds nullable snapshot columns for compatibility with legacy reservations, then adds:

- A foreign key to the persisted property payment configuration.
- Scale-zero VND and policy consistency checks.
- A check that the deposit cannot exceed the snapshotted booking total.
- A tenant-leading filtered index for reservations that have a deposit snapshot.

Production migration was not executed. The existing SQL Server validation runner discovers V30 through its version glob.

## Automated Verification

Command:

```powershell
Set-Location backend
.\mvnw.cmd '-Dtest=ReservationServiceTest,DepositPolicySnapshotTest,FinancialMigrationIntegrationTest' -DforkCount=0 test
```

Result: PASS - 26 tests, 0 failures, 0 errors, 0 skipped.

The suite covers server-owned percentage calculation, missing/disabled policy fail-closed behavior, no reservation or hold mutation on rejection, snapshot invariants and additive migration constraints.

## Other Layers

- Permission verification: Existing public/customer booking authorization remains unchanged; tenant identity comes from the selected server-owned room type and configuration lookup.
- Manual/browser verification: N/A for T051; payment-attempt presentation starts at T054-T068.
- Forward recovery: Revert application writes while leaving nullable V30 columns in place; existing reservations remain readable.
- Rollback: Do not drop snapshot columns or financial evidence in an applied production database. Production rollback requires disabling new writes and restoring through the separately approved database recovery process.
