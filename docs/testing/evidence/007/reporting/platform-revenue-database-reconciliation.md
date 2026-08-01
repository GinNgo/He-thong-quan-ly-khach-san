# T133 Platform Revenue Database Reconciliation

The H2 integration fixture persists a 1,000,000 VND subscription purchase, 100,000 VND refund and 50,000 VND downgrade credit for plan `PRO`. A successful simulator payment attempt is linked to the purchase debit. A separate 7,000,000 VND `BASIC` purchase verifies the system-scoped plan filter without introducing any property tenant predicate.

The real platform JPQL repository/service result is compared with a direct database aggregate. Gross, refunds, credits, net, detail row count and successful transaction count match to one VND, and the successful attempt produces no false missing-ledger mismatch.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=PlatformRevenueReconciliationIntegrationTest' -DforkCount=0 test
```

Result on 2026-08-02: 1 integration test passed, 0 failed, 0 skipped; full H2 schema, all platform report queries and direct system-ledger aggregate executed successfully.

Only the simulator environment was used. No production credentials, external provider, migration or real-money operation was used.
