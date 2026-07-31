# T052 Property Payment Attempt and Ledger Persistence

## Scope

- Added tenant-owned `PropertyPaymentAttempt` mapped to `property_payment_attempts` from V21.
- Added immutable `PropertyFinancialTransaction` mapped to `property_financial_transactions` from V21.
- Added repositories for idempotency, provider-event, reservation, attempt and locked public-ID lookups.
- Activated both Hibernate tenant filters in the request interceptor.

## Enforced Invariants

- Attempt amount is positive, exact scale-zero VND and cannot be changed after creation.
- Attempt status changes use `FinancialTransitionPolicy`; illegal transitions fail and equivalent transitions are idempotent.
- Provider order, transaction and event identities are write-once.
- Attempt ownership requires the reservation and configuration to belong to the same property.
- Ledger records are append-only, positive scale-zero VND evidence with unique effect identity.
- Ledger property, reservation, attempt and original transaction ownership must agree; `invoice_id` remains an immutable reference until the context-owned invoice aggregate is implemented.
- Refund ledger records require an original transaction; non-refunds cannot attach one.
- Attempt rows use optimistic versioning; repositories expose a pessimistic lock for callback/application processing.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=PropertyPaymentModelTest,PropertyPaymentPersistenceIntegrationTest,TenantFilterArchitectureTest' -DforkCount=0 test
```

Result on 2026-07-31:

- Tests run: 9
- Failures: 0
- Errors: 0
- Skipped: 0
- Build: SUCCESS

The persistence test verified that enabled Hibernate filters hide attempts and ledger rows owned by another property, including derived repository lookups. It also verified optimistic version advancement after a controlled state transition.

## Permissions and Environment

- Permission validation: N/A for this persistence-only task; no API or mutation endpoint was added.
- Provider execution: N/A; no external provider, sandbox merchant or real-money call was used.
- Production configuration/database: not accessed or changed.

## Schema and Recovery

- New migration: N/A. T052 maps the additive V21 tables already validated by the feature migration suite.
- Forward recovery: disable consumers of the new repositories while retaining append-only ledger evidence, then correct mapping/service code in a later deployment.
- Code rollback: remove the new entity/repository registrations and tenant filter names before any service writes depend on them. Do not delete persisted financial evidence.
