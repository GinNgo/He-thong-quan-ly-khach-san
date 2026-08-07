# Research: Home Discovery and Merchandising

## Decision 1 - Add two new sections instead of repurposing the editorial slideshow

**Decision**: Preserve `EditorialSlideshowComponent` as destination storytelling and add separate partner spotlight and destination recommendation components.

**Rationale**: The existing slideshow has static editorial semantics, one-card hero proportions and accessible autoplay controls. Mixing paid placements into it would blur disclosure and make organic content depend on advertising policy.

**Alternatives considered**:

- Replace the slideshow with partner banners: rejected because it removes completed destination storytelling.
- Feed all content through one generic carousel: rejected because editorial, sponsored and organic ranking need different contracts and evidence.

## Decision 2 - Deliver deterministic contextual recommendations, not AI personalization

**Decision**: Use current Home search location when valid; otherwise use popular destinations with active supply. Rank approved properties by documented server-side criteria with a stable tie-breaker.

**Rationale**: The project has location, availability, rating and review data but no consented behavioral profile or trained recommendation model. A deterministic service is testable and honest.

**Alternatives considered**:

- Claim personalized AI recommendations: rejected because no model, training data, consent or evaluation exists.
- Hard-code Vung Tau, Da Nang, Ho Chi Minh City, Hanoi and Nha Trang: rejected because it conflicts with current 34-province data and supply can change.

## Decision 3 - Use separate public endpoints

**Decision**: Expose destination tabs, recommendation items and spotlights through separate bounded endpoints.

**Rationale**: Organic recommendations can ship before promotion policy. Independent requests also allow isolated caching, retry and empty states.

**Alternatives considered**:

- One large Home payload: simpler request count but couples unrelated failures and invalidates the whole cache when one section changes.
- Repeated raw property-search calls from Angular: feasible for a prototype but duplicates ranking/default-destination rules in the client.

## Decision 4 - Keep sponsored ranking outside organic ranking

**Decision**: Spotlight cards occupy a dedicated section and declare `EDITORIAL` or `SPONSORED`. Organic destination results never receive a hidden sponsored boost.

**Rationale**: This follows parent requirement FR-013 and makes disclosure, relevance and test evidence unambiguous.

**Alternatives considered**:

- Insert paid cards into the organic grid: deferred until a separately approved labelled-slot policy exists.
- Label all placements as recommendations: rejected as misleading.

## Decision 5 - Preserve authoritative price boundaries

**Decision**: MVP cards show current server-returned price and availability only. Strike-through, member and promotion prices wait for the canonical quote work in T025-T031.

**Rationale**: The current Angular model contains discount-shaped fields, but the parent audit confirms that no complete campaign evaluator exists. Rendering them now would create fake commerce behavior.

## Decision 6 - Use mobile horizontal discovery without page overflow

**Decision**: Keep the page container clipped and allow horizontal scrolling only inside the tabs/card track with snap and visible controls where appropriate.

**Rationale**: This matches the reference's browsing density while retaining touch usability and the existing 375px release gate.

## Decision 7 - Preserve the current LuxeStay visual system

**Decision**: Reuse existing semantic CSS variables, navy/teal/gold identity, heading type and elevation scale.

**Rationale**: The UI/UX search output proposed amber and voice-first patterns that do not match a hotel marketplace or the established project brand. Only its accessibility, touch-target, lazy-image, stable-dimension and reduced-motion guidance is retained.

## Source Findings

| Finding | Evidence | Planning impact |
|---|---|---|
| Featured properties are one global list | `HomeComponent.loadFeaturedProperties()` calls property search once with `sortBy=RATING` | Add destination-specific service and local per-tab state |
| Destination data already exists | `/api/public/popular-destinations` and current-province compatibility are implemented | Reuse real destinations; do not hard-code tabs |
| Existing slideshow is static editorial content | `EditorialSlideshowComponent` uses local destination slides | Keep it separate from partner placements |
| Sponsored campaign domain is approved and implemented | OQ-005 is approved; parent T025-T035 and T110-T117 now have lifecycle/API/browser evidence | Keep spotlight rendering bound to approved placement records and visible disclosure |
| Home already has VI/EN and responsive evidence | Parent T036-T044 and T071-T072 are complete | Extend the same i18n/accessibility/performance gates |
