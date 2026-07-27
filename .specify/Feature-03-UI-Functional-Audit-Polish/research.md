# Research: Full UI Functional Audit & Premium Polish

## Decision 1 - Audit by exposed route and actor

**Decision**: Route/menu trong `frontend/src/app/app.routes.ts` và navigation động là inventory gốc. Mỗi route được kiểm tra với actor được cấp quyền và actor không được cấp quyền khi phù hợp.

**Rationale**: Route là bề mặt sản phẩm người dùng thực sự truy cập; tổ chức theo actor giúp phát hiện cả gap điều hướng, permission và tenant context.

**Alternatives rejected**:

- Chỉ test theo component: bỏ sót redirect, guard, route-data và menu.
- Chỉ dựa trên tài liệu: tài liệu có thể đi trước hoặc chậm hơn source.

## Decision 2 - Real browser/API evidence is authoritative

**Decision**: Trạng thái hoàn thiện chỉ được gán sau thao tác qua UI chạy với backend/API và dữ liệu local thật. Unit test, mocked Playwright hoặc source review là evidence hỗ trợ, không thay thế runtime evidence.

**Rationale**: Mục tiêu là tìm chức năng chưa hoàn thiện hoặc chưa nối thật; mock có thể che contract mismatch và permission/data gap.

**Alternatives rejected**:

- Dùng network interception cho mọi flow: nhanh nhưng không chứng minh integration.
- Suy luận từ code/API tồn tại: không chứng minh người dùng hoàn thành được hành trình.

## Decision 3 - Stable status taxonomy

**Decision**: Mọi inventory row dùng đúng một trạng thái:

- `COMPLETE`: hành trình chính và state quan trọng hoạt động bằng integration thật.
- `PARTIAL`: phần chính hoạt động nhưng thiếu nhánh/state/khả năng đã công bố.
- `MISSING`: feature mong đợi nhưng không có route/UI/domain hỗ trợ.
- `BLOCKED`: chưa thể đánh giá vì thiếu account, data, config hoặc external dependency.
- `BROKEN`: route/action hiện có thất bại hoặc gây kết quả sai.

**Rationale**: Tách thiếu implementation khỏi thiếu điều kiện test và lỗi hồi quy giúp backlog có thể hành động.

## Decision 4 - Evidence and gap traceability

**Decision**: Mỗi gap P1/P2 phải liên kết actor, route, scenario, expected/actual result, severity, category, evidence và next step. Evidence có thể là screenshot, browser observation, console/network note, test output hoặc source reference.

**Rationale**: Một danh sách lỗi không có bước tái hiện hoặc owner boundary rất khó ưu tiên và dễ bị đóng nhầm.

## Decision 5 - Premium polish uses the existing LuxeStay language

**Decision**: Giữ primary blue cho CTA, navy cho shell/surface có chiều sâu, gold cho premium/status có chủ đích. Tăng chất lượng bằng hierarchy, spacing, typography, soft elevation, state clarity, focus và responsive behavior; không thay palette wholesale.

**Rationale**: `docs/DESIGN.md`, `styles.css` và theme hiện tại đã định nghĩa nhận diện; thay palette diện rộng tạo hồi quy và làm phân mảnh sản phẩm.

**Alternatives rejected**:

- Tạo theme mới hoàn toàn: vượt phạm vi và xung đột design contract.
- Chỉnh từng component bằng màu cứng: khó bảo trì và vi phạm project rules.

## Decision 6 - Shared-first remediation

**Decision**: Ưu tiên sửa semantic tokens, `feedback-state`, shell, navigation, shared table/filter/dialog và async mutation pattern trước CSS/page-specific fixes.

**Rationale**: Ba shell và nhiều module dùng chung pattern; shared fixes cho độ phủ cao hơn và giảm duplicate code.

## Decision 7 - Responsive and accessibility baseline

**Decision**: Kiểm tra core pages ở 375, 768, 1024 và 1440px; touch target tối thiểu 44px; visible keyboard focus; semantic controls; không keyboard trap; hỗ trợ `prefers-reduced-motion`; horizontal scroll chỉ chấp nhận trong vùng table có chủ đích.

**Rationale**: Đây là các failure mode dễ thấy nhất của dashboard density và multi-column forms/tables.

## Decision 8 - Treat older audit claims as hypotheses

**Decision**: `Feature-02` audit và `docs/audit/*` được dùng làm input nhưng phải xác minh lại. Đặc biệt, kết luận Vitest/Playwright hỏng có thể đã lỗi thời sau commit UX redesign.

**Rationale**: Backlog cũ có giá trị lịch sử nhưng không phải trạng thái hiện tại.

## Decision 9 - Backend changes require proven minimum scope

**Decision**: Chỉ sửa backend khi audit chứng minh contract nhỏ đang chặn hành trình được công bố và thay đổi không mở rộng domain/schema đáng kể. Mixed RoomType booking, reviews và customer add-on services tiếp tục là gap/deferred nếu cần thiết kế nghiệp vụ riêng.

**Rationale**: Feature hiện tại là audit và UI quality; thay đổi domain lớn cần feature specification độc lập.

## Decision 10 - Verification stack

**Decision**: Kết luận cuối dựa trên bốn lớp: source inventory, frontend unit/build, backend regression và in-app browser regression. Browser review dùng viewport và role matrix đã định nghĩa.

**Rationale**: Không lớp đơn lẻ nào đủ bao phủ compilation, authorization, integration và visual behavior.
