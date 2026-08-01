# Property Commerce Revenue Reconciliation

**Bounded context**: guest-to-property booking money  
**API**: `GET /api/management/reports/property-revenue`  
**Export**: `GET /api/management/reports/property-revenue/export`  
**As-of evidence**: 2026-08-02

## Recognition equations

All amounts are integer VND and come from immutable financial evidence. Invoice allocations are reconciliation evidence and are not added to collected revenue a second time.

| Basis | Equation | Executed fixture result |
|---|---|---|
| Cash collected | successful property debits - property refund credits | `1,000,000 - 100,000 = 900,000` |
| Invoiced | finalized invoice gross - immutable credit notes | `1,000,000 - 150,000 = 850,000` |
| Net | gross - refunds - credits | checked by `ReportTotals` and every detail/breakdown row |
| Unpaid | finalized invoice balance - applicable credit notes, minimum zero | `200,000` in the service fixture |
| Held deposits | successful booking deposit - invoice allocations - linked refunds, minimum zero | `100,000` in the service fixture |

The invoiced-basis test also proves that a prior-period credit note remains visible when its original invoice falls outside the selected range. That fixture produces `0 - 150,000 = -150,000` net rather than silently dropping the correction.

## Database reconciliation fixture

`PropertyRevenueReconciliationIntegrationTest` persists:

- selected property debit: `1,000,000` VND;
- linked selected property refund: `100,000` VND;
- unrelated property debit: `7,000,000` VND.

The report is generated through the real repository/service path and compared with a direct H2 ledger aggregate. Gross, refund, net and row count match to one VND. The `7,000,000` VND other-property transaction is excluded, and the fixture emits no reconciliation issue.

## Mismatch queue behavior

The property report detects and returns stable, read-only issues without modifying ledger, invoice or credit-note evidence:

| Code | Condition |
|---|---|
| `ALLOCATION_TRANSACTION_MISSING` | Allocation references no authoritative transaction |
| `ALLOCATION_EXCEEDS_TRANSACTION` | Cumulative allocation exceeds immutable transaction amount |
| `INVOICE_ALLOCATION_MISMATCH` | Invoice paid amount differs from allocation total |
| `INVOICE_LINE_TOTAL_MISMATCH` | Finalized invoice header differs from immutable line effects |
| `CREDIT_NOTE_LINE_MISMATCH` | Credit-note header differs from credit-note lines |

The intentional mismatch unit fixture creates over-allocation, paid/allocation divergence, invoice-line divergence and credit-note-line divergence. It returns four issues, marks affected rows `MISMATCH` and reports two distinct unreconciled sources. These are expected negative-test results, not unresolved production-data findings.

## Export and UI parity

- CSV, OOXML Excel and PDF are rendered from the same `RevenueReportResult` returned by the report service.
- Every format carries normalized filters, totals, detail identifiers, row count and one SHA-256 checksum.
- Repeated rendering is byte-for-byte deterministic in the export integration test.
- The management dashboard keeps the active `propertyId`, exposes gross/refund/net/unpaid/held values and displays the reconciliation queue.
- Playwright proves `propertyId=11` remains in filtered requests and `propertyId=99` receives the tenant-safe not-found state.

## Security and executed evidence

- Report: `REPORT/VIEW`.
- Export: `REPORT/EXPORT`.
- Explicit property IDs pass through authenticated `PropertyAccessService` validation.
- Property filters cannot be passed to Platform Billing, and property services reject the platform context before repository access.

Executed evidence:

- `PropertyRevenueServiceTest`: calculation, negative correction and mismatch queue.
- `PropertyRevenueReconciliationIntegrationTest`: direct database aggregate parity.
- `RevenueExportServiceTest` and `RevenueExportIntegrationTest`: three-format parity and determinism.
- `FinancialReportingSecurityIntegrationTest`: independent view/export permissions.
- `financial-reporting.spec.ts`: browser filter and property-isolation journey.

No production credential, external merchant, production migration or real-money transaction was used. The deterministic database fixture has zero unexplained mismatches.
