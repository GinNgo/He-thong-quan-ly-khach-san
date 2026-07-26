# Implementation Plan: Feature-02A Design System & Shared Components

**Status:** IN PROGRESS

## Source findings

- `frontend/src/styles.css` has color tokens but no spacing/radius/shadow/focus/motion scale.
- `frontend/src/app/core/theme.ts` centralizes PrimeNG Aura primary palette.
- Existing shared controls cover select, date, filters, dialogs, tables, stat cards and charts.
- No common loading/empty/error/success/confirmation component exists.
- No lint script is configured; `ng test` and `ng build` are required gates.

## Minimal implementation

1. Extend `HotelPreset` with surface and focus-ring semantics.
2. Extend global CSS with compatible semantic scales, focus-visible, reduced-motion and screen-reader utility.
3. Add standalone `FeedbackStateComponent` with typed state, safe defaults, semantic roles and optional action.
4. Add focused unit tests for all states and action emission.
5. Run full frontend tests and production build.
6. Run Git whitespace/secret/artifact gates, explicit commit and non-force push.

## Files

- `frontend/src/app/core/theme.ts`
- `frontend/src/styles.css`
- `frontend/src/app/shared/components/feedback-state/feedback-state.component.{ts,html,css,spec.ts}`
- Feature-02 and Feature-02A artifacts

## Non-goals

- No page migration yet.
- No new UI framework or package.
- No backend/API change.
- No speculative export implementation.