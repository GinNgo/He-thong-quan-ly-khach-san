# T306 - Booking Financial Reconciliation Evidence

Task: T306 / STAY-016  
Branch: `codex/stay-lifecycle`  
Implementation commit: `7d4999b`

## Implemented verification

`FolioDatabaseReconciliationIntegrationTest` persists and reloads one checked-in
reservation with an immutable 1,000,000 VND room snapshot, 300,000 VND required
deposit, two authoritative payments, a partial refund, legacy and migrated service
rows, append-only surcharges and a reversing adjustment.

The fixture proves exact integer-VND reconciliation:

| Component | Expected VND |
|---|---:|
| Room charges | 1,000,000 |
| Service charges | 150,000 |
| Net surcharge charges | 25,000 |
| Gross charges | 1,175,000 |
| Successful payments | 1,200,000 |
| Successful refunds | 100,000 |
| Net settled | 1,100,000 |
| Remaining balance | 75,000 |

The migrated 50,000 VND legacy service is linked to its authoritative charge
line and counted once. An additional 999,999 VND successful legacy payment is
also persisted, but is not double-counted when the immutable property ledger is
present. A second database test verifies that a staff actor cannot read a
reservation belonging to another property and receives `RESOURCE_NOT_FOUND`.

## Focused tests

Direct worktree command:

```text
backend\mvnw.cmd -Dtest=FolioDatabaseReconciliationIntegrationTest test
```

Result: blocked before test compilation by pre-existing UTF-8 BOM errors in
`UserController.java` and `UserService.java`. Those shared files were not edited.

Isolated backend snapshot command:

```text
mvnw.cmd -Dtest=FolioDatabaseReconciliationIntegrationTest test
```

Surefire result: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`.
The wrapper timed out after Surefire wrote the completed report while the large
Spring/Hibernate context was tearing down; the report timestamp and XML/text
results confirm both test methods completed successfully.

## Scope and recovery

- No production credentials, provider calls, real-money actions or migrations were used.
- No financial policy was introduced; the test verifies existing authoritative reconciliation rules.
- The change is test/evidence only. Rollback is removal of the focused test and documentation commit.

