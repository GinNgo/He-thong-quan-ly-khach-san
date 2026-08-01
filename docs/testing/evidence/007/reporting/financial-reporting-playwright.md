# T139 - Financial reporting Playwright evidence

## Journey coverage

`frontend/e2e/financial-reporting.spec.ts` uses deterministic API interception and no real merchant or real-money flow.

1. A property owner opens `/management/property-revenue?propertyId=11`, verifies only Property Commerce evidence is shown, changes the basis/date filters and confirms the outgoing request keeps `propertyId=11`.
2. The same owner requests inaccessible `propertyId=99` and receives the recoverable not-found isolation state.
3. A system admin opens `/admin/platform-revenue`, verifies only Platform Billing evidence is shown, filters by `planCode=PRO` and downloads the Excel blob with the expected filename and export filter.
4. A user with only property `REPORT` permission is redirected to `/403` when opening the platform dashboard.

## Validation

Discovery:

```powershell
npx playwright test e2e/financial-reporting.spec.ts --list --project=chromium
```

Result: 3 tests discovered.

Execution:

```powershell
npx playwright test e2e/financial-reporting.spec.ts --project=chromium --reporter=line
```

Result: PASS, exit code 0, 3/3 Chromium journeys completed.

The first run identified an ambiguous duplicated page/layout heading selector. The test was corrected to target the page-level `h2`, then the full serial journey passed.
