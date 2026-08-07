# Implementation Plan: UI Flow Test Audit

**Branch**: `N/A - independent test artifact` | **Date**: 2026-08-01 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/008-ui-flow-test-audit/spec.md`

**Isolation Rule**: Plan này chỉ thiết kế và tổ chức kiểm thử. Không sửa chức năng sản phẩm, không đổi branch, không thay thế `specs/007-payment-billing-completion`, và sau khi tạo xong `.specify/feature.json` phải tiếp tục trỏ về feature 007.

## Summary

Tạo một chương trình audit UI độc lập để kiểm kê toàn bộ route/menu/control đang hiển thị, xác minh thao tác có hoàn thành đúng luồng nghiệp vụ theo vai trò, nhận diện chức năng chỉ có bề mặt nhưng chưa hoàn thiện, và chuẩn hóa bộ smoke/regression có bằng chứng. Cách tiếp cận kết hợp inventory tĩnh từ Angular router/menu với Playwright chạy trên integration thật; stub/mock chỉ hỗ trợ chẩn đoán và không đủ để gắn trạng thái `COMPLETE`.

## Technical Context

**Language/Version**: TypeScript 6.0.3, Angular 22.0.7; Java 21/Spring Boot 3 cho integration backend

**Primary Dependencies**: Angular Router, Reactive Forms, RxJS 7.8, PrimeNG 21.1.9, Playwright 1.61.1, Vitest 4.0.8

**Storage**: SQL Server 2022 qua backend hiện có; artifact test và evidence lưu trong repository/workspace

**Testing**: `npm test`, `npm run build`, `npx playwright test`; backend test/fixture dùng Maven theo cấu hình dự án

**Target Platform**: Web responsive; Chromium desktop baseline và viewport 375/768/1024/1440

**Project Type**: Full-stack web application, multi-role, multi-tenant hospitality platform

**Performance Goals**: Smoke P1 hoàn thành trong tối đa 45 phút; phản hồi bắt đầu thao tác có dấu hiệu trong 300 ms; không có blank screen hoặc redirect loop ở P1

**Constraints**: Test-only; không sửa code sản phẩm trong feature 008; completion evidence phải dùng integration thật; bảo vệ tenant và RBAC; không ghi secret/tài khoản vào artifact

**Scale/Scope**: 62 khai báo route con/redirect/wildcard hiện tại, 21 file Playwright với khoảng 89 test block, bốn nhóm actor và các luồng public/customer/admin/management

## Constitution Check

*GATE: Passed before Phase 0 and re-checked after Phase 1.*

- [x] **I. An toàn chức năng**: Feature 008 chỉ tạo artifact test; không thay đổi runtime hoặc plan 007.
- [x] **II. Hiểu biết toàn diện**: Đã đọc router, menu theo quyền, layout, package scripts, Playwright config, test inventory và các marker chức năng chưa hoàn thiện.
- [x] **III. Tái sử dụng**: Tận dụng Playwright config, E2E helpers, unit tests, route guards, data-testid/semantic locators và evidence conventions hiện có.
- [x] **IV. Validation & Error Handling**: Matrix bắt buộc có validation, loading, empty, error, retry, duplicate-submit và permission cases.
- [x] **V. Trải nghiệm thực tế**: Chỉ kết luận `COMPLETE` từ UI + backend/DB hoặc fixture integration thật; mock không được dùng làm bằng chứng hoàn thiện.
- [x] **VI & VII. Kiểm định & Xác minh**: Có quality gates cho build, unit, smoke, role journey, responsive, accessibility và evidence review.
- [x] **VIII. Ghi chép**: Gap register ghi expected/actual, severity, evidence, blocker và disposition.

## Scope Boundaries

### In Scope

- Inventory route, redirect, wildcard, menu động từ `/auth/my-menu`, menu tĩnh và control chính.
- Luồng Public, Customer, Admin, Property Owner/Manager đang được router/menu công bố.
- Primary, alternate, validation/error, recovery, permission, responsive và accessibility cases.
- Đối chiếu test Playwright hiện có với router hiện tại để phát hiện route/assertion lỗi thời.
- Gap register cho chức năng `DISPLAYED_ONLY`, `PARTIAL`, `BROKEN`, `BLOCKED`, `MISSING` và source `DORMANT`.
- Thiết kế smoke/regression và evidence có thể chạy lặp lại.

### Out of Scope

- Sửa các gap được phát hiện.
- Thiết kế lại UI, palette, typography hoặc component.
- Thay đổi API/schema/database vì mục tiêu audit.
- Chứng nhận browser ngoài Chromium trong vòng đầu.
- Gộp tasks hoặc trạng thái của feature 008 vào feature 007.

## Project Structure

### Documentation (this feature)

```text
specs/008-ui-flow-test-audit/
|-- spec.md
|-- plan.md
|-- research.md
|-- data-model.md
|-- quickstart.md
|-- test-matrix.md
|-- incomplete-function-register.md
|-- checklists/
|   `-- requirements.md
`-- contracts/
    |-- audit-record.md
    `-- evidence-and-status.md
```

### Source Code (repository root)

```text
frontend/
|-- src/app/
|   |-- app.routes.ts
|   |-- core/guards/
|   |-- core/services/
|   |-- features/
|   |-- layout/
|   `-- shared/
|-- e2e/
|-- playwright.config.ts
`-- package.json

backend/
|-- src/main/
`-- src/test/

.specify/
|-- feature.json
|-- memory/constitution.md
|-- scripts/python/setup_plan.py
`-- templates/
```

**Structure Decision**: Giữ nguyên full-stack structure. Feature 008 chỉ bổ sung artifact trong `specs/008-ui-flow-test-audit`; test implementation về sau nằm trong `frontend/e2e` và test unit hiện hữu, không tạo ứng dụng hoặc test project mới.

## Baseline Observations

| ID | Observation | Initial Classification | Required Runtime Check |
|----|-------------|------------------------|------------------------|
| B-001 | Admin dashboard có CTA cập nhật thông tin khách sạn nhưng không có handler | DISPLAYED_ONLY | Click và xác minh navigation/mutation |
| B-002 | CTA gửi yêu cầu duyệt có trạng thái disabled nhưng không có handler khi enabled | DISPLAYED_ONLY | Chuẩn bị đủ bước rồi click |
| B-003 | Bốn stat card dashboard dùng giá trị cố định `0` | PARTIAL | So sánh với API analytics có dữ liệu |
| B-004 | Work order table dùng `setTimeout` trả danh sách rỗng | DISPLAYED_ONLY | Network inspection và data fixture |
| B-005 | Export Excel/PDF chỉ `console.log` | DISPLAYED_ONLY | Click và kiểm tra download |
| B-006 | Khôi phục mật khẩu ghi rõ chưa hỗ trợ ở login khách/admin | MISSING / intentional | Xác minh không có route/flow thay thế |
| B-007 | Chuyến bay và đưa đón hiển thị disabled với Coming Soon | DISPLAYED_ONLY / intentional | Xác minh không phát request và nhãn rõ |
| B-008 | Một số liên kết legal/support/cookie dùng `href="#"` | BROKEN | Click và xác minh URL/focus |
| B-009 | `/management/properties` dùng cùng component với dashboard | PARTIAL candidate | So sánh nội dung và task intent của hai menu |
| B-010 | Test cũ dùng `/client/profile` trong khi router hiện tại dùng `/profile` | STALE TEST | Chạy discovery/test và cập nhật coverage map |

Các observation chỉ là baseline từ source; status cuối cùng phải dựa trên runtime evidence theo contract.

## Test Architecture

### Layer 1 - Static Inventory

- Trích route và route data từ `frontend/src/app/app.routes.ts`.
- Trích menu tĩnh từ client/management layout và menu động từ response `/auth/my-menu` theo tài khoản.
- Trích control tương tác trên từng route: navigation, form submit, table action, modal, export, download, upload, chat, payment callback.
- Đối chiếu route literal trong `frontend/e2e` để phát hiện route cũ, route thiếu test và suite trùng lặp.

### Layer 2 - Component/Unit Behavior

- Xác minh validation, state transition và error mapping có thể cô lập.
- Mock/stub được phép ở layer này để kiểm tra nhánh logic nhưng kết quả chỉ hỗ trợ chẩn đoán.
- Không chuyển status capability sang `COMPLETE` chỉ dựa trên layer này.

### Layer 3 - Integrated Browser Journey

- Chạy browser với backend và database/fixture E2E thật.
- Tạo account matrix cho customer, admin, owner/manager, expired subscription và insufficient permission.
- Xác minh UI result, network response và state sau reload/navigation.
- Dùng semantic locator ưu tiên role/name/label; chỉ thêm test id khi control không có accessible identity ổn định.

### Layer 4 - Cross-Cutting Quality

- Responsive tại 375/768/1024/1440.
- Keyboard/focus, accessible name, dialog focus trap/escape, error announcement.
- Console error, failed request, redirect loop, duplicate mutation, stale tenant data.
- Trace, screenshot và network evidence cho failure P1/P2.

## Execution Phases

### Phase 0 - Inventory and Research

1. Chốt status taxonomy và evidence rules trong `contracts/evidence-and-status.md`.
2. Lập route/menu/control inventory và nhóm theo actor/journey.
3. Map 21 file Playwright hiện có vào route/flow; đánh dấu stale, mocked-only, real-integration và uncovered.
4. Xác định fixture/account/data dependencies và blocker môi trường.
5. Ghi baseline gap từ static inspection vào `incomplete-function-register.md`.

**Exit Gate**: 100% route declaration có disposition; không còn surface chưa có actor/owner; mọi unknown được chuyển thành scenario hoặc blocker cụ thể.

### Phase 1 - Test Design and Contracts

1. Hoàn thiện `test-matrix.md` theo priority P1/P2/P3 và role.
2. Định nghĩa record schema, status decision, severity và evidence contract.
3. Thiết kế smoke suite tối thiểu và regression suite mở rộng.
4. Định nghĩa quickstart chạy build/unit/browser và quy tắc xử lý test skip/block.
5. Re-check constitution: real integration, tenant isolation, RBAC và không dùng mock làm completion evidence.

**Exit Gate**: Mỗi P1 journey có primary + error/recovery + permission khi phù hợp; mỗi known incomplete candidate có runtime scenario.

### Phase 2 - Test Implementation (future `/speckit-tasks`)

- Không thực hiện trong `/speckit-plan` này.
- Khi người dùng yêu cầu, tạo `tasks.md` riêng cho feature 008; không nối tasks vào feature 007.
- Ưu tiên sửa test stale và thêm smoke inventory trước khi mở rộng visual/regression.

### Phase 3 - Audit Execution and Reporting (future implementation)

- Chạy theo role và priority.
- Cập nhật `incomplete-function-register.md` từ candidate sang verified status.
- Chỉ tạo backlog sửa lỗi sau khi có evidence; backlog fix phải thuộc feature/issue khác.

## Quality Gates

| Gate | Pass Condition |
|------|----------------|
| Isolation | `.specify/feature.json` vẫn là `specs/007-payment-billing-completion` |
| Specification | Checklist requirements đạt 100%, không còn clarification marker |
| Inventory | 100% route/menu/control có matrix row hoặc disposition |
| Unit/Build | Lệnh hiện có chạy và báo exit code; lỗi được phân loại product/test/environment |
| Smoke P1 | Mọi scenario có PASS/FAIL/BLOCKED, không silent skip |
| Integration | `COMPLETE` chỉ khi UI + real integration evidence khớp |
| RBAC/Tenant | Không lộ route/action/data ngoài quyền và property scope |
| UX States | Loading/empty/error/retry/success/disabled được kiểm tra |
| Accessibility | Keyboard/focus/name/error announcement đạt yêu cầu P1 |
| Evidence | Failure P1/P2 có expected/actual, repro và trace/screenshot phù hợp |

## Post-Design Constitution Re-check

- [x] Không có thay đổi runtime trong artifact plan.
- [x] Test design bao phủ Angular routing/forms/RxJS lifecycle states và Playwright E2E.
- [x] Completion evidence không dựa vào mock/dummy.
- [x] RBAC, permission guard, backend authorization và tenant context đều có scenario.
- [x] Build, unit, E2E, responsive, accessibility và error handling đều có gate.
- [x] Feature selection 007 được phục hồi ngay sau khi chạy setup plan cho feature 008.

## Complexity Tracking

Không có constitution violation cần ngoại lệ. Việc giữ feature 008 ở thư mục riêng là lựa chọn đơn giản nhất để cô lập test plan khỏi plan triển khai đang chạy.
