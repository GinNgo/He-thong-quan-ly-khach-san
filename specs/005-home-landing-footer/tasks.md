# Tasks: LuxeStay Home Landing & Footer Polish

**Input**: Design documents from `/specs/005-home-landing-footer/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/home-footer-contract.md`, `quickstart.md`

## Phase 1: Setup

**Purpose**: Establish the traceable frontend scope without changing unrelated audit/thesis work.

- [x] T001 Confirm the active feature artifacts and source scope in `specs/005-home-landing-footer/plan.md` and `frontend/src/app/app.routes.ts`
- [x] T002 [P] Record the existing Home, client layout, design-token and support-widget conventions in `specs/005-home-landing-footer/research.md`

## Phase 2: Foundational

**Purpose**: Define shared interaction and responsive constraints before story-specific polish.

- [x] T003 [P] Add Home/Footer acceptance selectors and semantic IDs for `frontend/src/app/features/client/home/home.html` and `frontend/src/app/layout/client-layout/client-layout.html`
- [x] T004 [P] Confirm 44px focus targets, reduced-motion behavior and footer safe-area spacing against `frontend/src/styles.css` and `frontend/src/app/features/client/chat-widget/chat-widget.css`

## Phase 3: User Story 1 - Khám phá và bắt đầu tìm chỗ nghỉ (Priority: P1) - MVP

**Goal**: Make the first viewport and discovery sections lead clearly into the existing real search/detail flows without unexplained whitespace.

**Independent Test**: At 375px and desktop, observe the Home hierarchy, submit a real search, activate a destination/property card, and verify loading/empty states are explicit.

- [x] T005 [P] [US1] Recompose the Home hero, search overlap, section intro and responsive spacing in `frontend/src/app/features/client/home/home.html` and `frontend/src/app/features/client/home/home.css`
- [x] T006 [P] [US1] Add keyboard-accessible destination cards, loading skeletons and distinct empty/error states in `frontend/src/app/features/client/home/components/popular-destinations/popular-destinations.component.ts`
- [x] T007 [P] [US1] Add semantic property cards, loading skeletons and distinct empty/error states while preserving query navigation in `frontend/src/app/features/client/home/components/featured-properties/featured-properties.component.ts`
- [x] T008 [US1] Add focused Home discovery and state coverage in `frontend/src/app/features/client/home/home.spec.ts` and the destination/property component specs

## Phase 4: User Story 2 - Đăng chỗ nghỉ cho đối tác (Priority: P2)

**Goal**: Make the partner CTA informative, balanced and account-aware on every breakpoint.

**Independent Test**: Visit Home logged out and logged in, activate the partner CTA and verify the existing route decision is unchanged; review mobile wrapping and focus.

- [x] T009 [US2] Refine partner CTA hierarchy, benefit list, target size and responsive stacking in `frontend/src/app/features/client/home/home.html` and `frontend/src/app/features/client/home/home.css`
- [x] T010 [US2] Verify `openOwnerPortal()` routing and add a regression expectation for unauthenticated return URL in `frontend/src/app/features/client/home/home.spec.ts`

## Phase 5: User Story 3 - Điều hướng và nhận hỗ trợ từ footer (Priority: P2)

**Goal**: Deliver a complete footer information architecture that is responsive, keyboard usable and safe with the floating support widget.

**Independent Test**: At 375px and desktop, scroll to the bottom, navigate every footer action with keyboard and confirm no footer control is covered by support.

- [x] T011 [P] [US3] Replace the minimal footer with grouped explore/partner/support/contact/legal actions in `frontend/src/app/layout/client-layout/client-layout.html`
- [x] T012 [P] [US3] Implement footer grid, visual hierarchy, responsive stacking, focus states and support-safe bottom padding in `frontend/src/app/layout/client-layout/client-layout.css`
- [x] T013 [US3] Add client-layout footer structure and route/contact destination coverage in `frontend/src/app/layout/client-layout/client-layout.spec.ts`

## Phase 6: Polish & Cross-Cutting Verification

**Purpose**: Validate the integrated Home/Footer slice and record evidence.

- [x] T014 [P] Run `npm test -- --watch=false` and `npm run build -- --no-progress` from `frontend/`; record outcomes in `specs/005-home-landing-footer/quickstart.md`
- [x] T015 [P] Run browser review at 375/768/1024/1440px plus keyboard/reduced-motion contract checks, and record pass/fail evidence in `specs/005-home-landing-footer/quickstart.md`
- [x] T016 Run `git diff --check` and inspect the final scoped diff without staging unrelated worktree changes

## Dependencies & Execution Order

- Setup (T001-T002) precedes Foundational (T003-T004).
- User Story 1 (T005-T008) depends on T003-T004 and is the MVP slice.
- User Story 2 (T009-T010) depends on Home structure from T005.
- User Story 3 (T011-T013) can proceed in parallel with User Story 1 after T003-T004, but final verification waits for all stories.
- Polish (T014-T016) runs after the selected implementation and test tasks complete.

## Parallel Opportunities

- T005, T006 and T007 touch separate Home files/components and can run in parallel after T003-T004.
- T011 and T012 touch separate client-layout files and can run in parallel after T003-T004.
- T014 and T015 can run in parallel once implementation is stable.

## Implementation Strategy

1. Deliver User Story 1 first and verify real search/detail behavior.
2. Add the account-aware partner CTA polish without changing routing policy.
3. Replace the footer and reserve support-safe space.
4. Run full tests/build and browser matrix before considering this feature complete.
