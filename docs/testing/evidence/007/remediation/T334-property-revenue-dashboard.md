# T334 - Property revenue dashboard

## Outcome

The management property revenue report now handles large result sets with 50 rows per page and exposes the existing tenant-scoped CSV, Excel and PDF export API from the UI.

## Reconciliation and tenant evidence

- The repository-backed report is reconciled against raw property financial transactions on both H2 and an isolated SQL Server 2022 database, with totals matching to one VND.
- The SQL Server validation script creates a random local database and credential, runs only the focused reconciliation test, removes the environment values and deletes the exact named container.
- The browser fixture supplies 120 ledger rows and verifies filtering, page 2 navigation, CSV download and denial when a foreign property identifier is requested.
- Export actions reuse the selected canonical property scope and the existing authenticated property export endpoint.

## Verification

| Check | Result |
|---|---|
| `PropertyRevenueReconciliationIntegrationTest` on H2 | PASS, 1/1 |
| `property-revenue-sqlserver-validation.ps1` on SQL Server 2022 | PASS, 1/1; Microsoft JDBC connection and SQL Server dialect confirmed |
| `property-revenue.component.spec.ts` | PASS, 1/1 |
| Focused Chromium financial reporting journey | PASS, 1/1 |
| `npm run build -- --configuration production` | PASS |

Visual evidence: `T334-property-revenue-pagination-export.png`.

No production credentials, real payments, destructive migrations or shared aggregate files were used.
