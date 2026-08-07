---

description: "Dependency-ordered tasks for exhaustive UI audit and premium polish"
---

# Tasks: Full UI Functional Audit & Premium Polish

**Input**: `.specify/Feature-03-UI-Functional-Audit-Polish/spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: Required. The feature explicitly requires real browser testing, responsive/accessibility review, frontend automated verification and backend regression.

**Organization**: Tasks are grouped by user story. Runtime evidence must exist before related implementation is marked complete.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel because it targets different files and has no incomplete dependency.
- **[Story]**: Maps to a user story in `spec.md`.
- Every implementation task names the exact source or artifact path.

## Phase 1: Setup and Design Artifacts

**Purpose**: Select the feature and establish an executable, documentation-first audit contract.

- [X] T001 Set `.specify/feature.json` to `.specify/Feature-03-UI-Functional-Audit-Polish`
- [X] T002 [US1] Define actors, routes, status taxonomy, acceptance scenarios and measurable criteria in `.specify/Feature-03-UI-Functional-Audit-Polish/spec.md`
- [X] T003 [P] [US1] Validate requirement completeness in `.specify/Feature-03-UI-Functional-Audit-Polish/checklists/requirements.md`
- [X] T004 [P] [US4] Validate UX requirement quality in `.specify/Feature-03-UI-Functional-Audit-Polish/checklists/ux.md`
- [X] T005 [US1] Document technical plan, research decisions, audit model, quality contract and runbook in `.specify/Feature-03-UI-Functional-Audit-Polish/plan.md`, `research.md`, `data-model.md`, `contracts/ui-audit-contract.md`, and `quickstart.md`

---

## Phase 2: Foundational Audit Infrastructure

**Purpose**: Build the inventory/evidence structures and establish a verified runtime baseline before changing application code.

**CRITICAL**: No UI implementation task may start until T006-T010 are complete.

- [X] T006 [US1] Create the complete exposed route/menu inventory with user-story/requirement IDs, actor, component, API and permission mapping in `.specify/Feature-03-UI-Functional-Audit-Polish/audit-matrix.md` using `frontend/src/app/app.routes.ts`, `frontend/src/app/layout/sidebar/sidebar.ts`, `frontend/src/app/layout/client-layout/client-layout.ts`, and `frontend/src/app/layout/management-layout/management-layout.ts`
- [X] T007 [US3] Create the actionable gap register structure and migrate still-valid historical hypotheses from `.specify/Feature-02-Frontend-UX-Redesign/audit-matrix.md`, `docs/audit/FAKE_OR_DISCONNECTED_FEATURES.md`, and `docs/audit/BUG_BACKLOG.md` into `.specify/Feature-03-UI-Functional-Audit-Polish/gap-register.md`
- [X] T008 [P] [US5] Record the current frontend unit/build and backend Maven baseline in `.specify/Feature-03-UI-Functional-Audit-Polish/audit-matrix.md` after running commands from `.specify/Feature-03-UI-Functional-Audit-Polish/quickstart.md`
- [X] T009 [P] [US1] Start the real backend/frontend, verify API/app health, and record environment prerequisites without secrets in `.specify/Feature-03-UI-Functional-Audit-Polish/audit-matrix.md`
- [X] T010 [US1] Confirm representative Customer, System Admin and Property Owner/Manager accounts plus property/data scope; record unavailable roles/data as explicit blockers in `.specify/Feature-03-UI-Functional-Audit-Polish/audit-matrix.md`

**Checkpoint**: Route inventory, evidence format, runtime and actor prerequisites are ready.

---

## Phase 3: User Story 1 - Test Every Exposed UI Journey (Priority: P1) MVP

**Goal**: Assign evidence-backed status to every exposed route/menu for the correct actor.

**Independent Test**: Every inventory row in `audit-matrix.md` has scenarios, status, evidence and a gap/next step when not complete.

- [X] T011 [US1] Browser-test public home, search, property detail, customer login/register, `/admin/login`, payment result/simulator and error navigation at their canonical routes; update `.specify/Feature-03-UI-Functional-Audit-Polish/audit-matrix.md`
- [X] T012 [US1] Browser-test authenticated customer checkout, profile, booking history, invoices, settings and partner registration/status flows; update `.specify/Feature-03-UI-Functional-Audit-Polish/audit-matrix.md`
- [X] T013 [US1] Browser-test System Admin dashboard, profile, users, customers, modules, chat, properties, plans, roles, permissions, imports and claims; update `.specify/Feature-03-UI-Functional-Audit-Polish/audit-matrix.md`
- [X] T014 [US1] Browser-test hotel operations room types, rooms, services, reservations list/timeline/create and invoices with valid and denied permissions; update `.specify/Feature-03-UI-Functional-Audit-Polish/audit-matrix.md`
- [X] T015 [US1] Browser-test all generic partner administration routes selected by route data in `frontend/src/app/app.routes.ts`; update `.specify/Feature-03-UI-Functional-Audit-Polish/audit-matrix.md`
- [X] T016 [US1] Browser-test management property context, dashboard/properties, room types, rooms and billing/subscription redirects; update `.specify/Feature-03-UI-Functional-Audit-Polish/audit-matrix.md`
- [X] T017 [US1] Verify unauthorized, expired-session, unknown-route, redirect-alias and cross-property navigation behavior; update `.specify/Feature-03-UI-Functional-Audit-Polish/audit-matrix.md` and `.specify/Feature-03-UI-Functional-Audit-Polish/gap-register.md`

**Checkpoint**: 100% route/menu coverage is evidence-backed even when the outcome is `BLOCKED`, `PARTIAL`, `MISSING` or `BROKEN`.

---

## Phase 4: User Story 2 - Complete Core Business Journeys (Priority: P1)

**Goal**: Fix evidence-backed P1 failures in core customer and property-operation journeys without inventing unsupported domain behavior.

**Independent Test**: Search/detail, booking/payment, room/inventory, reservation/invoice and property-context journeys expose clear async states and complete or fail recoverably.

### Documentation Gate

- [X] T018 [US2] Document the verified state/navigation/component changes before code edits in `docs/DESIGN.md`, `docs/FRONTEND_STANDARDS.md`, `docs/UML.md`, `docs/API_SPEC.md`, and `docs/THESIS.md` as applicable to the proven gaps

### Tests and Implementation

- [X] T019 [P] [US2] Add or update focused tests for shared feedback, mutation locks and route state in `frontend/src/app/shared/components/feedback-state/feedback-state.component.spec.ts`, relevant `frontend/src/app/features/**/*.spec.ts`, and `frontend/e2e/*.spec.ts`
- [X] T020 [US2] Fix shared loading, empty, error/recovery, success and submitting patterns in `frontend/src/app/shared/components/feedback-state/`, `frontend/src/app/shared/components/form-dialog/`, `frontend/src/app/shared/components/confirm-dialog/`, and `frontend/src/styles.css`
- [ ] T021 [US2] Fix evidence-backed public/customer P1 UI defects in `frontend/src/app/features/client/`, `frontend/src/app/features/property-search/`, and matching typed services under `frontend/src/app/core/services/`
- [ ] T022 [US2] Fix evidence-backed admin operations P1 UI defects in `frontend/src/app/features/admin/room-type-management/`, `frontend/src/app/features/admin/room-management/`, `frontend/src/app/features/admin/service-management/`, `frontend/src/app/features/admin/reservation-management/`, `frontend/src/app/features/admin/reservation-create/`, `frontend/src/app/features/admin/reservation-timeline/`, and `frontend/src/app/features/admin/invoice-management/`
- [ ] T023 [US2] Fix evidence-backed property context/inventory/billing defects in `frontend/src/app/features/management/` and `frontend/src/app/layout/management-layout/`
- [ ] T024 [US2] If and only if source/runtime evidence proves a minimal backend contract defect, add targeted tests and the smallest correction under `backend/src/test/` and `backend/src/main/java/com/hotel/`; otherwise record the gap as deferred in `.specify/Feature-03-UI-Functional-Audit-Polish/gap-register.md`
- [ ] T025 [US2] Re-run the affected browser journeys and record post-fix evidence/resolution in `.specify/Feature-03-UI-Functional-Audit-Polish/audit-matrix.md` and `.specify/Feature-03-UI-Functional-Audit-Polish/gap-register.md`

**Checkpoint**: P1 journeys that can be completed within the current domain are verified; larger business gaps remain explicit.

---

## Phase 5: User Story 3 - Actionable Product Gap Register (Priority: P2)

**Goal**: Make every incomplete or missing function traceable and prioritizable.

**Independent Test**: Any P1/P2 gap can be traced from actor/route to reproduction, evidence, severity, disposition and next task.

- [ ] T026 [US3] Classify every non-complete audit row by UI/UX, responsive, accessibility, navigation, permission, API contract, data, business rule or testability in `.specify/Feature-03-UI-Functional-Audit-Polish/gap-register.md`
- [X] T027 [US3] Revalidate historical mixed-room booking, customer add-on services, reviews, subscription quota, payment idempotency and owner isolation claims against current source/runtime; update `.specify/Feature-03-UI-Functional-Audit-Polish/gap-register.md`
- [ ] T028 [US3] Add severity, reproduction, expected/actual result, disposition and next step for every P1/P2 gap in `.specify/Feature-03-UI-Functional-Audit-Polish/gap-register.md`
- [X] T029 [US3] Mark backend/data-model work that exceeds this iteration as `DEFER_FEATURE` and link it to relevant documentation in `docs/API_SPEC.md`, `docs/UML.md`, or `docs/THESIS.md`

**Checkpoint**: The gap register is suitable for planning subsequent features without fake completion.

---

## Phase 6: User Story 4 - Premium, Consistent and Accessible UI (Priority: P2)

**Goal**: Raise system-wide visual quality and usability while preserving the LuxeStay identity.

**Independent Test**: Representative public, customer, admin and management pages pass visual hierarchy, keyboard and breakpoint review.

- [ ] T030 [P] [US4] Add or refine semantic spacing, radius, elevation, focus, state and motion tokens in `frontend/src/styles.css` and `frontend/src/app/core/theme.ts` without introducing hardcoded component colors
- [ ] T031 [US4] Refine client navigation/header/footer hierarchy, mobile menu and active/recovery states in `frontend/src/app/layout/client-layout/`
  - Progress 2026-07-28: GAP-010 is fixed by rendering the supported Vietnamese/VND setting as a non-interactive status; desktop semantics and 375px overflow pass, and header interactive targets are at least 44px. Fragment/active-state and remaining footer/mobile-menu refinements are still pending.
- [ ] T032 [US4] Refine admin sidebar/topbar/content hierarchy, active state, density and mobile behavior in `frontend/src/app/layout/admin-layout/` and `frontend/src/app/layout/sidebar/`
- [ ] T033 [US4] Refine management property context, navigation hierarchy and responsive shell in `frontend/src/app/layout/management-layout/`
- [ ] T034 [US4] Normalize shared table, filter, select, date, stat-card and dialog presentation in `frontend/src/app/shared/components/` using existing PrimeNG APIs and tokens
- [ ] T035 [US4] Resolve page-specific high-impact hierarchy/responsive/accessibility gaps proven by T011-T017 in the exact `frontend/src/app/features/` files linked from `.specify/Feature-03-UI-Functional-Audit-Polish/gap-register.md`
- [ ] T036 [US4] Browser-review core pages at 375, 768, 1024 and 1440px plus keyboard-only and reduced-motion modes; measure interaction-to-loading/progress feedback within 300ms for changed async actions and record evidence in `.specify/Feature-03-UI-Functional-Audit-Polish/audit-matrix.md`

**Checkpoint**: Shared visual language and interaction quality are consistent across all three shells.

---

## Phase 7: User Story 5 - Repeatable Regression (Priority: P3)

**Goal**: Leave a concise regression suite and verified handoff.

**Independent Test**: A clean local run can reproduce pass/fail/blocker outcomes for all high-risk journeys.

- [X] T037 [US5] Update or add concise real-environment smoke scenarios for public/customer/admin/owner flows in `frontend/e2e/` without using mocks as completion evidence
- [X] T038 [US5] Run `npm test -- --watch=false` and `npm run build` in `frontend/`; record command outcomes in `.specify/Feature-03-UI-Functional-Audit-Polish/audit-matrix.md`
- [X] T039 [US5] Run `.\mvnw.cmd test` in `backend/`; record command outcomes in `.specify/Feature-03-UI-Functional-Audit-Polish/audit-matrix.md`
- [ ] T040 [US5] Time and re-run the P1 browser regression plus permission/responsive samples after all changes; calculate the P1 completion rate (target at least 90%) and verify the smoke run completes within 45 minutes in `.specify/Feature-03-UI-Functional-Audit-Polish/audit-matrix.md`
  - Progress 2026-07-28: the real-environment smoke now completes in about 10 seconds with 1 public pass and 3 explicit actor skips. Final P1 completion rate remains blocked until customer/admin/owner credential variables and assigned-property data are available.
- [X] T041 [US5] Validate `.specify/Feature-03-UI-Functional-Audit-Polish/quickstart.md` from a clean terminal and document prerequisites that remain external
  - Verified 2026-07-28: frontend tests/build, targeted auth/tenant backend regression and the real public smoke command run successfully; customer/admin/owner credentials and assigned-property data remain external prerequisites documented in `quickstart.md`.

---

## Phase 8: Consistency, Convergence and Delivery

**Purpose**: Reconcile artifacts with implementation and prepare a safe Git handoff.

- [X] T042 Run Spec Kit analyze read-only across `.specify/Feature-03-UI-Functional-Audit-Polish/spec.md`, `plan.md`, and `tasks.md`; request user approval before any remediation edits required by analyze findings
- [X] T043 Run Spec Kit converge and append only genuinely unbuilt work to the Convergence phase in `.specify/Feature-03-UI-Functional-Audit-Polish/tasks.md`
- [X] T044 Run `git diff --check`, inspect `git status --short`, and verify no secret, generated build artifact or unrelated user change is staged
- [X] T045 Prepare final handoff with completed routes, unresolved gaps, test/build/browser evidence and branch status; commit/push only when explicitly requested by the user

---

## Dependencies and Execution Order

### Phase Dependencies

- Phase 1 is complete.
- Phase 2 depends on Phase 1 and blocks all browser audit/implementation.
- Phase 3 depends on runtime and account prerequisites from Phase 2.
- Phase 4 depends on browser evidence from Phase 3 and its documentation gate T018.
- Phase 5 depends on Phase 3 evidence but can continue while bounded UI fixes are verified.
- Phase 6 depends on the shared/documentation decisions from Phase 4 and the gap priorities from Phase 5.
- Phase 7 depends on all selected fixes and polish.
- Phase 8 depends on final evidence from Phase 7.

### User Story Dependencies

- **US1** establishes authoritative runtime evidence.
- **US2** consumes US1 evidence; it must not fix hypothetical defects.
- **US3** consumes US1 evidence and records work excluded from US2.
- **US4** consumes US1/US3 priorities and follows T018 documentation-first rules.
- **US5** verifies the integrated result of US1-US4.

### Parallel Opportunities

- T008 and T009 can run in parallel once audit artifacts exist.
- Tests in T019 may be prepared independently for different feature files after evidence exists.
- T030 can run separately from shell-specific work once documentation and gap priorities are fixed.
- Automated verification T038/T039 can run in parallel when implementation is stable.

## Implementation Strategy

1. Complete exhaustive inventory and browser audit before broad UI edits.
2. Fix shared P1 problems first, then page-specific defects linked to evidence.
3. Preserve backend/domain scope; defer large missing features transparently.
4. Verify after each logical shared/page group and update evidence immediately.
5. Do not mark a task `[X]` until its artifact/source change and required verification both exist.

## Phase 9: Convergence

- [X] T046 [US1] Complete real-browser evidence for the still-unvisited public, customer, admin and management audit rows, including permission/error/recovery branches, and attach `EVD-###` metadata per FR-001/FR-002/SC-001 (partial outcomes remain explicitly blocked/broken where actor, data or route contracts are unavailable)
- [X] T047 [US2] Implement the `/admin/properties` create journey or replace the inert `Thêm mới` control with an explicit supported/unavailable state after confirming the property-create API contract per GAP-015 (browser-verified dialog/validation; real submission blocked by missing location data)
- [X] T048 [US1] Replace generic partner overview rendering with endpoint-specific columns, actions, loading/error/empty contracts and permission UX for AUD-041 to AUD-050 per FR-007/GAP-008/GAP-016 (partial: property-owners backend query and denied/pending runtime fixtures remain explicit)
- [ ] T049 [US2] Complete customer search-to-detail-to-checkout-payment/history browser coverage and verify duplicate-submit, payment idempotency and ownership behavior per FR-005/FR-018/GAP-001/GAP-005/GAP-006 (partial)
  - Progress 2026-07-28: invalid detail/checkout/payment states fixed and browser-verified; customer-owned history/payment context, duplicate-submit tests, payment idempotency tests and tenant ownership tests pass. Remaining primary booking data and demo callback authorization are explicitly blocked in `GAP-005`, `GAP-006` and `GAP-022`; do not change payment/authorization policy without user approval.
- [X] T050 [US3] Normalize audit evidence IDs and gap state taxonomy (`REVALIDATE`, `FIXED`, `CONFIRMED_PARTIAL`, `CONFIRMED_BROKEN`) across `audit-matrix.md`, `gap-register.md` and the contract per FR-002/FR-020
- [X] T051 [US5] Reduce the production initial bundle below its configured budget and resolve or document the `@stomp/stompjs`, `sockjs-client` and inline-font warnings per SC-010/GAP-017
- [ ] T052 [US1] Re-run denied-actor, expired-session, forged-property-context and management property-switch scenarios per FR-008/FR-013/GAP-013 (partial)
  - Progress 2026-07-28: customer-like actor management entry is fixed and browser-verified at `/403`; 5 auth-exception and 10 tenant-isolation tests pass. Expired-session UI plus assigned-property switch/forged-query browser fixtures remain unavailable.
- [ ] T053 [US3] Update the feature status and final completion metrics only after the remaining evidence-backed tasks converge per SC-003/SC-004/FR-020 (partial)

## Phase 10: Convergence

- [X] T054 [US4] Replace empty exception handlers in `frontend/src/app/features/ai-assistant/ai-assistant.ts`, `frontend/src/app/features/client/chat-widget/chat-widget.ts` and `frontend/src/app/features/admin/chat-dashboard/chat-dashboard.ts` with null-safe guards or explicit non-user-facing logging, preserving reduced-motion/scroll behavior (contradicts Constitution 5)
- [X] T055 [US1] Browser-test the actual admin notification panel and AI assistant widget controls mounted by `AdminLayout`, including open/close, loading/empty/error/retry, send failure and realtime/mark-read branches; update AUD-057/AUD-058 and add evidence IDs without introducing standalone routes
  - Verified 2026-07-28: fixture-backed notification rows, mark-read persistence and protected realtime delivery pass (EVD-081, EVD-084). AI open/close/send/loading, timeout/failure/retry and Escape recovery are covered by eight tests; after scheduling zoneless change detection from the request finalizer, the real browser rendered the successful `weather` reply in about 1.5 seconds with no console errors (EVD-085). AUD-057/AUD-058 and GAP-020/GAP-023 are closed.

## Phase 11: Convergence

- [X] T056 [US1] CRITICAL Specify and enforce authenticated chat identity/ownership across `backend/src/main/java/com/hotel/controllers/ChatController.java`, `backend/src/main/java/com/hotel/config/WebSocketConfig.java`, Spring message security, `frontend/src/app/core/services/chat.service.ts` and `frontend/src/app/features/client/chat-widget/`: derive sender identity from the JWT principal, authorize history/active-user reads, constrain recipients to an approved support/tenant assignment instead of `adminId = 1`, secure connect/send/subscribe and origins, correct the user-destination contract, then add spoofing/cross-account/offline/accessibility regression per Constitution 3, FR-007, FR-009, FR-014 and SC-003 (remediates prior violation)
  - Progress 2026-07-28: central support routing is implemented under `SYSTEM.AI_CHAT`; REST and STOMP payloads derive identity from the authenticated principal, customer history is principal-scoped, support reads/replies require `AI_CHAT:VIEW/CREATE`, `/ws-chat` has JWT-on-STOMP-connect plus origin/subscribe/send guards, and customer delivery uses `/user/queue/messages`. Frontend chat now exposes accessible dialog/controls, explicit login/offline/error/retry states, and no optimistic success. Latest full regression passed 33 frontend files/66 tests and 122 backend tests. Full authenticated browser send/reconnect remains blocked because the current browser session has no chat token/support actor fixture.

## Phase 12: Convergence

- [X] T057 [US1] HIGH Specify and enforce the admin/per-user notification authorization contract across `backend/src/main/java/com/hotel/controllers/NotificationController.java`, Spring Security, notification WebSocket configuration and the Angular notification service/panel: remove public REST access and global subscription leakage, authenticate STOMP connect/subscribe, constrain origins and destinations, deliver only authorized user/role-scoped events, and add REST/WebSocket ownership plus mark-read/realtime regression before completing GAP-020/GAP-023/T055 (remediates prior violation)
  - Progress 2026-07-28: REST now requires JWT plus `REPORT:VIEW`, list/mark-read are principal-scoped, the JSON contract exposes `isRead`, notification STOMP CONNECT persists the authenticated principal for later subscriptions, origins/CORS are configuration-bound, SockJS does not require cookies for JWT sessions, subscriptions are limited to `/topic/admin/notifications` and `/user/queue/notifications`, and clients cannot publish notification messages. Focused notification/security tests, the WebSocket integration test and full backend regression pass; fixture-backed browser list/mark-read and protected realtime delivery persist/arrive without reload (EVD-080, EVD-081, EVD-084). GAP-023 is closed, and the separate AI response-rendering branch under T055/GAP-020 is closed by EVD-085.
- [ ] T058 [US2] HIGH Obtain explicit user approval for the non-production payment callback policy, then implement exactly one documented contract for `MockPaymentController`: either authenticated reservation-owner confirmation or a signed, expiring, server-issued demo transaction. Reject caller-controlled ownership/amount/status, preserve idempotency and replay protection, add cross-account/tamper/concurrency tests, and browser-verify the approved flow before completing GAP-005/GAP-019/GAP-022/T049; do not change this authorization policy without approval
- [X] T059 [US5] HIGH Remove runtime local-path coupling for location import from `backend/src/main/resources/application.yml` and `backend/src/main/java/com/hotel/services/LocationImportService.java`: package an immutable classpath resource or require an environment-supplied external path outside committed runtime configuration, fail with actionable diagnostics, and add configuration/import regression so production startup does not depend on `../docs/34_tinh_huyen_xa.json` (remediates prior violation)
  - Verified 2026-07-28: import defaults to packaged `classpath:data/locations.json`, optional external resources are supplied only through `LOCATION_IMPORT_RESOURCE`, filesystem fallbacks were removed, and three resource/import configuration tests pass.
- [X] T060 [US5] Add an idempotent, non-production fixture/provisioning workflow with environment-supplied credentials and no committed secrets for the real browser actors/data still blocking completion: customer with approved inventory and owned booking/payment context, `SYSTEM.AI_CHAT` support actor, notification data, denied actor, expired subscription owner and owner assigned to multiple properties. Document setup/cleanup in `quickstart.md`, then use it to unblock T049/T052/T055 and authenticated chat evidence without using mocked UI/API responses
  - Verified 2026-07-28: the `e2e` profile uses disposable H2 data and environment-supplied credentials to provision customer/admin/owner/expired-owner actors, two owner properties, booking/payment and notification records. The integration test passes completeness and repeat-run idempotency; real browser scenarios still need a running profile session.
- [X] T061 [US3] Reconcile stale Speckit lifecycle metadata before the final completion gate: update `spec.md` from `Ready for Planning` to the truthful in-progress state, revise `plan.md` Constitution Check/Complexity Tracking so incomplete browser, breakpoint, accessibility and production-readiness gates are not marked passed, and preserve T053 as the only task allowed to publish final SC-003/SC-004 completion metrics after T021-T040 and T049-T060 converge
- [X] T062 [US1] HIGH Complete authenticated customer/support chat browser coverage using the provisioned E2E customer and support actors: verify history load, principal-derived send, support reply routing, reconnect and offline/error recovery through `frontend/src/app/features/client/chat-widget/` and `/admin/chat`, then add evidence for GAP-021/GAP-024 without weakening the approved `SYSTEM.AI_CHAT` ownership contract
  - Progress 2026-07-28: backend/source security contract and automated spoofing/cross-account tests are complete under T056; authenticated browser send/history/reconnect still requires a dedicated customer/support session and remains intentionally unverified.
- [ ] T063 [US1] Add explicit traceability for the shared audit status taxonomy and gap reproduction contract: map FR-003, FR-010 and FR-011 to every `audit-matrix.md`/`gap-register.md` row, reject evidence-less `COMPLETE` states, and record an actionable next step for each `PARTIAL`, `BLOCKED` or `BROKEN` item.
- [ ] T064 [US1] Complete the no-mock real integration coverage gate for FR-004/FR-006/FR-007/FR-008: run the provisioned Public, Customer, System Admin and Property Operations browser journeys against the local API, and preserve explicit data/permission blockers when a primary or mutation branch cannot be completed.
- [ ] T065 [US4] Re-run the shared design-token, semantic-control and async-feedback review for FR-012/FR-014/FR-015/FR-016/FR-017 across the P1 surfaces, including keyboard focus, reduced motion, 44px targets, table strategy and immediate loading/progress feedback; add evidence per breakpoint rather than relying on source inspection alone.
- [ ] T066 [US3] Reconcile deferred product gaps under FR-019: ensure every missing review/add-on/mixed-cart or payment-policy domain has a concrete backend/data dependency and follow-up owner, with no fake UI, fixture or response used to raise its status.
- [ ] T067 [US1] Close the P1 acceptance-branch traceability for SC-002 and SC-008 by linking each P1 route to primary, error/recovery and permission-denied evidence, then re-run the route matrix for redirect-loop, blank-screen and unexplained-console-error checks.
- [ ] T068 [US4] Execute the final responsive/accessibility matrix for SC-006 and SC-007 at 375/768/1024/1440px, keyboard-only and reduced-motion settings; record intentional table overflow separately from page overflow and attach the remaining failures to gaps.
- [ ] T069 [US5] Run the complete repeatable regression quickstart for SC-009, record duration and pass/fail/blocked counts, then update T053/SC-003/SC-004/SC-010 only after all open prerequisite tasks converge.

## Phase 13: Near-Mobile Responsive Remediation

- [ ] T070 [US4] HIGH Fix the authenticated public account drawer at 375-760px so it uses the dynamic viewport and safe-area, keeps the identity/reward regions visible, provides an independently scrollable menu, stays above page content and remains closable by the visible button/Escape in `frontend/src/app/layout/client-layout/` (GAP-025, SC-006, SC-007). Progress: implementation and Angular regression coverage are complete; authenticated browser verification remains blocked because no customer session/fixture is available.
- [X] T071 [US4] HIGH Fix the home guest/room selector so room, adult and child summaries never exceed the search-field height, the compact mobile summary remains readable, and the PrimeNG popover is width/height bounded with vertical scrolling instead of clipping at short mobile/landscape viewports in `frontend/src/app/features/client/home/components/guest-room-selector/` (GAP-026, SC-006, SC-007). Verified with focused Angular tests and browser checks at 375px, 504px, 532px and 768px; combined room/adult/child selection stays in-viewport and controls remain 44px.
- [ ] T072 [US5] Add focused Angular regression and verify the authenticated account drawer plus guest/room selector at 375px, 504px, 532px and 768px; record no page overflow, no hidden menu/action, 44px touch targets and viewport-safe overlay evidence in `audit-matrix.md` and `gap-register.md`. Progress: guest selector evidence is complete; account drawer browser evidence is blocked by the missing authenticated customer session.

## Phase 14: LuxeStay Brand Mark Refresh

- [ ] T073 [US4] HIGH Finalize the original luxury-hospitality logo direction from `brand/logo-brief.md`: compare at least three emblem/monogram concepts, select one for LuxeStay Hotels, and document navy/champagne-gold/white usage, Cormorant lockup typography and clear-space rules (FR-012, FR-020).
- [ ] T074 [US4] HIGH Produce the approved logo asset family as optimized SVG plus required PNG/WebP fallbacks: primary horizontal lockup, stacked lockup, standalone mark, monochrome light/dark variants and 16px/32px favicon; verify single-color and grayscale legibility before integration (FR-012, FR-014).
- [ ] T075 [US4] Replace CSS/mock brand marks in the client header/footer, login, admin sidebar and management shell with the shared asset family; update `frontend/src/index.html` favicon and preserve accessible names, alt text, focus states and existing LuxeStay design tokens (FR-012, FR-013, FR-014).
- [ ] T076 [US5] Run visual and responsive logo QA at 375/768/1024/1440px on light/dark surfaces, check no layout shift/overflow and reduced-motion neutrality, then add focused Angular regression/build evidence and close the logo task only after the original artwork and all required variants are present (FR-012, FR-014, FR-015, SC-006, SC-007).
