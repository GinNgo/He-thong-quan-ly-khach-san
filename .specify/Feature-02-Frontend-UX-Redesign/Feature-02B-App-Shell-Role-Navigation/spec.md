# Specification: Feature-02B App Shell, Sidebar, Header & Role Navigation

**Status:** IN PROGRESS
**Branch:** `feature/frontend-ux-redesign`
**Depends on:** Feature-02A PASSED (`1d393df`)
**Scope:** Angular shell/navigation only; no backend contract or authorization change.

## Clarified source facts

1. `/admin/**` uses `AdminLayout`, `authGuard`, route-level `permissionGuard`, and `/api/auth/my-menu`.
2. `Sidebar` currently substitutes a static fallback after menu API failure. This can show links outside actor menu even though backend still enforces authorization.
3. Admin shell already has header, notification API/WebSocket, user menu, collapse control and route outlet, but lacks loading/error semantics, mobile drawer behavior, breadcrumbs and complete route labels.
4. `/management/**` uses `ManagementLayout`; `ManagementApiService.context(activePropertyId?)` already returns tenant-scoped properties and active property.
5. Management menu exposes dead links `/management/bookings` and `/management/staff`; neither route exists.
6. Management dropdown is hover-only, layout has no mobile breakpoint, and auth subscription uses `any` without teardown.
7. Frontend menu remains UX only. Backend RBAC, tenant isolation and 401/403/404 contracts remain unchanged.

## User stories

### US-02B-01 — Authorized staff navigation

As authorized staff, I see only functions returned by `auth/my-menu`, clear loading/error states, current-route indication and a retry action when menu loading fails.

### US-02B-02 — Responsive admin shell

As staff using phone, tablet, keyboard or desktop, I can open/close navigation, identify current page through heading and breadcrumb, operate notification/user menus, and reach content without clipping.

### US-02B-03 — Property management context

As property actor, I can select one property from tenant-scoped management context and keep its ID in navigation state without exposing an unverified property.

### US-02B-04 — Valid management navigation

As property actor, every visible management link resolves to an existing route, exposes active state and remains keyboard-operable on mobile.

## Functional requirements

- **FR-02B-001:** Sidebar MUST render only deduplicated functions returned by `/auth/my-menu`; API failure MUST show retryable error, not privileged fallback links.
- **FR-02B-002:** Sidebar MUST expose loading, empty and error states with accessible status semantics.
- **FR-02B-003:** Active route MUST be visually and semantically identifiable.
- **FR-02B-004:** Desktop collapse and mobile drawer MUST use one labelled toggle and expose `aria-expanded`.
- **FR-02B-005:** Mobile drawer MUST provide backdrop, Escape close and close after navigation.
- **FR-02B-006:** Header MUST provide page heading, breadcrumb, labelled search, notifications and user menu.
- **FR-02B-007:** Notification and user controls MUST expose expanded state, popup labels and keyboard-close behavior.
- **FR-02B-008:** Management shell MUST load property options from `ManagementApiService.context`; selector MUST never invent property IDs.
- **FR-02B-009:** Selecting property MUST validate through context API and preserve selected ID in route query state.
- **FR-02B-010:** Management menu MUST not render links without configured routes.
- **FR-02B-011:** Existing auth/permission guards and backend security contracts MUST remain unchanged.
- **FR-02B-012:** No new dependency, backend endpoint, public route or production data change is allowed.

## Non-functional requirements

- 360 px width and 200% text zoom retain reachable navigation/content.
- Visible focus uses Feature-02A focus tokens.
- Motion obeys `prefers-reduced-motion`.
- Subscriptions terminate with component lifecycle.
- No `any` added to shell implementation.
- No uncaught console error in shell route smoke tests.

## Success criteria

- **SC-02B-001:** Sidebar unit tests prove success deduplication, loading/error handling, retry and no fallback links.
- **SC-02B-002:** Shell unit tests prove menu state, property validation, dead-route exclusion and session actions.
- **SC-02B-003:** Frontend full tests and production build exit 0.
- **SC-02B-004:** Browser smoke passes admin/management routes at 360 px and desktop with zero uncaught errors.
- **SC-02B-005:** Maven regression remains 86 or more tests with zero failure/error/skipped.
- **SC-02B-006:** Explicit staged files pass whitespace and secret/artifact scan.
- **SC-02B-007:** Green commit is pushed non-force to `origin/feature/frontend-ux-redesign`.

## Out of scope

- New reservation/staff management routes.
- Backend menu, permission or tenant contract changes.
- Child-page inventory redesign; Feature-02C owns page data behavior.
- Notification backend changes.