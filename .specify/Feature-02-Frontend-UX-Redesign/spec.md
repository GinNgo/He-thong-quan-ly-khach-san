# Feature Specification: Feature-02 Frontend UX Redesign

**Feature Branch:** `feature/frontend-ux-redesign`
**Created:** 2026-07-26
**Status:** In Progress
**Input:** Redesign LuxeStay UX across system, hotel-operation and customer journeys while preserving Feature-01 security and tenant isolation.

## User Scenarios & Testing

### User Story 1 — Operate a property safely (Priority: P1)

Authorized hotel staff can identify active property, navigate only relevant functions, manage inventory/reservations/billing, and understand loading, empty, error and success outcomes.

**Independent test:** Use authorized hotel-operation account, complete one source-supported workflow, then verify denied and cross-tenant requests retain 403/404 contracts.

**Acceptance scenarios:**

1. **Given** authenticated staff with permission, **when** opening an allowed route, **then** shell, context, content and actions render without redirect loop or uncaught error.
2. **Given** authenticated staff without permission, **when** requesting a restricted mutation, **then** backend returns 403 and UI shows access-denied state.
3. **Given** a resource from another property, **when** its ID is forged, **then** backend returns 404 and UI reveals no foreign data.

### User Story 2 — Complete a customer booking (Priority: P1)

Customer searches by destination/date/guests, inspects availability, books once, receives confirmation, pays through supported contract, and views or cancels only owned bookings.

**Independent test:** Run public search through owned booking lifecycle using real test API and verify request deduplication plus customer ownership.

**Acceptance scenarios:**

1. **Given** valid search criteria, **when** customer submits, **then** server-side query returns pageable results with loading/empty/error behavior.
2. **Given** valid booking data, **when** customer double-clicks submit, **then** client sends at most one in-flight request.
3. **Given** foreign booking/invoice ID, **when** customer requests it, **then** API denies access without exposing data.

### User Story 3 — Administer platform capabilities (Priority: P2)

System administrators manage properties, approvals, users, roles, permissions and subscriptions using consistent tables/forms and explicit transaction feedback.

**Independent test:** Exercise one authorized and one unauthorized action for every administration route group.

**Acceptance scenarios:**

1. **Given** administrator with required permission, **when** saving valid data, **then** operation completes once and confirmation identifies outcome.
2. **Given** actor lacking permission, **when** route is entered directly, **then** UI provides recovery while backend remains authoritative.

### User Story 4 — Use LuxeStay across devices and assistive input (Priority: P1)

Users complete primary journeys at mobile, tablet and desktop widths with keyboard-visible focus, semantic labels and sufficient contrast.

**Independent test:** Route smoke at 360 px, 768 px and 1280 px plus keyboard traversal and automated accessibility checks configured in repository.

**Acceptance scenarios:**

1. **Given** keyboard-only input, **when** traversing interactive controls, **then** focus remains visible and order follows content.
2. **Given** reduced-motion preference, **when** UI changes state, **then** nonessential animation is disabled.
3. **Given** mobile viewport, **when** opening navigation or data views, **then** no essential action becomes unreachable.

## Edge Cases

- Session expires during mutation: stop retry loop, preserve safe form state, redirect once according to auth contract.
- API returns 401/403/404/409/422/500: render distinct recoverable state; never treat 500 as success.
- Empty page after filters: preserve criteria and offer clear/reset action.
- Slow or duplicate input: cancel obsolete reads where appropriate and lock mutations.
- Active property disappears or becomes unauthorized: clear stale context and request valid context from backend.
- Payment callback refreshes: preserve backend signature and idempotency behavior.
- Offline/network error: expose retry only for safe operation; do not silently replay mutation.
- Long Vietnamese labels and 200% zoom: controls wrap without clipping.
- No Java endpoint/DTO evidence: mark API work BLOCKED, do not invent contract.

## Functional Requirements

- **FR-001:** System MUST use shared semantic design tokens for color, typography, spacing, radius, shadow, focus, motion and responsive breakpoints.
- **FR-002:** Reusable controls MUST provide keyboard interaction, accessible names, visible focus and disabled/loading semantics.
- **FR-003:** Every data page MUST expose loading, empty, error and success/confirmation behavior relevant to its workflow.
- **FR-004:** Navigation MUST be route-aware and actor-aware without becoming an authorization boundary.
- **FR-005:** Frontend MUST preserve centralized 401/403 behavior and avoid redirect loops.
- **FR-006:** Backend MUST remain authoritative for permission, tenant scope, customer ownership and subscription limits.
- **FR-007:** Search/list workflows MUST use backend pagination/filter/sort when scale or contract requires it.
- **FR-008:** Mutations MUST validate at trust boundaries, prevent accidental duplicate submission and report backend errors.
- **FR-009:** Backend changes MUST follow controller/service/DTO/repository evidence and remain minimal.
- **FR-010:** No new public endpoint, `permitAll`, tenant-guard removal, fake production data or swallowed exception is permitted.
- **FR-011:** Existing Angular/PrimeNG/Bootstrap/Tailwind stack MUST be reused; no duplicate large UI library.
- **FR-012:** Each sub-feature MUST own spec, plan, tasks, checklist, acceptance tests and convergence evidence.
- **FR-013:** Generated output, secrets, DB files, backups, logs and local artifacts MUST not enter commits.
- **FR-014:** Export actions MUST appear only when backed by working API/client implementation; console-only placeholders are not complete.
- **FR-015:** UI state MUST remain responsive at 360 px, 768 px and 1280 px reference widths.

## Key Entities

- **ActorContext:** authenticated identity, roles, permission masks and authorized properties.
- **PropertyContext:** active authorized property used for UX; backend validates every scoped operation.
- **FeedbackState:** loading, empty, error, success and confirmation presentation.
- **PageQuery:** page, size, search, filters, sort and date/availability range.
- **MutationState:** idle, submitting, succeeded or failed; prevents duplicate in-flight action.
- **Reservation/Invoice/Payment:** existing backend-owned business entities; frontend does not redefine source-of-truth rules.

## Success Criteria

- **SC-001:** All required sub-features reach PASSED or explicitly documented BLOCKED without claiming completion.
- **SC-002:** Frontend unit tests, configured lint/type checks and production build exit 0.
- **SC-003:** Backend Maven regression exits 0 with at least 86 tests, 0 failures and 0 errors.
- **SC-004:** Security regression demonstrates no-token 401, missing-permission 403, cross-tenant 404 and customer ownership.
- **SC-005:** Primary route smoke has no blank page, infinite API loop or uncaught browser-console error.
- **SC-006:** Primary tasks remain operable at 360 px, 768 px and 1280 px.
- **SC-007:** Required keyboard focus, labels, contrast and reduced-motion behavior pass acceptance checks.
- **SC-008:** Git whitespace and staged secret/artifact gates pass before every commit.
- **SC-009:** Every PASSED sub-feature has commit and non-force remote push evidence.

## Assumptions

- Angular 22 standalone architecture and installed UI packages remain.
- Feature-01 commit `70a73c3` is security baseline.
- Test environments use local/test data only.
- Existing API behavior is preserved unless source audit proves a minimal gap.
- Production deployment and migration remain outside scope.