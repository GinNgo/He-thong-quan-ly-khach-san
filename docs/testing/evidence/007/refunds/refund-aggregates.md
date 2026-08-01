# T112-T113 Refund Aggregates and Repositories

## Implemented Boundaries

- Property refunds bind to one tenant-owned `PropertyFinancialTransaction` debit and carry the same `hotel_id` tenant filter.
- Platform refunds bind to one `PlatformFinancialTransaction` debit and its `SubscriptionOrder`; no property-commerce entity is referenced.
- Both contexts persist request identity, VND amount, lifecycle state, optimistic version and provider-attempt evidence in the V23/V25 tables.
- Repository lock methods provide the row-lock boundary required by T114/T115 concurrency processing.

## Validation

Command from `backend/`:

```powershell
.\mvnw.cmd -DskipTests '-Dstyle.color=never' test-compile
```

Result on 2026-08-02: BUILD SUCCESS; 391 main sources and 112 test sources compiled.

No production provider call, real-money operation or migration execution was performed.
