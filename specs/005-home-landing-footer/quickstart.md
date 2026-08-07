# Quickstart: Home Landing & Footer Polish

## Prerequisites

- Start the existing backend and frontend development servers.
- Open `http://localhost:4200/` in a clean browser tab.

## Browser checks

1. Review the Home route at 375px, 768px, 1024px and 1440px.
2. Confirm the hero/search hierarchy, destination and property section spacing, loading/empty states and partner CTA.
3. Scroll to the footer and verify grouped navigation, contact actions, policy contact actions, copyright and VI/VND status.
4. Activate destination/property/partner/footer actions with mouse and keyboard Enter/Space.
5. Open/close the support widget at the bottom of the page and verify no footer action is covered.
6. Enable reduced motion and confirm skeleton/reveal transitions become effectively static.

## Automated checks

```powershell
Set-Location frontend
npm test -- --watch=false
npm run build -- --no-progress
```

Expected outcome: tests and production build pass with no new Angular template/type errors.

## Validation evidence - 2026-07-28

- Full frontend regression: `36` test files and `73` tests passed.
- Focused Home/Footer regression: `4` test files and `7` tests passed.
- Production build passed; initial bundle remained `1.08 MB` (`200.48 kB` estimated transfer).
- Browser matrix passed at requested widths `375`, `768`, `1024` and `1440`: document `scrollWidth` equalled `clientWidth` at every breakpoint.
- The live API error state rendered distinct “Chưa thể tải điểm đến” and “Chưa thể tải cơ sở nổi bật” messages instead of unexplained blank sections.
- All `13` footer links/buttons resolved to native interactive controls with an existing route or explicit contact action.
- Mobile and desktop visual review confirmed the partner CTA and footer hierarchy; footer bottom padding kept the fixed support trigger clear of navigation and copyright content.
- Browser console contained no errors after reload and breakpoint review.
- Reduced-motion behavior is enforced by the existing global media query plus component-level transition/animation overrides; no motion is required to access content.
- `git diff --check` passed. Existing CRLF conversion warnings belong to the pre-existing dirty worktree and were not modified as part of this feature.
