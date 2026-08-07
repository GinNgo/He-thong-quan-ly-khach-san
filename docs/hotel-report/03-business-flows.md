# Luồng nghiệp vụ hệ thống Hotel Management

Ngày rà soát: 2026-08-05<br>
Tổng số luồng chính được chuẩn hóa: **12**.

## 1. Quy ước trạng thái

- **PASS**: luồng cốt lõi có bằng chứng thực thi phù hợp.
- **PARTIAL**: luồng có thể đi qua một phần nhưng còn lỗi, permission, cấu hình ngoài hoặc thiếu kiểm chứng end-to-end.
- **BLOCKED**: không thể hoàn tất vì thiếu key/project/dịch vụ ngoài.
- Mọi chuyển trạng thái phải được backend xác nhận; UI không phải nguồn sự thật cho booking, payment hoặc permission.

## 2. Ma trận luồng chính

| ID | Luồng | Actor chính | Điểm vào | Kết quả mong đợi | Trạng thái audit |
|---|---|---|---|---|---|
| BF-01 | Đăng ký, xác minh, đăng nhập và duy trì phiên | Guest/Customer/Admin | `/register`, `/login`, `/admin/login` | Tài khoản hợp lệ và phiên được kiểm soát | PASS |
| BF-02 | Tìm kiếm khách sạn và xem chi tiết | Guest/Customer | Trang chủ, search result | Danh sách và chi tiết theo tiêu chí | PASS |
| BF-03 | Kiểm tra phòng trống và tạo booking hold | Customer | Hotel detail/checkout | Giữ chỗ có TTL, chống vượt tồn | PARTIAL |
| BF-04 | Checkout và thanh toán | Customer/Provider | Booking checkout | Giao dịch được tạo, callback xác minh, booking cập nhật | BLOCKED |
| BF-05 | Hủy booking và hoàn tiền | Customer/Staff/Provider | Booking history/admin | Hủy đúng policy, refund idempotent | PARTIAL |
| BF-06 | Check-in, lưu trú và check-out | Staff | Reservation/stay management | Stay lifecycle hợp lệ, phòng được giải phóng | PARTIAL |
| BF-07 | Đăng ký và phê duyệt đối tác/property | Partner/Admin | Partner register/property approvals | Property được kiểm duyệt trước publish | PARTIAL |
| BF-08 | Quản lý loại phòng, phòng, giá và tồn | Property staff/Owner | Management rooms/room types | Catalog và inventory nhất quán | PARTIAL |
| BF-09 | Housekeeping và dịch vụ phát sinh | Staff | Management housekeeping/services | Nhiệm vụ và charge được theo dõi | PARTIAL |
| BF-10 | Quản lý role và permission | System admin | Admin roles/permissions | Quyền UI/API nhất quán, least privilege | PARTIAL |
| BF-11 | Gói dịch vụ và subscription billing | Owner/System admin | Plans/subscription billing | Subscription và giới hạn sử dụng chính xác | PARTIAL |
| BF-12 | Dashboard, doanh thu, audit và chat | Admin/Owner/Staff | Dashboard/reports/audit/chat | Số liệu truy xuất được và có audit trail | PARTIAL |

## 3. Chi tiết từng luồng

### BF-01 - Đăng ký, xác minh, đăng nhập và duy trì phiên

**Actor:** Guest, Customer, Admin; Google/Facebook là actor ngoài tùy chọn.<br>
**Tiền điều kiện:** email/username hợp lệ; account active; JWT secret hợp lệ; SMTP/OAuth chỉ cần cho nhánh tương ứng.

Luồng chuẩn:

1. Người dùng đăng ký bằng credential; backend chuẩn hóa định danh và kiểm tra trùng.
2. Backend tạo user/role mặc định, phát token xác minh email và ghi mail outbox nếu bật.
3. Người dùng đăng nhập; backend kiểm tra trạng thái account và cấp access token cùng refresh cookie.
4. Angular lưu access token theo tab, interceptor gắn bearer token và làm mới một lần khi cần.
5. Logout/password change/account disable thu hồi phiên theo policy.

Nhánh lỗi: sai mật khẩu, account suspended, token hết hạn/replay, email trùng, OAuth audience không hợp lệ.<br>
**Bằng chứng:** các route auth, `AuthService`, Spring Security/JWT, refresh token migrations và các capability `AUTH-*` trong inventory.<br>
**Còn thiếu:** SMTP/OAuth sandbox thực chưa được xác minh; Google browser flow phát sinh FedCM/network warning.

### BF-02 - Tìm kiếm khách sạn và xem chi tiết

**Actor:** Guest, Customer.<br>
**Tiền điều kiện:** dữ liệu location/property đã seed/import và property được publish.

1. Người dùng nhập địa điểm, ngày nhận/trả, số khách/phòng.
2. Frontend chuẩn hóa query và gọi public search API.
3. Backend lọc property, availability, capacity và trạng thái publish.
4. Người dùng mở hotel detail, xem ảnh, tiện ích, dịch vụ, loại phòng và giá.
5. Hệ thống hiển thị lựa chọn phù hợp hoặc trạng thái empty/error.

**Bằng chứng thực thi:** `UI-001` đến `UI-004`; public API tại backend E2E trả HTTP 200.<br>
**Rủi ro:** cần E2E regression cho timezone, ranh giới ngày, đồng thời nhiều người đặt và dữ liệu location lớn.

### BF-03 - Kiểm tra phòng trống và tạo booking hold

**Actor:** Customer.<br>
**Tiền điều kiện:** đăng nhập, ngày hợp lệ, inventory còn đủ.

1. Customer chọn loại phòng và số lượng.
2. Backend tái kiểm tra availability thay vì tin dữ liệu UI.
3. Backend tạo hold với TTL (`RESERVATION_HOLD_TTL_MINUTES`, mặc định 15 phút).
4. Checkout nhận snapshot giá/phí/chính sách và thông tin khách.
5. Hold hết hạn được scheduler giải phóng nếu chưa hoàn tất.

**Bằng chứng:** màn chi tiết và checkout đã render; cấu hình hold ở `application.yml`.<br>
**Trạng thái PARTIAL:** chưa có bằng chứng tải đồng thời/oversell và toàn bộ backend regression đang không xanh.

### BF-04 - Checkout và thanh toán

**Actor:** Customer, backend payment orchestration, VNPay/MoMo/ZaloPay hoặc simulator.

1. Customer xác nhận booking và chọn phương thức thanh toán.
2. Backend tạo payment attempt/idempotency context và redirect URL đã ký.
3. Provider xử lý sandbox, redirect browser về payment result và gửi callback/IPN server-to-server.
4. Backend xác minh chữ ký, amount, merchant, trạng thái và chống replay.
5. Transaction/booking được cập nhật nguyên tử; recovery job query lại giao dịch chưa rõ kết quả.

**Callback hiện thấy trong source:**

- `/api/payments/vnpay-callback`, `/api/payments/vnpay-ipn`
- `/api/payments/momo-ipn`
- `/api/payments/zalopay-callback`
- `/api/payment-providers/property/{provider}/callback`
- `/api/payment-providers/platform/{provider}/callback`
- `/api/payment-providers/property/{provider}/refund-callback`
- `/api/payment-providers/platform/{provider}/refund-callback`

**Trạng thái BLOCKED:** simulator không có signed token chỉ hiển thị invalid session đúng thiết kế; VNPay/MoMo/ZaloPay chưa có bằng chứng merchant sandbox và callback public. MoMo còn thiếu contract biến môi trường trong `.env.example`.

### BF-05 - Hủy booking và hoàn tiền

**Actor:** Customer, property staff, system admin, payment provider.

1. Actor yêu cầu hủy; backend tải booking và policy hiện hành.
2. Backend kiểm tra quyền, thời hạn, trạng thái stay và số tiền có thể hoàn.
3. Booking chuyển sang trạng thái hủy phù hợp; refund request dùng idempotency key.
4. Provider trả kết quả đồng bộ hoặc callback; recovery query xử lý trạng thái chưa chắc chắn.
5. Invoice/revenue/audit được điều chỉnh, customer xem lịch sử hoàn tiền.

**Bằng chứng UI:** `UI-012`, `UI-037`; route management refund bị 403 với owner fixture.<br>
**Rủi ro:** test backend còn lỗi ở manual transfer confirmation/property payment configuration; cần xác minh double-refund và partial refund.

### BF-06 - Check-in, lưu trú và check-out

**Actor:** Receptionist/Staff/Manager.

1. Nhân viên tìm booking đã xác nhận và kiểm tra ngày/guest/payment.
2. Gán phòng thực tế, xác nhận check-in và chuyển room/stay status.
3. Trong thời gian lưu trú, dịch vụ và housekeeping phát sinh được ghi nhận.
4. Check-out tổng hợp charge, thanh toán còn lại và cập nhật invoice.
5. Room chuyển qua trạng thái cần dọn, sau đó available khi housekeeping hoàn tất.

**Trạng thái PARTIAL:** source có domain/API liên quan nhưng bộ ảnh chưa bao phủ đầy đủ thao tác check-in/out và test regression không xanh.

### BF-07 - Đăng ký và phê duyệt đối tác/property

**Actor:** Partner/Owner, System admin.

1. Partner đăng ký và gửi thông tin pháp lý/property.
2. Backend lưu hồ sơ ở trạng thái chờ duyệt.
3. Admin xem hàng đợi, approve/reject với lý do và audit.
4. Sau khi duyệt, owner nhận role/property context và mới được cấu hình catalog/publish.

**Bằng chứng UI:** `UI-008`, `UI-030`.<br>
**Rủi ro:** frontend test còn lỗi tại partner approval action; cần E2E cho reject/resubmit và giới hạn tenant.

### BF-08 - Quản lý loại phòng, phòng, giá và tồn

**Actor:** Property owner/manager/staff; system admin trong phạm vi hỗ trợ.

1. Actor chọn property trong tenant context.
2. Tạo/cập nhật loại phòng, sức chứa, tiện ích, ảnh và chính sách.
3. Tạo phòng vật lý và trạng thái vận hành.
4. Cấu hình giá/tồn theo ngày; backend kiểm tra overlap và quyền property.
5. Public search chỉ nhận catalog đã publish và còn inventory.

**Bằng chứng UI:** `UI-018`, `UI-019`, `UI-032`, `UI-033`.<br>
**Rủi ro:** cần kiểm thử isolation giữa property, bulk update, concurrent booking và dữ liệu giá lịch sử.

### BF-09 - Housekeeping và dịch vụ phát sinh

**Actor:** Housekeeping staff, reception, manager.

1. Check-out hoặc yêu cầu nội bộ tạo housekeeping task.
2. Nhân viên nhận việc, cập nhật tiến độ và kết quả.
3. Dịch vụ được đặt cho booking/stay, áp giá và ghi charge.
4. Hoàn tất housekeeping cập nhật room status; charge đi vào invoice.

**Bằng chứng UI:** `UI-021`, `UI-034`, `UI-035`.<br>
**Trạng thái PARTIAL:** cần E2E cho assignment race, cancellation, offline retry và invoice reconciliation.

### BF-10 - Quản lý role và permission

**Actor:** System admin.

1. Admin tạo/sửa role và tập permission.
2. Gán role cho user trong scope hợp lệ.
3. Frontend guard/menu chỉ hiển thị route được phép.
4. Backend vẫn kiểm tra permission/tenant cho mọi request.
5. Thay đổi quyền được audit và có hiệu lực theo session policy.

**Bằng chứng UI:** `UI-023`, `UI-024`.<br>
**Trạng thái PARTIAL:** bốn route management bị 403 với owner fixture, cần đối chiếu menu/guard/API authority và test matrix role x route x endpoint.

### BF-11 - Gói dịch vụ và subscription billing

**Actor:** System admin, property owner.

1. Admin quản lý plan, giới hạn và giá.
2. Owner chọn/nâng/hạ gói; backend kiểm tra chu kỳ và hiệu lực.
3. Usage được đo và enforcement theo property/subscription.
4. Billing/payment tạo invoice và cập nhật subscription sau xác nhận.

**Bằng chứng UI:** `UI-025`, `UI-039`.<br>
**Trạng thái PARTIAL:** route subscription billing trả 403 cho owner fixture; frontend test còn lỗi assertion về subscription policy text.

### BF-12 - Dashboard, doanh thu, audit và chat

**Actor:** System admin, owner, manager, staff.

1. Dashboard tải KPI theo time range và tenant scope.
2. Báo cáo doanh thu tổng hợp booking/payment/refund/invoice.
3. Audit log ghi actor, action, target, thời gian và correlation context.
4. Chat/notification dùng WebSocket với xác thực và origin allowlist.

**Bằng chứng UI:** `UI-015`, `UI-027` đến `UI-029`, `UI-031`, `UI-038`, `UI-040`, `UI-041`.<br>
**Rủi ro:** backend test có lỗi financial performance; frontend có lỗi chat setup và thiếu mock `listSocialIdentities()`; management audit log trả 403.

## 4. Business rule xuyên luồng

### P0-02G - Read access và subscription growth gate

- `GET /api/v1/hotels/my-hotels` là read-only principal-owned discovery: authenticated user nhận 200 kể cả khi chưa có growth quota.
- P0-02H giữ nguyên security flow: `/api/auth/email-verification/confirm` và các discovery/spotlight/promotion/quote route dưới `/api/public/**` là public có chủ đích; `/api/v1/hotels/my-hotels` đi qua authenticated fallback và chỉ truy vấn theo principal `userId`. Architecture allowlist chỉ được mở theo từng method, không theo controller/package.
- Subscription feature không thay thế role/property authorization. Mutation probe đúng role nhưng thiếu `MAX_PROPERTIES` trả 403; có feature nhưng sai role vẫn trả 403.
- `MAX_PROPERTIES` chỉ áp dụng khi tạo/claim thêm cơ sở; property-scoped room/type/image mutation dùng entitlement của target property theo T198.
- Luồng này giữ fail-closed cho mutation trả phí nhưng không khóa read/recovery access khi subscription hết hoặc thiếu feature.

| Rule | Yêu cầu |
|---|---|
| BR-01 | Mọi booking/payment/refund transition phải do backend xác nhận và có audit |
| BR-02 | Không cho oversell; availability phải được kiểm tra lại trong transaction/locking strategy |
| BR-03 | Callback payment phải xác minh chữ ký, merchant, amount, currency, replay và idempotency |
| BR-04 | Tenant/property scope phải được kiểm tra ở API và query, không chỉ route guard |
| BR-05 | Secret chỉ đi qua secret store/environment; frontend ID công khai không được nhầm với secret |
| BR-06 | Trạng thái phòng phải phù hợp với booking/stay/housekeeping lifecycle |
| BR-07 | Refund không được vượt số tiền đã thu trừ phần đã hoàn trước đó |
| BR-08 | Báo cáo tài chính phải truy xuất được về transaction/invoice/refund nguồn |

## 5. Acceptance gate cho demo

1. Given một customer fixture hợp lệ, when tìm kiếm đến checkout, then luồng chạy lại ổn định không có console/API error chặn thao tác.
2. Given từng role demo, when mở menu đã công bố, then route và API tương ứng trả đúng quyền, không có 403 ngoài dự kiến.
3. Given payment simulator có token hợp lệ, when hoàn tất success/fail/cancel, then booking/payment hiển thị trạng thái nhất quán và idempotent.
4. Given test environment sạch, when chạy toàn bộ frontend/backend test, then không có failure/error/unhandled exception thuộc phạm vi demo.
5. Given cấu hình sandbox provider, when callback qua URL HTTPS public, then chữ ký được xác minh và audit/recovery hoạt động.
