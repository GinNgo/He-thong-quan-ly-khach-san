# Quickstart: Validate Home Discovery and Merchandising

## Prerequisites

- The application runs with the normal local frontend/backend configuration.
- Current province/location imports are available.
- At least five current destinations have approved active properties and real media.
- Spotlight validation uses one active editorial, one active sponsored, one expired and one disabled fixture. Do not use production credentials or copied third-party assets.

## Documentation Gate

Before implementation, verify that the schema, flow and endpoints are reflected in:

- `docs/ERD.md`
- `docs/UML.md`
- `docs/API_SPEC.md`
- `docs/THESIS.md`

## Scenario 1 - Organic destination recommendations

1. Open Home with no selected location.
2. Confirm that up to five destinations with supply appear and one is selected.
3. Switch each tab and verify the card list changes without resetting dates or guest counts.
4. Open one card and return; verify the Home/search context remains consistent.
5. Use "View more" and confirm search results use the selected destination.

**Expected**: Approved organic properties only, stable ordering, real review/price data and no sponsored marker.

## Scenario 2 - Responsive interaction

1. Repeat at 375px, 768px, 1024px and 1440px.
2. At 375px, swipe/scroll the local tab and card tracks.
3. Use keyboard on desktop to reach every tab, carousel control, card and CTA.

**Expected**: No page-level horizontal overflow; touch targets are at least 44px; focus remains visible; mobile exposes a partial next card without clipping text or controls.

## Scenario 3 - Partner spotlight governance

1. Load Home with active editorial and sponsored fixtures.
2. Verify distinct localized labels.
3. Move the clock outside the active interval or use expired/disabled fixtures.
4. Verify that unapproved/inactive property placements do not render.
5. Activate each visible placement and verify its canonical property/search route.

**Expected**: 100% sponsored disclosure, zero expired/disabled cards, no arbitrary external route and no fake fallback card.

## Scenario 4 - Independent failure and recovery

1. Fail only the spotlight request.
2. Confirm that organic recommendations still load.
3. Fail one destination recommendation request and use retry.
4. Return an empty destination and verify the section provides a meaningful recovery path.

**Expected**: Each section owns its loading/error state; no frozen loading state or unexplained blank region remains.

## Scenario 5 - Pricing honesty

1. Run before canonical promotion tasks T028-T031.
2. Confirm that no strike-through, member or discount price is shown.
3. After those tasks pass, compare Home, search, detail and checkout for the same query.

**Expected**: Before integration, only authoritative current price appears. After integration, every final price and condition matches the canonical quote.

## Suggested Verification Commands

Run only after implementation files exist:

```powershell
cd frontend
npm test -- --watch=false
npm run build
npx playwright test e2e/home-discovery-merchandising.spec.ts --project=chromium --workers=1 --retries=0
```

```powershell
cd backend
.\mvnw.cmd "-Dtest=HomeRecommendationServiceTest,HomeSpotlightServiceTest,HomeDiscoveryControllerIntegrationTest" test
```

## Evidence to Record

- Exact command, exit code and test count.
- VI/EN desktop and mobile screenshots.
- Active versus expired placement response evidence.
- Query comparison showing destination/date/guest preservation.
- Overflow, focus, reduced-motion, console error and CLS results.
- Record the approved OQ-005 policy and the active fixture/configuration used for sponsored evidence; report a blocker only if that governed configuration is unavailable.
