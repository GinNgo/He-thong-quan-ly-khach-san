# T269 Home Search Journey Evidence

Date: 2026-08-04
Branch: `codex/public-booking`
Base: `origin/codex/ui-functional-audit-polish` at `88f7da5`
Capability: `PUB-001`
Production credentials, production data, destructive migration or real-money operation: N/A

## Implemented Contract

- Home search now exposes typed missing/past/invalid date errors instead of silently ignoring submit.
- Hero and sticky search keep invalid mobile sheets open, announce the error, mark date triggers invalid and return keyboard focus to the submitted date control.
- Date and guest selectors use semantic buttons; guest increment/decrement controls have stable accessible names.
- URL serialization continues to carry destination, property type, dates, adults, children and room quantity through search and property detail.
- A dedicated Playwright configuration starts an H2 E2E backend and deterministic local seed without production credentials. It supports isolated frontend/backend ports for parallel agents.

## Focused State Tests

```powershell
Set-Location frontend
npx vitest run src/app/features/client/home/services/home-search-state.service.spec.ts --config vitest-base.config.ts --globals
```

Result: 4/4 passed.

Covered missing check-in, invalid overnight range, complete URL serialization and clearing stale validation after a date change.

## API-Backed Browser Tests

The browser used the real Spring public APIs against an isolated H2 `create-drop` database seeded from the current location dataset and local demo inventory. Browser requests were reverse-proxied by Playwright only because Docker Desktop already owned local ports 8080 and 18080; response bodies came from Spring and were not mocked.

```powershell
Set-Location frontend
$env:PLAYWRIGHT_EXTERNAL_BACKEND='true'
$env:PUBLIC_BOOKING_BACKEND_PORT='28743'
$env:PUBLIC_BOOKING_FRONTEND_PORT='42769'
npx playwright test --config playwright.api.config.ts
```

Result: 2/2 passed in 1.0 minute.

- Desktop: selected HOTEL plus current province, raised adults/rooms, received non-empty HTTP 200 search results, opened detail, returned to the exact search URL and verified the full protected booking URL was encoded in `returnUrl`.
- Mobile: cleared the date range through the real date picker, received the visible validation alert, retained the page and returned focus to the invalid date trigger.

## Build and Backend Harness Checks

```powershell
Set-Location frontend
npm run build -- --configuration development --progress=false
```

Result: PASS after supplying the parallel i18n agent's two missing service files as a temporary, uncommitted compatibility overlay. The overlay was removed after validation and is not part of this branch.

```powershell
Set-Location backend
javac -encoding UTF-8 -cp <existing-compiled-main-and-maven-classpath> `
  -d target/t269-compile src/main/java/com/hotel/services/impl/DemoDataInitializer.java
```

Result: isolated E2E seed class compile PASS.

The normal focused Maven command was attempted but the base branch stops before tests because `UserService.java` and `UserController.java` contain a UTF-8 BOM. Both files are already modified in the root worktree by another parallel scope, so T269 did not overwrite or duplicate that work. The API-backed browser run used the already compiled application classes plus the same H2/current-location/demo seed properties.

## Permissions and Isolation

- Search, suggestion and public detail reads remain anonymous public endpoints.
- The protected booking URL is denied to an anonymous browser and preserved exactly by `clientAuthGuard` for post-login recovery.
- Tenant mutation is N/A; this task performs no property-owned write.
- All backend data was in-memory H2 and discarded when the test process stopped.

## Recovery

No schema or migration change exists. Rollback is the T269 commit revert; E2E seed behavior is restricted to the explicit `e2e` profile and local configuration.

## Follow-On Boundaries

- Day-use remains disabled and is owned by T270/PUB-002.
- The legacy detail room endpoint rejects the default same-day range while search accepts it; authoritative search/detail availability consistency remains T280/PUB-012.
- Full login/register/session-expiry recovery remains T286/PUB-019.
