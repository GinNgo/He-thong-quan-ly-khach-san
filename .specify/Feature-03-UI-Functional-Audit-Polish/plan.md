# Implementation Plan: Full UI Functional Audit & Premium Polish

**Branch**: `codex/ui-functional-audit-polish` | **Date**: 2026-07-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `.specify/Feature-03-UI-Functional-Audit-Polish/spec.md`; latest planning addition is the LuxeStay luxury logo refresh tracked by T073-T076.

## Summary

Kiểm kê toàn bộ route/menu đang được công bố cho Public, Customer, System Admin và Property Operations; kiểm thử từng hành trình bằng giao diện, API và dữ liệu thật; ghi lại bằng chứng cùng trạng thái `COMPLETE`, `PARTIAL`, `MISSING`, `BLOCKED` hoặc `BROKEN`; sau đó ưu tiên sửa các lỗi UI dùng chung, trạng thái bất đồng bộ, điều hướng, responsive và accessibility có tác động lớn. Thay đổi giao diện phải tái sử dụng LuxeStay design tokens, PrimeNG và component dùng chung hiện có. Gap cần backend hoặc data model lớn được ghi nhận và hoãn rõ ràng, không che bằng mock hoặc dữ liệu giả.

## Technical Context

**Language/Version**: TypeScript 6 / Angular 22; Java 21 / Spring Boot 3.2.5
**Primary Dependencies**: Angular standalone components, Angular Router, RxJS, PrimeNG 21, Bootstrap 5, Chart.js, Spring Security JWT, Spring Data JPA, Flyway
**Storage**: Microsoft SQL Server 2022 cho runtime; H2 chỉ dành cho test tự động
**Testing**: Angular/Vitest unit tests qua `npm test -- --watch=false`; Playwright specs hiện có; kiểm thử tương tác bằng in-app browser; Maven/JUnit/Spring integration tests qua `mvnw.cmd test`
**Target Platform**: Chromium-based web browsers trên desktop, tablet và mobile; local frontend `http://localhost:4200`, backend `http://localhost:8080`
**Project Type**: Full-stack web application với frontend Angular và backend Spring Boot
**Performance Goals**: Không tạo request lặp khi submit; loading feedback xuất hiện ngay; route chính không có layout shift/overflow gây cản trở; bảng lớn giữ phân trang phía server khi API hỗ trợ
**Constraints**: Documentation-first; dùng API thật; không nới auth/permission; không hardcode màu trong component; touch target tối thiểu 44px; visible focus; hỗ trợ `prefers-reduced-motion`; kiểm tra viewport 375/768/1024/1440
**Scale/Scope**: Hơn 40 route/redirect được công bố, 3 application shell, 4 nhóm actor, các hành trình public search/booking/payment, admin operations và owner management

## Constitution Check

*GATE: Initial design review passed; implementation re-check remains conditional until the open browser, responsive, accessibility and payment-policy work converges.*

- [ ] **I. An toàn chức năng**: Các remediation notification/chat/tenant đã có regression, nhưng callback thanh toán demo trong T058/GAP-022 vẫn chưa có policy được phê duyệt nên feature chưa đạt production-ready gate.
- [x] **II. Hiểu biết toàn diện**: Đã đọc route inventory, audit cũ, design/standards/development rules và cấu trúc source liên quan.
- [x] **III. Tái sử dụng**: Ưu tiên `feedback-state`, data table, filter panel, dialogs, shell và design tokens hiện có.
- [ ] **IV. Validation & Error Handling**: Shared async states và AI/notification widget branches đã được sửa; T021-T025, T049 và payment callback policy vẫn chưa hội tụ đủ.
- [ ] **V. Trải nghiệm thực tế**: Route inventory có real-browser evidence; protected notification realtime đã được fixture-verified, nhưng authenticated customer/owner journeys và chat reconnect vẫn cần fixture-based rerun.
- [ ] **VI & VII. Kiểm định & Xác minh**: Unit/build/backend regression đang pass; T036/T040 breakpoint, keyboard, reduced-motion và final P1 rate chưa hoàn tất.
- [x] **VIII. Ghi chép**: Audit matrix và gap register ghi route, actor, bước tái hiện, bằng chứng, severity và hướng xử lý; final metrics được giữ cho T053.

## Project Structure

### Documentation (this feature)

```text
.specify/Feature-03-UI-Functional-Audit-Polish/
|-- spec.md
|-- plan.md
|-- research.md
|-- data-model.md
|-- quickstart.md
|-- audit-matrix.md
|-- gap-register.md
|-- contracts/
|   `-- ui-audit-contract.md
|-- checklists/
|   |-- requirements.md
|   `-- ux.md
|-- brand/
|   `-- logo-brief.md
`-- tasks.md
```

### Source Code (repository root)

```text
frontend/
|-- e2e/                         # Existing Playwright regression scenarios
|-- src/
|   |-- app/
|   |   |-- core/                # Guards, interceptors, services, theme
|   |   |-- features/            # Public, customer, admin, management pages
|   |   |-- layout/              # Client, admin and management shells
|   |   `-- shared/components/   # Reusable tables, states, forms and charts
|   `-- styles.css               # Global LuxeStay semantic tokens
`-- package.json

backend/
|-- src/main/java/com/hotel/     # Controllers, services, repositories, DTOs
|-- src/main/resources/          # Runtime profiles and Flyway migrations
|-- src/test/                    # Unit, integration and security regression
`-- pom.xml

docs/
|-- DESIGN.md
|-- FRONTEND_STANDARDS.md
|-- DEVELOPMENT_RULES.md
|-- UML.md
|-- API_SPEC.md
|-- THESIS.md
`-- audit/
```

**Structure Decision**: Giữ nguyên web application hai phần. Feature này chủ yếu thay đổi tài liệu và Angular; backend chỉ được sửa khi browser/source audit chứng minh gap nhỏ, cần thiết để hoàn thành một hành trình đã công bố.

## Execution Phases

### Phase 0 - Research and Inventory

1. Trích xuất route/menu và ánh xạ actor, permission, component, API dependency.
2. Đối chiếu audit cũ với source và test hiện tại để loại bỏ kết luận đã lỗi thời.
3. Xác định account/data prerequisites và cách chạy local không chứa secret trong repo.
4. Chốt status taxonomy, evidence format, breakpoint và accessibility baseline.

**Output**: `research.md`, `contracts/ui-audit-contract.md`.

### Phase 1 - Audit Design

1. Tạo `audit-matrix.md` từ 100% route/menu được công bố.
2. Tạo `gap-register.md` liên kết gap tới route, actor, scenario và evidence.
3. Xác định smoke/regression journeys có thể lặp lại.
4. Re-check constitution trước khi thực thi.

**Output**: `data-model.md`, `quickstart.md`, checklist UX và task list.

### Phase 2 - Runtime UI Audit

1. Chạy backend/frontend với cấu hình local hợp lệ và kiểm tra health/API.
2. Kiểm thử Public và Customer: home, search, detail, auth, booking, payment, profile/history, invoice, settings, partner.
3. Kiểm thử System Admin: dashboard, CRUD, reservations, invoice, permissions, property/partner/subscription routes.
4. Kiểm thử Property Operations: context switch, dashboard, inventory và billing.
5. Lặp lại trang đại diện ở 375/768/1024/1440, keyboard-only và reduced motion.

### Phase 3 - Documentation-First Remediation

1. Cập nhật `docs/DESIGN.md`, `docs/FRONTEND_STANDARDS.md`, `docs/UML.md`, `docs/API_SPEC.md` hoặc `docs/THESIS.md` khi phạm vi thay đổi yêu cầu.
2. Sửa shared tokens/components/shell trước page-level CSS.
3. Sửa lỗi navigation, async states, duplicate submission, responsive và accessibility có severity cao.
4. Không triển khai fake UI cho review, mixed-room booking hoặc customer add-on services nếu backend/domain chưa hỗ trợ.
5. Với breakpoint gần mobile (375-760px), account drawer phải chiếm đúng `100dvh`, giữ header/điểm thưởng nhìn thấy và cho phép phần menu cuộn độc lập; guest/room selector phải giữ summary trong chiều cao field và giới hạn popover theo viewport/safe-area thay vì cắt nội dung.

### Phase 3A - Brand Identity Refresh

1. Turn the supplied luxury-hotel reference into an original LuxeStay design brief; do not reproduce its watermark, lettering or exact ornamental composition.
2. Create and approve the emblem/monogram direction before exporting assets.
3. Ship a reusable SVG-first asset family with light/dark, monochrome, stacked, horizontal and favicon variants.
4. Integrate one shared asset family across all application shells and verify contrast, clear space, small-size legibility and responsive layout behavior.

### Phase 4 - Verification and Convergence

1. Chạy frontend unit test và production build.
2. Chạy backend Maven tests nếu source/backend contract bị chạm hoặc để xác nhận baseline bảo mật.
3. Chạy lại browser regression và breakpoint review.
4. Cập nhật audit evidence, gap status và task checkboxes.
5. Chạy Spec Kit analyze read-only; chỉ sửa artifact inconsistency sau khi được người dùng chấp thuận nếu analyze phát hiện vấn đề.
6. Chạy converge để append phần việc còn thiếu vào `tasks.md`, không ghi đè lịch sử.
7. Kiểm tra riêng các chiều rộng 375px, 504px, 532px và 768px cho account drawer cùng guest/room selector; không chấp nhận page overflow, CTA/menu bị che hoặc summary vượt khỏi search-field.

## Test Strategy

| Layer | Coverage | Pass evidence |
|---|---|---|
| Static/source | Route/menu/API/permission mapping, token usage, no fake data | Audit row có source reference |
| Unit | Shared state, layout, route-data behavior, mutation locks | Test command và số test pass |
| Build | Angular strict compilation and production bundle | `npm run build` exit 0 |
| Backend | Auth, tenant, permission, reservation/payment regressions | `mvnw.cmd test` exit 0 |
| Browser functional | Primary/alternate/error/recovery/permission flows | Route, actor, steps, outcome, screenshot/note |
| Responsive/a11y | 375/768/1024/1440, keyboard, focus, reduced motion | Evidence attached to representative core pages |
| Brand asset QA | SVG variants, favicon sizes, contrast, clear space and shell integration | Approved brief, asset inventory, focused UI/build and browser evidence |

## Risk Controls

- Không dùng `any` cleanup diện rộng; chỉ type contract đã đối chiếu Java DTO/controller.
- Không coi nút/menu hiển thị là chức năng hoàn thành nếu mutation/API không thành công.
- Không đưa credential thật vào artifact; tài khoản test lấy từ biến môi trường hoặc dữ liệu local đã có.
- Không thay toàn bộ palette; giữ Primary Blue, navy surfaces và Gold có chủ đích.
- Không biến E2E mock/intercept thành bằng chứng runtime thật.
- Nếu DB hoặc account thiếu làm route không kiểm thử được, đánh dấu `BLOCKED` kèm điều kiện mở khóa.

Logo artwork must remain original and SVG-first; do not ship copied reference imagery, watermarks or raster-only marks, and do not allow a decorative emblem to reduce text/icon contrast below the accessibility baseline.

## Complexity Tracking

T073-T076 add an explicit brand-asset gate: the logo must be approved, original, reusable across shells and validated at small sizes before the final premium-polish gate can close.

Scope rộng được kiểm soát bằng audit-first, shared remediation và ưu tiên P1/P2. Hai vi phạm đã có remediation trong convergence là notification authorization/realtime transport (T057) và runtime local-path coupling (T059), đã có regression tương ứng. Payment simulator callback (T058/GAP-022) vẫn là thay đổi policy cần người dùng phê duyệt. AI response rendering, authenticated chat reconnect, assigned-property browser coverage, responsive/accessibility and final P1 metrics tiếp tục là gate mở, không được suy diễn là production-ready.
