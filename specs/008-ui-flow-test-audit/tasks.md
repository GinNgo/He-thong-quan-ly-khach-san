# Tasks: UI Flow Test Audit

**Input**: Design documents from `specs/008-ui-flow-test-audit/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Scope Guard**: Test-only. Không sửa product behavior trong feature 008; mọi lỗi được ghi vào register/backlog riêng. `.specify/feature.json` phải tiếp tục trỏ `specs/007-payment-billing-completion` sau mọi lệnh Spec Kit.

## Phase 1: Setup (Shared Test Infrastructure)

**Purpose**: Cô lập feature 008 và chuẩn bị nơi lưu implementation/evidence.

- [x] T001 Verify `.specify/feature.json` still points to `specs/007-payment-billing-completion` and record isolation in `specs/008-ui-flow-test-audit/execution-results.md`
- [x] T002 [P] Verify existing ignore coverage for Playwright reports, test results, Angular build output and environment files in `.gitignore`, `.dockerignore`, and `.prettierignore`
- [x] T003 [P] Create the execution report skeleton with run metadata, command results and blocker sections in `specs/008-ui-flow-test-audit/execution-results.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared helpers and audit contracts required by all user stories.

- [x] T004 Implement typed runtime issue collection, placeholder-link checks and audit attachment helpers in `frontend/e2e/helpers/ui-audit.ts`
- [x] T005 [P] Implement source inventory utilities for router declarations, route literals and incomplete markers in `frontend/e2e/helpers/source-inventory.ts`
- [x] T006 [P] Create audit fixture conventions and safe credential lookup without secret logging in `frontend/e2e/helpers/audit-fixtures.ts`
- [x] T007 Validate helper TypeScript compilation through Playwright discovery and record the result in `specs/008-ui-flow-test-audit/execution-results.md`

**Checkpoint**: Shared audit foundation is ready.

---

## Phase 3: User Story 1 - Kiểm kê toàn bộ bề mặt có thể thao tác (Priority: P1) MVP

**Goal**: Biến route/menu/control inventory thành các kiểm tra lặp lại được và phát hiện test stale.

**Independent Test**: Chạy riêng `ui-source-inventory.spec.ts`; nhận danh sách route declarations, placeholder links, stale route literals và known incomplete markers với kết quả rõ ràng.

- [x] T008 [P] [US1] Add router and E2E route-literal inventory assertions in `frontend/e2e/ui-source-inventory.spec.ts`
- [x] T009 [P] [US1] Add placeholder `href="#"`, unsupported and Coming Soon inventory assertions in `frontend/e2e/ui-source-inventory.spec.ts`
- [x] T010 [US1] Run `npx playwright test e2e/ui-source-inventory.spec.ts --project=chromium` and record PASS/FAIL evidence in `specs/008-ui-flow-test-audit/execution-results.md`
- [x] T011 [US1] Update stale-test and source-only entries from inventory output in `specs/008-ui-flow-test-audit/incomplete-function-register.md`
- [x] T012 [US1] Update route/control coverage disposition in `specs/008-ui-flow-test-audit/test-matrix.md`

**Checkpoint**: User Story 1 independently identifies coverage gaps without starting the application.

---

## Phase 4: User Story 2 - Xác minh thao tác đi đúng luồng nghiệp vụ (Priority: P1)

**Goal**: Chạy public và authenticated smoke trên runtime hiện có, phân biệt pass/fail/block và không tính skipped credential flows là pass.

**Independent Test**: Public smoke chạy không cần credential; authenticated groups hoặc pass bằng real integration hoặc được ghi `BLOCKED` với biến môi trường thiếu.

- [x] T013 [P] [US2] Add public navigation, legal-link, forgot-password and Coming Soon browser assertions in `frontend/e2e/ui-public-capability-audit.spec.ts`
- [x] T014 [P] [US2] Add real-environment result classification for customer/admin/owner credentials in `frontend/e2e/ui-real-flow-audit.spec.ts`
- [x] T015 [US2] Run the public capability audit and attach failure traces/screenshots through `frontend/e2e/ui-public-capability-audit.spec.ts`
- [x] T016 [US2] Run `frontend/e2e/real-environment-smoke.spec.ts` and `frontend/e2e/ui-real-flow-audit.spec.ts` against available runtime/credentials
- [x] T017 [US2] Record primary/error/recovery/permission outcomes and blockers in `specs/008-ui-flow-test-audit/execution-results.md`

**Checkpoint**: User Story 2 produces honest real-runtime smoke results.

---

## Phase 5: User Story 3 - Ghi nhận chức năng hiển thị nhưng chưa hoàn thiện (Priority: P1)

**Goal**: Tái hiện các candidate UIF-001..UIF-016 bằng browser/source evidence và xác định verified status.

**Independent Test**: Chạy riêng incomplete-capability suites và truy ngược mọi failure tới một `UIF-*` record.

- [x] T018 [P] [US3] Add admin dashboard CTA, hardcoded stat, simulated work-order and export assertions in `frontend/e2e/ui-admin-incomplete-audit.spec.ts`
- [x] T019 [P] [US3] Add management dashboard/properties differentiation assertion in `frontend/e2e/ui-management-incomplete-audit.spec.ts`
- [x] T020 [US3] Run incomplete-capability suites with controlled API fixtures and collect trace/network evidence under Playwright test results
- [x] T021 [US3] Verify dormant candidates have no active router entry and update `specs/008-ui-flow-test-audit/incomplete-function-register.md`
- [x] T022 [US3] Promote source-only candidates to verified status where evidence is sufficient and document unresolved dependencies in `specs/008-ui-flow-test-audit/incomplete-function-register.md`

**Checkpoint**: User Story 3 provides an actionable verified gap register without product fixes.

---

## Phase 6: User Story 4 - Tạo bộ hồi quy có thể chạy lặp lại (Priority: P2)

**Goal**: Có lệnh audit ổn định, responsive/accessibility checks và báo cáo tổng hợp.

**Independent Test**: Một lệnh runner thực thi source, public, incomplete và real smoke groups; kết quả fail/block vẫn tạo report rõ ràng.

- [x] T023 [P] [US4] Add 375/768/1024/1440 overflow and keyboard-focus checks in `frontend/e2e/ui-responsive-accessibility-audit.spec.ts`
- [x] T024 [P] [US4] Create a PowerShell audit runner that preserves individual exit codes in `frontend/scripts/run-ui-audit.ps1`
- [x] T025 [US4] Run responsive/accessibility audit and classify issues in `specs/008-ui-flow-test-audit/execution-results.md`
- [x] T026 [US4] Run the consolidated audit runner from `frontend/scripts/run-ui-audit.ps1` and preserve its report output
- [x] T027 [US4] Update repeatable commands and observed limitations in `specs/008-ui-flow-test-audit/quickstart.md`

**Checkpoint**: User Story 4 supplies a repeatable regression entry point.

---

## Phase 7: Validation & Reporting

**Purpose**: Chạy quality gates hiện có và hoàn tất traceability.

- [x] T028 Run `npm test -- --watch=false` in `frontend` and record exact exit code and failures in `specs/008-ui-flow-test-audit/execution-results.md`
- [x] T029 Run `npm run build` in `frontend` and record exact exit code and warnings in `specs/008-ui-flow-test-audit/execution-results.md`
- [x] T030 Run high-risk existing suites that can execute with available fixtures and record skipped/blocked dependencies in `specs/008-ui-flow-test-audit/execution-results.md`
- [x] T031 Reconcile every executed scenario with `specs/008-ui-flow-test-audit/test-matrix.md` and `specs/008-ui-flow-test-audit/incomplete-function-register.md`
- [x] T032 Verify all feature 008 checklists and task formatting, and confirm `.specify/feature.json` still points to feature 007
- [x] T033 Summarize verified complete, partial, displayed-only, broken, blocked, missing, dormant and stale-test counts in `specs/008-ui-flow-test-audit/execution-results.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1 has no dependencies.
- Phase 2 depends on Phase 1 and blocks all browser/source audit stories.
- US1, US2 and US3 depend on Phase 2; their test files are independent.
- US4 depends on the audit suites from US1-US3 so the runner has stable targets.
- Validation depends on all desired story implementations and runs last.

### User Story Dependencies

- **US1 (P1)**: Starts after foundational helpers; no dependency on runtime.
- **US2 (P1)**: Starts after foundational helpers; runtime/credentials can result in `BLOCKED` without blocking US1/US3 fixture audits.
- **US3 (P1)**: Starts after foundational helpers; uses controlled fixtures for handler/state evidence and real runtime where available.
- **US4 (P2)**: Depends on completed suite names from US1-US3.

### Parallel Opportunities

- T002 and T003 can run in parallel.
- T005 and T006 can run in parallel after T004 contract decisions are clear.
- T008/T009, T013/T014, T018/T019 and T023/T024 modify different files and can be parallelized.
- Unit/build and credential-free source audit can run independently once files compile.

---

## Parallel Example: User Story 3

```text
Task: "Add admin dashboard incomplete-capability assertions in frontend/e2e/ui-admin-incomplete-audit.spec.ts"
Task: "Add management route differentiation assertions in frontend/e2e/ui-management-incomplete-audit.spec.ts"
```

---

## Implementation Strategy

### MVP First - User Story 1

1. Complete isolation and helpers.
2. Implement source inventory suite.
3. Run it and update stale/source-only findings.
4. Continue to runtime stories without changing product code.

### Incremental Delivery

1. Static inventory establishes honest coverage.
2. Public/real smoke validates user journeys.
3. Incomplete suites verify displayed-only candidates.
4. Responsive/accessibility and runner make the audit repeatable.
5. Final validation records blockers rather than hiding them as pass.

## Notes

- `[P]` tasks touch different files and have no incomplete same-file dependency.
- Test failures caused by verified product gaps are valid audit outcomes; they must be documented, not fixed in feature 008.
- Credential or service absence is `BLOCKED`, never silent pass.
- Do not edit existing dirty E2E files unless a later explicit fix task is created outside feature 008.
