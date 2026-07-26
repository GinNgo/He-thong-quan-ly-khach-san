# Implementation Plan: Feature-02 Frontend UX Redesign

**Branch:** `feature/frontend-ux-redesign`
**Status:** IN PROGRESS
**Constitution:** `.specify/memory/constitution.md` v1.1.0 — no exception requested.

## Technical context

- Angular 22 standalone components, TypeScript strict mode.
- PrimeNG 22 with centralized preset; Bootstrap, Tailwind utilities and PrimeIcons already installed.
- Spring Boot backend, JUnit/Maven regression baseline at least 86 tests.
- Three route shells: client, admin and management.
- Existing shared controls: select, date picker, filter panel, form/confirm dialog, data table, stat card and charts.
- Feature-01 provides JWT, 401/403 behavior, permission guards and backend tenant/ownership enforcement.

## Constitution check

| Gate | Decision |
|---|---|
| Real integration | Use existing API; no production mock data |
| E2E quality | Keep Playwright route/customer flows; add tests only against supported test environment |
| Security | No `permitAll`, guard removal or tenant relaxation |
| UX consistency | Shared tokens/components before page CSS |
| Minimal change | Reuse PrimeNG and current shared controls; no dependency |
| Evidence | Test/build/runtime/Git evidence required before PASSED |

## Architecture

1. **Presentation baseline:** Extend PrimeNG preset and `styles.css` semantic tokens; add one reusable feedback-state component for loading/empty/error/success/confirmation.
2. **Shell:** Refine existing `ClientLayout`, `AdminLayout`, `ManagementLayout`; derive visible navigation from current permission service while backend stays authoritative.
3. **Domain slices:** Audit Angular service and matching Java controller/service/DTO first, then adjust page and minimal API only when gap is proven.
4. **Quality convergence:** Unit tests for state/route/mutation behavior, production build, Playwright smoke where environment supports it, Maven security regression.
5. **Delivery:** Explicit staging, secret/artifact scan, small commit and non-force push per green sub-feature.

## Sub-feature execution

Each `Feature-02X-*` directory contains `spec.md`, `plan.md`, `tasks.md`, `checklist.md`, and `acceptance-tests.md`.

For every slice:

1. Clarify source and current contract.
2. Write independently testable scenarios.
3. Implement shortest source-grounded diff.
4. Run related tests, frontend production build and required backend regression.
5. Update artifact evidence.
6. Run consistency and Git gates.
7. Commit explicit files and push.
8. Mark PASSED only after remote evidence.

## Testing strategy

- **Unit:** shared component semantics, route data, guards/interceptors and duplicate-submit behavior.
- **Build/type:** configured Angular production build; lint only when package script exists.
- **Integration:** Angular service contract and Java MVC/security tests.
- **Runtime:** browser console, route smoke and responsive widths 360/768/1280.
- **Security:** no-token 401, permission 403, cross-tenant 404, customer ownership and payment idempotency/signature.
- **Git:** `diff --check`, staged `diff --check`, explicit staged inventory and secret/binary/temp scan.

## Risk controls

- Generic pages and `any` responses: type only after DTO audit.
- Missing list pagination: verify Java repository/controller before change.
- Existing dirty local files: never bulk-add, restore, clean or hard reset.
- Payment flow: no real payment; test simulator/test contract only.
- Large scope: blocked work documented without committing broken code.