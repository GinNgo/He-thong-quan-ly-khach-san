# T335 - Platform Billing revenue dashboard

## Outcome

The Platform Billing report is now verified end to end from persisted SaaS ledger rows through report totals and transaction rows to the exported artifact metadata. The same focused reconciliation runs on H2 and an isolated SQL Server 2022 database.

## Database, export and isolation evidence

- A PRO purchase, refund and downgrade credit reconcile to database gross, refund, credit and net totals to one VND; an unrelated BASIC purchase is excluded by the selected plan filter.
- The sum of exported report row net amounts equals the report net total, the artifact row count equals the database transaction count and repeated exports retain the same canonical SHA-256 checksum.
- Platform rows carry no property identifier, and the CSV contains the selected PRO rows and totals without the BASIC transaction.
- The Chromium journey verifies the plan filter, Excel download name, checksum header, row-count header and checksum embedded in the downloaded fixture while proving no property-report API is called.
- The SQL Server runner uses a random local database and credential and removes the exact named container and environment values after validation.

## Verification

| Check | Result |
|---|---|
| `PlatformRevenueReconciliationIntegrationTest` on H2 | PASS, 1/1 |
| `platform-revenue-sqlserver-validation.ps1` on SQL Server 2022 | PASS, 1/1; Microsoft JDBC connection and SQL Server dialect confirmed |
| `platform-revenue.component.spec.ts` | PASS, 1/1 |
| Focused Chromium Platform Billing filter/export journey | PASS, 1/1 |
| `npm run build -- --configuration production` | PASS |

Visual evidence: `T335-platform-revenue-database-export.png`.

No production credentials, real payments, destructive migrations or shared aggregate files were used.
