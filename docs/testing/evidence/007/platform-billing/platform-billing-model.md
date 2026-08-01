# T092 Platform Billing Model and Persistence Boundary

## Scope

- Added system-scoped platform merchant configuration mapped to `platform_payment_configurations`.
- Added backend-snapshotted subscription orders and mutable provider attempts mapped to V24.
- Added append-only platform ledger transactions with unique economic-effect identities.
- Added software contract versions, current entitlements and append-only subscription history mapped to V25.
- Added repository lookups and pessimistic locks for order, attempt, contract and entitlement application boundaries.

## Enforced Invariants

- Property payment configuration is never referenced by Platform Billing entities.
- Prices and financial effects use exact scale-zero VND values.
- Orders retain plan identity, version, billing period, duration and feature snapshots after creation.
- Payment attempts retain the selected system provider/environment and immutable expected amount.
- Platform ledger and history records reject application-level update/delete operations.
- Credits/refunds require an original transaction; debit transactions cannot reference one.
- Contract, entitlement and history ownership checks compare persistent identifiers safely across Hibernate proxies.
- Production configuration fails closed without approval evidence.

## Automated Validation

Commands from `backend/`:

```powershell
.\mvnw.cmd -DskipTests compile
$env:JWT_SECRET='feature007-platform-billing-test-key-at-least-32-bytes'
.\mvnw.cmd '-Dtest=BackendApplicationTests,PlatformBillingModelTest' -DforkCount=0 test
```

Result on 2026-08-01:

- `PlatformBillingModelTest`: 3 passed, 0 failed, 0 skipped.
- `BackendApplicationTests`: 1 passed, 0 failed, 0 skipped.
- Spring context discovered 64 repository interfaces without bean-name collisions.
- Compile and test builds: SUCCESS.
- The JWT value was an ephemeral test-only key required by the existing test profile and was not persisted.

## Permissions and Environment

- Permission validation: N/A for this entity/repository-only task; no API endpoint was added.
- Provider execution: N/A; tests use in-memory model fixtures and no external callback or merchant.
- Production credentials, production database and real-money operations were not used.

## Schema and Recovery

- New migration: N/A. T092 maps the additive V24/V25 tables already validated by the migration tasks.
- Forward recovery: disable Platform Billing service consumers, retain immutable ledger/history evidence and correct mappings in a later additive deployment.
- Code rollback: remove repository/entity registration only before service writes depend on it; never delete persisted financial evidence.
