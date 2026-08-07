# Tổng quan hệ thống Hotel Management

Ngày rà soát: 2026-08-05<br>
Phạm vi: frontend, backend, cơ sở dữ liệu, xác thực/phân quyền, booking, thanh toán, vận hành khách sạn, báo cáo và khả năng triển khai.

## 1. Kết luận điều hành

Hệ thống là một nền tảng đặt phòng và quản lý khách sạn đa khu vực quản trị, có độ phủ nghiệp vụ lớn nhưng chưa đủ điều kiện deploy/demo như một bản production hoàn chỉnh. Source hiện có thể build frontend và compile/package backend; luồng public từ tìm kiếm đến checkout đã được tái hiện bằng trình duyệt. Tuy nhiên, bộ test hồi quy frontend và backend chưa xanh, cấu hình sandbox thanh toán chưa đầy đủ, một số route quản lý bị từ chối quyền ngoài dự kiến, và topology Docker hiện chỉ chứa SQL Server.

Đánh giá tổng thể: **NOT_READY** cho production; có thể chuẩn bị một bản demo có kiểm soát sau khi xử lý các blocker P0/P1 trong `04-deployment-readiness.md`.

## 2. Phương pháp và nguồn bằng chứng

- Kiểm kê route, component, controller, service, repository, entity và migration trực tiếp từ source.
- Đối chiếu 179 capability trong `docs/audit/system/MASTER_FUNCTION_INVENTORY.md` với route/API/data/test evidence.
- Chạy build và test ngày 2026-08-05; không đánh dấu PASS chỉ vì source tồn tại.
- Khởi động backend profile E2E biệt lập ở cổng 8082, xác nhận public API trả HTTP 200, sau đó dừng tiến trình.
- Dùng frontend HTTPS hiện có ở cổng 4200 để tái hiện luồng public, admin và management; lưu 41 ảnh tại `docs/hotel-report/screenshots/`.
- Chỉ kiểm tra sự hiện diện của biến cấu hình; không đọc hoặc ghi giá trị bí mật trong `.env.local`.

## 3. Quy mô source tại thời điểm audit

| Hạng mục | Số lượng |
|---|---:|
| Route frontend | 86 |
| Angular component | 98 |
| Nhóm feature frontend | 9 |
| REST controller backend | 51 |
| Endpoint method backend | 251 |
| Service/component backend | 136 |
| JPA repository | 84 |
| JPA entity | 87 |
| Flyway migration | 56 |
| Spec frontend | 142 |
| Test class backend | 210 |
| Capability được chuẩn hóa trong báo cáo | 179 |

Các con số là inventory tĩnh, không đồng nghĩa mọi chức năng đều hoàn chỉnh. Trạng thái thực tế nằm trong `02-functional-inventory.md`.

## 4. Kiến trúc tổng quan

| Lớp | Công nghệ và vai trò | Bằng chứng chính |
|---|---|---|
| Web client | Angular 22 standalone, TypeScript 6, RxJS, PrimeNG, Bootstrap/Tailwind; public/customer, system admin, property management | `frontend/package.json`, `frontend/src/app/` |
| API | Java 21, Spring Boot 3.2.5, Spring MVC, Security/JWT, WebSocket, Actuator | `backend/pom.xml`, `backend/src/main/java/com/hotel/` |
| Data | Spring Data JPA, SQL Server cho local/production, H2 cho test/E2E, Flyway migration | `backend/src/main/resources/application.yml`, `backend/src/main/resources/db/migration/` |
| Tích hợp | SMTP, Google/Facebook login, VNPay, MoMo, ZaloPay, payment simulator, import dữ liệu địa điểm | `backend/src/main/resources/application.yml`, các lớp config/gateway |
| Runtime hiện có | Angular chạy trực tiếp; backend chạy bằng Maven/Java; Compose mới chỉ cấp SQL Server | `frontend/package.json`, `backend/run-local.ps1`, `docker-compose.yml` |

Luồng tin cậy chính:

1. Trình duyệt gửi request qua Angular proxy hoặc URL API được cấu hình.
2. Spring Security xác thực JWT/cookie refresh và kiểm tra quyền endpoint.
3. Controller gọi service/domain, repository và SQL Server/H2.
4. Callback thanh toán là public endpoint nhưng phải xác minh chữ ký, idempotency và trạng thái giao dịch.
5. File ảnh upload hiện lưu trên filesystem cục bộ `uploads/`, không phải object storage bền vững.

## 5. Actor và khu vực giao diện

| Actor | Mục tiêu | Khu vực chính |
|---|---|---|
| Khách vãng lai | Tìm kiếm, xem khách sạn/phòng, đăng ký, đăng nhập | Public web |
| Khách hàng | Đặt phòng, thanh toán, xem booking/hóa đơn/hoàn tiền, quản lý hồ sơ | Customer web |
| Nhân viên khách sạn | Quản lý booking, check-in/out, phòng, dịch vụ, housekeeping | Property management |
| Chủ/đối tác khách sạn | Quản lý cơ sở, doanh thu, cấu hình thanh toán, gói thuê bao | Property management |
| Quản trị hệ thống | Quản lý user, property, role/permission, plan, payment, audit và báo cáo | System admin |
| Cổng thanh toán | Tạo giao dịch, redirect người dùng, gửi callback/IPN/refund callback | External provider |
| Dịch vụ email/OAuth | Gửi mail và xác thực danh tính xã hội | External provider |

## 6. Nhóm module và màn hình

### Public và customer

- Trang chủ, tìm kiếm theo địa điểm/ngày/số khách, danh sách kết quả và chi tiết khách sạn.
- Loại phòng, phòng trống, lựa chọn phòng, checkout, kết quả thanh toán và payment simulator.
- Đăng ký, đăng nhập, quên/đặt lại mật khẩu, xác minh email, đăng nhập Google/Facebook.
- Hồ sơ, cài đặt tài khoản, lịch sử booking, hóa đơn và lịch sử hoàn tiền.
- Đăng ký đối tác và các trạng thái onboarding.

### System admin

- Dashboard, quản lý người dùng, khách sạn, loại phòng, phòng, booking, dịch vụ và hóa đơn.
- Role, permission và ánh xạ quyền.
- Plan/gói dịch vụ, cấu hình thanh toán, doanh thu nền tảng.
- Audit log, chat, phê duyệt property.

### Property management

- Dashboard theo property, loại phòng, phòng, housekeeping và dịch vụ.
- Booking/stay lifecycle, doanh thu property, hoàn tiền.
- Cấu hình payment provider, subscription billing và audit log.
- Một số route nhạy cảm đã trả về 403 với fixture owner trong lần kiểm tra; xem `07-ui-screenshots.md`.

## 7. Backend và dữ liệu

Backend được phân lớp theo controller, service/domain, repository và entity. Các miền dữ liệu đáng chú ý gồm:

- Identity: users, roles, permissions, refresh token, verification/reset token.
- Property: properties, images, amenities, services, room types, rooms, pricing/inventory.
- Reservation: reservation/booking, guest, hold, stay, check-in/check-out, housekeeping.
- Commerce: payment transaction, provider configuration, callback, refund, invoice, revenue.
- SaaS: plan, subscription, limit/usage, billing.
- Operations: notification, chat, audit, outbox và observability.

Flyway có 56 migration, nhưng `spring.jpa.hibernate.ddl-auto` vẫn là `update` trong `application.yml`. Trước production cần chốt Flyway là nguồn schema duy nhất, kiểm tra migration trên bản sao dữ liệu và có backup/restore cùng rollback runbook.

## 8. Bảo mật và phân quyền

- Có JWT access token, refresh token rotation, logout/revocation và nhiều test evidence trong inventory.
- CORS và WebSocket origin mặc định về localhost; production phải đặt allowlist HTTPS chính xác.
- Callback thanh toán được permit public theo thiết kế nhưng phải fail-closed khi sai chữ ký hoặc replay.
- Route/menu permission cần tiếp tục đối chiếu với API permission vì bốn màn management bị 403 trong fixture owner.
- `issuer-uri` hiện là placeholder localhost và không phải cấu hình issuer production hợp lệ.
- Không được đưa JWT, DB password, SMTP password hoặc provider secret vào Angular, Git hay tài liệu.

## 9. Kết quả kiểm kê chức năng

| Trạng thái | Số lượng | Ý nghĩa |
|---|---:|---|
| PASS | 61 | Có bằng chứng source và kiểm chứng phù hợp trong inventory |
| PARTIAL | 55 | Có phần hoạt động nhưng còn giới hạn/test/config |
| FAIL | 56 | Hỏng, thiếu, placeholder hoặc chưa đạt tiêu chí |
| BLOCKED | 7 | Phụ thuộc tài khoản/key/dịch vụ ngoài chưa có |
| **Tổng** | **179** | 100% capability đã được phân loại |

## 10. Giới hạn của lần audit

- Không dùng credential production và không thực hiện giao dịch tiền thật.
- Không xác minh SMTP inbox thực, OAuth provider console, merchant portal hoặc callback public từ Internet.
- Không chạy load test, penetration test, backup/restore drill hoặc migration trên dữ liệu production-like.
- Ảnh UI chứng minh route đã render tại thời điểm chụp, không thay thế test nghiệp vụ đầy đủ.
- Worktree có nhiều thay đổi tồn tại trước audit; báo cáo không quy kết nguồn gốc các thay đổi đó.

## 11. Tài liệu liên quan

- Chi tiết từng capability: `02-functional-inventory.md`
- Luồng nghiệp vụ: `03-business-flows.md`
- Build/test/deploy: `04-deployment-readiness.md`
- Config/key còn thiếu: `05-missing-config-and-keys.md`
- Sơ đồ Mermaid: `06-diagrams.md`
- Chỉ mục ảnh UI: `07-ui-screenshots.md`
- Báo cáo nộp/thuyết trình: `08-final-report.md`
