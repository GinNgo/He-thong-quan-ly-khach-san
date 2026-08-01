# T101 Subscription Order Snapshot, Expiry and Policy Tests

## Coverage

- Backend catalog price, duration, plan identity and feature limits are snapshotted into the order.
- Equivalent idempotent replay returns the original snapshot without rereading a changed catalog.
- Order expiry is calculated from the configured bounded duration; unsafe values below 1 or above 1440 minutes are rejected.
- Monthly renewal duration is not hard-coded to one year.
- Inactive plans and lifetime renewal conversion are rejected before persistence.
- A second open upgrade/renewal order is blocked before reading a changed catalog.
- Reusing an idempotency key for a different target is rejected before catalog read or persistence.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=SubscriptionOrderServiceTest' -DforkCount=0 test
```

Result on 2026-08-01:

- `SubscriptionOrderServiceTest`: 8 passed, 0 failed, 0 errors, 0 skipped.
- Test build: SUCCESS.

## Safety

- Tests use mocked repositories and a fixed clock only.
- No database mutation, provider call, production credential or real-money operation is used.
- Schema and rollback: N/A; T101 changes tests/evidence only.
