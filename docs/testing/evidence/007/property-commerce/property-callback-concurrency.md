# T062 Property Callback Replay and Concurrency

## Scope

- Added database-backed integration tests for sequential replay and simultaneous callback delivery.
- Runs the real Spring transaction proxy, JPA repositories, pessimistic attempt lock, immutable ledger entity, adapter registry and financial audit service.
- Uses a deterministic signed simulator callback against an isolated in-memory H2 database.

## Verified Invariants

- The first verified callback transitions the attempt to `SUCCESS` and creates one immutable ledger debit.
- An equivalent sequential replay returns the existing transaction and creates no second ledger effect.
- Two callbacks released from the same thread barrier both complete successfully.
- Exactly one concurrent result is marked replayed.
- The database contains exactly one transaction for the attempt after concurrent delivery.
- The persisted attempt remains `SUCCESS` and retains one provider event/transaction identity.
- Each accepted delivery records audit evidence, producing two audit rows without duplicating money.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=PropertyPaymentCallbackConcurrencyIntegrationTest,PropertyPaymentCallbackServiceTest' -DforkCount=0 test
```

Final result:

- Integration replay/concurrency tests: 2
- Callback service regression tests: 5
- Total tests: 7
- Failures: 0
- Errors: 0
- Skipped: 0
- Build: SUCCESS

## Safety

- No external provider endpoint, production credential, production merchant or real-money operation was used.
- The test database is isolated and created/dropped by the test context.

## Remaining Work

- T063 remains open for database-backed manual confirmation permission, audit and cross-property isolation.
- T064-T068 remain open for the Angular API client, payment panel and browser journeys.

## Recovery

- This task changes tests/evidence only; there is no runtime or production data rollback.
