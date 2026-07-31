# T057 Manual Transfer Confirmation

## Scope

- Added a backend-only manual/QR transfer confirmation service.
- Required `PROPERTY_PAYMENT_CONFIRM_MANUAL` with the `APPROVE` action unless the actor is a system administrator.
- Required active access to the attempt property and returned resource-not-found semantics across properties.
- Explicitly blocked the reservation owner from confirming their own transfer, even if that account has a permission mask.
- Added persisted request idempotency plus one deterministic ledger identity per manual attempt.
- Added immutable ledger and audit evidence with actor, reason and external evidence reference.

## Financial Rules

- Only `MANUAL_TRANSFER` and `QR_TRANSFER` attempts can enter this workflow.
- Only `PENDING_VERIFICATION` attempts can be confirmed; expired attempts are rejected.
- The service locks the attempt before authorization-sensitive mutation.
- The expected amount comes only from the persisted attempt and cannot be supplied by the confirmer.
- Equivalent idempotency replays return the original transaction without a second debit.
- A conflicting evidence reference for an existing effect is rejected.
- Successful confirmation records `verifiedBy`, write-once evidence identities and one append-only property ledger debit.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=ManualTransferConfirmationServiceTest,PropertyPaymentCallbackServiceTest,PropertyPaymentAttemptServiceTest,PropertyPaymentModelTest,FinancialIdempotencyServiceTest' -DforkCount=0 test
```

Final result:

- Tests run: 25
- Failures: 0
- Errors: 0
- Skipped: 0
- Build: SUCCESS

## T063 Integration Validation

Added `ManualTransferConfirmationIntegrationTest` with a real Spring Data JPA/H2 context covering:

- an assigned staff actor with `PROPERTY_PAYMENT_CONFIRM_MANUAL/APPROVE` creates one successful attempt transition, one property ledger debit, one completed idempotency record and one append-only audit event;
- a staff actor without `APPROVE` is denied before any financial mutation;
- the reservation owner cannot self-confirm even when an approval mask is present;
- a staff actor assigned only to another property receives resource-not-found semantics and creates no ledger, audit or idempotency row;
- an equivalent idempotency replay returns the original transaction without a second ledger or audit effect.

Regression command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=ManualTransferConfirmationIntegrationTest,ManualTransferConfirmationServiceTest,FinancialIdempotencyServiceTest,FinancialAuditServiceTest' '-Dspring.jpa.show-sql=false' -DforkCount=0 test
```

Final result:

- Tests run: 14
- Failures: 0
- Errors: 0
- Skipped: 0
- Build: SUCCESS

## Safety and Remaining Work

- No public/customer self-confirm endpoint was added.
- No provider call, production credential, production merchant or real-money transaction was used.
- T058 exposes the permission-annotated management endpoint.
- T063 now verifies database effects, permission denial, self-confirm denial, cross-property concealment and replay behavior.

## Recovery

- This task adds no migration and never rewrites an existing ledger row.
- A transient failure is recovered by replaying the same idempotency key and payload.
- Rollback is application-only: disable the management confirmation endpoint while preserving attempts, ledger entries and audit events.
