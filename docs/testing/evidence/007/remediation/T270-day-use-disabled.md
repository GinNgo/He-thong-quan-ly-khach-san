# T270 Day-Use Disabled Evidence

Date: 2026-08-04
Branch: `codex/public-booking`
Base: `origin/codex/ui-functional-audit-polish` at `88f7da5`
Capability: `PUB-002`
Production credentials, production data, destructive migration, real-money operation, or new financial policy: N/A

## Implemented Contract

- The public home state is now compile-time restricted to `OVERNIGHT`.
- The disabled day-use presentation remains visibly labelled as coming soon but no longer exposes a `DAY_USE` form value or selection handler.
- Home search URLs no longer generate `stayType`.
- Search-page API forwarding strips a stale incoming `stayType` parameter, so bookmarked legacy URLs cannot send an unsupported day-use request to the backend.
- Overnight check-out validation remains mandatory. No hourly inventory, price, capacity, checkout, or financial rule was invented.

## Focused Unit Tests

```powershell
Set-Location frontend
npx vitest run src/app/features/client/home/services/home-search-state.service.spec.ts --config vitest-base.config.ts --globals
```

Result: 5/5 passed.

The suite proves the state remains overnight-only and generated search query parameters do not contain `stayType`.

## API-Backed Browser Test

The browser used the real Angular application and Spring/H2 public search API on isolated ports. The backend reused existing compiled application classes with the branch's `LocationImportService` and `DemoDataInitializer` compiled into temporary test-only output directories because unrelated BOM/source drift prevents a normal Maven compile in this worktree.

```powershell
Set-Location frontend
$env:PLAYWRIGHT_EXTERNAL_BACKEND='true'
$env:PUBLIC_BOOKING_BACKEND_PORT='28743'
$env:PUBLIC_BOOKING_FRONTEND_PORT='42769'
npx playwright test --config playwright.api.config.ts --grep 'day-use unavailable'
```

Result: 1/1 passed.

The test proves the page exposes no `DAY_USE` input, the navigated search URL contains no `stayType`, the real HTTP request contains no `stayType`, and API-backed property results render successfully.

## Angular Build

```powershell
Set-Location frontend
npm run build -- --configuration development --progress=false
```

Result: PASS after temporarily supplying the two i18n service files owned by the parallel i18n scope. The compatibility files were removed after validation and are not part of this branch.

## Permissions and Isolation

- Search remains an anonymous public read path.
- Tenant mutation and property ownership checks are N/A because this task creates no booking, payment, property, or reservation mutation.
- The isolated H2 database was discarded after the browser run.

## Recovery

No migration or configuration change exists. Reverting the T270 commit restores the previous client behavior. Day-use must remain disabled until separately approved hours, overlap, capacity, pricing, checkout, and booking contracts exist.
