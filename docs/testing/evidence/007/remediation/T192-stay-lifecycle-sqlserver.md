# T192 Stay Lifecycle SQL Server Concurrency And Rollback Evidence

Date: 2026-08-02
Base commit: `729cedf` (T191)
Backend profile: isolated H2 persistence test plus SQL Server 2022 Developer container
Production credentials or provider operations: N/A

## Commands

```powershell
Set-Location backend
.\mvnw.cmd -q '-Dtest=ReservationLifecycleLockingTest,CheckoutPersistenceRollbackIntegrationTest,ReservationCheckoutTransactionTest,CheckoutOperationsServiceTest' test
.\tools\stay-lifecycle-sqlserver-validation.ps1
```

The SQL Server script creates a disposable database inside a disposable SQL Server
2022 container, sets test-only datasource environment variables, runs
`StayLifecycleSqlServerIT`, and removes the exact container in a `finally` block.
It never uses a production database or merchant credential.

## Results

| Layer / suite | Tests | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| `ReservationLifecycleLockingTest` | 3 | 3 | 0 | 0 | 0 |
| `CheckoutPersistenceRollbackIntegrationTest` | 2 | 2 | 0 | 0 | 0 |
| `ReservationCheckoutTransactionTest` | 5 | 5 | 0 | 0 | 0 |
| `CheckoutOperationsServiceTest` | 6 | 6 | 0 | 0 | 0 |
| `StayLifecycleSqlServerIT` | 2 | 2 | 0 | 0 | 0 |
| **Total** | **18** | **18** | **0** | **0** | **0** |

## Verified Boundaries

- Assignment locks the reservation first, locks existing active assignments, sorts
  requested room IDs and locks all candidate rooms before conflict validation or writes.
- Repeating an identical assignment request returns the existing assignment without
  inserting another `reservation_rooms` row; a competing reservation receives a conflict.
- Check-in locks assigned rows and physical rooms before changing room state to `OCCUPIED`.
- Concurrent checkout calls serialize on the reservation and assignment/room locks;
  exactly one release and one tenant-scoped housekeeping effect are committed.
- A forced exception after checkout operational writes rolls back assignment status,
  room state and housekeeping task creation together in both H2 and SQL Server.
- SQL Server evidence is executable through `stay-lifecycle-sqlserver-validation.ps1`;
  the test remains opt-in when a SQL Server container is not available.

## Scope Boundary

This closes STAY-029's concurrency and persistence-evidence slice. STAY-019 remains
`PARTIAL` because invoice-finalization failure injection and the complete financial
checkout aggregate still require their own database-backed failure matrix. The UI
journey row STAY-028 also remains `PARTIAL` because its current browser evidence uses
intercepted APIs.
