# T130 Shared Revenue Export

`RevenueExportService` accepts the already-normalized `RevenueReportResult` returned by either bounded-context report service. It does not query a second data source or recalculate totals, so CSV, OOXML Excel and deterministic PDF outputs share the same rows, filters, totals and SHA-256 checksum.

The Excel artifact is a dependency-free OOXML zip package, the PDF uses the existing deterministic lightweight renderer pattern, and CSV uses stable quoting for commas, quotes and newlines. Each artifact includes the report context, recognition basis, filter range/zone, totals, detail rows, reconciliation issues and checksum.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=RevenueExportServiceTest' -DforkCount=0 test
```

Result on 2026-08-02: 2 tests passed, 0 failed, 0 skipped. Tests verify cross-format checksum/row parity, valid PDF/OOXML signatures, checksum sensitivity and defensive byte-array copying.

No production credentials, external provider, migration or real-money operation was used.
