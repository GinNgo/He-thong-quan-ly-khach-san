# Implementation Plan: LuxeStay Booking Marketplace Readiness

**Branch**: `[006-booking-marketplace-roadmap]` | **Date**: 2026-07-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-booking-marketplace-roadmap/spec.md`

## Summary

Deliver the marketplace improvements as independently verifiable phases rather than one high-risk rewrite. Start with the public conversion defect (header/search/date), then add landmark discovery and a reproducible nationwide landmark catalog before governed promotions/VIP/advertising, VI/EN localization and accessible Home motion. Payment/refund/concurrent inventory, tenant support/social channels and subscription reconciliation follow behind explicit security/product decision gates. Existing source contracts and Feature-03 audit evidence are retained; no phase is complete without real API/browser evidence.

## Technical Context

**Language/Version**: TypeScript 6 / Angular 22 standalone components; Java 21 / Spring Boot 3; SQL Server production and H2 test/e2e profiles

**Primary Dependencies**: Angular Router/Forms/Animations, PrimeNG 21, `@ngx-translate/core`, RxJS, STOMP/SockJS, Spring MVC/Security/Data JPA/WebSocket, VNPay integration, pinned `provinces.open-api.vn` v2 current-administration data, versioned GeoNames/OpenStreetMap and curated MIT-licensed Vietnam travel data for offline landmark generation

**Storage**: Existing relational database plus Flyway migrations for landmark provenance/import runs, campaign/placement, reservation holds, payment/refund lifecycle and tenant channel configuration

**Testing**: Vitest Angular unit tests, Maven/JUnit/MockMvc/integration tests, concurrency tests, Playwright/in-app browser at 375/768/1024/1440px, keyboard/reduced-motion/performance checks

**Target Platform**: Responsive web marketplace and authenticated admin/management portals

**Project Type**: Full-stack multi-tenant hotel booking web application

**Performance Goals**: Search response remains within existing baseline; Home motion causes CLS <= 0.05; no page overflow; provider webhooks acknowledge safely within provider timeout and process asynchronously when added

**Constraints**: Preserve dirty worktree changes; no fake promotions/social success; VND-only settlement; callback policy and external credentials require approval; tenant isolation and payment idempotency cannot be weakened

**Scale/Scope**: Public Home/search/detail/booking/payment, 34 current public provinces backed by a 63-province compatibility alias layer, nationwide landmark data, admin/management subscription and support surfaces, backend discovery/pricing/reservation/payment/chat/subscription domains

## Constitution Check

*GATE: Re-check at each phase boundary.*

- [x] **I. An toàn chức năng**: Phases are isolated; payment/social mutations sit behind explicit gates and current Feature-03 contracts are preserved.
- [x] **II. Hiểu biết toàn diện**: Home/search/date, payment/refund, reservation locks, chat authorization, locations, subscriptions, i18n assets and audit artifacts were inspected.
- [x] **III. Tái sử dụng**: Reuse `HomeSearchStateService`, existing public APIs, `Location`, pricing DTOs, pessimistic locks, chat security and subscription entities/services.
- [x] **IV. Validation & Error Handling**: Each domain defines invalid, pending, failure, expiry, retry and idempotent recovery states.
- [x] **V. Trải nghiệm thực tế**: Persisted campaigns/landmarks and official provider sandboxes are required; no hard-coded campaign or simulated provider success may close a task.
- [x] **VI & VII. Kiểm định & Xác minh**: Unit, integration, concurrency, full regression and browser matrices are phase acceptance gates.
- [x] **VIII. Ghi chép**: Requirements, business rules, contracts, traceability, open decisions and quickstart evidence are feature artifacts.

## Evidence Baseline

| Capability | Current assessment | Evidence and consequence |
|---|---|---|
| Header partner CTA | Complete for requested removal | Standalone `.partner-button` and mobile CTA are removed; account/Home owner entry points remain and are covered by header regression tests |
| Responsive Home search/date | Phase 1 complete for overnight flow; day-use partial/blocked | Hero/sticky search use responsive layouts and bounded overlays; date controls are labelled, local-date safe and tested. Day-use remains disabled because the backend contract still requires checkout |
| Landmark discovery | Phase 2 search contract and Phase 2C backend/data migration complete | Search/radius behavior is implemented. The legacy 63 rows remain compatibility data; public discovery exposes 34 `VN34-*` identities through an explicit 63-to-34 alias catalog. The generated catalog reaches 34/34 current units with at least three active coordinate-valid landmarks each; backend/data and representative browser evidence are recorded |
| Promotions/VIP/ads | Missing domain | Unused `PromotionsComponent`; pricing exposes discount-shaped fields but backend currently returns no discount/badges and no campaign/placement entities |
| VI/EN | Complete for scoped public P1 routes | `vi.json`/`en.json`, pre-render app locale initialization, PrimeNG/document locale mapping, persisted accessible controls, key parity and browser switching evidence pass |
| Home motion | Complete for scoped release evidence | Controlled slideshow/editorial content has stable dimensions, pause/navigation, visibility/focus pause, image fallback and reduced-motion behavior; five cold desktop/mobile performance runs pass the interaction and CLS budgets |
| Booking concurrency | Hold and no-overbooking proof complete | `RoomTypeRepository.findByIdForUpdate()` serializes create-booking checks through persisted hold creation; scheduler/replay tests release expired inventory once and a real two-thread H2 test proves one success/one sold-out result with one room |
| Payment/refund | Local completion, live gate open | Server-bound expiring payment sessions, authoritative VNPay/MoMo/ZaloPay callback verification, reconciliation tests, explicit refund/provider-attempt lifecycle, official MoMo/ZaloPay query/refund adapters and scheduled timeout-safe recovery pass local tests; only live sandbox verification remains open |
| Internal chat | Tenant-aware in-app scope implemented | Principal-derived customer identity, property/reservation conversation context, tenant-scoped messages/audit events, private user queues, assignment/escalation and cross-tenant denial evidence pass; external provider channels remain gated |
| Facebook/Zalo | Missing | No code/configuration found; official provider contracts, secrets, consent and webhooks required |
| Subscription packages | Partial | Canonical plan/feature/usage DTOs and active-plan/history APIs feed admin/management screens; hard-coded benefits and fake purchase messaging are removed. T066-T068 enforce the five advertised quotas, preserve reads after expiry, verify active/expired/lifetime/unlimited/multi-plan behavior in integration and 5/5 browser journeys, and retain a real support-contact upgrade path. Only admin plan/feature lifecycle remains gated by OQ-010/configuration approval |

## Phased Delivery

### Phase 0 - Baseline and Decision Gates

- Freeze current behavior with targeted tests and screenshots.
- Approve status vocabulary, pending-hold TTL, promotion stacking, sponsored placement policy, social channel custody and payment callback contract.
- Produce canonical API/data contracts before schema changes.
- Exit gate: no unresolved decision capable of changing Phase 1-4 implementation; payment/social policy may remain gated for later phases.

### Phase 1 - Public Search and Date Reliability

- Remove partner CTA from desktop/mobile header only.
- Redesign search hierarchy for mobile/tablet/desktop; use bounded mobile sheet and responsive month count.
- Centralize date state/serialization and add interaction/accessibility tests.
- Exit gate: browser matrix and overnight/date regression pass with no search URL regression; day-use stays `PARTIAL/BLOCKED` until the backend accepts a single service date.

### Phase 2 - Landmark Discovery

- Seed/import bilingual landmarks with region and coordinates.
- Return landmark suggestions and resolve selection to search geography.
- Add nearby sorting, radius expansion and result context.
- Exit gate: persisted landmark autocomplete/search cases pass end to end.

### Phase 2B - Nationwide Landmark Catalog

- Freeze the supported 63-code administrative compatibility baseline, fill missing provinces from the same source schema and document the future 34-unit migration/alias risk.
- Build a reproducible offline catalog pipeline from a versioned, licensed 315-place curated source and open geographic coordinates; never bulk-scrape public Nominatim.
- Normalize Vietnamese names/categories, assign province codes, attach provenance, score match quality and quarantine unresolved or ambiguous candidates.
- Deduplicate by provider key plus normalized-name/spatial similarity, preserve manual overrides and soft-deactivate missing source rows only after a reviewed policy threshold.
- Import the generated UTF-8 artifact idempotently and publish coverage, duplicate, coordinate and per-province reports.
- Exit gate: every supported province has at least three active coordinate-valid landmarks, repeated build/import is stable and all rejected candidates are auditable.

### Phase 2C - Current 34-Province Compatibility Migration

- Pin the 34-unit current province catalog and official codes from the versioned v2 source; assign independent `VN34-*` application identities.
- Preserve all legacy province/ward rows referenced by hotels and add a complete, versioned 63-to-34 alias map instead of renaming numeric source codes.
- Import current provinces before landmarks, expose only current provinces publicly and expand current province filters to current plus mapped legacy database ids.
- Remap generated landmarks to current province identities while retaining legacy province names and bounding boxes only for source-row coordinate validation.
- Exit gate: public API returns 34 current units, all 63 legacy codes map exactly once, current-region search includes all constituent legacy hotels, 34/34 current units have verified landmark breadth and repeated build/import remains stable.

### Phase 3 - Promotions, VIP and Advertising

- Add campaign, eligibility, application and sponsored-placement models.
- Build one server-side price quote used by search/detail/checkout/invoice/refund.
- Add labelled Home deal/member/advertising surfaces and admin configuration.
- Deliver the organic destination-tab recommendation slice independently before sponsored policy is complete; then add the governed partner spotlight carousel. Detailed scope, API and responsive behavior are defined in `home-discovery-merchandising/plan.md`.
- Exit gate: price consistency, stacking, expiry, quota and disclosure tests pass.

### Phase 4 - VI/EN and Accessible Home Motion

- Configure app-level locale store, translation loader and dynamic PrimeNG locale.
- Migrate public P1 strings with missing-key checks.
- Add a controlled editorial slideshow with stable layout, pause/navigation and reduced-motion fallback.
- Exit gate: zero missing public keys and accessibility/performance browser checks pass.

### Phase 5 - Booking, Payment and Refund Completion

- Unify reservation/payment/refund statuses and transitions.
- Add expiring inventory holds and scheduled/idempotent release.
- Prove concurrent booking behavior and callback/refund replay handling.
- Implement the approved T058/GAP-022 contract; verify amount, ownership/signature, expiry and gateway state.
- Exit gate: concurrency/security/payment lifecycle evidence passes. This phase must stop until callback policy approval exists.

### Phase 6 - Tenant Support and Social Channels

- Complete internal authenticated browser chat coverage.
- Add tenant/property conversation context, assignment and isolation.
- Add optional Facebook/Zalo adapters only through official OAuth/webhooks and provider sandbox credentials.
- Exit gate: cross-tenant denial plus provider signature/dedup/retry/consent tests pass; unavailable provider credentials remain an explicit blocker, not simulated completion.

### Phase 7 - Subscription Package Reconciliation

- Define canonical plan DTO and feature catalog.
- Add admin plan/feature lifecycle and truthful management comparison/usage UI.
- Map every advertised entitlement to backend gates; remove fake purchase messaging.
- Add approved order/payment activation path or retain an explicit real contact path.
- Exit gate: active/expired/unlimited/multiple-plan and read-only preservation tests pass.

### Phase 8 - Integrated Release Gate

- Run complete backend/frontend regression, full browser journey matrix, accessibility, reduced-motion, locale, performance and security checks.
- Reconcile Feature-03 open tasks/gaps and publish truthful `COMPLETE/PARTIAL/BLOCKED` status.
- Exit gate: no high/critical unresolved defect without owner, decision and documented limitation.

## Project Structure

### Documentation (this feature)

```text
specs/006-booking-marketplace-roadmap/
|-- spec.md
|-- plan.md
|-- research.md
|-- requirements.md
|-- business-rules.md
|-- acceptance-criteria.md
|-- traceability-matrix.md
|-- open-questions.md
|-- data-model.md
|-- quickstart.md
|-- contracts/marketplace-api-contract.md
`-- tasks.md
```

### Source Code (repository root)

```text
frontend/src/app/
|-- layout/client-layout/
|-- features/client/home/
|-- features/property-search/
|-- features/client/{hotel-detail,booking-checkout,payment-result,payment-simulator,chat-widget}/
|-- features/admin/{chat-dashboard,subscription-plans}/
|-- features/management/subscription-billing/
`-- core/{services,i18n}/

backend/src/main/java/com/hotel/
|-- controllers/{PublicDiscoveryController,PropertySearchController,ReservationController,PaymentController,ChatController,SubscriptionController}.java
|-- services/{PublicSearchSuggestionService,ReservationService,RoomAvailabilityService,ChatService,SubscriptionFeatureService}.java
|-- services/impl/{PropertySearchServiceImpl,PaymentServiceImpl}.java
|-- entities/
|-- repositories/
|-- security/
`-- tools/landmarks/

backend/src/test/java/com/hotel/
|-- services/
|-- integration/
`-- security/
```

**Structure Decision**: Extend existing Angular/Spring feature boundaries. New marketplace domains live beside current location/payment/subscription entities and services; provider-specific social adapters are isolated behind a common support-channel interface.

Every new property-related business entity MUST carry `hotel_id`/`property_id`, resolve tenant scope from the authenticated principal through `PropertyAccessService`, and use the constitution-mandated Hibernate `@Filter`. Client-supplied tenant identifiers are never the source of truth.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| External Facebook/Zalo adapters | Per-tenant support is explicitly requested and provider protocols differ | Hard-coded links cannot provide conversation routing, consent, delivery status or tenant isolation |
| New promotion/placement domain | Pricing and disclosure must be consistent and auditable | Reusing the unused UI-only promotions array would produce fake, non-enforceable discounts |
| Offline nationwide data pipeline | Coordinate/province matching, licensing and repeatable refresh need tooling outside the runtime request path | Hand-editing hundreds of rows cannot prove provenance, deduplication, coverage or idempotency |
