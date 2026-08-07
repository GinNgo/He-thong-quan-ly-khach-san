# Tasks: LuxeStay Booking Marketplace Readiness

**Input**: Design documents from `/specs/006-booking-marketplace-roadmap/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `requirements.md`, `business-rules.md`, `acceptance-criteria.md`, `open-questions.md`, `data-model.md`, `contracts/marketplace-api-contract.md`

**Execution rule**: Complete phases sequentially. Within a phase, `[P]` tasks may run in parallel only when they touch independent files. Do not start payment/social mutations before their decision gates.

## Phase 0: Baseline and Decision Gates

**Goal**: Freeze truthful current behavior and resolve high-impact product/security contracts.

- [x] T001 Record current branch/worktree scope, Feature-03 dependencies and baseline evidence in `specs/006-booking-marketplace-roadmap/quickstart.md`
- [x] T002 [P] Add focused baseline tests for Home search state/date behavior in `frontend/src/app/features/client/home/services/home-search-state.service.spec.ts` and `frontend/src/app/features/client/home/components/date-range-selector/date-range-selector.component.spec.ts`
- [x] T003 [P] Add a source-backed capability status appendix to `specs/006-booking-marketplace-roadmap/research.md` for payment, booking, chat, social channels, landmarks, promotions and subscriptions
- [ ] T004 Obtain product/security owner approval and record OQ-002 to OQ-005 and OQ-010 in `specs/006-booking-marketplace-roadmap/open-questions.md`; implementers must not decide financial/VIP/advertising rules unilaterally
- [x] T005 Obtain explicit approval for OQ-001 / Feature-03 T058 before any demo callback policy implementation; record the exact selected contract in `specs/006-booking-marketplace-roadmap/contracts/marketplace-api-contract.md`
- [x] T006 Define canonical reservation/payment/refund transition tables and compatibility migration rules in `specs/006-booking-marketplace-roadmap/business-rules.md`

**Checkpoint**: Phase 1 may proceed while payment/social gates remain open; Phase 5/6 provider mutations may not.

## Phase 1: Public Search and Date Reliability (P1 MVP)

**Independent Test**: At 375/768/1024/1440px, complete overnight and day-use search, reload results and verify no overflow, clipping, timezone shift or lost search state.

- [x] T007 [P] [US1] Add header regression expectations for removed desktop/mobile partner CTA and preserved account/Home owner access in `frontend/src/app/layout/client-layout/client-layout.spec.ts`
- [x] T008 [US1] Remove `.partner-button` and the mobile partner button from `frontend/src/app/layout/client-layout/client-layout.html`; adjust `frontend/src/app/layout/client-layout/client-layout.css` without changing account-menu routing
- [x] T009 [P] [US1] Extract deterministic date-range state helpers and local-date serialization in `frontend/src/app/features/client/home/services/home-search-state.service.ts`
- [x] T010 [P] [US1] Cover overnight, day-use, invalid/same-day, month/year boundary, recent-search restoration and timezone cases in `frontend/src/app/features/client/home/services/home-search-state.service.spec.ts`
- [x] T011 [US1] Rebuild `frontend/src/app/features/client/home/components/date-range-selector/date-range-selector.component.ts` with labelled controls, explicit overlay state, responsive month count and invalid feedback
- [x] T012 [US1] Add date picker keyboard/focus/mobile overlay tests in `frontend/src/app/features/client/home/components/date-range-selector/date-range-selector.component.spec.ts`
- [x] T013 [P] [US1] Recompose responsive search field layout and action hierarchy in `frontend/src/app/features/client/home/components/hero-search/hero-search.component.ts` and `frontend/src/app/features/client/home/components/sticky-search-bar/sticky-search-bar.component.ts`
- [x] T014 [P] [US1] Align overlay width, z-index and mobile safe-area styling in `frontend/src/app/features/client/home/home.css`, `frontend/src/app/features/client/home/components/location-autocomplete/location-autocomplete.component.css`, the inline styles in `frontend/src/app/features/client/home/components/date-range-selector/date-range-selector.component.ts` and `frontend/src/styles.css`
- [x] T015 [US1] Run targeted unit tests and real browser matrix for AC-101 to AC-104; record screenshots, viewport metrics and failures in `specs/006-booking-marketplace-roadmap/quickstart.md`

## Phase 2: Landmark Discovery (P2)

**Independent Test**: Select a persisted landmark, reload the URL and receive nearby approved/available properties ordered by distance with a recoverable no-result state.

- [x] T016 [P] [US3] Add landmark migration fields/indexes and representative non-production fixtures under `backend/src/main/resources/db/migration/` and `backend/src/main/resources/data/`
- [x] T017 [P] [US3] Extend `backend/src/main/java/com/hotel/entities/Location.java`, DTO mapping and import validation for active landmark metadata
- [x] T018 [US3] Implement province-scoped landmark lookup in `backend/src/main/java/com/hotel/services/PublicSearchSuggestionService.java` and repository queries in `backend/src/main/java/com/hotel/repositories/LocationRepository.java`
- [x] T019 [P] [US3] Add populated/inactive/duplicate-name/missing-coordinate tests in `backend/src/test/java/com/hotel/integration/PublicDiscoveryControllerIntegrationTest.java` and `backend/src/test/java/com/hotel/services/LocationImportServiceTest.java`
- [x] T020 [US3] Add `landmarkId` resolution and bounded radius filtering in `backend/src/main/java/com/hotel/dto/PropertySearchRequestDTO.java` and `backend/src/main/java/com/hotel/services/impl/PropertySearchServiceImpl.java`
- [x] T021 [P] [US3] Add landmark/radius/relevance integration tests in `backend/src/test/java/com/hotel/integration/PropertySearchControllerIntegrationTest.java`
- [x] T022 [US3] Persist selected landmark id/coordinates/radius in `frontend/src/app/features/client/home/services/home-search-state.service.ts` and client API models
- [x] T023 [P] [US3] Render landmark groups, disambiguation and result context/recovery in `frontend/src/app/features/client/home/components/location-autocomplete/` and `frontend/src/app/features/property-search/`
- [x] T024 [US3] Run AC-201 to AC-204 against persisted local data and record evidence in `specs/006-booking-marketplace-roadmap/quickstart.md`

## Phase 2B: Nationwide Landmark Catalog (P2)

**Independent Test**: Rebuild the catalog twice from pinned inputs, import twice into a real test database, and prove 63/63 compatibility provinces have at least three active coordinate-valid landmarks with no duplicate source key or silently rejected candidate.

- [x] T076 [P] [US3A] Audit and pin the administrative, editorial and geographic data sources with license, URL, version/date and checksum in `backend/tools/landmarks/SOURCES.md` and `specs/006-booking-marketplace-roadmap/research.md`
- [x] T077 [US3A] Restore all 63 legacy compatibility provinces in `backend/src/main/resources/data/locations.json`; document current 34-unit aliases and prohibit in-place code repurposing
- [x] T078 [P] [US3A] Add landmark provenance, quality/manual-override fields and import-run/issue schema in a new Flyway migration plus `backend/src/main/java/com/hotel/entities/Location.java`
- [x] T079 [US3A] Implement `backend/tools/landmarks/build_vietnam_landmarks.py` with pinned downloads/cache, UTF-8 normalization, category mapping, coordinate matching and deterministic output
- [x] T080 [P] [US3A] Add curated province-name aliases and source-row corrections under `backend/tools/landmarks/config/` without rewriting raw upstream data
- [x] T081 [US3A] Add exact provider-key and normalized-name/spatial deduplication, confidence scoring and machine-readable quarantine/coverage reports
- [x] T082 [US3A] Generate the versioned nationwide artifact in `backend/src/main/resources/data/landmarks.json` with source attribution and at least three publishable landmarks per current supported province
- [x] T083 [P] [US3A] Extend `backend/src/main/java/com/hotel/services/LocationImportService.java` to persist provenance/quality fields, preserve manual overrides and avoid destructive one-run cleanup
- [x] T084 [P] [US3A] Add generator validation tests for encoding, malformed input, coordinates, province coverage, category normalization, deterministic output and duplicate handling under `backend/tools/landmarks/tests/`
- [x] T085 [P] [US3A] Extend `backend/src/test/java/com/hotel/services/LocationImportServiceTest.java` with nationwide idempotency, provenance, manual-override and safe-deactivation cases
- [x] T086 [US3A] Run the generator and import twice; record checksums, counts, current-province coverage and quarantine reasons in `specs/006-booking-marketplace-roadmap/quickstart.md`
- [x] T087 [US3A] Run landmark suggestion/radius regression and measure autocomplete/query behavior with the nationwide row count; add indexes only from observed evidence
- [x] T088 [US3A] Complete real browser verification for representative north/central/south/island landmarks and update AC-205 to AC-210 plus the traceability matrix truthfully

## Phase 2C: Current 34-Province Compatibility Migration (P2)

**Independent Test**: Public province APIs expose 34 current units; selecting representative merged provinces returns hotels from every mapped legacy member; all generated landmarks use `VN34-*` province identities and coverage reports reconcile 34/34 without deleting legacy rows.

- [x] T089 [P] [US3A] Pin the 34-unit current province source, checksums and complete 63-to-34 membership in `backend/src/main/resources/data/provinces-current-34.json` and `backend/tools/landmarks/SOURCES.md`
- [x] T090 [US3A] Update `backend/tools/landmarks/build_vietnam_landmarks.py`, `backend/tools/landmarks/config/manual_landmarks.json` and generator tests to remap legacy editorial rows to current `VN34-*` provinces and report 34-unit breadth plus three-per-province editorial depth
- [x] T091 [P] [US3A] Extend `backend/src/main/java/com/hotel/services/LocationImportService.java` and `backend/src/test/java/com/hotel/services/LocationImportServiceTest.java` to import current provinces before landmarks while preserving legacy province/ward rows and stable landmark identities
- [x] T092 [P] [US3A] Add `backend/src/main/java/com/hotel/services/ProvinceCompatibilityService.java`, repository scope queries and a guarded lookup index migration under `backend/src/main/resources/db/migration/`
- [x] T093 [US3A] Update `backend/src/main/java/com/hotel/controllers/LocationController.java` and `backend/src/main/java/com/hotel/services/PublicSearchSuggestionService.java` so public province/ward/suggestion/popular responses use current province identities and aggregate legacy hotel counts
- [x] T094 [US3A] Update `backend/src/main/java/com/hotel/services/impl/PropertySearchServiceImpl.java` so current province and landmark searches expand to mapped legacy hotel province ids and display current province context
- [x] T095 [P] [US3A] Add importer, public discovery and property search integration coverage for 34-unit lists, code-collision safety, merged-province expansion and remapped landmarks under `backend/src/test/java/com/hotel/`
- [x] T096 [US3A] Run generator/import/search suites twice, record current checksums and truthful 34-unit breadth/editorial-depth results in `specs/006-booking-marketplace-roadmap/quickstart.md`, then update T089-T096 status

## Phase 3: Promotions, VIP Deals and Governed Advertising (P2)

**Independent Test**: Configure real campaigns/tiers/placements, verify eligibility and one authoritative final price across search, detail, checkout and invoice.

- [x] T025 [US4] Finalize promotion stacking, tier and sponsored ranking decisions from OQ-003 to OQ-005 in `specs/006-booking-marketplace-roadmap/business-rules.md`
- [x] T026 [P] [US4] Add migrations and tenant-filtered entities/repositories in `backend/src/main/resources/db/migration/`, `backend/src/main/java/com/hotel/entities/PromotionCampaign.java`, `PromotionRedemption.java`, `MembershipTier.java`, `CustomerMembership.java`, `SponsoredPlacement.java` and matching files under `backend/src/main/java/com/hotel/repositories/`; every property-related row must carry `hotel_id` and use Hibernate `@Filter`
- [x] T027 [P] [US4] Define typed campaign, quote and placement DTOs under `backend/src/main/java/com/hotel/dtos/`
- [x] T028 [US4] Implement a canonical quote/promotion evaluator under `backend/src/main/java/com/hotel/services/` with money rounding, eligibility, stacking, budget/quota and idempotent redemption
- [x] T029 [P] [US4] Add unit/property tests for expiry, eligibility, stacking, max discount, quota and replay under `backend/src/test/java/com/hotel/services/`
- [x] T030 [US4] Integrate the canonical quote into property search/detail/booking/invoice/refund services without duplicating frontend price calculations
- [x] T031 [P] [US4] Add cross-surface price consistency integration tests under `backend/src/test/java/com/hotel/integration/`
- [x] T032 [US4] Implement authorized admin/tenant campaign and sponsored-placement APIs/controllers with tenant and subscription gates
- [x] T033 [P] [US4] Replace the unused hard-coded `PromotionsComponent` contract with typed API data and integrate deal/member sections into `frontend/src/app/features/client/home/`
- [x] T034 [P] [US4] Add transparent original/final price, tier condition and sponsored labels to search/detail/checkout components with VI/EN-ready keys
- [x] T035 [US4] Run AC-301 to AC-305 including browser disclosure and no-fake-data checks; record evidence in `specs/006-booking-marketplace-roadmap/quickstart.md`

### Phase 3A: Home Destination Recommendations (P1, independent of sponsored policy)

**Design input**: `specs/006-booking-marketplace-roadmap/home-discovery-merchandising/`

**Independent Test**: Switch among real destination tabs, verify approved organic properties and open matching search/detail routes while preserving Home date/guest context.

- [x] T102 [US4] Update `docs/ERD.md`, `docs/UML.md`, `docs/API_SPEC.md` and `docs/THESIS.md` with the Home recommendation/spotlight projections, flow and endpoint contracts before runtime code
- [x] T103 [P] [US4] Add typed Home recommendation destination/item DTOs under `backend/src/main/java/com/hotel/dtos/home/` and mirror strict TypeScript models in `frontend/src/app/core/services/client-api.service.ts`
- [x] T104 [US4] Implement `HomeRecommendationService` with current-province compatibility, approved/active/available property gates, deterministic rating-review-id ordering and context-preserving query validation
- [x] T105 [P] [US4] Add `/api/public/home/recommendation-destinations` and `/api/public/home/recommendations` contracts to `PublicDiscoveryController` with bounded limits and independent error handling
- [x] T106 [P] [US4] Add backend unit/integration coverage for default destination, merged-province expansion, availability, stable ranking, empty supply and invalid query cases
- [x] T107 [US4] Implement `destination-recommendations` as a standalone Angular component using signals for local selected/loading/error state, cancellable request flow, real tabs, responsive snap track and canonical search/detail navigation
- [x] T108 [P] [US4] Add VI/EN keys and Angular tests for tab semantics, rapid switching, loading/empty/error/retry, image fallback, no fake discount, reduced motion and 44px controls
- [x] T109 [US4] Integrate destination recommendations into Home without replacing the existing editorial slideshow or resetting `HomeSearchStateService`

### Phase 3B: Governed Home Partner Spotlights (P2, depends on T025-T027 and T032)

**Independent Test**: Render one editorial and one sponsored active placement, exclude expired/disabled/unapproved placements, and verify 100% sponsored disclosure.

- [x] T110 [US4] Record the `EDITORIAL` versus `SPONSORED` Home placement policy, target-route allowlist, configuration authority and disclosure wording in `business-rules.md` and `open-questions.md`
- [x] T111 [US4] Extend the parent sponsored-placement migration/entity/repository with Home surface, localized creative, authorized asset, schedule, quota, target and audit fields defined in `home-discovery-merchandising/data-model.md`
- [x] T112 [P] [US4] Implement `HomeSpotlightService` and `/api/public/home/spotlights` so only active scheduled approved in-quota projections are public and organic ranking remains untouched
- [x] T113 [P] [US4] Add authorized admin/tenant placement configuration tests plus public expiry, quota, disclosure, target allowlist and cross-tenant denial tests
- [x] T114 [US4] Implement `partner-spotlight-carousel` with stable dimensions, lazy authorized images, manual previous/next controls, keyboard/focus support and reduced-motion behavior
- [x] T115 [P] [US4] Add VI/EN component tests for sponsored/editorial labels, empty omission, endpoint failure isolation, image fallback and mobile card-track overflow
- [x] T116 [US4] Integrate canonical promotion quote fields only after T028-T031 pass; before that milestone render no strike-through, member or inferred discount price
- [x] T117 [US4] Run the addendum quickstart and Playwright journey at 375/768/1024/1440px; record screenshots, disclosure, context preservation, console, missing-key, overflow and CLS evidence in the parent quickstart/traceability matrix

## Phase 4: Vietnamese/English and Accessible Home Motion (P2)

**Independent Test**: Switch VI/EN through public P1 routes, reload, operate the Home slideshow by keyboard and verify reduced-motion static behavior.

- [x] T036 [P] [US5] Add app-level translate provider/loader and locale store under `frontend/src/app/core/i18n/` and `frontend/src/app/app.config.ts`
- [x] T037 [P] [US5] Add dynamic PrimeNG locale mapping and locale-aware date/number helpers under `frontend/src/app/core/i18n/`
- [x] T038 [US5] Convert header locale status into a real accessible VI/EN control in `frontend/src/app/layout/client-layout/` while keeping currency explicitly VND
- [x] T039 [P] [US5] Migrate Home/search/detail/booking/payment/account/support P1 strings to `frontend/src/assets/i18n/vi.json` and `frontend/src/assets/i18n/en.json`
- [x] T040 [P] [US5] Add automated missing-key and locale persistence tests under `frontend/src/app/core/i18n/` and relevant component specs
- [x] T041 [US5] Implement a stable-dimension editorial slideshow component under `frontend/src/app/features/client/home/components/` using real configured content, pause/previous/next and no focus theft
- [x] T042 [P] [US5] Add slideshow keyboard, pause, visibility and reduced-motion tests plus image failure behavior
- [x] T043 [US5] Integrate the slideshow into Home without displacing the primary search hierarchy; update `frontend/src/app/features/client/home/home.html`, `frontend/src/app/features/client/home/home.css` and `frontend/src/app/features/client/home/home.ts`
- [x] T044 [US5] Run AC-401 to AC-404 including VI/EN screenshots, missing-key scan, reduced-motion evidence and five cold desktop/mobile Performance API runs reporting p75 interaction delay and CLS

## Phase 5: Booking, Payment and Refund Completion (P1, High Risk)

**STOP GATE**: T005/OQ-001 must be approved before callback policy code or browser mutation.

**Independent Test**: One remaining room under concurrent booking; idempotent payment/refund callbacks; visible pending/failure/reconciliation states.

- [x] T045 [US2] Add canonical reservation/payment/refund enums or validated value objects and a data migration for legacy `PENDING`/payment strings
- [x] T046 [P] [US2] Add transition-table unit tests for valid/invalid/late-event behavior under `backend/src/test/java/com/hotel/services/`
- [x] T047 [US2] Add tenant-filtered `ReservationHold` persistence with `hotel_id`, configurable TTL and idempotent expiry/release service in `backend/src/main/java/com/hotel/entities/ReservationHold.java`, `backend/src/main/java/com/hotel/repositories/ReservationHoldRepository.java` and `backend/src/main/java/com/hotel/services/ReservationHoldService.java`
- [x] T048 [P] [US2] Add scheduler/restart/replay tests proving expired holds release inventory once
- [x] T049 [US2] Preserve room-type pessimistic locking and add a real two-thread booking integration test with one remaining room under `backend/src/test/java/com/hotel/integration/ReservationConcurrencyIntegrationTest.java`
- [x] T050 [US2] Implement the explicitly approved demo payment session/callback contract in payment controllers/services; reject caller-controlled ownership/amount/status and preserve unique transaction idempotency
- [x] T051 [P] [US2] Add cross-account, tamper, expiry, replay and concurrent callback tests in payment integration/security tests
- [x] T052 [US2] Validate VNPay callback amount/reference/status against the server-side session and route late success to reconciliation rather than silent confirmation
- [x] T053 [US2] Introduce explicit refund request/provider-attempt lifecycle and ensure points/inventory/notifications change exactly once after authoritative outcomes
- [x] T054 [US2] Update customer/admin payment, booking history and cancellation UI for pending, failed, expired, refund-pending, refunded and reconciliation states; run AC-501 to AC-505 and record evidence
  - Complete: safe reservation payment/refund summaries, customer cancellation-to-REQUESTED flow, admin lifecycle badges with paginated refund coverage, zoneless change-detection regression test, and 2/2 real Playwright customer/admin browser journeys with screenshots are recorded in `quickstart.md`.
- [x] T097 [P] [US2] Remove the deferred Stripe checkout choice, expose VNPay/MoMo/ZaloPay truthfully and add authenticated payment-session status polling on the browser return page
- [x] T098 [P] [US2] Implement the official MoMo Test create/IPN signature contract with environment-only credentials, server-owned amount/reference, replay-safe processing and provider-specific tests
- [x] T099 [P] [US2] Implement the official ZaloPay Sandbox create/callback signature contract with environment-only credentials, server-owned amount/reference, replay-safe processing and provider-specific tests
- [x] T100 [US2] Add MoMo/ZaloPay transaction query and asynchronous refund/query adapters, then connect them to the explicit refund request/provider-attempt lifecycle from T053
  - Complete: official MoMo query/refund/refund-query HMAC contracts, ZaloPay query/refund/refund-query form contracts, server-bound amount/reference validation, scheduled missed-callback recovery, deterministic provider refund references, timeout-safe pending retries and idempotent lifecycle integration. Live sandbox proof remains T101.
- [ ] T101 [US2] Run live VNPay/MoMo/ZaloPay sandbox journeys with provisioned credentials and public callback URLs; record provider request ids, callback acknowledgements and reconciliation evidence without committing secrets

## Phase 6: Tenant Support and Facebook/Zalo Channels (P2, External Dependency)

**STOP GATE**: OQ-006 to OQ-009 and provider sandbox credentials are required before external message transmission.

**Independent Test**: Authenticated internal chat passes end to end; tenant isolation is proven; each enabled provider passes official sandbox signature/dedup/retry tests.

- [x] T055 [US6] Complete Feature-03 T062 authenticated customer/support browser coverage for history, send, reply, reconnect and offline recovery
  - Complete: login now persists the server-owned `userId`; `support-chat-lifecycle.spec.ts` passes history reload, principal-derived customer send, support queue/reply delivery, blocked-transport recovery and reconnect against isolated E2E actors.
- [x] T056 [P] [US6] Add `SupportConversation` and tenant/property/reservation context migration plus `backend/src/main/java/com/hotel/entities/SupportConversation.java` and its repository; tenant conversations/messages must carry `hotel_id` and use Hibernate `@Filter`
  - Complete: `V20__tenant_support_conversations.sql` adds tenant-scoped conversations/events and links new messages to both `conversation_id` and `hotel_id`; pre-migration messages remain explicitly `legacy_unscoped` instead of receiving guessed tenant ownership.
- [x] T057 [US6] Replace central receiver-only routing with authorized conversation assignment while preserving principal-derived identity and `/user/queue` delivery
  - Complete: customer context is resolved from an owned reservation or approved/active property, support queues are tenant-scoped, replies use conversation ids, assignment/escalation is explicit and STOMP delivery uses private `/user/queue` destinations.
- [x] T058 [P] [US6] Add cross-tenant list/history/reply denial and assignment/escalation integration tests
  - Complete: focused service/controller/security coverage passes `12/12`; H2 tenant-isolation integration passes `2/2`, including hidden cross-tenant history/reply, audit events and controlled assignment/escalation handoff.
- [ ] T059 [US6] Obtain and record OQ-006 to OQ-009 approvals, then implement secret-safe, tenant-filtered `TenantSupportChannel` management API/UI with `hotel_id`, connect/disconnect/health state and audit events; do not store credentials or transmit messages before approval
- [ ] T060 [P] [US6] Implement the first approved provider adapter using official OAuth/webhook APIs, signature validation, event idempotency, retry/rate-limit and revoked-token handling
- [ ] T061 [US6] Implement the second provider only if separately approved and sandbox credentials are available after the first adapter passes; otherwise record it as DEFERRED/BLOCKED. Run AC-601 to AC-604 and report each provider independently

## Phase 7: Subscription Package Reconciliation (P2)

**Independent Test**: Canonical plan/feature/usage contract renders identically in admin/management and correctly gates active, expired, lifetime, unlimited and multi-plan fixtures.

- [x] T062 [P] [US7] Define canonical plan/feature/usage DTOs and feature catalog under `backend/src/main/java/com/hotel/dtos/` and `specs/006-booking-marketplace-roadmap/contracts/marketplace-api-contract.md`
- [x] T063 [US7] Update subscription APIs/services to return only canonical active offer data plus effective usage/limits; preserve inactive historical plans
- [ ] T064 [P] [US7] Add admin-authorized plan/feature lifecycle APIs and validation if plan configuration is approved
- [x] T065 [P] [US7] Reconcile Angular models and remove hard-coded inferred benefits/fake purchase messaging in `frontend/src/app/features/management/subscription-billing/` and `frontend/src/app/features/admin/subscription-plans/`
- [x] T066 [US7] Inventory every advertised feature and add backend entitlement checks to each mutation, preserving authorized read-only history after expiry
  - Verified inventory: `MAX_PROPERTIES`, `MAX_ROOM_TYPES`, `MAX_ROOMS`, `MAX_IMAGES` and `MAX_STAFF`. Direct room/room-type CRUD, bulk room creation, image association, staff mutation, management property creation and property-claim approval now use effective-subscription checks; property-claim requester/reviewer identities come from the authenticated principal and admin endpoints accept the canonical `SUPER_ADMIN` authority; read APIs remain ungated.
- [x] T067 [P] [US7] Add active/expired/lifetime/unlimited/multiple-subscription service, integration and browser tests
  - E2E fixtures provision active, expired, lifetime/unlimited and multi-plan owners. Repository/service integration plus five real Playwright journeys verify effective limits, merged maxima, historical reads and expired mutation denial.
- [x] T068 [US7] Implement only the approved upgrade path (real contact or complete order/payment/activation); run AC-701 to AC-704 and record evidence
  - The truthful contact path remains `mailto:support@luxestay.vn`; Playwright verifies it is visible for upgrade candidates and that no fake `Mua ngay` action is rendered. Online order/payment activation remains intentionally unclaimed.

## Phase 8: Integrated Quality and Release Gate

- [x] T069 [P] Run full frontend tests and production build from `frontend/`; record counts, warnings and bundle metrics
- [x] T070 [P] Run full backend tests plus payment/security/concurrency suites from `backend/`; record counts and failures
- [x] T071 Run real public/customer/admin/management browser journeys at 375/768/1024/1440px with keyboard, reduced motion and VI/EN
  - Complete: `integrated-release-matrix.spec.ts` passes all four viewport journeys against the dedicated E2E backend. Home, Search, Customer, Admin and Management surfaces render in alternating VI/EN contexts with reduced-motion coverage, valid keyboard focus and no runtime errors.
- [x] T072 [P] Run page overflow, focus target, missing translation, sponsored disclosure, console error and performance checks
  - Complete at the 2026-07-31 Phase 8 checkpoint: all 20 surface/viewport audits report no page overflow, no missing P1 translation keys, no console/page/HTTP errors, visible in-viewport focus and CLS below `0.04`. Its historical zero-sponsored result recorded the then-open gate; T035/T117 now supersede it with approved and visibly disclosed placement evidence.
- [x] T073 Reconcile Feature-03 GAP-005/GAP-019/GAP-021/GAP-022/GAP-024 and open task status without overwriting unrelated evidence
- [x] T074 Update `specs/006-booking-marketplace-roadmap/traceability-matrix.md` with final evidence and truthful COMPLETE/PARTIAL/BLOCKED status
- [x] T075 Run `git diff --check`, inspect only Feature-06/scoped source changes and prepare a release report; do not stage/commit unrelated worktree files

## Dependency Order

`Phase 0 -> Phase 1 -> Phase 2 -> Phase 2B -> Phase 3 -> Phase 4 -> Phase 5 -> Phase 6 -> Phase 7 -> Phase 8`

- Phase 1 is the first implementation slice and should be delivered before broader marketplace domains.
- Phase 2 depends on stable search state from Phase 1.
- Phase 2B depends on the Phase 2 landmark contract and must complete its data-quality/import gate before nationwide discovery is advertised.
- Phase 3 depends on search/booking contracts and approved product rules.
- Phase 4 may start after Phase 1 and can overlap late Phase 2/3 work if files are separated, but release verification waits for them.
- Phase 5 must not cross the payment decision gate.
- Phase 6 internal chat work can begin without external credentials; provider adapters cannot.
- Phase 7 may proceed independently of provider adapters but must consume Phase 5 payment policy if online purchase is approved.
- Phase 8 requires every selected phase to be either complete or explicitly blocked/deferred.

## Suggested Delivery Increments

1. **Increment A**: Phase 0-1 - visible header/search/date fixes.
2. **Increment B**: Phase 2-2B - landmark discovery plus nationwide catalog.
3. **Increment C**: Phase 3-4 - marketplace merchandising, bilingual UI and Home motion.
4. **Increment D**: Phase 5 - inventory/payment/refund trust.
5. **Increment E**: Phase 6-7 - tenant support channels and subscription governance.
6. **Increment F**: Phase 8 - integrated release proof.
