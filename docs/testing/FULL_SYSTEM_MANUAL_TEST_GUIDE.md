# LuxeStay - Full-System Manual Test Guide

**Feature:** `007-payment-billing-completion`

**Task:** `T155`

**Audience:** người kiểm thử không cần biết lập trình

**Nguồn hành trình:** [Feature 007 quickstart](../../specs/007-payment-billing-completion/quickstart.md), mục 9
**Ngày soạn:** 2026-08-03

> **Trạng thái guide: `PARTIAL`.** Đây là hướng dẫn thao tác và tiêu chí kiểm tra, không phải bằng chứng rằng toàn bộ hệ thống đã chạy đạt. T151/T152 và các cổng final T158-T163 còn mở. Các chỗ chưa có chạy trên worktree cuối, chưa có database thật, hoặc chưa có ảnh được ghi rõ là `PARTIAL`, `BLOCKED_EXTERNAL` hoặc `GAP`.

## 1. Quy tắc an toàn

1. Chỉ dùng database cô lập và tài khoản test. Không dùng production database, merchant thật, tài khoản ngân hàng thật, thẻ thật, QR thật hay tiền thật.
2. Trước khi chạy, xác nhận `PAYMENT_PRODUCTION_ENABLED=false` và `PAYMENT_PRODUCTION_APPROVED=false`. Chọn `SIMULATOR`; chỉ dùng `SANDBOX` khi đã có credential test được phê duyệt. Không dùng fallback tự động sang môi trường khác.
3. Không chụp token, mật khẩu, chữ ký callback, secret, số tài khoản, email/số điện thoại khách thật hoặc request body chứa thông tin nhạy cảm.
4. Khi cần đối chiếu HTTP, chỉ lưu: method, path, status, `code`, `retryable`, `currentState`, `correlationId` và có/không có `Idempotency-Key`. Không lưu giá trị JWT, `X-Payment-Signature` hoặc secret.
5. Mọi giá tiền là số nguyên VND. Không nhập dấu chấm/phẩy vào API; trên UI có thể nhập `300000` và kiểm tra màn hình hiển thị `300.000 VND`.

### Vai trò sử dụng

| Vai trò | Dùng trong guide | Không được làm |
|---|---|---|
| Guest | Tìm kiếm, xem chi tiết, chọn phòng | Không tạo payment attempt nếu chưa có booking hợp lệ |
| Customer | Đặt phòng, xem deposit, hủy booking, yêu cầu refund, xem hóa đơn của mình | Không xác nhận chuyển khoản manual, không xem property khác |
| Property Owner/Hotel Manager/Receptionist | Cấu hình thanh toán, check-in, charge, checkout, xem báo cáo của property | Không xem hoặc sửa property khác |
| Admin/Super Admin | Duyệt property, phê duyệt refund; Super Admin xem Platform Billing/report system | Không dùng quyền admin để giả lập merchant production |
| Housekeeping | Chỉ kiểm tra task/phòng sau checkout nếu route đã được cấp | Không tự thay đổi folio/invoice/payment |

### Bộ dữ liệu thao tác

Dùng một `RUN_TAG` mới cho mỗi lần, ví dụ `20260803-01`.

| Dữ liệu | Giá trị mẫu an toàn |
|---|---|
| Owner mới | Họ tên `Manual 007 Owner`; email/username `manual007-owner-20260803-01@example.test`; mật khẩu `Manual007!2026`; điện thoại `0900000001` |
| Property mới | Tên `Manual 007 Hotel 20260803-01`; địa chỉ `1 Test Street, Ho Chi Minh City` |
| Customer | Dùng credential trong `LUXESTAY_E2E_CUSTOMER_USERNAME/PASSWORD`; nếu tạo mới, dùng email có cùng `RUN_TAG` và mật khẩu test riêng |
| Admin | Dùng credential trong `LUXESTAY_E2E_ADMIN_USERNAME/PASSWORD`; không ghi giá trị vào evidence |
| Owner fixture | Dùng `LUXESTAY_E2E_OWNER_USERNAME/PASSWORD`; property id phải lấy từ API/UI của lần chạy, không đoán id |
| Ngày tìm phòng | Check-in `2026-08-10`, check-out `2026-08-12`, người lớn `2`, trẻ em `0`, phòng `1` |
| Refund mẫu A | Giao dịch thành công VND `1.000.000`; yêu cầu một phần `300.000`; lý do `Partial guest cancellation - RUN_TAG` |
| Refund mẫu B | Giao dịch thành công riêng VND `1.000.000`; yêu cầu toàn phần `1.000.000`; lý do `Full guest cancellation - RUN_TAG` |
| Payment simulator | Provider/method `SIMULATOR`; callback dùng secret test ở biến môi trường, không dán secret vào guide/evidence |

Nếu fixture không có các số trên, giữ nguyên quy tắc: **amount, room id, reservation id, plan id và expiry phải lấy từ response server**, không tự sửa cho khớp ví dụ.

### Cách ghi kết quả

Mỗi journey cần ghi `PASS`, `FAIL`, `BLOCKED_EXTERNAL`, `PARTIAL` hoặc `GAP`, kèm thời điểm, role, `RUN_TAG`, property/reservation/order/attempt/refund/invoice id và đường dẫn ảnh. `EXPECTED` trong guide chỉ là tiêu chí; không được ghi thành `OBSERVED` nếu chưa có response/log/database/screenshot tương ứng.

## 2. Chuẩn bị và thu bằng chứng

1. Khởi động frontend local và backend test profile trên port đã được dự án cấu hình (thường `http://localhost:4200` và API `http://localhost:8080/api`).
2. Tạo database test mới hoặc tên database có `RUN_TAG`. Không chạy trên database đang chứa dữ liệu người dùng.
3. Đăng nhập đúng vai trò, mở DevTools → **Network**. Đánh dấu request tương ứng với mỗi nút; chụp phần UI sau khi request hoàn tất và phần response đã che dữ liệu nhạy cảm.
4. Với database, dùng tài khoản read-only và các câu truy vấn ở từng journey. Chỉ đối chiếu số dòng, state, amount, khóa liên kết; không `UPDATE`/`DELETE` để “làm cho đúng”.
5. Với audit, kiểm tra màn hình `/admin/audit-log` hoặc `/management/audit-log` nếu actor có quyền. Financial audit hiện có bảng append-only nhưng chưa có viewer chuyên dụng; khi không xem được UI, ghi `NOT_OBSERVED` và dùng query read-only nếu được cấp quyền.

## 3. Journey 1 - Owner đăng ký, duyệt property, mua gói và kích hoạt

**Vai trò:** người đăng ký (Owner), Admin duyệt property, Owner mua gói, hệ thống/provider simulator callback.

**Status hiện tại:** `PARTIAL`/`BLOCKED_EXTERNAL`.

**Evidence đã có:** `T109` chỉ mới list Playwright và ghi rõ skip nếu thiếu biến simulator; `T103` callback H2 replay/concurrency đạt 2/2; `T110` negative suite được phát hiện nhưng skip nếu thiếu biến. Xem [platform subscription purchase](../testing/evidence/007/platform-billing/platform-subscription-purchase.md), [platform negative](../testing/evidence/007/platform-billing/platform-subscription-negative.md), [platform callback concurrency](../testing/evidence/007/platform-billing/platform-callback-concurrency.md). Chưa có final-worktree manual screenshot.

### Input và thao tác

1. Owner mở `/partner/register`, nhập đúng bộ Owner/Property ở mục 1, bấm **Đăng ký**.
2. Owner đăng nhập, mở danh sách property của mình. Ghi `propertyId` được server trả về; trạng thái ban đầu phải là `PENDING_APPROVAL`.
3. Admin mở `/admin/property-approvals`, tìm đúng property theo `RUN_TAG`, bấm **Duyệt cơ sở**. Không duyệt nhầm property khác.
4. Owner mở `/management/billing?propertyId={propertyId}`. Chờ catalog và entitlement tải xong; chọn plan đang `ACTIVE` đầu tiên, ghi `planId`, `price`, `planVersion`, `duration` và feature snapshot hiển thị.
5. Bấm **Create purchase order**. Ghi `order.publicId`, `orderCode`, expiry và snapshot backend.
6. Chọn provider/method `SIMULATOR`, bấm **Create payment attempt**. Ghi `attempt.publicId`, expected amount, provider reference, environment và expiry.
7. Gửi callback simulator hợp lệ bằng harness/provider test đã được phê duyệt. Không tự gõ chữ ký. Bấm **Refresh server status**.

### Kỳ vọng UI / HTTP / database / state / audit

| Điểm kiểm | Kỳ vọng |
|---|---|
| Đăng ký | UI chuyển về login hoặc báo đăng ký thành công; HTTP `POST /api/partner/register` trả thành công; property mới ở `PENDING_APPROVAL`. Nếu duplicate email/username, UI giữ form và báo lỗi 409; không tạo bản ghi thứ hai. |
| Duyệt | Admin thấy property trong `/admin/property-approvals`; HTTP `POST /api/v1/hotels/{propertyId}/approve`; property chuyển `APPROVED` và `ACTIVE` theo policy. UI không hiển thị nút duyệt lại khi đã terminal. |
| Catalog/order | UI hiển thị giá và thời hạn từ backend. HTTP `GET /api/platform/subscription-plans`; tạo order bằng `POST /api/platform/subscription-orders` body chỉ gồm `{targetHotelId, planId}` và có `Idempotency-Key`; client không gửi `price`, `duration`, `features` hoặc merchant secret. DB `platform_subscription_orders` lưu snapshot bất biến, `status=CREATED` hoặc `PENDING_PAYMENT`, `currency=VND`. |
| Attempt | HTTP `POST /api/platform/subscription-orders/{orderPublicId}/payment-attempts` body `{provider:"SIMULATOR",method:"SIMULATOR"}`; UI hiển thị `SIMULATOR`, expected amount, masked merchant/reference, expiry. DB `platform_payment_attempts` liên kết đúng order, không có amount do client quyết định. |
| Callback thành công | HTTP `POST /api/payment-providers/platform/SIMULATOR/callback` được provider xác thực; UI sau refresh hiển thị server activation, không có nút “activate” phía client. DB đúng một row `platform_financial_transactions` (`SUBSCRIPTION_PURCHASE`), order `APPLIED`, một `platform_software_contracts`, một entitlement active và một history `PURCHASED`. |
| Audit | Financial audit append-only có context `PLATFORM_BILLING`, aggregate order/attempt, previous/new state, provider/idempotency identity, correlation id; metadata không chứa signature/secret. Property approval/lifecycle audit thuộc `operational_audit_events` nếu path đã nối audit. |

**Query read-only sau khi callback:**

```sql
SELECT public_id, order_code, operation, target_hotel_id, plan_id, plan_version,
       price, currency, status, expires_at, applied_at
FROM dbo.platform_subscription_orders
WHERE public_id = '<ORDER_PUBLIC_ID>';

SELECT public_id, order_id, provider, environment, expected_amount, currency,
       status, provider_order_ref, provider_transaction_ref
FROM dbo.platform_payment_attempts
WHERE order_id = (SELECT id FROM dbo.platform_subscription_orders WHERE public_id = '<ORDER_PUBLIC_ID>');

SELECT COUNT_BIG(*) AS transaction_count
FROM dbo.platform_financial_transactions
WHERE order_id = (SELECT id FROM dbo.platform_subscription_orders WHERE public_id = '<ORDER_PUBLIC_ID>');

SELECT target_hotel_id, action_type, COUNT_BIG(*) AS history_count
FROM dbo.platform_subscription_histories
WHERE target_hotel_id = <PROPERTY_ID>
GROUP BY target_hotel_id, action_type;
```

### Negative, permission, retry và timeout

| Case | Expected result |
|---|---|
| Admin không có quyền duyệt hoặc Owner mở property người khác | `403`/`404` theo boundary; không đổi property, không lộ trạng thái tenant. |
| Client sửa giá/feature/duration trong request | Server bỏ qua hoặc từ chối; order snapshot vẫn lấy từ catalog. |
| Callback sai amount/merchant/reference/signature | `400` (`CALLBACK_AMOUNT_MISMATCH`, `CALLBACK_MERCHANT_MISMATCH`, `CALLBACK_REFERENCE_MISMATCH`) hoặc `401` (`CALLBACK_SIGNATURE_INVALID`); order/attempt/ledger/entitlement không đổi; callback audit chỉ ghi metadata đã redact. |
| Gửi cùng callback hai lần hoặc đồng thời | Lần đầu `replayed=false`, lần sau `replayed=true`; đúng một ledger/contract/entitlement/history effect. |
| Hủy order chưa thanh toán rồi gửi callback muộn | Order `CANCELLED`, callback `409 INVALID_STATE_TRANSITION`, không kích hoạt. |
| Hết hạn | Với profile expiry 1 phút, attempt/order trả `409 ATTEMPT_EXPIRED`; tạo intent mới bằng key mới, không hồi sinh order cũ. Hiện browser expiry chưa có final run (`GAP`). |
| Provider/HTTP timeout | `503 PROVIDER_UNAVAILABLE`, `retryable=true`, không đánh dấu thành công. Retry cùng idempotency key cho cùng intent sau khi readiness trở lại; không bấm tạo order mới mù quáng. |
| Rollback | Lỗi giữa đăng ký-property-owner mapping phải không để orphan owner/property; lỗi giữa platform ledger, order, contract, entitlement và history phải rollback toàn effect. Full SQL Server application rollback/final browser proof hiện là `GAP`; không kết luận đạt từ một UI error đơn lẻ. |

### Screenshot

`GAP`: chưa có ảnh current cho đăng ký partner, danh sách duyệt property, billing catalog, order snapshot, simulator attempt hoặc applied entitlement. Không dùng các ảnh admin `*-current-blocked.png` làm bằng chứng thành công.

## 4. Journey 2 - Customer tìm kiếm, đặt phòng, trả deposit và thấy property

**Vai trò:** Guest/Customer, property owner hoặc provider simulator.

**Status hiện tại:** `PARTIAL`.

**Evidence đã có:** T068/T069 browser contract đạt (API bị intercept), T062 backend callback replay/concurrency đạt 7/7; xem [property payment API](../testing/evidence/007/property-commerce/property-payment-api.md) và [property callback concurrency](../testing/evidence/007/property-commerce/property-callback-concurrency.md). Inventory ghi browser real API là `PARTIAL/GAP`.

### Input và thao tác

1. Guest mở `/search`, nhập check-in `2026-08-10`, check-out `2026-08-12`, adults `2`, children `0`, rooms `1`, chọn property đang `APPROVED`/`ACTIVE`.
2. Mở chi tiết `/hotel/{propertyId}`, chọn room type còn bán và bấm đặt. Nếu app yêu cầu đăng nhập, Customer đăng nhập trước.
3. Ở `/booking/{roomTypeId}`, nhập họ `Nguyen`, tên `An`, điện thoại `0900000000`, chọn phương thức đã được property cấu hình (ví dụ `MOMO`/`MANUAL_TRANSFER`). Không nhập tổng tiền vào request.
4. Ghi `reservationId` từ response. Trong payment panel, ghi expected amount, currency, environment, receiver đã mask, unique transfer content, expiry và trạng thái ban đầu.
5. Gửi callback simulator hợp lệ hoặc chờ staff xác nhận manual ở màn hình management. Sau đó refresh/poll status.

### Kỳ vọng UI / HTTP / database / state / audit

| Điểm kiểm | Kỳ vọng |
|---|---|
| Search/visibility | UI chỉ liệt kê property/room type có thể bán; không hiển thị property pending/suspended hoặc hết capacity. Các ảnh search hiện có là historical candidate, không phải final proof. |
| Booking | HTTP `POST /api/reservations/book` dùng availability/price server; body không có `totalAmount` hoặc `expectedAmount` do client tự quyết. `201` tạo một reservation và hold theo policy. |
| Deposit attempt | HTTP `POST /api/reservations/{reservationId}/payment-attempts` body chỉ chọn `{purpose:"DEPOSIT",method:"..."}`; response hiển thị amount server-owned, `VND`, expiry và môi trường `SIMULATOR`/`SANDBOX`. DB có một `property_payment_attempts`, `status=PENDING_VERIFICATION`/`PENDING`, idempotency key và snapshot receiver. |
| Callback/confirmation | Callback `POST /api/payment-providers/property/{provider}/callback` hoặc manual confirmation `POST /api/management/payment-attempts/{attemptId}/confirm-manual` (quyền `PROPERTY_PAYMENT_CONFIRM_MANUAL`). Không cho Customer tự confirm manual. |
| Thành công | Attempt chuyển `SUCCESS`; booking financial summary chuyển theo số tiền (`DEPOSIT_PAID`, `PARTIALLY_PAID` hoặc `PAID`); đúng một `property_financial_transactions` debit; UI hiển thị thành công và giữ nguyên property/amount/reference. |
| Audit | Financial audit có callback/confirmation state change, actor/provider/idempotency/correlation; không ghi raw receiver, signature hay secret. |

**Query read-only:**

```sql
SELECT public_id, hotel_id, reservation_id, purpose, method, provider, environment,
       expected_amount, currency, unique_transfer_content, status, expires_at
FROM dbo.property_payment_attempts
WHERE reservation_id = <RESERVATION_ID>
ORDER BY id DESC;

SELECT public_id, hotel_id, reservation_id, transaction_type, direction,
       amount, currency, provider, environment, idempotency_identity
FROM dbo.property_financial_transactions
WHERE reservation_id = <RESERVATION_ID>
ORDER BY id;

SELECT reservation_id, hotel_id, gross_charges, deposit_required,
       successful_payments, successful_refunds, remaining_balance, financial_state
FROM dbo.booking_financial_summaries
WHERE reservation_id = <RESERVATION_ID>;
```

### Negative, permission, retry và timeout

- Ngày check-out bằng ngày check-in, room capacity không đủ hoặc property không active: UI không cho submit hoặc HTTP `409`; không tạo payment attempt.
- Thay query `nightlyPrice=1`/`estimatedTotal=1`: amount panel vẫn là amount server; booking body không mang amount authoritative.
- Callback sai signature: `401 CALLBACK_SIGNATURE_INVALID`; attempt/ledger không đổi.
- Đọc attempt của customer/property khác: `404 RESOURCE_NOT_FOUND` (không tiết lộ receiver/amount).
- Attempt hết hạn: UI hiển thị `EXPIRED`; nút retry tạo attempt mới với key mới nhưng không tạo reservation thứ hai.
- Hai callback tương đương đồng thời: đúng một effect, một response replay; nếu provider timeout/unavailable thì `503 PROVIDER_UNAVAILABLE`, retryable và không giả định đã trả tiền.
- `Idempotency-Key` đã dùng cho payload khác: `409 IDEMPOTENCY_KEY_REUSED`; không overwrite row cũ.
- Rollback: nếu booking đã commit nhưng attempt creation lỗi, booking/hold giữ trạng thái recoverable, không có attempt/ledger nửa chừng và retry phải dùng lại reservation; nếu callback transaction lỗi, attempt/ledger/audit cùng transaction không được commit một phần. Final real-browser + SQL rollback cho toàn journey vẫn là `GAP`.

### Screenshot

- Có candidate historical: `docs/screenshots/home-search-after-desktop.png`, `home-search-after-mobile.png`, `search-result-after.png`, `room-selection-after.png`.
- `GAP`: chưa có current screenshot của booking form, payment panel với amount/expiry/environment, callback success/replay và property visibility sau payment. Các candidate phải chụp lại nếu route/data thay đổi.

## 5. Journey 3 - Check-in, charge, nhiều payment, checkout, invoice và housekeeping

**Vai trò:** Receptionist/Hotel Manager/Admin thực hiện; Customer chỉ xem invoice; Housekeeping kiểm tra phòng/task.

**Status hiện tại:** `PARTIAL` cho UI, backend rollback/concurrency đã có evidence riêng.

**Evidence đã có:** `stay-checkout-invoice.spec.ts` đạt với API interception; T192 tổng 18/18 và T211 SQL Server 2/2 cho locking/rollback; inventory STAY-028 vẫn `PARTIAL` vì browser dùng intercepted APIs. Xem [T192](../testing/evidence/007/remediation/T192-stay-lifecycle-sqlserver.md) và [T211](../testing/evidence/007/remediation/T211-atomic-checkout-sqlserver.md).

**Manual UI blockers:** checkout workspace hiện không có control người dùng đã được xác minh để tạo nhiều balance payment attempt/xác nhận manual; T091 gọi các API này bằng browser script. Housekeeping queue/list/claim/start UI cũng đang `MISSING` trong inventory. Vì vậy phần nhiều payment và housekeeping của journey này là `GAP` cho người không kỹ thuật cho tới khi có UI hoặc test operator hỗ trợ bằng harness được phê duyệt.

### Input và thao tác

1. Chọn reservation `CONFIRMED` trong `/admin/reservations`, có room đã assignment. Ghi `reservationId`, `roomId`, `roomNumber`; không dùng id giả nếu fixture khác.
2. Bấm **Check-in**. Source client gọi `POST /api/reservations/{id}/check-in`; test harness T091 hiện intercept `PUT /api/reservations/{id}/status?status=CHECKED_IN`, đây là khác biệt cần ghi `GAP` nếu gặp.
3. Mở folio/checkout workspace. Chọn service server-owned, ví dụ service `BREAKFAST` (fixture T091 dùng `serviceId=81`), quantity `1`; không nhập unit price.
4. Thêm surcharge dương, ví dụ type `LATE_CHECK_OUT`, amount `100000`, mô tả `Late checkout - RUN_TAG`. Negative adjustment phải dùng quyền riêng và lý do rõ ràng.
5. Gọi **Checkout preview**. Nếu còn nợ, test operator tạo hai balance attempts tuần tự bằng amount do server trả về và xác nhận manual có reason/evidence; ví dụ fixture T091 dùng khoản còn lại `400000` rồi `350000`, nhưng chỉ dùng các số này nếu preview thật khớp. Nếu không có harness/UI được phê duyệt, ghi `GAP`, không dùng SQL để tự chèn payment.
6. Refresh preview đến `SETTLED`, bấm **Chốt checkout & hóa đơn**.
7. Customer mở `/my-invoices`, mở invoice vừa tạo. Staff/Housekeeping chỉ mở room/task view nếu deployment đã có route queue hợp lệ; nếu không, ghi `GAP` và đối chiếu read-only database cho `DIRTY`/một task.

### Kỳ vọng UI / HTTP / database / state / audit

| Điểm kiểm | Kỳ vọng |
|---|---|
| Check-in | UI status `Đang lưu trú`/`CHECKED_IN`, room `OCCUPIED`; HTTP endpoint có permission `CHECKIN:UPDATE`; không check-in room khác property. |
| Service/surcharge | `POST /api/management/reservations/{id}/charges/services` body `{serviceId,chargeType,quantity}`; server trả giá và snapshot. Surcharge dùng `/charges/surcharges`, negative adjustment cần permission riêng. DB append-only `reservation_charge_lines`, không sửa dòng cũ. |
| Preview | `POST /api/management/reservations/{id}/checkout-preview` chỉ đọc; UI hiển thị room/service/surcharge/tax/fee/payment/refund/balance và `SETTLED`/`OUTSTANDING`/`OVERPAID`. |
| Underpaid | HTTP `409 OUTSTANDING_BALANCE`; UI khóa checkout; không tạo invoice, allocation, dirty room hoặc housekeeping task. |
| Successful checkout | `POST /api/management/reservations/{id}/checkout` chỉ gửi override id nếu có, không gửi total. Response có `invoiceId/invoiceNumber`, `CHECKED_OUT`, financial summary `SETTLED`. DB có một `FINALIZED` invoice, invoice lines, allocation mỗi transaction một lần, reservation `CHECKED_OUT`, room `DIRTY`, đúng một housekeeping task với `checkout_effect_key`. |
| Customer invoice | `/my-invoices` và `GET /api/invoices/{invoiceId}` chỉ cho owner/property role; invoice header/lines/allocations immutable; PDF/email chỉ dùng snapshot đã finalized. |
| Audit | Charge/surcharge/manual confirmation/checkout có financial audit state + correlation. Reservation/room/housekeeping lifecycle có operational audit `TENANT`, before/after, actor, reason. |

**Query read-only:**

```sql
SELECT id, status, hotel_id, room_id FROM dbo.reservations WHERE id = <RESERVATION_ID>;
SELECT id, hotel_id, reservation_id, charge_type, code, name, quantity, unit_price, total_amount
FROM dbo.reservation_charge_lines WHERE reservation_id = <RESERVATION_ID> ORDER BY id;
SELECT id, reservation_id, invoice_number, status, total_amount, paid_amount, refunded_amount, balance_amount
FROM dbo.property_invoices WHERE reservation_id = <RESERVATION_ID>;
SELECT invoice_id, transaction_id, allocated_amount
FROM dbo.property_invoice_payment_allocations
WHERE invoice_id = (SELECT id FROM dbo.property_invoices WHERE reservation_id = <RESERVATION_ID> AND status = 'FINALIZED');
SELECT id AS room_id, status, housekeeping_status FROM dbo.rooms WHERE id = <ROOM_ID>;
SELECT id, hotel_id, room_id, reservation_id, status, checkout_effect_key
FROM dbo.housekeeping_tasks WHERE reservation_id = <RESERVATION_ID>;
```

### Negative, permission, retry và timeout

- Role thiếu `CHECKIN`, `RESERVATION_SERVICE_ADD`, `RESERVATION_SURCHARGE_ADD`, `RESERVATION_CHECKOUT` hoặc `RESERVATION_DEBT_OVERRIDE`: `403`; không có business write.
- Service quantity `0`, amount âm, VND phân số hoặc surcharge thiếu lý do: `400 VALIDATION_FAILED`/`INVALID_AMOUNT`; giữ form; không thêm charge line.
- Checkout underpaid/overpaid: `409 OUTSTANDING_BALANCE` hoặc `OVERPAYMENT_REQUIRES_RESOLUTION`; không bấm retry mù.
- Hai checkout đồng thời hoặc refresh/retry sau mất response: cùng reservation phải cho một kết quả authoritative, không duplicate invoice/allocation/housekeeping effect; giữ cùng idempotency identity.
- Fault injection ở invoice/room/housekeeping/final reservation boundary là test harness/database case, không tự tạo trên database dùng chung. Kỳ vọng rollback toàn bộ và reservation vẫn `CHECKED_IN`; T211 chứng minh backend SQL Server, còn browser final evidence là `GAP`.
- HTTP/provider timeout khi thu balance: không chuyển `SUCCESS` nếu chưa có verification; poll/retry attempt cùng intent, không tạo khoản thu thứ hai.

### Screenshot

`GAP`: chưa có current screenshot cho check-in, folio preview underpaid/settled, invoice detail/PDF, room `DIRTY` và housekeeping task. `docs/screenshots/admin-rooms-after.png` là historical candidate; `admin-rooms-current-blocked.png` chỉ là blocked error, không phải bằng chứng đạt.

## 6. Journey 4 - Hủy booking và hoàn tiền toàn phần/một phần

**Vai trò:** Customer yêu cầu/hủy; Property role hoặc Admin phê duyệt theo policy; simulator/provider trả kết quả.

**Status hiện tại:** `PARTIAL`.

**Evidence đã có:** T124 Playwright refund lifecycle 3/3 (intercepted browser), T119 backend concurrency 3/3. Xem [refund Playwright](../testing/evidence/007/refunds/refund-playwright.md) và [refund concurrency](../testing/evidence/007/refunds/refund-concurrency-integration.md).

### Input và thao tác

1. Customer mở `/profile?tab=bookings`, chọn một booking `CONFIRMED` có payment `SUCCEEDED`, bấm **Cancel booking**, xác nhận dialog. Ghi `transactionId`/reservation id từ UI hoặc response.
2. Với fixture A, mở `/refunds?transactionId={transactionIdA}`, nhập amount `300000`, reason `Partial guest cancellation - RUN_TAG`, bấm submit.
3. Property role/Admin mở `/admin/refunds`, kiểm tra request, approve nếu policy yêu cầu. Không approve refund thuộc property khác.
4. Theo dõi status qua UI hoặc `GET /api/property-refunds/{refundId}`. Nếu có provider attempt, dùng simulator callback hợp lệ sau khi attempt được tạo.
5. Với fixture B độc lập, yêu cầu full refund amount `1000000`, reason `Full guest cancellation - RUN_TAG`; theo dõi tới `REFUNDED` và remaining `0`.
6. Lặp lại cùng request/idempotency key; sau đó thử amount vượt remaining balance và hai callback đồng thời.

### Kỳ vọng UI / HTTP / database / state / audit

| Điểm kiểm | Kỳ vọng |
|---|---|
| Hủy | UI booking chuyển `CANCELLED`, hiển thị refund request đang chờ provider; HTTP cancellation tạo đúng request theo policy, không đánh dấu refund thành công trước callback. |
| Partial request | `POST /api/property-payments/{transactionId}/refunds` body `{amount:300000,reason:"..."}` + `Idempotency-Key`; UI `REQUESTED`/`PENDING_APPROVAL`. DB `property_refund_requests` lưu original transaction, amount, reason, actor, remaining snapshot. |
| Approval/provider | `POST /api/property-refunds/{refundId}/approve` nếu cần; provider attempt/callback giữ `PENDING_PROVIDER`/`SUCCEEDED`/`FAILED`. Thành công tạo một CREDIT transaction liên kết `original_transaction_id`; debit gốc không đổi. |
| Reconciliation | Booking summary chuyển `PARTIALLY_REFUNDED` hoặc `REFUNDED`; tổng credit thành công không vượt debit gốc. UI hiển thị remaining refundable amount. |
| Audit | Financial audit ghi request/approval/provider success/failure/replay, context `PROPERTY_COMMERCE`, correlation/idempotency/provider identity; không ghi raw signature/secret. |

**Query read-only:**

```sql
SELECT public_id, hotel_id, original_transaction_id, requested_amount,
       approved_amount, succeeded_amount, status, idempotency_key
FROM dbo.property_refund_requests
WHERE public_id = '<REFUND_PUBLIC_ID>';

SELECT id, refund_request_id, attempt_number, provider, environment,
       provider_reference, status, failure_code, retryable
FROM dbo.property_refund_attempts
WHERE refund_request_id = (SELECT id FROM dbo.property_refund_requests WHERE public_id = '<REFUND_PUBLIC_ID>');

SELECT original_transaction_id, SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE 0 END) AS refunded
FROM dbo.property_financial_transactions
WHERE original_transaction_id = <ORIGINAL_TRANSACTION_ID>
GROUP BY original_transaction_id;
```

### Negative, permission, retry và timeout

- Customer khác hoặc property khác đọc/request refund: `404 RESOURCE_NOT_FOUND` hoặc `403`; không lộ amount/receiver và không có write.
- Amount `0`, âm, phân số hoặc lớn hơn remaining: `400 INVALID_AMOUNT` hoặc `409 REFUND_EXCEEDS_BALANCE`; không tạo request/ledger.
- Fake/invalid callback signature: `401 CALLBACK_SIGNATURE_INVALID`, không CREDIT.
- Provider failure: UI `FAILED`, không false success; tạo attempt retry mới chỉ khi `retryable=true`, giữ cùng refund intent.
- Cùng idempotency key/cùng payload: trả kết quả cũ với `replayed=true`; key cũ khác payload: `409 IDEMPOTENCY_KEY_REUSED`.
- Hai partial requests/callbacks đồng thời: khóa original transaction; một request được phép, request vượt balance bị `REFUND_EXCEEDS_BALANCE`; chỉ một effect cho event tương đương.
- Provider timeout là `BLOCKED_EXTERNAL` nếu không có sandbox test. Không kết luận refund thành công chỉ vì UI mất kết nối.
- Rollback: lỗi sau khi bắt đầu xử lý refund phải không để CREDIT, refund `SUCCEEDED`, booking summary và audit transition lệch nhau; original debit luôn bất biến. Final SQL Server refund rollback matrix hiện là `GAP`.

### Screenshot

Có file candidate `docs/screenshots/payment-refund-customer.png` và `docs/screenshots/payment-refund-admin.png`. Manifest hiện chưa phân loại hai file này là current/final cho T155; cần ghi lại role, route, `RUN_TAG`, thời điểm và privacy check trước khi dùng làm evidence. Ảnh full refund, excessive amount, invalid callback và concurrent replay còn `GAP`.

## 7. Journey 5 - Gia hạn/nâng cấp subscription và báo cáo platform

**Vai trò:** Owner thực hiện renewal/upgrade; Super Admin xem Platform Billing report; Property Owner xem property report.

**Status hiện tại:** `PARTIAL`/`BLOCKED_EXTERNAL`/`GAP`.

**Evidence đã có:** T139 reporting Playwright 3/3 nhưng API interception; platform purchase/negative browser bị environment-gated; policy downgrade/proration còn `POLICY_NOT_CONFIGURED`. Xem [financial reporting Playwright](../testing/evidence/007/reporting/financial-reporting-playwright.md), [subscription negative](../testing/evidence/007/platform-billing/platform-subscription-negative.md), [subscription policy blockers](../testing/evidence/007/platform-billing/subscription-policy-blockers.md).

### Input và thao tác

1. Dùng property/owner đã `APPROVED`, có entitlement active từ Journey 1. Mở `/management/billing?propertyId={propertyId}`; ghi plan hiện tại và plan cao hơn từ catalog.
2. Bấm plan hiện tại để tạo renewal. Kỳ vọng HTTP `POST /api/platform/subscriptions/{propertyId}/renewal-orders` với `Idempotency-Key`.
3. Bấm plan cao hơn để tạo upgrade. Kỳ vọng HTTP `POST /api/platform/subscriptions/{propertyId}/upgrade-orders` body `{targetPlanId}` với `Idempotency-Key`. Không gửi price/features.
4. Tạo payment attempt và callback simulator như Journey 1; refresh order và entitlement.
5. Mở `/management/property-revenue?propertyId={propertyId}` với owner; chọn basis `NET`, khoảng `2026-07-01` đến `2026-08-01`.
6. Đăng nhập Super Admin, mở `/admin/platform-revenue`, lọc `planCode=PRO`, basis `NET`, cùng kỳ. Nếu có nút Excel, tải file test và ghi filename/checksum; property export UI hiện chưa hoàn chỉnh (`CROSS-026`).

### Kỳ vọng UI / HTTP / database / state / audit

| Điểm kiểm | Kỳ vọng |
|---|---|
| Renewal/upgrade | UI tạo order snapshot mới với operation `RENEW`/`UPGRADE`, backend price/version/duration/features; order ban đầu `PENDING_PAYMENT`, sau callback `APPLIED`. |
| Contract/entitlement | DB `platform_software_contracts` có contract mới; contract cũ được supersede khi policy yêu cầu; `platform_subscription_entitlements` chỉ có projection hiện hành cho property; `platform_subscription_histories` có đúng một `RENEWED` hoặc `UPGRADED`. |
| Report property | Owner chỉ thấy `PROPERTY_COMMERCE` của property đang chọn; API `GET /api/management/reports/property-revenue` giữ đúng `propertyId`; không thấy Platform Billing row. |
| Report platform | Super Admin chỉ thấy Platform Billing; API `GET /api/admin/reports/platform-revenue` không có property leakage; filter plan/date/basis giữ nguyên qua request. Export platform dùng cùng model/filter, file không chứa property report. |
| Reconciliation | `gross - refunds - credits = net` theo context; pending/failed/cancelled/expired attempts không vào collected money; deposits không bị allocate lần hai. Mismatch phải hiển thị hàng đợi/điều tra, không phát hành file hợp lệ. |
| Audit | Financial audit cho purchase/renew/upgrade và history; report/reconciliation là read-only, không tạo ledger/order/entitlement mutation. |

**Query read-only:**

```sql
SELECT public_id, target_hotel_id, operation, plan_id, plan_version,
       price, currency, status, expires_at, applied_at
FROM dbo.platform_subscription_orders
WHERE target_hotel_id = <PROPERTY_ID>
ORDER BY id DESC;

SELECT target_hotel_id, contract_id, plan_id, status, effective_from, effective_until
FROM dbo.platform_subscription_entitlements
WHERE target_hotel_id = <PROPERTY_ID>;

SELECT target_hotel_id, order_id, action_type, occurred_at
FROM dbo.platform_subscription_histories
WHERE target_hotel_id = <PROPERTY_ID>
ORDER BY occurred_at DESC;
```

### Negative, permission, retry và timeout

- Client sửa giá catalog sau khi order đã tạo: order snapshot không đổi; không dùng giá mới cho order cũ.
- Owner khác mở property/order: `404 RESOURCE_NOT_FOUND`; Property-only user mở `/admin/platform-revenue`: điều hướng `/403` hoặc `403` API; không có platform rows.
- Downgrade/proration chưa có policy: `409 POLICY_NOT_CONFIGURED`, không tạo order, không đổi entitlement/history. Đây là expected blocker, không phải test fail.
- Callback sai merchant/amount hoặc replay: cùng mã và invariants như Journey 1; chỉ một transaction/contract/history effect.
- Expiry 1 phút cần bật profile test riêng; hiện chưa có final browser run (`GAP`).
- Report/export timeout, cancellation hoặc `EXPORT_RECONCILIATION_MISMATCH` chưa có execution evidence; ghi `GAP`, không tự retry file mù. Khi `retryable=true` mới retry đọc/export; source financial rows không được đổi.
- Rollback: lỗi khi apply renewal/upgrade phải không để ledger, contract, entitlement và history commit một phần; report/export failure không được sửa source rows hoặc phát hành file hợp lệ. Full SQL Server subscription application rollback vẫn là `GAP`.

### Screenshot

`GAP`: chưa có current screenshot cho renewal/upgrade order, entitlement applied, property revenue và platform revenue report/export. Không dùng ảnh search/home/payment-refund để thay thế. T139 có browser assertions nhưng không tạo ảnh final trong manifest.

## 8. Bảng mã lỗi dùng chung

| HTTP | Code | UI phải làm | Database/state | Retry |
|---:|---|---|---|---|
| 400 | `VALIDATION_FAILED`, `INVALID_AMOUNT`, `INVALID_CURRENCY`, callback mismatch | Hiện lỗi cạnh field hoặc banner, giữ input | Không business mutation | Không, sửa input |
| 401 | `CALLBACK_SIGNATURE_INVALID` | Hiện callback không được xác thực | Không attempt/ledger success | Không; sửa provider |
| 403 | `TENANT_ACCESS_DENIED` hoặc permission denied | Hiện không có quyền, không lộ tenant | Không mutation | Không |
| 404 | `RESOURCE_NOT_FOUND` | Hiện không tìm thấy; không phân biệt cross-tenant | Không mutation | Refresh context |
| 409 | `OUTSTANDING_BALANCE`, `OVERPAYMENT_REQUIRES_RESOLUTION`, `ATTEMPT_EXPIRED`, `REFUND_EXCEEDS_BALANCE`, `POLICY_NOT_CONFIGURED`, `IDEMPOTENCY_KEY_REUSED`, `INVALID_STATE_TRANSITION` | Hiện state hiện tại và hướng xử lý; không blind retry | Transaction business rollback; row cũ giữ nguyên | Chỉ theo hướng dẫn từng code |
| 409 | `CONCURRENT_MODIFICATION` | Refresh state, thông báo thử lại an toàn | Losing transaction rollback; idempotency record là evidence | Cùng key + payload sau refresh |
| 503 | `PAYMENT_ENVIRONMENT_DISABLED`, `PROVIDER_UNAVAILABLE` | Hiện readiness/provider unavailable | Không ghi unverified success | Có nếu `retryable=true`, cùng intent |
| 422 | `EXPORT_RECONCILIATION_MISMATCH` | Không phát hành file, đưa người kiểm tra vào reconciliation | Source rows unchanged | Không tự động; xử lý mismatch trước |

Luôn hiển thị/copy `correlationId` cho support. Không dùng nội dung message đã dịch để quyết định retry; dùng `code` và `retryable`.

## 9. Reset an toàn sau mỗi journey

1. Lưu các id và kết quả trước khi reset: `propertyId`, `reservationId`, `roomId`, `orderPublicId`, `attemptId`, `transactionId`, `refundPublicId`, `invoiceId`, correlation id và screenshot path.
2. Đăng xuất tất cả browser context; chỉ xóa `localStorage/sessionStorage` của origin test (không xóa thư mục workspace, không chạy `git clean`, `reset`, `checkout` hay xóa toàn bộ database).
3. Với order/attempt/refund chưa terminal, dùng nút **Cancel** hoặc endpoint cancel được cấp quyền; không sửa trực tiếp state trong SQL. Callback chưa xác minh phải để `PENDING`/`FAILED` và ghi lại lý do.
4. Nếu cần database reset, tạo database test mới có `RUN_TAG` và chạy migration/seed được phê duyệt. Chỉ drop đúng database test đó sau khi đã lưu evidence; tuyệt đối không drop tên database chưa kiểm tra.
5. Không xóa financial ledger, invoice, refund, entitlement, history hoặc audit rows để làm sạch màn hình. Nếu fixture dùng chung, dùng property/order mới và đánh dấu dữ liệu cũ là fixture lịch sử.
6. Sau khi test xong, đặt lại `PAYMENT_DEMO_ENABLED=false`, `PAYMENT_SANDBOX_ENABLED=false` (nếu đã bật), giữ production flags `false`, tắt provider recovery scheduler nếu đã bật cho test.
7. Kiểm tra lại `git status --short --untracked-files=all`; không reset hoặc ghi đè dirty-worktree changes. Screenshot mới phải nằm trong thư mục evidence được chỉ định và không thay thế binary cũ nếu chưa có approval.

## 10. Điều kiện kết thúc T155

Guide chỉ được nâng từ `PARTIAL` lên hoàn tất khi có đủ cả năm journey chạy trên cùng worktree/database test được fingerprint:

- Mỗi journey có positive, validation, permission/IDOR, replay/concurrency, timeout/provider-failure và safe-retry case phù hợp.
- Mỗi case có UI result, HTTP method/status/code/correlation, database/state effect và audit expectation/observation; `N/A` phải ghi rõ.
- Có screenshot current cho role/route/state chính, caption gồm role, route, ngày, capability và privacy check. Ảnh `BLOCKED` hoặc historical candidate không được dùng làm PASS.
- Không còn claim “final” khi T158-T163 hoặc external sandbox/mailbox stop gate chưa hoàn tất; `BLOCKED_EXTERNAL` và `GAP` phải giữ nguyên.
- Reset đã thực hiện bằng database cô lập hoặc thao tác cancel/expire an toàn; không xóa financial evidence.

### Evidence references

- [Quickstart](../../specs/007-payment-billing-completion/quickstart.md)
- [Financial API contract](../../specs/007-payment-billing-completion/contracts/financial-api-contract.md)
- [Full-system test matrix](../audit/system/FULL_SYSTEM_TEST_MATRIX.md)
- [Error expectation catalog](../audit/system/FULL_SYSTEM_ERROR_EXPECTATION_CATALOG.md)
- [Sandbox configuration guide](./SANDBOX_CONFIGURATION_GUIDE.md)
- [Screenshot manifest](../thesis-assets/SCREENSHOT_MANIFEST.md)
