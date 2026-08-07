# P0-SMTP-VERIFY - SMTP, email regression and runtime readiness

Ngày xác minh: 2026-08-06 (Asia/Saigon)  
Trạng thái: **PASS**  
Phạm vi: SMTP/email production changes, regression, invoice PDF và runtime readiness; không xử lý payment hoặc blocker backend khác; không gửi lại 9 email preview.

## File thay đổi

Production:

- `backend/src/main/java/com/hotel/services/EmailService.java`.
- `backend/src/main/java/com/hotel/services/EmailVerificationMailer.java`.

Test:

- `backend/src/test/java/com/hotel/services/BookingConfirmationEmailTest.java`.
- `backend/src/test/java/com/hotel/services/EmailVerificationMailerTest.java`.

Harness gửi preview và harness xuất PDF tạm đã được xóa. Không có startup hook hoặc test thường nào gửi email thật.

## Git, SMTP config và secret handling

- Branch: `codex/ui-functional-audit-polish`.
- HEAD: `88f7da5d52dbcc6b802a44bcf19e9aca97ae905f`.
- Worktree lúc bắt đầu: 555 status entry; không reset/revert thay đổi người dùng hoặc P0 trước đó.
- `application.yml`: Gmail SMTP 587/STARTTLS; username/password lấy từ `${MAIL_USERNAME:}` và `${MAIL_PASSWORD:}`.
- `backend/run-local.ps1` đọc `.env.local` vào process trước khi chạy Spring Boot.
- `.env.local` khớp rule `.gitignore:5`, không được track; `.env.example` chỉ chứa placeholder rỗng.
- Không in password, app password hoặc recipient list đầy đủ vào evidence.
- Exact-value scan không thấy SMTP username/password bên ngoài `.env.local`; không thấy bearer token dài hoặc private-key marker trong evidence.

## Loại email đã kiểm tra

| Loại | Contract | Kết quả |
|---|---|---|
| Chào mừng đăng ký | Subject tiếng Việt, UTF-8 HTML, inline CSS | PASS |
| Xác thực email lần đầu | Tiếng Việt, one-time URL, expiry | PASS |
| Xác nhận đổi email | Tiếng Việt, one-time URL, expiry | PASS |
| Reset mật khẩu | Tiếng Việt; không tiết lộ password | PASS |
| Xác nhận booking | Tiếng Việt mặc định; text + HTML | PASS |
| Hóa đơn | HTML tiếng Việt + PDF | PASS |
| Payment/tenant E2E | Preview label, không phải production lifecycle template | Ghi nhận; không gửi lại |

Không tìm thấy production template riêng cho cập nhật/hủy booking; không tạo giả template trong lượt verify.

## Booking MIME

Root cause đã sửa: direct booking delivery tạo alternative plain-text/HTML nhưng dùng non-multipart helper, gây `IllegalStateException` với JavaMail provider thật. Production nay dùng `MimeMessageHelper(..., true, "UTF-8")` và đặt cả text fallback lẫn HTML body.

Test mới xác nhận subject tiếng Việt, `JavaMailSender.send()` được gọi, message finalize thành `multipart/*`, HTML không bị gửi như plain text và charset helper là UTF-8.

## Focused và regression

| Nhóm | Kết quả |
|---|---|
| Email/auth/verification/reset/booking/invoice/outbox focused | PASS 81/81 |
| P0-02A đến P0-02I guards | PASS 131/131 |
| Maven package | PASS - `BUILD SUCCESS` |

Coverage gồm registration, verification/change-email, password reset, booking HTML/outbox, observability, outbox idempotency/retry/dead-letter/SENT, JavaMail adapter, invoice access/finalization/immutability/model và PDF rendering.

## Full backend trước và sau

| Mốc | Tổng | PASS | FAIL | ERROR | SKIPPED |
|---|---:|---:|---:|---:|---:|
| Baseline yêu cầu trước SMTP | 836 | 826 | 6 | 4 | 0 |
| Sau P0-02J, trước email test mới | 836 | 827 | 5 | 4 | 0 |
| P0-SMTP-VERIFY | 837 | 828 | 5 | 4 | 0 |

PASS tăng 1 do test booking MIME mới. Không có email FAIL/ERROR mới. Full Maven vẫn `BUILD FAILURE` do 5 FAIL và 4 ERROR tồn tại trước SMTP changes.

5 FAIL còn lại:

1. `TenantIsolationIntegrationTest`: expected 404, actual 409.
2. `FinancialPerformanceIntegrationTest`: p95 3521,8 ms > 3000 ms.
3. `PropertyPaymentConfigurationIntegrationTest`: payment deferred.
4. `ManualTransferConfirmationIntegrationTest`: 2 payment failures deferred.

4 ERROR, đều payment-deferred/context cũ:

- `BackendApplicationTests`.
- `MockPaymentControllerIntegrationTest`.
- `PaymentSessionConcurrencyIntegrationTest`.
- `RefundLifecycleConcurrencyIntegrationTest`.

## Invoice PDF

- Production fixture tạo PDF 5.105 byte, `%PDF-1.4`, 2 trang A4.
- `PropertyInvoiceDocumentServiceTest` PASS và xác nhận deterministic bytes/checksum, property/customer snapshot, line items, payment, refund/credit và totals.
- `InvoiceAccessIntegrationTest` PASS cho HTTP `application/pdf`, filename và email authorization.
- Poppler đọc được PDF và render được cả hai trang PNG.
- Visual QA: không cắt/chồng chữ; trang 1 chứa snapshot/line items, trang 2 chứa payment/refund/totals. Font fallback warning không làm mất nội dung.
- Email invoice dùng `<invoiceNumber>.pdf`, MIME `application/pdf`, bytes clone và kích thước > 0.

## Runtime SMTP/readiness

Backend khởi động bằng `backend/run-local.ps1`, profile `e2e`, cổng 8082, nạp `.env.local`.

| Probe | Kết quả |
|---|---|
| Startup | PASS - Spring Boot started in 25,218 s |
| `/actuator/health` | HTTP 200, `UP` |
| `/actuator/health/liveness` | HTTP 200, `UP` |
| `/actuator/health/readiness` | HTTP 200, `UP` |
| `/v3/api-docs` | HTTP 200 |
| `/swagger-ui/index.html` | HTTP 200 |
| `/api/public/properties/search` | HTTP 200 |
| `/actuator/health/mail` | HTTP 401 vì component-detail endpoint không public; không dùng làm readiness probe |

Overall health trước đây 503 khi SMTP không được nạp; HTTP 200 hiện tại chứng minh mail health contributor không còn kéo aggregate xuống. Runtime không có `AuthenticationFailed`, SMTP auth error, `SendFailed` hoặc MIME error.

## Outbox và startup auto-send

- Outbox idempotency, payload mismatch, exponential retry, dead-letter, manual retry, bounce và SENT/provider ID PASS.
- Worker và controller contract PASS; chuỗi subject/body tiếng Việt không còn mojibake.
- Runtime worker chạy với queue rỗng: 0 dòng `MAIL_DELIVERY`, không có email gửi lúc startup.
- Focused/full tests dùng mock hoặc fail-closed adapter, không dùng real SMTP.
- Runtime đã dừng và cổng 8082 được giải phóng.

## URL, token và UTF-8

- Template tiếng Việt dùng `lang="vi"`, table layout và inline CSS.
- Verification/reset token chỉ nằm trong URL truyền vào mailer; template không log token và không hiển thị password.
- Expiry text dùng giá trị contract từ verification/reset service.
- `localhost` trong runtime là local/E2E chủ đích; staging phải inject public frontend URL.
- Không còn mojibake trong hai production mail service.

## Evidence

- `evidence/P0-SMTP-VERIFY-focused.log`.
- `evidence/P0-SMTP-VERIFY-guards.log`.
- `evidence/P0-SMTP-VERIFY-full.log`.
- `evidence/P0-SMTP-VERIFY-package.log`.
- `evidence/P0-SMTP-VERIFY-runtime.log`, `runtime-error.log`, `runtime-probes.log`.
- `evidence/P0-SMTP-VERIFY-secret-scan.txt`.
- `evidence/P0-SMTP-VERIFY-template-contract.txt`.
- `evidence/P0-SMTP-VERIFY-invoice.pdf`, `invoice-pdfinfo.txt`, `invoice-page1.png`, `invoice-page2.png`.

## Deployment readiness

SMTP/email/readiness: **READY_FOR_LOCAL_E2E**. SMTP hoạt động khi được inject đúng; overall health/readiness đều 200.

Deployment tổng thể: **NOT_READY**. Còn backend non-email failures, payment, staging secret store/rotation, CI/CD và deployment topology.

## Hướng dẫn kiểm tra thủ công

1. Chạy `git check-ignore -v .env.local`; xác nhận file bị ignore và không mở/in password.
2. Trong `backend`, chạy focused command theo evidence; kỳ vọng 81/81 PASS.
3. Chạy guard command theo evidence; kỳ vọng 131/131 PASS.
4. Chạy `./mvnw.cmd test`; kỳ vọng 837 total, 828 PASS, 5 FAIL, 4 ERROR, không có email regression.
5. Chạy `./mvnw.cmd -DskipTests package`; kỳ vọng `BUILD SUCCESS`.
6. Từ repo root chạy `./backend/run-local.ps1`; xác nhận backend lắng nghe 8082.
7. Probe overall/liveness/readiness, OpenAPI, Swagger và public search; kỳ vọng HTTP 200.
8. Kiểm tra startup log: không có `MAIL_DELIVERY`, `AuthenticationFailed`, `SendFailed` hoặc SMTP password.
9. Mở `P0-SMTP-VERIFY-invoice.pdf`; xác nhận 2 trang và dữ liệu fixture đầy đủ.
10. Dừng backend; xác nhận cổng 8082 được giải phóng.

## Kết luận

**P0-SMTP-VERIFY: PASS**

Bước tiếp theo chỉ đề xuất, chưa xử lý: `P0-02K - TenantIsolationIntegrationTest 404/409 contract`. Payment giữ `DEFERRED_PAYMENT_PHASE`.
