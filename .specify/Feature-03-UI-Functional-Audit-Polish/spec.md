# Feature Specification: Full UI Functional Audit & Premium Polish

**Feature Branch**: `codex/ui-functional-audit-polish`

**Created**: 2026-07-27

**Status**: Ready for Planning

**Input**: User description: "Dùng Spec Kit lên kế hoạch test lại tất cả chức năng qua giao diện, ghi chú chức năng chưa hoàn thiện hoặc còn thiếu, đồng thời làm giao diện đẹp, cao cấp và đầy đủ hơn."

## User Scenarios & Testing

### User Story 1 - Kiểm thử toàn bộ hành trình theo vai trò (Priority: P1)

Người kiểm thử có thể đi qua toàn bộ route và hành động đang được hệ thống công bố cho Public, Customer, System Admin và Property Operations bằng giao diện thật, với dữ liệu và API thật, để xác định chính xác chức năng nào hoạt động, hoạt động một phần, bị chặn hoặc còn thiếu.

**Why this priority**: Không thể cải tiến có căn cứ nếu chưa có bản đồ chức năng và bằng chứng thực tế theo từng vai trò.

**Independent Test**: Có thể kiểm tra độc lập bằng cách đăng nhập lần lượt bằng tài khoản đại diện cho từng nhóm vai trò, truy cập mọi menu/route được cấp và ghi nhận kết quả theo ma trận audit.

**Acceptance Scenarios**:

1. **Given** danh sách route và menu hiện có, **When** người kiểm thử rà từng route với đúng vai trò, **Then** mỗi route có trạng thái, bằng chứng, lỗi quan sát được và hành động tiếp theo.
2. **Given** một chức năng hiển thị trên giao diện nhưng không hoàn thành được hành trình, **When** người kiểm thử thực hiện thao tác chính và các nhánh lỗi, **Then** chức năng được đánh dấu PARTIAL hoặc BLOCKED thay vì ghi nhận hoàn thiện.
3. **Given** một chức năng chỉ tồn tại trong tài liệu/mockup nhưng không có route hoặc luồng sử dụng, **When** đối chiếu inventory, **Then** chức năng được đánh dấu MISSING và không được mô phỏng bằng dữ liệu giả.
4. **Given** người dùng không đủ quyền, **When** truy cập route hoặc thao tác bị hạn chế, **Then** hệ thống hiển thị trạng thái từ chối rõ ràng, không vòng lặp chuyển hướng và không lộ dữ liệu ngoài phạm vi.

---

### User Story 2 - Hoàn thành các hành trình nghiệp vụ cốt lõi (Priority: P1)

Khách hàng và nhân sự khách sạn có thể hoàn thành các hành trình cốt lõi qua giao diện với trạng thái tải, lỗi, rỗng, thành công và vô hiệu hóa rõ ràng.

**Why this priority**: Các hành trình tìm phòng, đặt phòng, thanh toán, quản lý phòng, đặt chỗ, hóa đơn và quyền truy cập tạo ra giá trị vận hành trực tiếp.

**Independent Test**: Có thể kiểm tra độc lập bằng các hành trình end-to-end từ điểm bắt đầu tới kết quả cuối cùng, bao gồm ít nhất một nhánh lỗi hoặc phục hồi.

**Acceptance Scenarios**:

1. **Given** khách chưa đăng nhập, **When** tìm kiếm, lọc và mở chi tiết cơ sở lưu trú, **Then** nội dung chính hiển thị rõ ràng trên desktop và mobile, có empty/error/retry khi dữ liệu không khả dụng.
2. **Given** khách đã đăng nhập và chọn phòng hợp lệ, **When** hoàn thành checkout và thanh toán, **Then** thao tác không bị gửi lặp, kết quả được giải thích rõ và lịch sử liên quan có thể truy cập lại.
3. **Given** nhân sự có quyền quản lý, **When** thao tác với phòng, loại phòng, dịch vụ, đặt chỗ hoặc hóa đơn, **Then** giao diện thể hiện đúng phạm vi property, trạng thái xử lý và kết quả mutation.
4. **Given** owner/manager có nhiều property, **When** chuyển ngữ cảnh property, **Then** nội dung và điều hướng phản ánh đúng property được cấp quyền mà không dùng dữ liệu của property khác.

---

### User Story 3 - Nhận diện và ưu tiên khoảng trống sản phẩm (Priority: P2)

Nhóm phát triển có một danh sách khoảng trống có thể hành động, liên kết từ route/chức năng tới bằng chứng, mức ảnh hưởng và đề xuất xử lý.

**Why this priority**: Danh sách lỗi chung chung không đủ để quyết định nên sửa UI, nối API hay bổ sung nghiệp vụ.

**Independent Test**: Có thể kiểm tra độc lập bằng cách chọn bất kỳ mục gap nào và truy ngược tới route, vai trò, bước tái hiện, bằng chứng và task liên quan.

**Acceptance Scenarios**:

1. **Given** kết quả audit, **When** một gap được ghi nhận, **Then** gap có loại UI, API, permission, data, responsive, accessibility hoặc nghiệp vụ.
2. **Given** nhiều gap cùng ảnh hưởng một hành trình, **When** sắp xếp backlog, **Then** mức ưu tiên phản ánh tác động tới P1 journey, bảo mật và khả năng phục hồi.
3. **Given** gap cần thay đổi backend hoặc mô hình dữ liệu lớn, **When** iteration hiện tại tập trung UI, **Then** gap được ghi rõ là deferred thay vì tạo giao diện giả hoặc response giả.

---

### User Story 4 - Trải nghiệm nhất quán, cao cấp và dễ sử dụng (Priority: P2)

Người dùng cảm nhận một hệ thống hospitality SaaS chuyên nghiệp, thống nhất giữa public site, customer account, admin và management portal, nhưng vẫn giữ hierarchy riêng của từng nhóm người dùng.

**Why this priority**: Tính nhất quán, rõ ràng và phản hồi tốt làm tăng khả năng hoàn thành tác vụ và giảm cảm giác sản phẩm chưa hoàn thiện.

**Independent Test**: Có thể kiểm tra độc lập bằng visual review trên các breakpoint chuẩn, keyboard-only review và so sánh các thành phần tương đương giữa module.

**Acceptance Scenarios**:

1. **Given** người dùng mở bất kỳ trang chính nào, **When** quan sát hierarchy, spacing, typography và CTA, **Then** trang có một primary action rõ ràng và dùng cùng ngôn ngữ thiết kế LuxeStay.
2. **Given** người dùng thao tác bằng bàn phím, **When** di chuyển qua navigation, form, dialog và table actions, **Then** focus luôn nhìn thấy, thứ tự hợp lý và không có keyboard trap.
3. **Given** viewport 375, 768, 1024 hoặc 1440 pixel, **When** người dùng đi qua các trang cốt lõi, **Then** không có horizontal overflow ngoài vùng table có chủ đích và nội dung không bị fixed navigation che khuất.
4. **Given** người dùng giảm chuyển động, **When** mở trang hoặc thay đổi trạng thái, **Then** animation không cản trở thao tác và tuân theo tùy chọn reduced motion.

---

### User Story 5 - Hồi quy có thể lặp lại (Priority: P3)

Nhóm phát triển có bộ kịch bản smoke/regression qua giao diện đủ ngắn để chạy lại sau mỗi đợt sửa nhưng vẫn bao phủ các hành trình rủi ro cao.

**Why this priority**: UI polish diện rộng dễ gây hồi quy điều hướng, permission và responsive nếu không có bộ kiểm tra lặp lại.

**Independent Test**: Có thể kiểm tra bằng việc chạy lại quickstart và danh sách browser scenarios trên môi trường local sạch.

**Acceptance Scenarios**:

1. **Given** một bản build mới, **When** chạy regression suite, **Then** các P1 journey có kết quả pass/fail rõ ràng và ảnh/chú thích cho lỗi giao diện.
2. **Given** một route chưa đủ dữ liệu để kiểm thử, **When** suite chạy, **Then** route được đánh dấu BLOCKED kèm điều kiện cần, không bị bỏ qua im lặng.

### Edge Cases

- Tài khoản có token hợp lệ nhưng menu rỗng hoặc permission thay đổi giữa phiên.
- API trả 401, 403, 404, 409, 422 hoặc 500 trong khi người dùng đang ở trang có dữ liệu cũ.
- Người dùng có nhiều property, chỉ một property, hoặc không còn property được cấp quyền.
- Danh sách không có dữ liệu, có dữ liệu rất dài, chuỗi tiếng Việt dài hoặc ảnh bị lỗi.
- Mạng chậm khiến loading kéo dài, mutation timeout hoặc người dùng nhấn nút nhiều lần.
- Viewport nhỏ, zoom chữ lớn, landscape mobile, table nhiều cột và menu nhiều cấp.
- Route tồn tại nhưng menu không trỏ tới, menu trỏ tới route redirect, hoặc route dùng chung component với route-data khác nhau.
- Feature bị khóa bởi subscription nhưng dữ liệu lịch sử vẫn cần đọc được.

## Requirements

### Functional Requirements

- **FR-001**: Hệ thống audit MUST lập inventory cho 100% route và mục menu đang được công bố trong public, customer, admin và management portal.
- **FR-002**: Mỗi mục inventory MUST xác định actor/role, route, hành động chính, dependency dữ liệu/API, permission, trạng thái và bằng chứng kiểm thử.
- **FR-003**: Kết quả MUST dùng trạng thái thống nhất `COMPLETE`, `PARTIAL`, `MISSING`, `BLOCKED`, `BROKEN` và nêu rõ lý do.
- **FR-004**: Kiểm thử MUST sử dụng giao diện và integration thật; không dùng mock/fake UI làm bằng chứng hoàn thiện.
- **FR-005**: Audit MUST bao phủ primary, alternate, error, recovery và permission-denied scenarios cho mọi P1 journey.
- **FR-006**: Public/customer coverage MUST gồm home, search, hotel detail, authentication, booking checkout, payment result, profile, booking history, invoices, settings và partner registration/status.
- **FR-007**: Admin coverage MUST gồm dashboard, profile, users, customers, room types, rooms, services, reservations, timeline/create, invoices, modules, chat, properties, plans, roles, role permissions, imports/claims và partner administration routes.
- **FR-008**: Management coverage MUST gồm property context, dashboard, properties, room types, rooms và subscription/billing.
- **FR-009**: Mọi async page hoặc mutation được sửa trong iteration MUST có loading, empty, error/recovery, success và disabled/submitting states phù hợp.
- **FR-010**: Mọi gap MUST được phân loại ít nhất theo một nhóm: UI/UX, responsive, accessibility, route/navigation, permission, API contract, data, business rule hoặc testability.
- **FR-011**: Mọi gap ưu tiên P1/P2 MUST có bước tái hiện, expected outcome, actual outcome, severity và đề xuất bước tiếp theo.
- **FR-012**: UI polish MUST dùng design token hiện có, không hardcode màu trong component, giữ Primary Blue cho primary action và Gold cho premium/status có chủ đích.
- **FR-013**: Navigation MUST thể hiện vị trí hiện tại, có đường thoát/recovery rõ ràng và không tạo redirect loop khi thiếu quyền hoặc hết phiên.
- **FR-014**: Các tương tác chính MUST sử dụng semantic controls, visible focus, label/aria phù hợp và touch target tối thiểu 44 pixel.
- **FR-015**: Các trang cốt lõi MUST hoạt động ở viewport 375, 768, 1024 và 1440 pixel mà không che nội dung hoặc tạo overflow ngoài vùng được thiết kế.
- **FR-016**: Tables và data-heavy views MUST có chiến lược responsive rõ ràng, giữ khả năng đọc và thao tác trên mobile.
- **FR-017**: Giao diện MUST cung cấp phản hồi trong vòng 300 ms cho thao tác bắt đầu xử lý; tác vụ lâu hơn MUST có progress/loading feedback.
- **FR-018**: Mutation actions MUST ngăn gửi lặp trong khi đang xử lý và MUST giải thích cách phục hồi khi thất bại.
- **FR-019**: Feature gaps cần backend/data-model lớn MUST được ghi deferred với dependency cụ thể; iteration không được giả lập dữ liệu để che gap.
- **FR-020**: Tài liệu audit, quickstart và tasks MUST duy trì traceability từ user story và requirement tới route, bằng chứng và thay đổi code.

### Key Entities

- **UI Capability**: Một route hoặc hành động người dùng có actor, permission, dependency, trạng thái audit và mức ưu tiên.
- **Audit Evidence**: Ảnh chụp, console/network observation, bước tái hiện và kết quả thực tế của một scenario.
- **Product Gap**: Chức năng thiếu/chưa hoàn thiện với loại gap, severity, dependency và disposition hiện tại.
- **Regression Scenario**: Kịch bản có precondition, actor, viewport, bước thao tác, expected outcome và kết quả gần nhất.
- **Visual Quality Rule**: Quy tắc token, hierarchy, responsive, accessibility hoặc motion áp dụng cho một nhóm surface.

## Success Criteria

### Measurable Outcomes

- **SC-001**: 100% route và menu item hiện có được ánh xạ trong audit matrix với actor và trạng thái rõ ràng.
- **SC-002**: 100% P1 journey có ít nhất một primary scenario, một error/recovery scenario và một permission scenario khi phù hợp.
- **SC-003**: Tất cả gap phát hiện được ghi nhận; không có chức năng chưa xác minh nào bị đánh dấu COMPLETE.
- **SC-004**: Ít nhất 90% P1 browser scenarios có thể hoàn thành trên môi trường local chuẩn; phần còn lại có blocker và dependency cụ thể.
- **SC-005**: 100% trang được thay đổi có loading/error/empty/success states phù hợp và có đường phục hồi khi request thất bại.
- **SC-006**: Không có horizontal page overflow không chủ đích tại 375, 768, 1024 và 1440 pixel trên các P1 surfaces.
- **SC-007**: 100% primary actions và navigation controls được thay đổi có accessible name, visible focus và touch target tối thiểu 44 pixel.
- **SC-008**: Các P1 journey được hoàn thành mà không gặp blank screen, redirect loop hoặc lỗi console chưa được giải thích.
- **SC-009**: Bộ regression có thể chạy lại và tạo kết quả pass/fail/block rõ ràng trong một phiên kiểm thử dưới 45 phút.
- **SC-010**: Tất cả thay đổi qua được frontend unit test, production build và backend regression hiện có trước khi bàn giao.

## Assumptions

- Phạm vi bao gồm các route và chức năng đang tồn tại hoặc được tài liệu hiện hành xác nhận là core; các ý tưởng mockup chưa có domain model được ghi gap thay vì triển khai toàn bộ.
- Môi trường local có thể dùng dữ liệu seed và tài khoản demo hiện có; secret không được ghi vào spec, test evidence hoặc Git.
- Backend vẫn là nguồn thẩm quyền cho permission và tenant isolation; UI guard chỉ cải thiện điều hướng và khả năng hiểu lỗi.
- Iteration ưu tiên khắc phục các gap UI/UX và wiring nhỏ có thể xác minh; migration dữ liệu hoặc thay đổi nghiệp vụ lớn được tách thành follow-up.
- Light theme là baseline bắt buộc; dark mode chỉ được sửa nếu đã có implementation hiện hành cần đồng bộ.
- Bằng chứng browser được thu trên Chrome-compatible runtime với desktop và mobile viewport emulation.
