# T056 Property Payment Callback Orchestration

## Scope

- Added locked callback lookup by provider plus server-owned payment reference.
- Added provider/environment/merchant/amount/currency/reference/expiry verification through the shared adapter registry.
- Added write-once provider order, transaction and event identities on the payment attempt.
- Added exactly-once immutable property ledger creation using a hashed provider-event idempotency identity.
- Added append-only audit evidence for accepted, failed, replayed, mismatched and unknown callbacks.
- Routed online attempts in `SIMULATOR` mode through the signed simulator provider while keeping the selected customer method.

## Transaction and Replay Rules

- The attempt row is pessimistically locked before any financial mutation.
- A provider event already owned by another attempt is rejected before mutation.
- A successful callback transitions the attempt and writes the immutable ledger effect in one transaction.
- An equivalent replay returns the existing transaction and writes no second ledger row.
- If a ledger effect exists but the attempt still shows `PENDING`, a verified equivalent callback repairs the attempt to `SUCCESS` without duplicating money.
- A verified provider failure transitions the attempt to `FAILED` and creates no collected-money ledger entry.
- Invalid signature, amount, currency, merchant, reference, environment or expiry produces no attempt or ledger mutation.
- Database uniqueness remains the final protection for provider event and ledger idempotency races.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=PropertyPaymentCallbackServiceTest,PaymentProviderAdaptersTest,PropertyPaymentAttemptServiceTest,PropertyPaymentModelTest' -DforkCount=0 test
```

Final result:

- Tests run: 23
- Failures: 0
- Errors: 0
- Skipped: 0
- Build: SUCCESS

## Safety and Remaining Work

- No external provider endpoint, production credential, production merchant or real-money transaction was used.
- Callback commands receive credentials from a server-side resolver; property-scoped vault wiring remains deployment/configuration work.
- T061 remains open for the complete provider contract matrix.
- T062 remains open for database-backed concurrent callback integration evidence.
- T058 remains open for public callback endpoints and HTTP acknowledgement/error mapping.

## Recovery

- This task adds no migration and does not rewrite existing financial evidence.
- Forward recovery after a transient uniqueness race is a safe replay of the same verified callback.
- Rollback is application-only: disable callback routing and preserve all attempts, transactions and audit events for inspection.
