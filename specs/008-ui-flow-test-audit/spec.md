# Feature Specification: UI Flow Test Audit

**Feature Branch**: `N/A - test-plan-only; active branch remains unchanged`

**Created**: 2026-08-01

**Status**: Draft - Independent Test Plan

**Input**: User description: "Dùng Spec Kit lập kế hoạch test giao diện độc lập; kiểm tra lại các thao tác có đi đúng luồng và phát hiện chức năng đã hiển thị nhưng chưa hoàn thiện, không liên quan plan đang chạy."

## User Scenarios & Testing

### User Story 1 - Kiểm kê toàn bộ bề mặt có thể thao tác (Priority: P1)

Nhóm kiểm thử có một danh sách đầy đủ các trang, menu, nút, biểu mẫu, hộp thoại và hành động đang được người dùng nhìn thấy theo từng vai trò để không bỏ sót chức năng hoặc đánh đồng việc "có giao diện" với "đã hoàn thiện".

**Why this priority**: Không thể đánh giá đúng luồng nếu chưa biết chính xác hệ thống đang công bố những khả năng nào cho Public, Customer, Admin và Property Management.

**Independent Test**: Có thể kiểm tra độc lập bằng cách đối chiếu route, menu theo quyền và control tương tác trên từng trang, sau đó gán chủ sở hữu, điều kiện truy cập và trạng thái kiểm thử cho từng mục.

**Acceptance Scenarios**:

1. **Given** một vai trò hợp lệ, **When** người kiểm thử mở mọi menu và route mà vai trò đó được phép thấy, **Then** mỗi bề mặt có một mục duy nhất trong ma trận kiểm thử.
2. **Given** một hành động xuất hiện trên giao diện, **When** không tìm thấy kết quả nghiệp vụ hoặc đường chuyển tiếp hợp lệ, **Then** hành động được gắn `DISPLAYED_ONLY`, `PARTIAL` hoặc `BROKEN`, không được ghi nhận `COMPLETE`.
3. **Given** route tồn tại nhưng không có lối vào từ menu, hoặc menu trỏ tới route không tồn tại, **When** đối chiếu inventory, **Then** sai lệch được ghi thành gap điều hướng.

---

### User Story 2 - Xác minh thao tác đi đúng luồng nghiệp vụ (Priority: P1)

Người kiểm thử thực hiện các hành trình chính từ điểm bắt đầu đến kết quả cuối cùng bằng giao diện thật và dữ liệu tích hợp thật, bao gồm nhánh thành công, thất bại, phục hồi và từ chối quyền.

**Why this priority**: Trang tải được không chứng minh người dùng có thể hoàn thành tìm kiếm, đặt phòng, thanh toán, vận hành phòng, quản lý đặt chỗ hoặc quản trị quyền.

**Independent Test**: Mỗi hành trình có thể chạy độc lập với tài khoản, dữ liệu đầu vào, bước thao tác, kết quả mong đợi và bằng chứng rõ ràng.

**Acceptance Scenarios**:

1. **Given** dữ liệu và quyền hợp lệ, **When** người dùng hoàn thành hành trình chính, **Then** trạng thái UI, dữ liệu hiển thị và kết quả lưu trữ nhất quán với nhau.
2. **Given** request thất bại hoặc dữ liệu không hợp lệ, **When** người dùng thử lại hoặc sửa đầu vào, **Then** giao diện giải thích lỗi, giữ dữ liệu phù hợp và cung cấp đường phục hồi.
3. **Given** người dùng không đủ quyền hoặc ngoài phạm vi cơ sở lưu trú, **When** truy cập route hay gửi thao tác, **Then** hệ thống từ chối rõ ràng và không lộ dữ liệu ngoài phạm vi.
4. **Given** một mutation đang xử lý, **When** người dùng nhấn lại CTA, **Then** không phát sinh kết quả trùng lặp.

---

### User Story 3 - Ghi nhận chức năng hiển thị nhưng chưa hoàn thiện (Priority: P1)

Nhóm sản phẩm nhận được một sổ đăng ký các chức năng đang được hiển thị nhưng chưa có handler, chưa nối dữ liệu thật, dùng dữ liệu cố định/giả lập, bị vô hiệu hóa không có lộ trình, hoặc chỉ tạo phản hồi bề ngoài mà không hoàn thành nghiệp vụ.

**Why this priority**: Đây là nhóm gây hiểu nhầm lớn nhất cho người dùng và là mục tiêu chính của đợt kiểm thử này.

**Independent Test**: Có thể chọn bất kỳ mục nào trong sổ đăng ký và truy ngược đến trang, control, bước tái hiện, kết quả thực tế, bằng chứng và đề xuất xử lý.

**Acceptance Scenarios**:

1. **Given** control có thể nhìn thấy, **When** thao tác không gây thay đổi, chỉ ghi log, chỉ hiện thông báo tạm hoặc dùng dữ liệu giả, **Then** mục được gắn `DISPLAYED_ONLY` hoặc `PARTIAL` kèm severity.
2. **Given** chức năng chủ động ghi "chưa hỗ trợ" hoặc "sắp ra mắt", **When** kiểm kê, **Then** mục vẫn được ghi nhận là chưa hoàn thiện nhưng phân biệt rõ với lỗi hồi quy.
3. **Given** source cũ có mock nhưng không còn route/menu sử dụng, **When** đối chiếu runtime, **Then** mục được gắn `DORMANT` thay vì báo lỗi giao diện đang hoạt động.

---

### User Story 4 - Tạo bộ hồi quy có thể chạy lặp lại (Priority: P2)

Nhóm phát triển có một bộ kịch bản smoke và regression theo rủi ro, tạo kết quả pass/fail/block cùng bằng chứng để chạy lại sau mỗi thay đổi mà không phụ thuộc vào plan tính năng khác.

**Why this priority**: Audit một lần sẽ nhanh chóng lỗi thời nếu không có bộ kiểm tra có thể chạy lại và quy tắc cập nhật trạng thái.

**Independent Test**: Có thể chạy riêng smoke P1 hoặc một nhóm theo vai trò và nhận báo cáo nhất quán mà không cần thực thi toàn bộ backlog sản phẩm.

**Acceptance Scenarios**:

1. **Given** một bản build có thể chạy, **When** thực thi smoke P1, **Then** mỗi hành trình trả về `PASS`, `FAIL` hoặc `BLOCKED` và có lý do.
2. **Given** một scenario thất bại, **When** xem báo cáo, **Then** có bước tái hiện, ảnh/trace phù hợp, expected/actual và liên kết tới gap.
3. **Given** plan tính năng đang chạy thay đổi, **When** cập nhật feature test này, **Then** artifact test vẫn ở thư mục riêng và không đổi feature selection hiện tại của Spec Kit.

### Edge Cases

- Token hết hạn giữa một form nhiều bước hoặc trong lúc mutation đang xử lý.
- Menu theo quyền tải lỗi, trả rỗng, trùng route hoặc trả route frontend không hỗ trợ.
- Một người dùng có nhiều cơ sở, một cơ sở, không có cơ sở hoặc mất quyền giữa phiên.
- API trả 400, 401, 403, 404, 409, 422, 429 hoặc 500; WebSocket mất kết nối và kết nối lại.
- Danh sách rỗng, phân trang dài, chuỗi tiếng Việt dài, ảnh lỗi, số tiền lớn và dữ liệu ngày ở biên.
- Nhấn đúp, quay lại, reload, mở nhiều tab hoặc callback thanh toán được gửi lại.
- Viewport 375, 768, 1024 và 1440 pixel; zoom 200%; bàn phím; reduced motion.
- Control bị disabled nhưng không giải thích lý do, hoặc mang nhãn "sắp ra mắt" nhưng vẫn phát request.
- Route cũ còn trong test tự động nhưng không còn tồn tại trong router hiện tại.

## Requirements

### Functional Requirements

- **FR-001**: Kế hoạch test MUST tồn tại trong một feature directory riêng và MUST không thay đổi plan hoặc feature selection đang chạy sau khi tạo xong.
- **FR-002**: Inventory MUST bao phủ 100% route, redirect, wildcard, menu động và menu tĩnh đang được công bố.
- **FR-003**: Mỗi surface MUST ghi actor, route, control chính, precondition, dependency, permission và trạng thái kiểm thử.
- **FR-004**: Trạng thái capability MUST dùng thống nhất `COMPLETE`, `PARTIAL`, `DISPLAYED_ONLY`, `BROKEN`, `BLOCKED`, `MISSING` hoặc `DORMANT`.
- **FR-005**: Bằng chứng `COMPLETE` MUST đến từ thao tác qua giao diện với integration thật; test dùng stub/mock không đủ để kết luận hoàn thiện end-to-end.
- **FR-006**: Mỗi hành trình P1 MUST có primary, validation/error, recovery và permission scenario khi phù hợp.
- **FR-007**: Phạm vi Public MUST gồm home, search, property detail, đăng nhập, đăng ký, partner entry và các liên kết/footer được hiển thị.
- **FR-008**: Phạm vi Customer MUST gồm checkout, payment result, profile, booking history, invoices, settings và support chat.
- **FR-009**: Phạm vi Admin MUST gồm dashboard, user/customer, property, room type, room, service, reservation, timeline/create, invoice, role/permission, module, plan, import/claim, partner overview và support chat.
- **FR-010**: Phạm vi Management MUST gồm property context, dashboard/properties, inventory, payment configuration và subscription/billing.
- **FR-011**: Mỗi async surface MUST được kiểm tra loading, empty, error, retry/recovery, success và disabled/submitting states.
- **FR-012**: Mỗi mutation MUST được kiểm tra chống gửi lặp, phản hồi lỗi và tính nhất quán sau reload.
- **FR-013**: Route guard, menu visibility và backend authorization MUST được kiểm tra đồng thời cho các hành động nhạy cảm.
- **FR-014**: Chuyển property MUST được kiểm tra để phát hiện dữ liệu cũ hoặc dữ liệu chéo tenant.
- **FR-015**: Mỗi control đang hiển thị nhưng chưa hoàn thiện MUST có bước tái hiện, expected, actual, severity, loại gap và disposition.
- **FR-016**: Các control chủ động disabled/coming-soon MUST được tách khỏi lỗi hồi quy nhưng không được tính là chức năng hoàn thiện.
- **FR-017**: Kiểm thử responsive MUST bao phủ viewport 375, 768, 1024 và 1440 pixel trên các hành trình P1.
- **FR-018**: Kiểm thử accessibility MUST bao phủ keyboard, visible focus, accessible name, dialog focus và lỗi form được thông báo.
- **FR-019**: Mỗi scenario tự động MUST có traceability tới route/capability và không được dùng route đã bị loại bỏ.
- **FR-020**: Báo cáo MUST phân biệt lỗi sản phẩm, blocker môi trường/dữ liệu và test tự động đã lỗi thời.

### Key Entities

- **UI Surface**: Trang, menu hoặc vùng giao diện có actor, route và tập control được công bố.
- **Interaction Flow**: Chuỗi thao tác từ precondition đến kết quả nghiệp vụ cuối cùng.
- **Test Scenario**: Một primary, alternate, error, recovery, permission, responsive hoặc accessibility case.
- **Audit Evidence**: Ảnh, trace, console/network observation và dữ liệu kết quả của một lần chạy.
- **Capability Gap**: Chức năng thiếu/chưa hoàn thiện với status, severity, loại gap, dependency và disposition.
- **Execution Run**: Một lần chạy có build, môi trường, tài khoản, viewport và kết quả tổng hợp.

## Success Criteria

### Measurable Outcomes

- **SC-001**: 100% route và menu đang công bố được ánh xạ vào test matrix, bao gồm sai lệch menu-route.
- **SC-002**: 100% hành trình P1 có primary scenario và ít nhất một error/recovery scenario; permission scenario được thêm khi có phân quyền.
- **SC-003**: 100% control hiển thị được phân loại; không có mục chưa xác minh bị gắn `COMPLETE`.
- **SC-004**: Tất cả chức năng hiển thị nhưng chưa hoàn thiện được ghi vào gap register với bằng chứng hoặc ghi rõ điều kiện cần để xác minh.
- **SC-005**: 100% test tự động dùng route cũ hoặc assertion không còn phù hợp được nhận diện trước khi dùng làm bằng chứng release.
- **SC-006**: Smoke P1 có thể trả kết quả pass/fail/block rõ ràng trong tối đa 45 phút trên môi trường chuẩn.
- **SC-007**: Không có hành trình P1 được ghi pass nếu xuất hiện blank screen, redirect loop, mutation lặp hoặc lỗi console chưa giải thích.
- **SC-008**: Các trang P1 không có overflow ngoài thiết kế tại bốn viewport mục tiêu và có thể hoàn thành bằng bàn phím.
- **SC-009**: Mỗi failure P1 có thể tái hiện từ báo cáo mà không cần hỏi lại người chạy test.
- **SC-010**: Sau khi tạo artifact, `.specify/feature.json` vẫn trỏ tới plan đang chạy trước đó.

## Assumptions

- Đây là feature test-only: không sửa chức năng sản phẩm trong plan này; gap được chuyển thành backlog riêng sau khi xác minh.
- Plan đang chạy tại thời điểm tạo là `specs/007-payment-billing-completion` và phải tiếp tục là feature selection mặc định.
- Tài khoản đại diện và dữ liệu E2E có thể được chuẩn bị cho Public, Customer, Admin và Property Owner/Manager.
- Bằng chứng hoàn thiện dùng backend và database thật hoặc fixture E2E được khởi tạo qua ứng dụng; interception chỉ dùng để cô lập lỗi kỹ thuật, không dùng để chứng minh end-to-end complete.
- Chromium là browser baseline hiện có; kiểm thử mobile dùng viewport mô phỏng trong giai đoạn đầu.
- Các mục được phát hiện bằng đọc source là baseline cần xác minh lại trên runtime trước khi chốt severity cuối cùng.
