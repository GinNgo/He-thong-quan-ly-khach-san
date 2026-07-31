# T059 Legacy Property Payment Compatibility

## Scope

- Added a temporary read-only adapter for legacy `payment_sessions` rows and legacy `Payment` entities.
- Resolves and authorizes the reservation before any legacy table or repository read.
- Preserves legacy identifiers and raw values for reconciliation instead of silently rewriting them.
- Maps recognized lifecycle values into the new payment state model without mutating source rows.
- Treats expired active legacy sessions as expired in the compatibility view without writing status back.

## Double-Count Protection

- The adapter checks for new Property Commerce attempts and immutable ledger transactions first.
- Legacy attempt fallback is disabled when authoritative attempts already exist.
- Legacy settlement fallback is disabled when an authoritative ledger already exists.
- `PENDING`, `FAILED`, unknown, fractional-VND and explicitly reconciliation-required records remain diagnostic only.
- A positive `REFUNDED` legacy row is not accepted as settled evidence because its economic direction is ambiguous.
- Returned warnings identify missing legacy tables, authoritative replacements and reconciliation requirements.

## Migration Safety

- The adapter is annotated `@Transactional(readOnly = true)` and contains no save/update/delete operation.
- The optional `payment_sessions` table is detected through `INFORMATION_SCHEMA`; migration states without that table still expose legacy `Payment` rows.
- The implementation does not depend on or stage the existing untracked `PaymentSession` source files.
- No backfill, cleanup, destructive migration or production database operation is performed by this task.

## Automated Validation

Initial regression command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=LegacyPropertyPaymentAdapterTest,PropertyPaymentAttemptServiceTest' -DforkCount=0 test
```

Result:

- Tests run: 16
- Failures: 0
- Errors: 0
- Skipped: 0
- Build: SUCCESS

After tightening reconciliation, failure-state and ambiguous-refund rules, the final adapter was rebuilt and validated with:

```powershell
.\mvnw.cmd '-Dtest=LegacyPropertyPaymentAdapterTest' -DforkCount=0 test
```

Final adapter result:

- Tests run: 5
- Failures: 0
- Errors: 0
- Skipped: 0
- Build: SUCCESS

Coverage includes reservation-owner authorization, cross-account hiding, optional session-table handling, identifier preservation, expiry projection, invalid/fractional records, authoritative-record shadowing and zero writes.

## Remaining Work

- T061 remains open for provider callback contract coverage.
- T062 remains open for database-backed callback replay/concurrency coverage.
- T063 remains open for database-backed manual confirmation permission/audit/isolation coverage.
- Legacy fallback must remain temporary and should be removed only after final reconciliation proves parity.

## Recovery

- Disable callers of the adapter to stop compatibility reads; no source financial record needs rollback.
- Ambiguous legacy rows stay visible with reconciliation warnings and are never promoted automatically.
