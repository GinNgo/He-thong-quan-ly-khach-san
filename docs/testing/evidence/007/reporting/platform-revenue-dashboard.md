# T138 - Platform revenue dashboard evidence

## Scope

The admin portal now exposes `/admin/platform-revenue` behind `PLATFORM_REVENUE/VIEW`. The page is explicitly system-scoped and labels that Property Commerce booking revenue is excluded.

Implemented surfaces:

- Date, recognition basis, plan, provider, method and transaction-type filters.
- Platform gross, refunds, credits, net and reconciliation health cards.
- Responsive plan/transaction mix chart, breakdown table, SaaS ledger and mismatch queue.
- CSV, Excel and PDF export controls with loading, success and recoverable error states.
- Frontend `PLATFORM_REVENUE` permission mapping, guarded lazy route and admin quick-link title mapping.

The contract-defined property and platform `/export` endpoints were also wired to the shared `RevenueExportService`. Both endpoints reuse the same normalized report result, require context-specific `EXPORT` permissions and return deterministic filename, media type, checksum and row-count headers.

## Validation

| Check | Result |
|---|---|
| Angular focused spec | PASS - 1 test |
| `PropertyRevenueControllerTest` | PASS - 4 tests |
| `PlatformRevenueControllerTest` | PASS - 3 tests |
| `FinancialReportingSecurityIntegrationTest` | PASS - 3 tests |
| Focused Maven aggregate | PASS - 10 tests, 0 failures/errors/skips |
| `git diff --check` | PASS |

Angular test command:

```powershell
npx ng test --no-watch --no-progress --include src/app/features/admin/platform-revenue/platform-revenue.component.spec.ts
```

Backend test command:

```powershell
.\mvnw.cmd '-Dtest=PropertyRevenueControllerTest,PlatformRevenueControllerTest,FinancialReportingSecurityIntegrationTest' -DforkCount=0 test
```
