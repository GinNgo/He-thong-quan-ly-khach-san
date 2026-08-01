# T132 Property Revenue Database Reconciliation

The H2 integration fixture persists one selected property debit of 1,000,000 VND, a linked 100,000 VND refund and a 7,000,000 VND debit for another property. The report is generated through the real JPQL repository/service path and compared with a direct authoritative database aggregate.

Assertions prove gross, refunds, net and row count match the database to one VND, the other property is excluded and no false reconciliation issue is emitted.

The integration context also exposed ambiguous Spring constructor selection on report/reconciliation services. Their production constructors now carry explicit `@Autowired` annotations, so application startup selects the dependency constructor while package-private clock constructors remain available to deterministic unit tests.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=PropertyRevenueReconciliationIntegrationTest' -DforkCount=0 test
```

Result on 2026-08-02: 1 integration test passed, 0 failed, 0 skipped; full H2 schema, all six property report queries and the direct ledger aggregate executed successfully.

No production credentials, external provider, migration or real-money operation was used.
