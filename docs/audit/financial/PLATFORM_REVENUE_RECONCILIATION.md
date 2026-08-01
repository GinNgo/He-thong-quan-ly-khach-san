# Platform Billing Revenue Reconciliation

**Bounded context**: owner-to-platform subscription money  
**API**: `GET /api/admin/reports/platform-revenue`  
**Export**: `GET /api/admin/reports/platform-revenue/export`  
**As-of evidence**: 2026-08-02

## Recognition equations

Platform Billing is system-scoped. It accepts plan/provider/method/transaction filters and never accepts a property identifier.

| Basis | Equation | Executed fixture result |
|---|---|---|
| Cash collected | successful platform debits | `1,500,000` VND in the service fixture |
| Refunds | immutable platform refund credits | `100,000` VND |
| Credits | downgrade/proration policy credits | `50,000` VND |
| Net | gross - refunds - credits | `1,500,000 - 100,000 - 50,000 = 1,350,000` |
| Unpaid | unpaid authoritative subscription order balances | `300,000` VND in the service fixture |

Failed, cancelled, expired and pending attempts are excluded from collected money. Attempt status still appears in provider-status breakdowns so operational failures remain visible without inflating revenue.

## Database reconciliation fixture

`PlatformRevenueReconciliationIntegrationTest` persists:

- `PRO` purchase debit: `1,000,000` VND;
- `PRO` refund: `100,000` VND;
- `PRO` downgrade credit: `50,000` VND;
- successful simulator attempt linked to the purchase;
- unrelated `BASIC` purchase: `7,000,000` VND.

For the `PRO` filter, the direct database aggregate and report both produce:

`1,000,000 - 100,000 - 50,000 = 850,000` VND net.

Gross, refunds, credits, net, row count and successful transaction count match to one VND. The `BASIC` transaction is excluded, and the linked successful attempt emits no false missing-ledger issue.

## Mismatch queue behavior

| Code | Condition |
|---|---|
| `PLATFORM_PAYMENT_LEDGER_MISSING` | Successful provider attempt has no immutable platform debit |
| `PLATFORM_PAYMENT_AMOUNT_MISMATCH` | Successful attempt amount differs from its immutable ledger transaction |
| `REPORT_ROW_GROSS_MISMATCH` | Reconciliation runner row gross differs from report gross |
| `EXPORT_CHECKSUM_MISMATCH` | Export checksum differs from the canonical report checksum |

The negative service fixture includes one linked success, one success without ledger and one failed attempt. It reports one failed transaction, one unreconciled source and one `PLATFORM_PAYMENT_LEDGER_MISSING` issue. The reconciliation-runner negative fixture additionally proves one row mismatch and one checksum mismatch per CSV/Excel/PDF artifact without mutating the report.

These are intentional negative-test results. The deterministic database reconciliation fixture has zero unexplained mismatches.

## Export and UI parity

- CSV, OOXML Excel and PDF use the exact normalized platform report result.
- Date range, provider, method, transaction type, plan, totals, rows, checksum and row count are identical across formats.
- Export endpoints return deterministic media type, filename, checksum and row-count headers and require `PLATFORM_REVENUE/EXPORT`.
- The admin dashboard labels the system boundary, exposes gross/refund/credit/net values, plan mix, provider status, mismatch queue and recoverable export states.
- Playwright proves the platform request carries `planCode=PRO`, never sends `propertyId`, downloads Excel and never calls the Property Commerce report endpoint.

## Security and executed evidence

- Report: `PLATFORM_REVENUE/VIEW`.
- Export: `PLATFORM_REVENUE/EXPORT`.
- A caller with property `REPORT` permission receives `403` for platform reporting.
- Platform services reject Property Commerce filters before repository access.
- No property tenant filter or property financial table participates in platform totals.

Executed evidence:

- `PlatformRevenueServiceTest`: purchase/refund/credit equations and missing-ledger behavior.
- `PlatformRevenueReconciliationIntegrationTest`: direct database aggregate and plan-filter parity.
- `RevenueExportServiceTest` and `RevenueExportIntegrationTest`: three-format parity and determinism.
- `FinancialReconciliationServiceTest`: reconciled run plus source/row/export mismatch queue.
- `FinancialReportingSecurityIntegrationTest`: independent view/export permissions.
- `financial-reporting.spec.ts`: browser filter, export and bounded-context isolation.

Only simulator/deterministic fixtures were used. No production credential, external merchant, production migration or real-money transaction was used.
