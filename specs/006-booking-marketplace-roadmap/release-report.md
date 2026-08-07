# Feature 006 Release Report

Date: 2026-07-31
Branch: `codex/ui-functional-audit-polish`

## Delivered in this increment

- Canonical subscription plan, feature and usage DTOs are served by `/api/subscriptions/plans`, `/api/subscriptions/me` and `/api/subscriptions/me/usage`.
- Active plans are the only public offers; expired subscriptions remain available through the authenticated history response.
- Management and admin subscription screens render API-provided feature limits and use an explicit contact workflow. Price/code-based benefits and simulated purchase redirects are removed.
- Effective subscription quotas now protect direct and management mutations for properties, room types, rooms, images and staff. Expired/no-plan accounts keep authorized reads but cannot mutate gated resources.
- Property-claim requests and admin review actions now derive requester/reviewer identity from the authenticated JWT principal; claim admin endpoints use the canonical `SUPER_ADMIN` authority instead of role-prefix semantics.
- Active, expired, lifetime/unlimited and multiple-subscription fixtures now pass repository/service integration and real browser verification; upgrade candidates receive a real support email path instead of simulated purchase success.
- Payment/refund E2E fixtures now expose pending, failed, expired, reconciliation and all four refund states. Customer history creates a real `REQUESTED` refund on cancellation, and admin reservation management renders the same lifecycle contract across pagination.
- T100 adds official MoMo/ZaloPay transaction query, refund and refund-query adapters with environment-only credentials, deterministic provider references, scheduled missed-callback recovery and timeout-safe pending retries. Local tests never claim a live provider refund.
- T055 closes authenticated central-support chat browser coverage: the server-owned user id reaches the Angular session, and real customer/support contexts pass history, send, reply, reload, transport failure and reconnect recovery.
- T056-T058 add tenant-aware in-app support conversations, reservation/property context, private support queues, explicit assignment/escalation and audited cross-tenant denial. Historical unscoped messages are retained but excluded from the new conversation path.
- Default seed data now creates the feature catalog and plan limits so a clean local database has the same entitlement contract as the demo profile.
- Phase 8 reconciliation records Feature-03 GAP-005/GAP-019/GAP-021/GAP-022/GAP-024 without changing the existing audit evidence.
- T071-T072 complete the integrated release browser gate across 4 viewports and 5 surfaces per viewport. Stable shell dimensions remove the measured lazy-route/account/header shifts without changing product behavior.

## Verification

- Backend full regression: `216/216` tests passed across 54 suites, including T100 provider query/refund recovery.
- Frontend full regression: `46/46` test files, `113/113` tests passed.
- Frontend production build: passed; initial bundle `1.10 MB` raw / `205.14 kB` estimated transfer.
- Focused subscription coverage includes catalog, quota arithmetic, direct room/room-type bypass, staff and property-claim denial tests.
- Focused property-claim controller security coverage: `6/6` tests passed for `401`, principal-derived actor ids, permission denial and `SUPER_ADMIN` access.
- Subscription browser evidence: `5/5` Playwright tests passed against a dedicated local E2E backend, including expired mutation denial and contact-path verification.
- Payment/refund browser evidence: `2/2` Playwright tests passed against the dedicated local E2E backend. The customer journey covers truthful lifecycle copy and cancellation-to-refund-request; the admin journey verifies payment/refund states, page-2 failed refund visibility and no horizontal overflow.
- Home locale/motion evidence: `3/3` Playwright tests passed with five cold desktop/mobile runs, p75 interaction delay below `5 ms` and max CLS below `0.002`. A focused mobile account-menu journey passes after making the scrollable menu body independent from its profile header; the public Home audit reports zero horizontal overflow at `375/768/1024/1440px` and 44px slideshow/tab targets.
- Public/customer data-quality evidence: `5/5` Playwright tests passed against a dedicated E2E backend. The suite verifies real Home media/prices, overlapping-reservation availability, customer/pending/owner account actions and mobile logout; the E2E reservation now creates the same `ReservationDetail` inventory record used by production availability queries.
- T071/T072 integrated browser matrix: `4/4` Playwright journeys passed against the dedicated E2E backend. At `375x812`, `768x1024`, `1024x768` and `1440x900`, Home, Search, Customer, Admin and Management all have zero page overflow, zero missing translation keys, zero console/page/HTTP errors and valid visible keyboard focus.
- T072 performance evidence: all 20 surfaces stay below the CLS budget. The highest measured value is `0.03959` (Customer at 375px); Home peaks at `0.03039`, Search at `0.00026`, Admin at `0.00787` and Management at `0.02006`. Navigation timing stays within `15 s`.
- Tenant support evidence: focused Java main/test compilation passed, focused service/controller/security coverage passed `12/12`, H2 tenant-isolation/assignment coverage passed `2/2`, and focused TypeScript compilation passed. The standard Maven and Angular runners timed out after `304 s`; a later official five-suite Maven retry also timed out after `184 s` without output or a new Surefire report. The exact Maven wrapper/JVM processes were stopped while ports `4200` and `8082` remained running, and the timeout is retained as a tooling limitation rather than reported as a pass.
- Sponsored disclosure check at the 2026-07-31 release checkpoint: Search rendered zero sponsored/ad markers because the policy gate was still open. The later 2026-08-03 T035/T117 evidence supersedes this historical state with an approved fixture-backed placement and visible VI/EN disclosure.
- Post-change focused Angular tests were retried with one worker but produced no test output and were terminated as a tooling timeout. The production build was retried with `NG_BUILD_MAX_WORKERS=1` and also timed out after `304 s` without compiler output; its isolated build process was stopped while the developer frontend on port `4200` remained running. These retries do not replace the earlier successful full-regression/build baseline.
- `git diff --check`: passed.

## T054 payment/refund lifecycle evidence

- `E2eFixtureInitializer` provisions `PENDING`, `FAILED`, `EXPIRED`, reconciliation-required payment sessions and `REQUESTED`, `PENDING_PROVIDER`, `SUCCEEDED`, `FAILED` refund rows without exposing provider secrets.
- The admin table now explicitly triggers change detection after the reservation HTTP response so Angular zoneless mode cannot leave a successful response rendered as an empty table. A focused component test reproduces and guards this delayed-response case.
- `payment-refund-lifecycle.spec.ts` authenticates real customer and `SUPER_ADMIN` actors, confirms the browser request reaches the E2E API, verifies every lifecycle state, executes customer cancellation and traverses PrimeNG pagination for the final failed refund row.
- Evidence screenshots: `docs/screenshots/payment-refund-customer.png` and `docs/screenshots/payment-refund-admin.png`. These prove UI/state handling only; live provider refund/query success remains unclaimed.

## T066 entitlement mutation audit

- The advertised catalog is limited to `MAX_PROPERTIES`, `MAX_ROOM_TYPES`, `MAX_ROOMS`, `MAX_IMAGES` and `MAX_STAFF`; no inferred benefit is treated as an entitlement.
- Direct room/room-type controllers can no longer bypass management quotas. Single create, bulk create, update and deactivate paths resolve effective subscriptions server-side; super-admin maintenance remains exempt.
- Image usage includes property, room-type and room image associations. Room-type replacement validates the resulting total, while room creation validates associated nonblank images before persistence.
- Property creation and imported-property claim approval enforce `MAX_PROPERTIES`; staff create/update/delete enforce `MAX_STAFF`. Claim approval fails before claim/property state changes when quota is exhausted, and requester/reviewer ids cannot be supplied or overridden by the caller.
- Read endpoints remain available after expiry. Expired/no-plan accounts expose no active limits, while authorized subscription and resource history remains readable.

## T067-T068 lifecycle and upgrade evidence

- E2E fixture provisioning creates finite active, expired, lifetime/unlimited and multi-plan owners using environment-supplied credentials; repeated provisioning remains idempotent.
- Effective entitlement integration verifies expired subscriptions are excluded, lifetime limits remain unlimited and multiple active subscriptions merge each feature to the highest limit.
- Five Playwright journeys pass against the real Angular app and a dedicated local backend on port `8082`. The expired actor can still view plan history but a real room mutation returns `409`; active, lifetime and multi-plan usage values match the server contract.
- The supported upgrade route is a visible `mailto:support@luxestay.vn` contact link. The page contains no fake `Mua ngay` control and no online order/payment activation is claimed.

## Release status

`PARTIAL` / not production-ready. The Phase 8 integrated browser gate is complete, but the following product/external gates remain explicit: promotion/VIP/advertising policy approval, live payment-provider credentials and public callbacks, social-channel approvals/credentials and subscription admin plan/feature lifecycle approval. Phase 4 locale/motion/performance evidence, T054 customer/admin lifecycle evidence, T055-T058 tenant-aware in-app support evidence, T066-T068 subscription enforcement/lifecycle/contact evidence and T071-T072 cross-role viewport evidence are complete.

No files were staged, committed or pushed in this increment. The worktree contains pre-existing changes from other feature/audit tasks and must be reviewed by scope before any commit.
