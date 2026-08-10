# Feature Specification: Danh mục chức năng, phân quyền động và thanh toán VNPay

**Feature Branch**: `[009-role-permissions-vnpay]`

**Created**: 2026-08-09

**Status**: Draft

**Input**: Liệt kê và mô tả chức năng cho Khách hàng, Admin, Quản lý, Kế toán và Lễ tân; quy định hành vi khi bật hoặc tắt quyền xem, thêm, sửa, xóa và thực hiện tác vụ; hỗ trợ thanh toán đặt phòng và gói dịch vụ qua VNPay.

## User Scenarios & Testing

### User Story 1 - Quản trị viên cấu hình quyền theo chức năng (Priority: P1)

Admin có thể xem danh mục chức năng và bật hoặc tắt từng quyền hành động cho từng vai trò, trong khi hệ thống luôn giới hạn dữ liệu theo đúng khách sạn được phân công.

**Why this priority**: Đây là nền tảng bảo mật quyết định mọi vai trò có thể nhìn thấy và thực hiện nghiệp vụ nào.

**Independent Test**: Bật và tắt từng quyền trên một vai trò thử nghiệm, đăng nhập lại bằng người dùng thuộc vai trò đó và xác minh đồng nhất trên menu, màn hình, hành động và yêu cầu trực tiếp.

**Acceptance Scenarios**:

1. **Given** vai trò Lễ tân có quyền xem đặt phòng, **When** lễ tân đăng nhập, **Then** menu và danh sách đặt phòng trong khách sạn được phân công được hiển thị.
2. **Given** quyền sửa đặt phòng bị tắt, **When** lễ tân mở chi tiết hoặc gửi yêu cầu cập nhật trực tiếp, **Then** nút sửa không khả dụng và yêu cầu bị từ chối mà không thay đổi dữ liệu.
3. **Given** Admin thay đổi quyền của một vai trò, **When** người dùng thuộc vai trò đó bắt đầu yêu cầu tiếp theo, **Then** quyền mới được áp dụng và thay đổi được ghi nhận trong lịch sử kiểm toán.

---

### User Story 2 - Nhân sự hoàn thành đúng tác vụ nghiệp vụ (Priority: P1)

Quản lý, Kế toán và Lễ tân chỉ thấy các hàng đợi công việc thuộc phạm vi và chỉ được thực hiện các bước tương ứng với quyền `TASK_EXECUTE` hoặc quyền nghiệp vụ cụ thể.

**Why this priority**: Việc chỉ phân quyền xem, thêm, sửa, xóa chưa đủ cho các hành động nghiệp vụ có tác động lớn như xác nhận thanh toán, check-in, check-out, hoàn tiền hoặc khóa phòng.

**Independent Test**: Giao cùng một tác vụ cho ba tài khoản có bộ quyền khác nhau và xác minh tài khoản chỉ xem được, tài khoản được thực hiện, và tài khoản bị thu hồi quyền cho kết quả khác nhau đúng quy định.

**Acceptance Scenarios**:

1. **Given** người dùng có quyền xem tác vụ nhưng không có quyền thực hiện, **When** mở tác vụ, **Then** họ xem được thông tin nhưng không thể chuyển trạng thái nghiệp vụ.
2. **Given** một tác vụ đang mở và quyền thực hiện bị tắt, **When** người dùng cố hoàn thành tác vụ, **Then** hệ thống từ chối, giữ nguyên trạng thái và không tự động chuyển tác vụ cho người khác.
3. **Given** người dùng có đủ quyền và đúng phạm vi khách sạn, **When** hoàn thành tác vụ hợp lệ, **Then** trạng thái, người thực hiện, thời gian và kết quả được lưu vào lịch sử.

---

### User Story 3 - Khách hàng đặt phòng và thanh toán qua VNPay (Priority: P1)

Khách hàng tìm phòng, tạo đặt phòng, chọn VNPay, hoàn tất thanh toán và nhận kết quả đặt phòng chính xác mà không thể tự xác nhận giao dịch thành công.

**Why this priority**: Đây là luồng doanh thu trực tiếp và ảnh hưởng đến tồn phòng, tiền của khách và uy tín khách sạn.

**Independent Test**: Tạo một đặt phòng, thanh toán trong môi trường thử nghiệm VNPay và kiểm tra các nhánh thành công, thất bại, hủy, hết hạn, tải lại và thông báo lặp.

**Acceptance Scenarios**:

1. **Given** đặt phòng còn hiệu lực và số tiền được hệ thống xác định, **When** khách chọn VNPay, **Then** hệ thống tạo một phiên thanh toán duy nhất và chuyển khách đến đúng trang thanh toán.
2. **Given** VNPay xác nhận giao dịch hợp lệ với đúng số tiền và tham chiếu, **When** kết quả được xử lý, **Then** thanh toán và đặt phòng được cập nhật đúng một lần, đồng thời khách nhận được xác nhận.
3. **Given** chữ ký, số tiền hoặc tham chiếu không hợp lệ, **When** kết quả thanh toán được gửi về, **Then** đặt phòng không được đánh dấu đã thanh toán và giao dịch được đưa vào diện cần đối soát.

---

### User Story 4 - Chủ cơ sở hoặc Quản lý mua gói sử dụng qua VNPay (Priority: P1)

Người đại diện cơ sở có quyền quản lý gói có thể chọn gói phần mềm, thanh toán qua VNPay và chỉ được kích hoạt quyền lợi sau khi giao dịch hợp lệ được xác nhận.

**Why this priority**: Gói sử dụng quyết định giới hạn chức năng của khách sạn và là nguồn doanh thu nền tảng tách biệt với tiền đặt phòng.

**Independent Test**: Mua một gói bằng tài khoản có quyền, phát lại kết quả thanh toán nhiều lần và xác minh thời hạn cùng quyền lợi chỉ được cộng một lần.

**Acceptance Scenarios**:

1. **Given** người dùng có quyền mua gói, **When** chọn gói đang bán, **Then** đơn hàng lưu đúng giá, thời hạn và quyền lợi tại thời điểm mua.
2. **Given** VNPay xác nhận thanh toán gói thành công, **When** kết quả được xử lý, **Then** gói được kích hoạt hoặc gia hạn đúng một lần.
3. **Given** giao dịch thất bại, bị hủy hoặc hết hạn, **When** người dùng quay lại hệ thống, **Then** gói không được kích hoạt và người dùng có thể tạo lần thanh toán mới.

---

### User Story 5 - Kế toán đối soát giao dịch và doanh thu (Priority: P2)

Kế toán có thể xem giao dịch, hóa đơn, công nợ, hoàn tiền và báo cáo doanh thu của khách sạn được phân công, nhưng không mặc nhiên được sửa đặt phòng hoặc quản trị nhân sự.

**Why this priority**: Phân tách nhiệm vụ làm giảm sai lệch doanh thu và rủi ro gian lận.

**Independent Test**: Đăng nhập bằng tài khoản Kế toán chỉ có quyền tài chính, đối soát một giao dịch VNPay và xác minh các chức năng vận hành ngoài phạm vi đều bị chặn.

**Acceptance Scenarios**:

1. **Given** kế toán có quyền xem và đối soát thanh toán, **When** lọc theo ngày và khách sạn, **Then** tổng tiền và chi tiết khớp với giao dịch hợp lệ trong cùng phạm vi.
2. **Given** kế toán không có quyền hoàn tiền, **When** mở giao dịch thành công, **Then** có thể xem nhưng không thể gửi yêu cầu hoàn tiền.

### Edge Cases

- Một người dùng có nhiều vai trò hoặc được phân công nhiều khách sạn; quyền hiệu lực là hợp quyền cho phép nhưng dữ liệu vẫn bị giới hạn theo khách sạn đang hoạt động.
- Quyền bị tắt trong lúc người dùng đang mở màn hình hoặc đang chuẩn bị gửi biểu mẫu.
- Tắt quyền xem nhưng vẫn để quyền thêm, sửa, xóa hoặc thực hiện tác vụ; hệ thống phải coi cấu hình này không hợp lệ hoặc vô hiệu hóa các quyền phụ thuộc.
- Quyền xóa được bật nhưng bản ghi đã phát sinh giao dịch, hóa đơn hoặc lịch sử kiểm toán; hệ thống phải dùng hủy/ngừng hoạt động thay vì xóa vật lý.
- Người dùng lưu đường dẫn cũ hoặc gọi trực tiếp chức năng đã bị ẩn khỏi menu.
- Tác vụ đang được nhận xử lý khi quyền thực hiện bị thu hồi, vai trò bị gỡ hoặc người dùng mất quyền truy cập khách sạn.
- Hai nhân viên cùng xử lý một tác vụ hoặc cùng xác nhận một thanh toán.
- Khách tải lại trang thanh toán, bấm thanh toán nhiều lần hoặc VNPay gửi kết quả lặp.
- VNPay báo thành công nhưng sai số tiền, đơn hàng, loại giao dịch hoặc môi trường.
- Giá hoặc quyền lợi của gói thay đổi trong khi đơn hàng đang chờ thanh toán.
- Khách thanh toán đặt phòng thành công sau khi giữ chỗ đã hết hạn hoặc đặt phòng đã bị hủy.

## Requirements

### Functional Requirements

- **FR-001**: Hệ thống MUST duy trì danh mục chức năng theo các nhóm: tài khoản, khách hàng, khách sạn, phòng, đặt phòng, lễ tân, tác vụ, dịch vụ, thanh toán, hóa đơn, kế toán, báo cáo, nhân sự, vai trò/quyền, gói sử dụng và cấu hình hệ thống.
- **FR-002**: Mỗi chức năng MUST hỗ trợ tập hành động phù hợp gồm `VIEW`, `CREATE`, `UPDATE`, `DELETE` và `TASK_EXECUTE`; chức năng không có hành động nào phải loại bỏ hành động đó khỏi cấu hình.
- **FR-003**: `VIEW` MUST là quyền nền cho `CREATE`, `UPDATE`, `DELETE` và `TASK_EXECUTE`; không được lưu cấu hình có quyền phụ thuộc đang bật trong khi `VIEW` bị tắt.
- **FR-004**: Khi `VIEW` bị tắt, menu, route, dữ liệu danh sách, chi tiết và kết quả tìm kiếm của chức năng MUST không khả dụng đối với người dùng.
- **FR-005**: Khi `CREATE`, `UPDATE`, `DELETE` hoặc `TASK_EXECUTE` bị tắt, điều khiển tương ứng MUST bị ẩn hoặc vô hiệu hóa và mọi yêu cầu trực tiếp MUST bị từ chối mà không thay đổi dữ liệu.
- **FR-006**: Thay đổi quyền MUST có hiệu lực chậm nhất từ yêu cầu nghiệp vụ tiếp theo; phiên đăng nhập cũ không được tiếp tục sử dụng quyền đã bị thu hồi.
- **FR-007**: Mọi kiểm tra quyền MUST kết hợp với phạm vi khách sạn của người dùng; có quyền chức năng không đồng nghĩa được truy cập dữ liệu của khách sạn khác.
- **FR-008**: Mọi thay đổi vai trò, quyền, phạm vi dữ liệu và hành động nhạy cảm MUST ghi lịch sử gồm người thao tác, đối tượng, giá trị trước/sau, thời gian và lý do khi cần.
- **FR-009**: Các bản ghi có lịch sử tài chính, lưu trú hoặc kiểm toán MUST không bị xóa vật lý; quyền `DELETE` trên các bản ghi này MUST thực hiện hủy, vô hiệu hóa hoặc lưu trữ theo quy tắc nghiệp vụ.
- **FR-010**: `TASK_EXECUTE` MUST được dùng cho hành động chuyển trạng thái nghiệp vụ như xác nhận đặt phòng, nhận phòng, trả phòng, xác nhận thanh toán, đối soát, hoàn tiền, duyệt yêu cầu và hoàn thành công việc.
- **FR-011**: Thu hồi `TASK_EXECUTE` MUST ngăn mọi bước xử lý mới trên tác vụ đang mở, giữ nguyên lịch sử và cho phép người có thẩm quyền phân công lại.
- **FR-012**: Hệ thống MUST ngăn hai người hoàn thành cùng một tác vụ hoặc tạo hai hiệu ứng từ cùng một yêu cầu đồng thời.
- **FR-013**: Vai trò Khách hàng mặc định MUST được quản lý hồ sơ cá nhân, tìm kiếm/yêu thích, tạo và xem đặt phòng của mình, thanh toán, xem hóa đơn, gửi yêu cầu hủy/hoàn tiền theo chính sách và nhận hỗ trợ.
- **FR-014**: Vai trò Admin mặc định MUST quản lý cấu hình nền tảng, tài khoản, khách sạn, danh mục gói, vai trò/quyền, kiểm toán và báo cáo nền tảng; dữ liệu tài chính khách sạn chỉ được truy cập khi có quyền rõ ràng.
- **FR-015**: Vai trò Quản lý mặc định MUST quản lý tài nguyên, phòng, giá, nhân sự, đặt phòng, dịch vụ, vận hành, báo cáo và gói sử dụng trong các khách sạn được phân công.
- **FR-016**: Vai trò Kế toán mặc định MUST xem hóa đơn, giao dịch, công nợ, hoàn tiền, đối soát và báo cáo tài chính trong phạm vi được phân công; quyền sửa nghiệp vụ đặt phòng hoặc quản lý nhân sự không được cấp mặc định.
- **FR-017**: Vai trò Lễ tân mặc định MUST xem/tạo/cập nhật đặt phòng, gán phòng, check-in, ghi nhận dịch vụ, thu tiền được phép và check-out trong phạm vi được phân công; quản trị quyền, gói và báo cáo nền tảng không được cấp mặc định.
- **FR-018**: Admin được phép tạo vai trò tùy chỉnh và sao chép bộ quyền mặc định, nhưng hệ thống MUST cảnh báo khi cấu hình phá vỡ phân tách nhiệm vụ tài chính hoặc trao quyền ngoài phạm vi quản lý.
- **FR-019**: Thanh toán đặt phòng và thanh toán gói sử dụng MUST là hai loại giao dịch, đơn hàng, báo cáo và phạm vi cấu hình tách biệt.
- **FR-020**: Khách hàng MUST có thể chọn VNPay cho đặt phòng đủ điều kiện; người đại diện cơ sở có `VIEW` và `TASK_EXECUTE` trên gói sử dụng MUST có thể thanh toán gói qua VNPay.
- **FR-021**: Số tiền, loại giao dịch, đơn hàng, khách sạn, gói và thời hạn thanh toán MUST được xác định từ dữ liệu đáng tin cậy của hệ thống, không lấy giá trị do trình duyệt tự khai báo làm nguồn sự thật.
- **FR-022**: Mỗi lần thanh toán VNPay MUST có mã tham chiếu duy nhất, thời hạn, trạng thái và khóa chống xử lý trùng.
- **FR-023**: Hệ thống MUST hỗ trợ trạng thái khởi tạo, chờ thanh toán, thành công, thất bại, bị hủy, hết hạn và cần đối soát cho giao dịch VNPay.
- **FR-024**: Chỉ kết quả VNPay hợp lệ, đúng môi trường, đúng đơn hàng, đúng số tiền và chưa được xử lý mới được thay đổi trạng thái thanh toán, đặt phòng hoặc gói sử dụng.
- **FR-025**: Trang người dùng quay về sau thanh toán MUST chỉ hiển thị kết quả; không được tự quyết định giao dịch thành công nếu chưa có xác nhận đáng tin cậy.
- **FR-026**: Kết quả VNPay lặp hoặc các yêu cầu đồng thời MUST tạo đúng một hiệu ứng tài chính, một lần xác nhận đặt phòng và một lần kích hoạt/gia hạn gói.
- **FR-027**: Giao dịch sai chữ ký, số tiền, tham chiếu, loại giao dịch hoặc môi trường MUST không kích hoạt quyền lợi và MUST được ghi nhận để Kế toán/Admin đối soát.
- **FR-028**: Đơn hàng gói MUST lưu ảnh chụp giá, thời hạn, giới hạn và quyền lợi tại thời điểm tạo để thay đổi danh mục sau đó không làm đổi đơn hàng đang chờ.
- **FR-029**: Gói hết hạn MUST chặn các thao tác tạo/sửa vượt quyền lợi nhưng vẫn cho phép người có quyền xem và xuất dữ liệu lịch sử theo chính sách.
- **FR-030**: Kế toán hoặc người có quyền đối soát MUST xem được giao dịch VNPay theo thời gian, trạng thái, loại giao dịch và khách sạn; tổng hợp đặt phòng không được trộn với doanh thu gói nền tảng.

### Ma trận quyền mặc định

| Nhóm chức năng | Khách hàng | Admin | Quản lý | Kế toán | Lễ tân |
|---|---|---|---|---|---|
| Hồ sơ cá nhân | Xem, sửa của mình | Xem theo quyền | Xem, sửa của mình | Xem, sửa của mình | Xem, sửa của mình |
| Tìm kiếm, yêu thích | Xem, thêm, xóa của mình | Xem | Xem | Xem | Xem |
| Đặt phòng | Tạo, xem, yêu cầu hủy của mình | Xem theo quyền | Xem, thêm, sửa, hủy, thực hiện tác vụ | Xem tài chính | Xem, thêm, sửa, thực hiện tác vụ |
| Check-in/check-out | Xem trạng thái của mình | Xem theo quyền | Xem, sửa, thực hiện tác vụ | Xem kết quả tài chính | Xem, sửa, thực hiện tác vụ |
| Phòng, loại phòng, giá | Xem công khai | Quản trị theo quyền | Xem, thêm, sửa, ngừng hoạt động | Xem | Xem |
| Dịch vụ phát sinh | Xem trên đặt phòng | Xem theo quyền | Xem, thêm, sửa, thực hiện tác vụ | Xem doanh thu | Xem, thêm, sửa, thực hiện tác vụ |
| Thanh toán, hóa đơn | Thanh toán và xem của mình | Xem/đối soát theo quyền | Xem, thu tiền theo quyền | Xem, đối soát, hoàn tiền theo quyền | Xem, thu tiền theo quyền |
| Báo cáo khách sạn | Không | Xem theo quyền | Xem | Xem, xuất | Xem hạn chế theo quyền |
| Nhân sự khách sạn | Không | Xem theo quyền | Xem, thêm, sửa, ngừng hoạt động | Không | Không |
| Vai trò và quyền | Không | Xem, thêm, sửa, ngừng hoạt động | Xem hoặc quản lý khi được ủy quyền | Không | Không |
| Gói sử dụng | Xem quyền lợi liên quan | Quản lý danh mục và giao dịch nền tảng | Xem, mua, gia hạn | Xem hóa đơn/đối soát khi được cấp | Không |
| Cấu hình nền tảng | Không | Xem, thêm, sửa, ngừng hoạt động | Không | Không | Không |

### Key Entities

- **Role**: Vai trò hệ thống hoặc vai trò tùy chỉnh, thuộc phạm vi nền tảng hay khách sạn.
- **FunctionCatalogItem**: Một chức năng có mã ổn định, nhóm nghiệp vụ và tập hành động được hỗ trợ.
- **RolePermission**: Liên kết vai trò, chức năng và tập quyền hành động đang bật.
- **PropertyAssignment**: Quan hệ giữa người dùng, vai trò và khách sạn được phép truy cập.
- **OperationalTask**: Công việc nghiệp vụ có loại, trạng thái, đối tượng liên quan, người được giao và lịch sử xử lý.
- **PermissionAuditEvent**: Bằng chứng thay đổi vai trò, quyền hoặc phạm vi dữ liệu.
- **BookingPaymentOrder**: Yêu cầu thanh toán cho đặt phòng, tách biệt với đơn hàng gói.
- **SubscriptionOrder**: Yêu cầu mua, gia hạn hoặc thay đổi gói với ảnh chụp quyền lợi và giá.
- **VNPayTransaction**: Lần giao dịch liên kết đúng một đơn hàng, có tham chiếu, số tiền, trạng thái và bằng chứng đối soát.
- **ReconciliationCase**: Trường hợp chênh lệch hoặc kết quả bất thường cần Kế toán/Admin xử lý.

## Success Criteria

### Measurable Outcomes

- **SC-001**: 100% chức năng trong menu quản trị được ánh xạ tới ít nhất một mã chức năng và một quyền hành động rõ ràng.
- **SC-002**: 100% thử nghiệm tắt quyền xác nhận cả giao diện và yêu cầu trực tiếp đều không cho phép hành động bị thu hồi.
- **SC-003**: Quyền bị thu hồi có hiệu lực từ yêu cầu nghiệp vụ tiếp theo đối với 100% trường hợp kiểm thử, không cần đăng xuất để ngăn hành động nhạy cảm.
- **SC-004**: 100% truy cập chéo khách sạn trong bộ kiểm thử bị từ chối và không tạo thay đổi dữ liệu.
- **SC-005**: 100% thay đổi quyền và hành động nhạy cảm có bản ghi kiểm toán đầy đủ người thực hiện, thời gian, đối tượng và kết quả.
- **SC-006**: Người dùng thuộc năm vai trò hoàn thành đúng luồng công việc chính trong không quá 3 phút và không nhìn thấy chức năng ngoài quyền mặc định.
- **SC-007**: Mỗi giao dịch VNPay thành công, kể cả khi kết quả được gửi lặp hoặc đồng thời, tạo đúng một hiệu ứng lên đặt phòng hoặc gói sử dụng.
- **SC-008**: 100% kết quả VNPay sai chữ ký, sai số tiền, sai tham chiếu hoặc sai môi trường không kích hoạt đặt phòng đã thanh toán hay quyền lợi gói.
- **SC-009**: Báo cáo đối soát khớp đến đơn vị VND giữa đơn hàng, giao dịch thành công, hóa đơn và hoàn tiền trong toàn bộ bộ dữ liệu kiểm thử.
- **SC-010**: Toàn bộ hành trình phân quyền, đặt phòng-thanh toán và mua gói-thanh toán được kiểm thử đầu cuối trên tích hợp thử nghiệm thực, không thay bằng nút giả xác nhận thành công.

## Assumptions

- “Quản lý” tương ứng vai trò quản lý/chủ cơ sở trong phạm vi một hoặc nhiều khách sạn được phân công.
- Quyền “thêm” được chuẩn hóa thành `CREATE`; “sửa” thành `UPDATE`; “xóa” thành `DELETE`; “thực hiện tác vụ” thành `TASK_EXECUTE`.
- `TASK_EXECUTE` không thay thế quyền sửa dữ liệu thông thường; nó bảo vệ các chuyển trạng thái nghiệp vụ có ảnh hưởng vận hành hoặc tài chính.
- Xóa vật lý không áp dụng cho giao dịch tài chính, hóa đơn, đặt phòng đã phát sinh nghiệp vụ và lịch sử kiểm toán.
- Phiên bản đầu sử dụng VND và môi trường thử nghiệm VNPay; bật thanh toán tiền thật cần quy trình phê duyệt sẵn sàng riêng.
- Chính sách hủy, hoàn tiền, tiền cọc và thời điểm hết hạn giữ chỗ được lấy từ cấu hình khách sạn hiện có.
- Các vai trò mặc định là điểm khởi đầu; Admin có thể tinh chỉnh bằng quyền động nhưng không được vượt phạm vi dữ liệu của người dùng.

