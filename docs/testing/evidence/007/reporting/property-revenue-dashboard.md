# T137 - Property revenue dashboard evidence

## Scope

The management portal now exposes `/management/property-revenue` behind the `REPORT/VIEW` permission. The screen uses the typed `RevenueReportService` client and keeps the active property in the management query context.

Implemented evidence surfaces:

- Date range, recognition basis, provider, method, transaction type and room type filters.
- Loading, error, empty and retry states with accessible labels and live status messaging.
- Gross, refund/credit, net, unpaid and held-deposit summary cards.
- Responsive CSS bar chart from reconciled report breakdowns.
- Breakdown table, normalized transaction ledger and reconciliation issue queue.
- Mobile-first stacking, touch-sized controls and reduced-motion transitions using the existing hotel design tokens.

## Validation

| Check | Result |
|---|---|
| `npx ng test --no-watch --no-progress --list-tests --include src/app/features/management/property-revenue/property-revenue.component.spec.ts` | PASS - focused spec discovered |
| `npx ng test --no-watch --no-progress --include src/app/features/management/property-revenue/property-revenue.component.spec.ts` | PASS - Angular test builder exited 0 |
| `npx ng build --configuration development` | PASS - application bundle generated |
| `git diff --check` | PASS |

The focused Angular spec stubs the management context and report client, then verifies the active property name, reconciled state and transaction row are rendered.
