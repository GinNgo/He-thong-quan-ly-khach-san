# T134 Revenue Export Reconciliation

The file-level integration test generates CSV, OOXML Excel and deterministic PDF from one Platform Billing report result containing a purchase and refund.

All three artifacts are checked for the same:

- normalized date range, provider, method and plan filters;
- gross/refund/net totals;
- purchase/refund detail row identifiers;
- SHA-256 report checksum and row count.

Repeated rendering is byte-for-byte deterministic for every format. The export service was also tightened so every format carries all normalized filter fields and all report totals, including cash, invoiced, unpaid, held deposits and transaction counts.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=RevenueExportServiceTest,RevenueExportIntegrationTest' -DforkCount=0 test
```

Result on 2026-08-02: 3 tests passed, 0 failed, 0 skipped.

No production credentials, external provider, migration or real-money operation was used.
