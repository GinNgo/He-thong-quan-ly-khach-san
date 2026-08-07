# Implementation Plan: LuxeStay Home Landing & Footer Polish

**Branch**: `[005-home-landing-footer]` | **Date**: 2026-07-28 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-home-landing-footer/spec.md`

## Summary

Refine the public Home route so the hero, search entry, destination discovery, featured properties and partner CTA form a compact, readable conversion path. Replace the minimal client footer with a responsive information architecture that uses existing routes and explicit contact actions. The implementation stays within existing Angular standalone components and real client API/search state services; no backend or payment policy changes are needed.

## Technical Context

**Language/Version**: TypeScript with Angular 22+ standalone components; CSS; HTML templates

**Primary Dependencies**: Angular Router, existing `HomeSearchStateService` and `ClientApiService`, PrimeNG Carousel/DatePicker, Tailwind utility layer, existing LuxeStay design tokens

**Storage**: N/A for this UI-only feature; Home discovery reads existing public API responses

**Testing**: Vitest/Angular component tests, production build, browser smoke at 375/768/1024/1440px, keyboard focus and reduced-motion review

**Target Platform**: Modern desktop and mobile browsers served by the existing Angular frontend

**Project Type**: Web application public/client shell

**Performance Goals**: Preserve current initial load budget; avoid layout shift from empty data; keep hover/reveal motion under 300ms and disable it for reduced-motion users

**Constraints**: Reuse current API and account-aware routing; no fake destinations, mocked responses, backend schema changes or payment authorization changes; no horizontal overflow at required breakpoints

**Scale/Scope**: One public Home route, two Home section components, the client layout footer and the existing floating support widget's safe visual context

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **I. An toàn chức năng**: Chỉ thay đổi presentation, semantic controls and layout; existing search and partner routing are preserved.
- [x] **II. Hiểu biết toàn diện**: Home, client layout, chat widget, design tokens, routes and current Speckit audit artifacts were inspected.
- [x] **III. Tái sử dụng**: Reuses `HomeSearchStateService`, `ClientApiService`, `ImageFallbackService`, `navigatePartner()` and existing design tokens.
- [x] **IV. Validation & Error Handling**: Loading, empty and image-fallback states are explicit; route actions use existing guards/services.
- [x] **V. Trải nghiệm thực tế**: Destination/property content continues to use the real public API; no mock data or fake UI response is added.
- [x] **VI & VII. Kiểm định & Xác minh**: Targeted unit checks, full frontend test/build and real browser breakpoint/focus review are required.
- [x] **VIII. Ghi chép**: Research, UI contract and quickstart record decisions, evidence and remaining route dependencies.

## Project Structure

### Documentation (this feature)

```text
specs/005-home-landing-footer/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/home-footer-contract.md
└── tasks.md
```

### Source Code (repository root)

```text
frontend/
└── src/app/
    ├── features/client/home/home.html
    ├── features/client/home/home.css
    ├── features/client/home/components/popular-destinations/
    ├── features/client/home/components/featured-properties/
    └── layout/client-layout/client-layout.{html,css}
```

**Structure Decision**: Keep the existing Angular feature/component boundaries. Home owns the landing composition and partner CTA; discovery components own their section states and keyboard behavior; the client layout owns global footer navigation and the support-safe bottom spacing.

## Complexity Tracking

No constitution violations. The feature stays inside the existing frontend public shell and does not add a new service, backend endpoint or persisted entity.
