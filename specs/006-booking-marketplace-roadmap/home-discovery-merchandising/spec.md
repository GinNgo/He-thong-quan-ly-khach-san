# Feature Addendum: Home Discovery and Merchandising

**Parent feature**: `006-booking-marketplace-roadmap`

**Status**: Planned

**Input**: Add two Agoda-inspired Home capabilities without copying Agoda assets or presenting fake promotions:

1. A horizontal partner/editorial spotlight carousel.
2. A location-tabbed list of featured stays with ratings, location, availability and real pricing.

## User Story 1 - Explore featured stays by destination (Priority: P1)

A visitor can switch between up to five destinations and inspect a compact list of approved stays for the selected destination before opening the full search result.

**Why this priority**: This capability can reuse real property search and destination data, improves discovery immediately, and does not depend on promotion or advertising policy.

**Independent Test**: Open Home as a guest, switch destination tabs, open a property and use "View more"; verify that the selected destination, dates and guest counts are preserved.

### Acceptance Scenarios

1. **Given** Home has a valid location in the current search state, **When** recommendations load, **Then** that location is selected when it has active supply; otherwise the first popular destination with supply is selected.
2. **Given** a visitor selects another destination tab, **When** the request completes, **Then** only approved, active properties in that destination are displayed in deterministic organic order.
3. **Given** a recommendation card has real review and pricing data, **When** it renders, **Then** the score, review count, location, room availability and server-provided VND price are displayed without invented discounts.
4. **Given** a visitor opens a card or "View more", **When** navigation occurs, **Then** the selected destination and current date/guest query are retained.
5. **Given** the viewport is 375px wide, **When** tabs and cards render, **Then** tabs and cards use touch-friendly horizontal scrolling or snapping without page-level horizontal overflow.
6. **Given** a tab request is loading, empty or failed, **When** the state changes, **Then** the section shows a bounded skeleton, meaningful empty state or retry action and does not leave an unexplained blank area.

## User Story 2 - Discover governed partner spotlights (Priority: P2)

A visitor can browse scheduled partner/editorial spotlights while clearly understanding whether an item is sponsored or selected editorially by LuxeStay.

**Why this priority**: The visual pattern improves merchandising, but sponsored content requires policy, tenant authorization, asset rights, schedule and disclosure controls before it can be shown truthfully.

**Independent Test**: Configure one editorial placement, one sponsored placement, one expired placement and one disabled placement; verify that only active eligible placements appear and the sponsored one is labelled in VI/EN.

### Acceptance Scenarios

1. **Given** a sponsored placement is active, approved and within its schedule, **When** it appears on Home, **Then** it carries a visible and accessible sponsored disclosure.
2. **Given** an editorial placement is active, **When** it appears, **Then** it uses a distinct editorial label and is not reported as paid placement.
3. **Given** a placement is expired, disabled, outside quota or tied to an inactive/unapproved property, **When** Home loads, **Then** it is excluded.
4. **Given** a placement links to a property or search collection, **When** it is activated, **Then** navigation uses an existing canonical route and preserves relevant search state.
5. **Given** the carousel has multiple cards, **When** it is operated by keyboard, pointer or touch, **Then** previous/next controls are available, focus is visible, and reduced-motion users are not forced through autoplay.
6. **Given** no eligible placements exist or the endpoint fails, **When** Home renders, **Then** the section is omitted or replaced by a compact recoverable state without blocking organic recommendations.

## Functional Requirements

- **HDM-FR-001**: Organic recommendations MUST be separated from sponsored placement ranking and evidence.
- **HDM-FR-002**: The default recommendation destination MUST use current Home search context when valid, then fall back to a popular destination with active supply.
- **HDM-FR-003**: Destination tabs MUST be returned from real current-province/location data and MUST NOT be hard-coded in the Angular component.
- **HDM-FR-004**: Recommendation results MUST include only approved, active properties and use stable deterministic ordering with a documented tie-breaker.
- **HDM-FR-005**: Recommendation navigation MUST preserve selected location, dates, stay type and guest/room counts.
- **HDM-FR-006**: Review score, review count, availability and price MUST come from authoritative backend data; absent values MUST use honest unavailable states.
- **HDM-FR-007**: Original/discount/member prices MUST NOT render until the canonical promotion quote from parent tasks T025-T031 is available.
- **HDM-FR-008**: Partner spotlights MUST be persisted, tenant-scoped where property-related, status-controlled, scheduled and auditable.
- **HDM-FR-009**: Every placement MUST declare `EDITORIAL` or `SPONSORED`; sponsored content MUST expose a VI/EN disclosure in visual and accessible text.
- **HDM-FR-010**: Placements MUST use authorized local/managed assets and canonical internal routes; copied Agoda or third-party brand assets are out of scope.
- **HDM-FR-011**: The two Home sections MUST fail independently so a spotlight outage cannot hide organic recommendations.
- **HDM-FR-012**: Desktop and mobile layouts MUST have no page-level overflow at 375px, 768px, 1024px and 1440px.
- **HDM-FR-013**: All controls MUST provide visible focus, semantic names and at least 44px touch targets.
- **HDM-FR-014**: Motion MUST use stable dimensions, pause on interaction and respect `prefers-reduced-motion`.
- **HDM-FR-015**: All public copy, disclosures, empty states and retry states MUST have VI/EN keys.

## Success Criteria

- **HDM-SC-001**: A visitor can switch destinations and reach matching full search results in at most two interactions from the section.
- **HDM-SC-002**: 100% of rendered sponsored placements expose a visible and screen-reader-accessible disclosure.
- **HDM-SC-003**: Expired, disabled, unapproved and quota-exhausted placement fixtures render zero cards.
- **HDM-SC-004**: Organic and spotlight sections have zero page-level horizontal overflow at all required breakpoints.
- **HDM-SC-005**: Reloading or navigating back preserves the selected Home search context; destination recommendation requests do not reset dates or guests.
- **HDM-SC-006**: Targeted unit, backend integration, production build and real browser scenarios pass without missing translation keys, runtime errors or CLS above 0.1.

## Out of Scope

- Machine-learning personalization or inferred sensitive-user profiling.
- Online sale or billing of advertising inventory.
- Copying Agoda layout pixel-for-pixel, logos, campaign art or brand content.
- Showing strike-through prices, member discounts or urgency claims before the canonical quote and inventory evidence exist.
- Replacing the existing editorial destination slideshow.

## Assumptions

- The first organic release uses deterministic destination and rating/review ordering rather than claiming AI personalization.
- The selected destination is session/context based; account-history personalization can be specified later with a separate privacy review.
- The existing LuxeStay navy/teal/gold tokens remain authoritative; the reference image supplies information architecture, not a new visual identity.
