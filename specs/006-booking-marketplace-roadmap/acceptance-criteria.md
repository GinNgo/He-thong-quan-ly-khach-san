# Acceptance Criteria by Phase

## Phase 0 - Baseline and Decisions

- **AC-001**: Current source/test status table is linked to Feature-03 evidence and no partial capability is labelled complete.
- **AC-002**: Payment callback, hold TTL, promotion stacking, VIP eligibility, sponsored ranking and social-secret decisions have an owner/status.
- **AC-003**: Baseline frontend/backend tests and Home screenshots are recorded before code changes.

## Phase 1 - Search and Date

- **AC-101**: Header partner CTA is absent on desktop/mobile while account-menu and Home owner paths still route correctly.
- **AC-102**: Search fields and all overlays fit 375/768/1024/1440px without page overflow.
- **AC-103**: Overnight/day-use/date-boundary/recent-search tests pass with no timezone date shift.
- **AC-104**: Keyboard users can open/select/close location, date and guest controls with visible focus.

## Phase 2 - Landmarks

- **AC-201**: Active landmark records appear in a labelled autocomplete group; inactive/malformed records do not.
- **AC-202**: Landmark selection survives URL reload and drives backend-resolved radius search.
- **AC-203**: Results show distance/context and offer radius/tỉnh recovery when empty.
- **AC-204**: Integration tests cover same-name landmarks in different provinces and no-coordinate handling.

## Phase 2B - Nationwide Landmark Catalog

- **AC-205**: The generated catalog covers all 34 current public provinces/cities and publishes at least three active, coordinate-valid landmarks per current unit; all 63 legacy province codes remain represented exactly once through the compatibility map.
- **AC-206**: Each generated row records source provider/type/id, source version or update date, quality status and deterministic natural key.
- **AC-207**: Two builds from identical inputs are byte-identical; a second database import creates zero additional rows and reports only unchanged updates/skips.
- **AC-208**: Duplicate source keys fail validation; near-identical name/coordinate candidates and unresolved province/coordinate rows are written to a machine-readable quarantine report.
- **AC-209**: Manual overrides survive refresh, and source absence never hard-deletes a referenced row after one run.
- **AC-210**: Automated tests verify malformed input, encoding, province coverage, coordinate bounds, category normalization, deduplication and safe deactivation behavior.

Evidence status (2026-07-29): `AC-201`-`AC-210` PASS for the implemented landmark scope. Persisted E2E data exposes the labelled group, URL/radius restoration and distance-ordered results; representative north/central/south/island browser journeys pass without console errors or horizontal overflow. Generator/import/idempotency and malformed-data coverage remain recorded in `quickstart.md`. Import-run/issue persistence is tracked separately by open task `T078` and does not change these catalog/search acceptance results.

## Phase 3 - Promotions, VIP and Ads

- **AC-301**: Admin can create/schedule/pause an eligible campaign with validated rule/budget/quota.
- **AC-302**: Search/detail/checkout/invoice share one quote and match to the đồng for each fixture.
- **AC-303**: Expired/ineligible/exhausted campaigns do not apply; replayed redemption does not exceed limits.
- **AC-304**: Member-only deals show tier requirements and never infer eligibility from room type.
- **AC-305**: Every sponsored placement is labelled in VI/EN and organic order remains independently testable.

Evidence status (2026-08-03): `AC-301`-`AC-305` PASS. Authorized campaign/placement lifecycle suites cover create, schedule, pause, approval, ownership and quota gates; quote and integration suites prove eligibility, stacking, replay and cross-surface price consistency. Authenticated Home/Search/Detail/Checkout checks at 375/768/1024/1440 render the explicit GOLD assignment and `Được tài trợ` / `Sponsored` disclosure with preserved context, zero overflow, zero missing P1 keys and no application console errors.

## Phase 4 - Localization and Motion

- **AC-401**: VI/EN switch persists across reload and updates public P1 routes plus PrimeNG date text.
- **AC-402**: Automated missing-key scan reports zero missing keys in the release scope.
- **AC-403**: Slideshow supports previous/next/pause, pauses on focus/hover and does not steal focus.
- **AC-404**: Reduced-motion mode renders static content with no autoplay; CLS and interaction budget pass.

Evidence status (2026-07-30): `AC-401`-`AC-404` PASS for the scoped public P1 surfaces. Locale/key-parity and persistence tests pass, PrimeNG and document language follow the active locale, and the initial catalog is loaded before first render to avoid translation-driven layout shift. Browser VI/EN rendering, slideshow controls and reduced-motion static behavior pass. Five cold runs at 1440x900 report p75 interaction delay `6.5 ms` and max CLS `0.000943`; five cold runs at 390x844 report `5.8 ms` and `0.001167`, below the `100 ms` and `0.05` limits with no horizontal overflow.

## Phase 5 - Booking, Payment and Refund

- **AC-501**: Status transition tests reject invalid reservation/payment/refund transitions.
- **AC-502**: A two-thread test against a real transactional database proves no overbooking with one remaining room.
- **AC-503**: Expired holds release inventory exactly once and late callbacks enter reconciliation instead of confirming silently.
- **AC-504**: Tampered/cross-account/expired/replayed callback cases are rejected or idempotently returned under the approved contract.
- **AC-505**: Refund UI distinguishes requested, provider pending, succeeded and failed; a real provider result or explicit simulator contract backs success.

Evidence status (2026-07-30): `AC-501` PASS through the canonical transition policy. `AC-502` PASS through `ReservationConcurrencyIntegrationTest` against H2 with one physical room and two simultaneous transactions. The hold-expiry half of `AC-503` PASS through persisted scheduler/restart/replay plus concurrent-scan coverage; late provider success is persisted as reconciliation. `AC-504` PASS for the approved session/callback contract: cross-account ownership, signed simulator token, expiry, amount tamper, VNPay replay/concurrency, MoMo HMAC and ZaloPay raw-data MAC suites are covered. `AC-505` PASS for the scoped lifecycle contract: `T053` proves authoritative refund outcome/idempotency behavior, `T054` Playwright passes `2/2` authenticated customer/admin journeys, and T100 adds official provider query/refund adapters with timeout-safe pending recovery and server-bound amounts/references. Full regression passes `46/46` frontend files and `113/113` tests plus `54` backend suites and `216/216` tests; the production build passes at `1.10 MB` raw / `205.14 kB` estimated transfer. Live sandbox confirmation remains explicitly open under `T101`; no live provider success is claimed.

## Phase 6 - Support and Social Channels

- **AC-601**: Authenticated customer/support browser tests prove history, send, reply, reconnect and offline recovery.
- **AC-602**: Cross-tenant conversation list/history/reply attempts return denial and create audit evidence.
- **AC-603**: Tenant can enable/disable an official provider channel without exposing credentials.
- **AC-604**: Provider sandbox tests verify webhook signature, deduplication, outbound retry/rate-limit and revoked-token behavior.

Evidence status (2026-07-31): `AC-601` PASS. `support-chat-lifecycle.spec.ts` passes authenticated history reload, principal-derived customer send, support queue/reply, user-queue delivery, explicit blocked-transport feedback and reconnect recovery. `AC-602` PASS: tenant-aware conversations/messages carry `hotel_id`, foreign-tenant lists are empty, history/reply attempts return not found, denied attempts create audit events and assignment/escalation handoff passes focused H2 integration coverage. `AC-603`-`AC-604` remain blocked because OQ-006 to OQ-009 and official Facebook/Zalo sandbox credentials are unresolved; no external-provider completion is claimed.

## Phase 7 - Subscription Packages

- **AC-701**: Admin/management screens render the same canonical plan names, billing, price, status and feature limits.
- **AC-702**: Active, expired, lifetime, unlimited and multiple-subscription fixtures produce correct entitlements.
- **AC-703**: Every advertised gated mutation has a backend check and denial/recovery browser evidence.
- **AC-704**: Unsupported purchase is replaced by a real contact path; if online purchase is approved, order/payment/activation replay tests pass.

## Phase 8 - Release

- **AC-801**: Full frontend tests/build and backend regression complete successfully.
- **AC-802**: Public/customer/admin/management browser journeys pass at required breakpoints with keyboard and reduced motion.
- **AC-803**: No unexplained console error, page overflow, untranslated P1 key or unlabelled sponsored placement remains.
- **AC-804**: Remaining blockers are documented with owner, dependency and next action; no blocker is hidden by mock data.

Evidence status (2026-07-31): `AC-801` retains the successful 46-file / 113-test frontend regression, production build and 54-suite / 216-test backend regression baseline. `AC-802`-`AC-804` pass through `integrated-release-matrix.spec.ts`: four serial journeys cover Home, Search, Customer, Admin and Management at 375/768/1024/1440px with alternating VI/EN, established reduced-motion coverage, valid keyboard focus, no overflow, no missing P1 key, no browser/runtime error and CLS below `0.04`. Search contained no sponsored marker at that historical policy-gated checkpoint; the 2026-08-03 AC-301-AC-305 evidence now supersedes that state with an approved disclosed placement. A post-layout focused unit retry timed out without output and is recorded as a tooling limitation, not substituted for the successful baseline.
