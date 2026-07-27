# Plan: Feature-02B App Shell & Role Navigation

**Status:** APPROVED FOR IMPLEMENTATION

## Approach

1. Harden existing `Sidebar`; remove unsafe fallback, add explicit loading/error/empty/retry state and navigation event.
2. Refactor existing `AdminLayout` markup/CSS into responsive semantic shell; retain notification/profile APIs.
3. Refactor existing `ManagementLayout`; typed auth lifecycle, route-backed menu only, accessible mobile drawer/user menu, breadcrumb and context-backed property selector.
4. Reuse Angular Router, RxJS, Bootstrap/PrimeNG icons and Feature-02A tokens. Add no dependency.
5. Add focused shell tests, then full frontend tests/build, browser smoke and Maven regression.

## Contracts retained

- `/api/auth/my-menu` remains actor menu source.
- `/api/management/context?activePropertyId=` remains property context source and tenant validator.
- `authGuard`, `clientAuthGuard`, `permissionGuard`, interceptor and backend security remain unchanged.
- Property selection travels as `propertyId` query param; child pages may adopt shared state in Feature-02C.

## Files

- `frontend/src/app/layout/sidebar/*`
- `frontend/src/app/layout/admin-layout/*`
- `frontend/src/app/layout/management-layout/*`
- Focused `*.spec.ts`
- Feature-02 roadmap/audit/spec artifacts

## Verification sequence

1. Focused Vitest shell tests.
2. Full `npm test -- --watch=false`.
3. `npm run build -- --configuration production`.
4. Browser route/responsive/console smoke.
5. `backend\mvnw.cmd -q -f backend\pom.xml test`.
6. Git whitespace and staged-content gates.
7. Explicit commit and non-force push.

## Risks and controls

- Menu API failure: show retry only; never invent authorization.
- Context mismatch: accept only ID returned as active from server.
- Mobile focus: labelled buttons, Escape close, backdrop close.
- Existing dirty tree: stage explicit Feature-02B files only.