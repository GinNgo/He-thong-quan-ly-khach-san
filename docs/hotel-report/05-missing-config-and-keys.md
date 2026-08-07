# Danh sách cấu hình, key và dịch vụ còn thiếu

Ngày rà soát: 2026-08-05<br>
Nguyên tắc: tài liệu chỉ ghi **tên biến và loại thông tin**, không ghi giá trị secret thật. Trạng thái `.env.local` chỉ được kiểm tra theo sự hiện diện, không trích xuất giá trị.

## 1. Payment

### 1.1 Ma trận provider

| Cấu hình | Chức năng ảnh hưởng | Nơi tham chiếu | Biến/config cần có | Hiện trạng | Cần tạo/xin | Bước tiếp theo | Ưu tiên |
|---|---|---|---|---|---|---|---|
| Property payment encryption | Mã hóa credential payment theo property | `application.yml` dòng payment property | `PROPERTY_PAYMENT_ENCRYPTION_KEY` | Contract có; fallback sang `JWT_SECRET` làm test context lỗi và không nên dùng chung secret | Secret ngẫu nhiên riêng, đủ entropy, version/rotation plan | Inject qua secret store; bỏ fallback dùng chung; thêm startup/test config | P0 |
| Payment simulator signing | Demo payment an toàn | `application.yml`, payment simulator flow | `PAYMENT_DEMO_ENABLED`, `PAYMENT_DEMO_SIGNING_SECRET`, `PAYMENT_DEMO_BASE_URL` | Màn hình chạy nhưng request không signed cho trạng thái invalid session | Secret demo riêng và URL HTTPS đúng | Bật chỉ ở demo; tạo signed token từ backend; test success/fail/cancel/replay | P0 demo |
| VNPay sandbox | Thanh toán/return/IPN/refund | `application.yml`, `VnpayConfig`, `PaymentController` | `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, URL/return/IPN | Contract có; chưa có bằng chứng merchant sandbox/callback public | Tài khoản merchant sandbox, TMN code, hash secret, cấu hình domain/IP | Đăng ký sandbox; đặt HTTPS return/IPN; test chữ ký, amount, duplicate callback | P0 |
| MoMo sandbox | Create/query/refund/IPN | `MomoPaymentConfig`, gateway và refund resolver | partner code, access key, secret key, create/query/refund URLs, redirect/IPN URL | Java config có nhưng `application.yml` và `.env.example` chưa khai báo explicit | MoMo Business sandbox partner/project và credential | Bổ sung env contract; đăng ký IPN HTTPS; test create-query-refund-recovery | P0 |
| ZaloPay sandbox | Create/query/refund/callback | `application.yml`, `ZaloPayConfig` | `ZALOPAY_APP_ID`, `ZALOPAY_KEY1`, `ZALOPAY_KEY2`, endpoint/redirect/callback | Biến credential local được ghi nhận là trống; chưa có callback evidence | Sandbox app, app id, key1/key2 | Cấu hình app; dùng callback HTTPS public; test MAC và idempotency | P0 |
| Payment mode approval | Ngăn nhầm sandbox/production | `application.yml` | `PAYMENT_SANDBOX_ENABLED`, `PAYMENT_PRODUCTION_ENABLED`, `PAYMENT_PRODUCTION_APPROVED` | Có switch và production mặc định tắt | Quy trình phê duyệt vận hành riêng | Giữ production fail-closed; chỉ bật sau security/finance approval | P0 prod |
| Provider recovery | Query giao dịch pending/unknown | `application.yml` | `PAYMENT_PROVIDER_RECOVERY_*` | Có default; chưa có vận hành/alert evidence | Không cần key mới ngoài provider | Test scheduler, leader/locking, retry/backoff và alert | P1 |

### 1.2 Callback/redirect cần công bố

| Loại | Route hiện có | Yêu cầu production/staging |
|---|---|---|
| VNPay return | Frontend payment result URL | HTTPS public, allowlist tại merchant portal |
| VNPay callback/IPN | `/api/payments/vnpay-callback`, `/api/payments/vnpay-ipn` | HTTPS public, xác minh HMAC và replay |
| MoMo redirect/IPN | frontend redirect; `/api/payments/momo-ipn` | HTTPS public, đúng partner project |
| ZaloPay redirect/callback | frontend redirect; `/api/payments/zalopay-callback` | HTTPS public, MAC validation |
| Property provider callback | `/api/payment-providers/property/{provider}/callback` | Tenant/provider mapping và credential isolation |
| Platform provider callback | `/api/payment-providers/platform/{provider}/callback` | Platform credential và audit |
| Refund callback | property/platform `/{provider}/refund-callback` | Idempotency, amount cap và reconciliation |

Không dùng URL localhost khi provider gọi từ Internet. Khi demo local cần tunnel HTTPS được kiểm soát hoặc staging domain; không ghi token tunnel vào source.

## 2. Authentication secret

| Cấu hình | Ảnh hưởng | Nơi tham chiếu | Biến cần có | Hiện trạng | Việc cần làm | Ưu tiên |
|---|---|---|---|---|---|---|
| JWT signing secret | Login/access/refresh/API auth | `application.yml` | `JWT_SECRET` | Bắt buộc; không xác minh giá trị thật | Secret riêng mỗi môi trường, rotation và incident plan | P0 |
| Refresh cookie security | Silent refresh/session | `application.yml` | `AUTH_REFRESH_COOKIE_SECURE` và cookie domain/same-site policy | Mặc định false cho local | Bật secure sau HTTPS; test proxy headers/cross-site policy | P0 prod |
| OAuth issuer | Resource server validation | `application.yml` | issuer URL/cấu hình tương ứng | Placeholder `http://localhost:8080` | Chốt issuer thật hoặc bỏ block không dùng; test discovery/JWK nếu dùng | P1 |
| Google login | Social login | backend `GOOGLE_CLIENT_ID`; Angular environment | `GOOGLE_CLIENT_ID` và public frontend client ID khớp nhau | Chưa có provider sandbox evidence; browser có FedCM/network error | Tạo OAuth client, localhost/staging origins, test account | P1 |
| Facebook login | Social login | backend config và Angular environment | `FACEBOOK_APP_ID`, `FACEBOOK_APP_SECRET`; public app ID frontend | Chưa xác minh provider console | Tạo Meta app/test user, valid OAuth redirect/domain | P1 |

## 3. Email/SMTP

| Cấu hình | Ảnh hưởng | Nơi tham chiếu | Biến cần có | Hiện trạng | Cần tạo/xin | Bước tiếp theo | Ưu tiên |
|---|---|---|---|---|---|---|---|
| SMTP credential | Welcome, verify/change email, reset password, booking, invoice | `application.yml` mail block; `.env.local` ignored | `MAIL_USERNAME`, `MAIL_PASSWORD` | Local/E2E credential đã xác minh: preview 9/9; health/readiness 200; không commit secret | Secret-managed credential tách theo môi trường | Bổ sung staging provider policy, rotation, bounce/delivery monitoring | P1 |
| Mail feature switches | Không gửi nhầm từ local/test | `.env.example`, `application.yml` | registration/reset/outbox delivery switches | Local example mặc định tắt delivery thích hợp | Không cần account mới | Chốt profile dev/staging/prod và fail-safe default | P1 |
| Mail outbox worker | Retry và quan sát gửi mail | `application.yml` | `MAIL_OUTBOX_*` | Có config; delivery mặc định false | Queue/DB outbox đang có cần vận hành/alert | Test retry/backoff/dead letter và dashboard/alert | P1 |
| Public link URLs | Link login/reset/verify | `application.yml`, `.env.example` | reset/login/base URLs | Localhost/localhost HTTPS | Domain staging/prod | Đặt URL tuyệt đối đúng môi trường, chống host-header injection | P1 |

P0-SMTP-VERIFY xác nhận `.env.example` chỉ chứa placeholder, `.env.local` bị `.gitignore` chặn và runtime phải nạp environment qua `backend/run-local.ps1` hoặc cơ chế tương đương. Gmail SMTP local là bằng chứng vận hành, chưa thay thế secret store và rotation policy cho staging/production.

## 4. Database

| Cấu hình | Ảnh hưởng | Nơi tham chiếu | Biến cần có | Hiện trạng | Cần chuẩn bị | Bước tiếp theo | Ưu tiên |
|---|---|---|---|---|---|---|---|
| SQL Server connection | Toàn bộ dữ liệu | `application.yml`, Compose | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `MSSQL_SA_PASSWORD` local | Contract có; Compose fail-fast khi thiếu SA password | DB instance/user least privilege, TLS certificate | Không dùng SA cho app; private network; rotate secret | P0 |
| Schema migration | Startup/deploy | Flyway + Hibernate | Flyway location/version; JPA ddl policy | Flyway bật nhưng `ddl-auto:update` | Migration owner/process và rollback runbook | Chuyển validate/none; rehearsal trên bản sao | P0 |
| Backup/restore | Phục hồi dữ liệu | Chưa thấy workflow repository | backup target, retention, encryption | Thiếu bằng chứng | Storage backup, RPO/RTO owner | Tạo automated backup và restore drill | P0 prod |
| H2 test schema | Backend regression | test/E2E profiles | test datasource/migration config | Một số test lỗi thiếu table | Không cần dịch vụ ngoài | Đồng bộ H2 compatibility hoặc dùng SQL Server test container | P1 |

## 5. Storage / Upload

| Cấu hình | Ảnh hưởng | Nơi tham chiếu | Biến cần có | Hiện trạng | Cần chuẩn bị | Bước tiếp theo | Ưu tiên |
|---|---|---|---|---|---|---|---|
| Upload path | Avatar/hình ảnh | `FileUploadService` | `upload.path` | Mặc định filesystem `uploads/` | Persistent volume hoặc object storage/bucket | Tách storage adapter; backup, lifecycle và quota | P1 |
| Image limits | Bảo mật/tài nguyên | `FileUploadService`, multipart config | max bytes/width/height/pixels | Có default và kiểm tra signature/dimension trong source | Không cần key | Thêm malware/content scan nếu cần, test malformed image | P2 |
| Public asset base URL | Hiển thị ảnh sau deploy | upload controller và frontend | CDN/API asset URL | Dựa trên API local | Domain/CDN và cache policy | Chốt URL, authorization/public policy, orphan cleanup | P1 |

## 6. Map / Geolocation

| Cấu hình | Ảnh hưởng | Nơi tham chiếu | Biến cần có | Hiện trạng | Cần chuẩn bị | Bước tiếp theo | Ưu tiên |
|---|---|---|---|---|---|---|---|
| Location datasets | Tìm kiếm tỉnh/phường/địa danh | `application.yml` location resources | `LOCATION_IMPORT_RESOURCE`, `CURRENT_PROVINCE_IMPORT_RESOURCE`, `LANDMARK_IMPORT_RESOURCE` | Dùng JSON đóng gói, không thấy external map key | Quy trình cập nhật dataset | Version dữ liệu, validation và import report | P2 |
| Accommodation import | Import nguồn ngoài | `application.yml` | provider, rate/delay, user-agent nếu yêu cầu | Provider Nominatim và default delay có trong config | Tuân thủ usage policy, attribution và cache | Xác minh điều khoản, rate limit, retry và provenance | P1 nếu bật |
| Map UI/geocoding | Hiển thị/tìm theo tọa độ | Không thấy contract key map thương mại rõ ràng | Tùy provider được chọn | Chưa xác định provider production | Chọn provider hoặc xác nhận không cần bản đồ | Ghi privacy/cost/quota và key restriction | P2 |

## 7. Third-party integration

| Tích hợp | Ảnh hưởng | Hiện trạng | Việc cần làm | Ưu tiên |
|---|---|---|---|---|
| Google OAuth/FedCM | Đăng nhập Google | Chưa xác minh; browser báo network/FedCM warning | Provider console, origins, consent screen, test users, monitoring | P1 |
| Facebook OAuth | Đăng nhập Facebook | Chưa xác minh | App/domain/redirect/test user/data deletion URL nếu yêu cầu | P1 |
| WebSocket/STOMP | Chat/notification | Source có; build báo CommonJS; test chat có lỗi | WSS origin, proxy upgrade, reconnect/rate limit, full test | P1 |
| Nominatim/import | Dữ liệu accommodation | Có config mặc định | Policy, rate limit, attribution, caching và batch recovery | P2 |

## 8. Analytics

Không thấy API key cho Google Analytics, Matomo hoặc provider analytics bên ngoài. Dashboard/reporting hiện dựa trên dữ liệu nội bộ.

| Cấu hình | Ảnh hưởng | Hiện trạng | Bước tiếp theo | Ưu tiên |
|---|---|---|---|---|
| Product analytics | Funnel/search/booking conversion | Chưa tích hợp provider ngoài | Chỉ chọn sau khi có consent/privacy/data retention; không cần cho demo cốt lõi | P3 |
| Operational metrics | Health/latency/error/scheduler | Actuator health/info/metrics có expose | Kết nối Prometheus/APM/log sink, dashboard và alert | P1 prod |
| Financial reconciliation | Doanh thu/refund/payment | Báo cáo nội bộ có nhưng test financial performance còn lỗi | Sửa test, định nghĩa nguồn sự thật và daily reconciliation | P1 |

## 9. Other external services và production controls

| Cấu hình | Ảnh hưởng | Hiện trạng | Bước tiếp theo | Ưu tiên |
|---|---|---|---|---|
| TLS/DNS/reverse proxy | Toàn hệ thống | Chưa có deploy topology | Domain staging/prod, certificate automation, proxy headers và SPA fallback | P0 prod |
| CORS/WebSocket origins | API/chat | Mặc định localhost | Allowlist HTTPS chính xác, không wildcard với credential | P0 prod |
| Secret manager | Mọi secret | `.env.example` có contract, chưa có production store | Chọn Vault/cloud secret/Kubernetes secret phù hợp; audit/rotation | P0 prod |
| Logging/alerting | Điều tra lỗi/payment/mail | Có correlation pattern, chưa thấy centralized sink | Redaction, retention, alert callback/outbox/scheduler/error rate | P1 |
| AI assistant | Trợ lý trong app | Logic được ghi nhận là keyword/mock, không có external AI key | Gắn nhãn demo; nếu tích hợp AI thật phải có privacy/cost/safety design | P3 |
| CI/CD | Build/test/deploy | Chưa thấy workflow | Tạo pipeline và approval production; scan dependency/image/SBOM | P1 |

## 10. Thứ tự hoàn thiện đề xuất

1. Tách `PROPERTY_PAYMENT_ENCRYPTION_KEY` khỏi JWT fallback; cung cấp test-safe secret contract và làm xanh backend context.
2. Làm xanh toàn bộ frontend/backend regression thuộc phạm vi demo.
3. Hoàn tất payment simulator signed flow; sau đó chọn **một** provider sandbox ưu tiên để chạy E2E đầy đủ.
4. Bổ sung MoMo env/config contract và đăng ký callback HTTPS cho provider đã chọn.
5. Chốt role/permission matrix và sửa bốn route management bị 403 ngoài dự kiến.
6. Chốt staging domain, TLS, CORS/WebSocket, OAuth/SMTP sandbox và secret store.
7. Hoàn thiện container/service topology, private DB, persistent upload, migration và backup/restore.
8. Thêm CI/CD quality gate, centralized logs/metrics và rehearsal trước buổi demo.
## P0-03A database/profile clarification (2026-08-06)

- SQL Server remains the production database contract: base `application.yml` keeps Flyway enabled at `classpath:db/migration`; released SQL Server migrations were not edited, moved, repaired or skipped.
- Disposable local runtime must use `--spring.profiles.active=local-h2`. This profile uses H2 with Hibernate `create-drop` and disables Flyway only because no H2 migration set exists.
- Do not run a packaged JAR with profile `test`: `src/test/resources/application-test.yml` is a Maven-test resource and is not packaged into the executable artifact.
- Future work, only if H2 migration parity becomes required: create explicit `db/migration/common` and `db/migration/h2` sets rather than making SQL Server migrations dialect-ambiguous.
