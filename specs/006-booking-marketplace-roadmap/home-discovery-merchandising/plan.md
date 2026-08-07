# Implementation Plan: Home Discovery and Merchandising

**Parent feature**: `006-booking-marketplace-roadmap` | **Date**: 2026-08-03 | **Spec**: [spec.md](./spec.md)

## Summary

Add two independent Home discovery slices. Deliver organic destination recommendations first by extending the real public discovery/search contract. Deliver partner spotlights second behind the existing sponsored-placement policy and admin configuration tasks. Keep the current editorial destination slideshow intact, preserve LuxeStay design tokens, and never render fake brands, fake discounts or unlabelled sponsored content.

## Technical Context

**Frontend**: Angular 22 standalone components, TypeScript 6, zoneless change detection, signals for local state, RxJS/HttpClient at service boundaries, ngx-translate, PrimeIcons and existing CSS variables.

**Backend**: Java 21, Spring Boot 3, controller-service-repository layering, Flyway migrations, tenant filtering for property-owned records and H2-backed integration tests.

**Existing dependencies**: `HomeSearchStateService`, `ClientApiService`, public property search, current 34-province compatibility, `ImageFallbackService`, public VI/EN locale service and the existing Home layout.

**Testing**: Angular/Vitest component tests, Spring service/integration/security tests, production build and Playwright at 375/768/1024/1440px.

**Constraints**: Preserve the dirty worktree; update documentation before runtime code; no fake campaign data; no direct frontend price calculation; no copied external assets; no change to the active `.specify/feature.json` pointer.

## Current Baseline

| Surface | Current behavior | Gap |
|---|---|---|
| Editorial slideshow | Static, real local destination content with accessible controls | Not a partner/store spotlight and must not be overloaded with ad semantics |
| Featured properties | One global `sortBy=RATING` list | No location tabs, destination-scoped fetch, context preservation or per-tab loading state |
| Promotion/member card | Truthful account status/empty copy | No persisted campaigns, sponsored placements or configured partner creative |
| Public discovery API | Popular destinations and property search | No Home-specific destination projection or governed spotlight endpoint |

## Constitution Check

- [x] **Functional safety**: Organic and sponsored ranking are separated; price and navigation remain server/context-owned.
- [x] **Comprehensive understanding**: Existing Home, slideshow, featured property component, public discovery API and Feature 006 policy gates were inspected.
- [x] **Reuse**: Existing search state, location compatibility, property search DTOs, i18n and image fallback remain the foundation.
- [x] **Validation and errors**: Loading, empty, error, retry, expiry and unavailable states are explicit.
- [x] **Real experience**: No fake placements, fake discounts or placeholder routes are allowed.
- [x] **Verification**: Unit, integration, responsive, keyboard, reduced-motion and browser evidence are required.
- [x] **Documentation first**: ERD/UML/API/THESIS updates precede schema/API/UI implementation.

## Architecture

```text
HomeComponent
|-- PartnerSpotlightCarouselComponent
|   `-- GET /api/public/home/spotlights
|       `-- HomeSpotlightService -> SponsoredPlacement repository
`-- DestinationRecommendationsComponent
    |-- GET /api/public/home/recommendation-destinations
    `-- GET /api/public/home/recommendations
        `-- HomeRecommendationService -> location compatibility + property search/quote
```

The endpoints stay separate so each section can load, fail, retry and be cached independently. The Home component only composes sections; each child owns its local selected/loading/error state.

## Delivery Phases

### Phase A - Product and Documentation Gate

1. Confirm that Home spotlights support both `EDITORIAL` and `SPONSORED` types.
2. Confirm the OQ-005 slot/disclosure policy and who may configure a placement.
3. Update `docs/ERD.md`, `docs/UML.md`, `docs/API_SPEC.md` and `docs/THESIS.md` before runtime code.
4. Pin the default destination rule: current valid Home search location, then popular destination with active supply.

**Exit gate**: No unresolved disclosure, ownership, target-route or default-ranking decision.

### Phase B - Organic Destination Recommendations MVP

1. Add typed destination-tab and recommendation-item DTOs.
2. Add a Home recommendation service using current province compatibility and the existing property search/query contract.
3. Expose destination and recommendation public endpoints with bounded limits and stable tie-breakers.
4. Implement an Angular destination recommendation component with local signals, request cancellation on rapid tab changes, skeleton/empty/error/retry states and context-preserving navigation.
5. Keep pricing limited to authoritative current price fields; do not show discount/member badges yet.

**Exit gate**: Five-or-fewer real destination tabs and their organic cards work independently on desktop/mobile with no policy blocker.

### Phase C - Governed Partner Spotlights

1. Complete parent tasks T025-T027 and T032 for sponsored-placement policy, persistence, DTOs and authorized management.
2. Add placement schedule/status/type/target/asset/disclosure validation and tenant filters.
3. Expose only active eligible public projections; never return secrets, admin notes or unauthorized targets.
4. Implement a separate accessible carousel with manual controls, stable image ratio, lazy images and reduced-motion behavior.
5. Omit the section when no eligible placement exists; do not substitute static fake cards.

**Exit gate**: Editorial and sponsored fixtures render distinct labels; expired/disabled/unapproved fixtures never render.

### Phase D - Canonical Promotion Enrichment

1. After parent tasks T028-T031 pass, attach canonical quote summaries to eligible organic cards.
2. Show original/final/member price only when supplied by the backend quote with condition and expiry metadata.
3. Verify price consistency across Home, search, detail and checkout.

**Exit gate**: Home never computes or infers a discount, and every displayed price reconciles with the canonical quote.

### Phase E - Quality and Release Evidence

1. Add component, service, integration, authorization and schedule tests.
2. Add Playwright journeys for destination switching, deep-link preservation, disclosure, expiry, independent failure and mobile snapping.
3. Run VI/EN missing-key, keyboard/focus, reduced-motion, overflow, console error and CLS checks.
4. Record evidence in the parent quickstart and traceability matrix.

## UI Direction

- Use the Agoda reference for section hierarchy only: short heading, compact horizontal discovery, location tabs and information-dense cards.
- Keep the LuxeStay navy/teal/gold system and existing heading font/tokens; reject the generated amber/voice-first recommendation because it conflicts with the established brand and product context.
- Desktop: 3 spotlight cards or 4 property cards per visible row depending on container width.
- Mobile: one large spotlight card and approximately 1.15 property cards visible to signal horizontal discovery; the page itself must not scroll horizontally.
- Tabs use native buttons with `aria-selected`, roving or natural tab order, visible focus and a 44px minimum target.
- Motion is limited to transform/opacity in the 150-300ms range; autoplay is optional and disabled under reduced motion.

## Project Structure

```text
specs/006-booking-marketplace-roadmap/home-discovery-merchandising/
|-- spec.md
|-- plan.md
|-- research.md
|-- data-model.md
|-- quickstart.md
`-- contracts/home-discovery-api-contract.md

frontend/src/app/features/client/home/components/
|-- partner-spotlight-carousel/
`-- destination-recommendations/

backend/src/main/java/com/hotel/
|-- controllers/PublicDiscoveryController.java
|-- dtos/home/
|-- services/HomeRecommendationService.java
`-- services/HomeSpotlightService.java
```

## Complexity Tracking

The separate component/API design adds one additional public request, but it prevents sponsored content failure or policy changes from contaminating organic discovery. No new client state library or recommendation ML system is justified.
