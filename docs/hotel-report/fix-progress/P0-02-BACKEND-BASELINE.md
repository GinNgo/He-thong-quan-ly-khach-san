# P0-02 - Backend build, automated tests và runtime baseline

Ngày kiểm tra: 2026-08-05 (Asia/Saigon)  
Trạng thái: **BASELINE_COMPLETE - BACKEND_REGRESSION_FAIL**  
Phạm vi: khảo sát backend, không sửa production/test source, không xử lý blocker, không xử lý payment.

## 1. Kết luận điều hành

| Hạng mục | Kết quả |
|---|---|
| Dependency restore | **PASS** |
| Backend build | **PASS** |
| Automated tests | **FAIL** - 801/833 PASS, 9 FAIL, 23 ERROR, 0 SKIPPED |
| Backend startup | **PASS_WITH_DEGRADED_HEALTH** - ứng dụng chạy ổn định trên cổng 8082 |
| H2 database connection | **PASS** |
| Public API/OpenAPI/Swagger | **PASS** |
| Overall health | **FAIL** - HTTP 503 do SMTP health không có credential |
| Liveness | **PASS** - HTTP 200 |
| Readiness | **FAIL** - HTTP 503 do MailHealthIndicator |
| Payment | **DEFERRED_PAYMENT_PHASE** - không chạy provider/sandbox, không sửa payment |

Baseline đã hoàn thành vì restore, build, full test và runtime probe đều đã được thực thi bằng số liệu thực tế. Không có source file nào được sửa trong P0-02.

## 2. Git baseline

| Thuộc tính | Giá trị |
|---|---|
| Branch | `codex/ui-functional-audit-polish` |
| Commit | `88f7da5d52dbcc6b802a44bcf19e9aca97ae905f` |
| Working tree | Rất bẩn: 544 status entry |
| Tracked changed | 283 entry |
| Untracked | 261 entry |
| Hành động bảo toàn | Không reset, checkout, stash, revert hoặc ghi đè thay đổi hiện hữu |

`docs/hotel-report/` đã là một thư mục untracked trước baseline. P0-02 chỉ thêm report và evidence trong thư mục này; Maven chỉ cập nhật artifact ignored trong `backend/target/`.

Không tạo Git checkpoint vì lượt này không sửa blocker hoặc source.

## 3. Xác định đúng backend project

Yêu cầu đính kèm mô tả quy trình .NET, nhưng repository Hotel không phải dự án .NET.

| Trường yêu cầu | Kết quả thực tế |
|---|---|
| Solution file | `N/A` - không có `.sln` |
| .NET project | `N/A` - không có `.csproj`, `.fsproj`, `global.json` |
| Backend project | `backend/pom.xml` |
| Module | Một Maven module: `com.hotel:backend:0.0.1-SNAPSHOT` |
| Backend entry project | `backend/` |
| Startup class | `backend/src/main/java/com/hotel/BackendApplication.java` |
| Test project | Cùng Maven module, source root `backend/src/test/java` |
| Main source | 542 Java file |
| Test source | 214 Java file |
| Target framework | `N/A` cho .NET; Java 21 cho Hotel |
| appsettings | `N/A`; dùng `application.yml`, `application-e2e.yml`, test `application-test.yml` |

## 4. Toolchain

| Công cụ | Phiên bản |
|---|---|
| Java | Microsoft OpenJDK 21.0.11 LTS |
| Maven Wrapper | Apache Maven 3.9.16 |
| Spring Boot | 3.2.5 |
| Hibernate ORM | 6.4.4.Final |
| .NET SDK trên máy | 10.0.301; không được Hotel sử dụng |
| .NET runtime trên máy | 8.0.29 và 10.0.x; không được Hotel sử dụng |

Lệnh `.NET` bắt buộc trong yêu cầu đã được đối chiếu bằng `dotnet --info`; kết quả chỉ chứng minh SDK có trên máy, không tạo ra solution/project Hotel để restore/build/test bằng `dotnet`.

## 5. Database, ORM và migration

| Môi trường | Provider | ORM/schema |
|---|---|---|
| Production/local | SQL Server qua `mssql-jdbc` | Spring Data JPA/Hibernate + Flyway |
| Test/E2E | H2 in-memory | Hibernate `create-drop`; Flyway tắt |

- Production/local mặc định dùng `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
- Có 56 migration SQL trong `backend/src/main/resources/db/migration`.
- E2E baseline dùng `jdbc:h2:mem:e2edb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`.
- Runtime baseline kết nối H2 thành công: Hikari thêm `conn0`, JPA EntityManagerFactory khởi tạo thành công.
- Flyway production không được chạy trong baseline E2E, nên trạng thái 56 migration là **PRESENT_NOT_REHEARSED**.
- Fixture, demo seed, nationwide seed, accommodation import và location import đều tắt trong runtime probe; không có seed error.

## 6. Dependency và external configuration

Các dependency ngoài cần cho từng chế độ:

| Dependency/config | Production/local | E2E baseline |
|---|---|---|
| SQL Server | Bắt buộc | Không dùng; thay bằng H2 |
| JWT secret | Bắt buộc | Giá trị ngẫu nhiên process-only, không ghi log |
| Property payment encryption key | Source yêu cầu khi tạo context | Giá trị ngẫu nhiên process-only; payment vẫn tắt |
| SMTP | Cần cho gửi mail/health | Không có credential; làm health/readiness 503 |
| Google/Facebook OAuth | Cần nếu bật social login | Không kiểm tra |
| Upload/static storage | Filesystem/persistent storage cần chốt khi deploy | Không kiểm tra upload trong probe |
| Location datasets | Packaged JSON | Import tắt; endpoint trả mảng rỗng hợp lệ |
| Payment provider keys | Thuộc phase payment | **DEFERRED_PAYMENT_PHASE** |

Không có secret, password hoặc connection string nhạy cảm nào xuất hiện trong evidence. Secret scan trên ba runtime log trả `NO_MATCH`.

## 7. Restore

Lệnh:

```powershell
Set-Location backend
.\mvnw.cmd -DskipTests dependency:go-offline
```

Kết quả:

- **PASS**, exit code 0.
- Maven total time: 04:02.
- Không có dependency resolution blocker.
- Evidence: `docs/hotel-report/fix-progress/evidence/P0-02-backend-restore.log`.

Phân loại: `BACKEND_RESTORE = PASS`.

## 8. Build

Lệnh:

```powershell
Set-Location backend
.\mvnw.cmd -DskipTests package
```

Kết quả module duy nhất:

| Project | Compile | Package | Artifact |
|---|---|---|---|
| `com.hotel:backend` | PASS | PASS | `backend/target/backend-0.0.1-SNAPSHOT.jar` |

- **PASS**, exit code 0.
- Maven total time: 13.853 giây.
- Spring Boot repackage thành công.
- Compile error: 0.
- Evidence: `docs/hotel-report/fix-progress/evidence/P0-02-backend-build.log`.

Phân loại: `BACKEND_BUILD = PASS`.

## 9. Full automated tests

Lệnh:

```powershell
Set-Location backend
.\mvnw.cmd test
```

Kết quả:

| Chỉ số | Số lượng |
|---|---:|
| Surefire suite | 211 |
| Tổng test | 833 |
| PASS | 801 |
| FAIL | 9 |
| ERROR | 23 |
| SKIPPED | 0 |
| Hang/crash | 0 |

- **FAIL**, exit code 1.
- Maven total time: 08:04; shell elapsed khoảng 489.4 giây.
- Evidence đầy đủ: `docs/hotel-report/fix-progress/evidence/P0-02-backend-tests.log`.

### Failing/error suites

| Suite | FAIL | ERROR | Nhóm nguyên nhân |
|---|---:|---:|---|
| `SubscriptionControllerIntegrationTest` | 0 | 6 | WebMvc slice thiếu dependency/metrics bean |
| `CredentialRegistrationIntegrationTest` | 0 | 5 | H2 shared database bị drop nhưng cached context còn dùng |
| `ReservationLifecycleLockingTest` | 0 | 3 | Fixture thiếu `PropertyRefundRequestRepository` |
| `HotelControllerIntegrationTest` | 0 | 2 | WebMvc slice thiếu `PublicPlacementDisclosureService` |
| `ManualTransferConfirmationIntegrationTest` | 2 | 0 | Payment - deferred |
| `BackendApplicationTests` | 0 | 1 | Payment encryption property/context - deferred |
| `AvatarUploadIntegrationTest` | 0 | 1 | Cleanup vướng FK `AUTH_REFRESH_TOKENS` |
| `MockPaymentControllerIntegrationTest` | 0 | 1 | Multiple `@SpringBootConfiguration`; payment suite deferred |
| `PackagedLocationImportIntegrationTest` | 0 | 1 | Multiple `@SpringBootConfiguration` |
| `PaymentSessionConcurrencyIntegrationTest` | 0 | 1 | Multiple `@SpringBootConfiguration`; payment deferred |
| `PropertySearchControllerIntegrationTest` | 1 | 0 | Numeric JSON assertion `300000.0` so với `300000` |
| `RefundLifecycleConcurrencyIntegrationTest` | 0 | 1 | Multiple `@SpringBootConfiguration`; payment deferred |
| `TenantIsolationIntegrationTest` | 1 | 0 | HTTP contract lệch 404/409 |
| `UnicodeAndInventoryIntegrationTest` | 0 | 1 | Multiple `@SpringBootConfiguration` |
| `FinancialPerformanceIntegrationTest` | 1 | 0 | p95 4191.5 ms vượt budget 3000 ms |
| `PropertyPaymentConfigurationIntegrationTest` | 1 | 0 | Payment - deferred |
| `AuthExceptionIntegrationTest` | 1 | 0 | Error response/message contract lệch |
| `EndpointSecurityArchitectureTest` | 1 | 0 | 7 endpoint thiếu security annotation theo architecture rule |
| `FeatureGateIntegrationTest` | 1 | 0 | Expected 403, actual 200 |

## 10. Runtime startup baseline

Lệnh ứng dụng tương đương đã chạy:

```powershell
Set-Location backend
java -jar target/backend-0.0.1-SNAPSHOT.jar `
  --spring.profiles.active=e2e `
  --server.port=8082 `
  --app.e2e-fixtures.enabled=false `
  --app.location-import.enabled=false `
  --payment.sandbox.enabled=false `
  --payment.production.enabled=false `
  --payment.demo.enabled=false `
  --payment.provider-recovery.enabled=false
```

`JWT_SECRET`, `PROPERTY_PAYMENT_ENCRYPTION_KEY` và placeholder bắt buộc được inject bằng biến môi trường ngẫu nhiên chỉ trong process; giá trị không được in hoặc lưu.

Kết quả:

- Tomcat khởi động trên port 8082.
- `BackendApplication` startup hoàn tất sau 40.42 giây.
- Dependency injection hoàn tất; không có context creation error.
- H2 connection và JPA initialization PASS.
- WebSocket simple broker khởi động.
- Background reservation hold, outbox và provider recovery scheduler thực thi không lỗi trong startup window; payment mode vẫn tắt.
- Process được dừng đúng PID sau probe; port 8082 đã được giải phóng.

Evidence:

- `docs/hotel-report/fix-progress/evidence/P0-02-backend-startup.stdout.log`
- `docs/hotel-report/fix-progress/evidence/P0-02-backend-startup.stderr.log`
- `docs/hotel-report/fix-progress/evidence/P0-02-backend-runtime-probes.log`

Phân loại: `BACKEND_STARTUP = PASS_WITH_DEGRADED_HEALTH`.

## 11. Endpoint probe

| Endpoint | HTTP | Kết quả |
|---|---:|---|
| `/actuator/health` | 503 | FAIL - MailHealthIndicator không có SMTP password |
| `/actuator/health/liveness` | 200 | PASS |
| `/actuator/health/readiness` | 503 | FAIL - mail contributor nằm trong readiness aggregate hiện tại |
| `/v3/api-docs` | 200 | PASS, JSON 177894 byte |
| `/swagger-ui/index.html` | 200 | PASS, HTML |
| `/api/public/locations/provinces` | 200 | PASS, JSON `[]` vì import tắt |

Không có database connection error, migration error, seed error, CORS error hoặc upload/static error trong phạm vi direct probes. Cross-origin CORS, upload lifecycle và persistent storage chưa được xác minh bởi baseline này.

## 12. Startup/runtime findings

| Phân loại | Trạng thái | Finding |
|---|---|---|
| `DEPENDENCY_INJECTION` | PASS runtime | Full E2E context tạo thành công khi có process-only config bắt buộc |
| `DATABASE_CONNECTION` | PASS E2E | H2 connection thành công |
| `MIGRATION` | NOT_RUN | Flyway tắt trong E2E; 56 SQL migration chưa rehearsal |
| `SEED` | PASS_DISABLED | Fixture và seed chủ động tắt, không có seed error |
| `AUTH_CONFIGURATION` | CONDITIONAL | JWT secret bắt buộc; runtime probe dùng random process-only value |
| `EXTERNAL_CONFIGURATION` | FAIL/PARTIAL | SMTP credential thiếu làm health/readiness 503 |
| `RUNTIME` | PASS_WITH_FINDING | API/OpenAPI chạy; mail health làm readiness không đạt |
| `PAYMENT_PENDING_CONFIGURATION` | DEFERRED | Không bật sandbox/production/demo, không gọi provider |

## 13. Phân nhóm blocker backend theo nguyên nhân gốc

Có **8 nhóm blocker non-payment** được nhận diện. Ngoài ra có **1 nhóm payment-deferred** không đủ điều kiện chọn trong phase này.

| Ưu tiên | Nhóm blocker non-payment | Tác động chính | Function ID tiêu biểu |
|---:|---|---|---|
| 1 | Shared H2 test database lifecycle/test isolation | 5 direct error registration, scheduled task thấy database rỗng, full-suite result phụ thuộc thứ tự | `AUTH-001`, `AUTH-010`, `AUTH-022`, `CROSS-042` |
| 2 | Spring test context/bootstrap và stale WebMvc slices | 6 subscription error, 2 hotel error, nhiều suite không chọn được application | `PROP-SUB-019`, `PUB-030`, `CROSS-041`, `CROSS-042` |
| 3 | Reservation locking fixture incomplete | 3 error, không kiểm chứng được lifecycle locking/refund coordination | `STAY-029` |
| 4 | Endpoint security/feature gate contract | Architecture test và 403/200 gate failure | `AUTH-019`, `PROP-OPS-029`, `PROP-SUB-021`, `CROSS-042` |
| 5 | HTTP/JSON assertion contract drift | Search decimal, tenant status và auth error response lệch | `PUB-006`, `CROSS-031`, `CROSS-042` |
| 6 | Avatar cleanup referential integrity | Test cleanup không xóa refresh token trước user | `AUTH-013` |
| 7 | SMTP health/readiness policy | Backend chạy nhưng readiness 503, chặn staging health gate | `CROSS-009`, `CROSS-010`, `CROSS-041` |
| 8 | Financial performance budget | p95 4191.5 ms vượt 3000 ms | `CROSS-018`, `CROSS-023`, `CROSS-024` |

Nhóm payment gồm property payment context/configuration, manual transfer, payment session và refund/provider tests. Toàn bộ được giữ nguyên là **`DEFERRED_PAYMENT_PHASE`** và không tham gia lựa chọn blocker tiếp theo.

## 14. Blocker backend được chọn duy nhất

### P0-02A - Shared H2 test database lifecycle làm full suite mất schema

Đây là **blocker non-payment duy nhất được đề xuất xử lý tiếp theo**. Không có sửa chữa nào được thực hiện trong baseline.

| Thuộc tính | Chi tiết |
|---|---|
| Root category | `BACKEND_TEST` + `DATABASE_CONNECTION/TEST_ISOLATION` |
| Direct failing suite | `CredentialRegistrationIntegrationTest` |
| Direct errors | 5/5 khi chạy trong full suite |
| Focused control run | PASS 5/5 khi chạy riêng |
| Failure location | `CredentialRegistrationIntegrationTest.cleanUsers()` dòng 55 |
| Missing table observed | `EMAIL_VERIFICATION_TOKENS` |
| Additional evidence | Background outbox scheduler cũng thấy `EMAIL_OUTBOX` missing/database empty |
| Shared config | `backend/src/test/resources/application-test.yml` dùng `jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1` + `ddl-auto:create-drop` |
| Contract files | `EmailVerificationToken.java`; production migration `V52__email_verification.sql` |
| Function ID | `AUTH-001`, `AUTH-010`, `AUTH-022`, `CROSS-042` |

### Root cause đã chứng minh ở mức baseline

1. Hibernate có tạo `email_verification_tokens` khi context test khởi tạo; entity và V52 đều tồn tại.
2. Nhiều Spring test context dùng chung named H2 database `testdb`, giữ database sống bằng `DB_CLOSE_DELAY=-1`, đồng thời dùng `create-drop`.
3. Full log cho thấy một context đóng EntityManagerFactory/Hikari và drop schema, trong khi context cache khác tiếp tục tham chiếu cùng named database.
4. Ngay sau đó scheduled task báo `EMAIL_OUTBOX` không tồn tại và suite registration báo database rỗng tại `@BeforeEach`.
5. Chạy riêng `CredentialRegistrationIntegrationTest` tạo schema sạch và PASS 5/5.

Vì vậy đây là lỗi isolation/lifecycle của test harness theo thứ tự toàn suite, không phải bằng chứng production registration logic hoặc migration V52 sai.

Focused diagnostic evidence: `docs/hotel-report/fix-progress/evidence/P0-02-backend-selected-blocker-focused.log`.

### Tại sao phải xử lý trước

- Full backend quality gate đang đỏ và kết quả phụ thuộc thứ tự chạy, làm mất độ tin cậy của mọi regression evidence dùng chung H2.
- Lỗi trực tiếp chặn 5 test cho registration/concurrency/validation và có dấu hiệu lan sang scheduled jobs, không chỉ một assertion cục bộ.
- Inventory hiện ghi các luồng `AUTH-001`, `AUTH-010`, `AUTH-022`, `CROSS-042` đã có evidence PASS; order-dependent database teardown làm evidence này không còn tái lập trong full suite.
- Đây là blocker test infrastructure non-payment, có phạm vi rõ và nên được ổn định trước khi đánh giá các failure nghiệp vụ độc lập.
- Production runtime vẫn khởi động, nên phase tiếp theo phải sửa test isolation tối thiểu và không được biến thành refactor authentication/permission.

## 15. Rủi ro demo và staging

### Demo

- Backend có thể khởi động và public API/Swagger dùng được.
- Full regression không đáng tin cậy do shared H2 lifecycle; một luồng có thể PASS riêng nhưng ERROR trong suite.
- Registration/email verification evidence có thể bị hiểu sai là production defect nếu không tách test isolation khỏi business behavior.
- Readiness 503 có thể khiến rehearsal script báo backend unavailable dù API đang phục vụ.

### Staging

- Không thể dùng full Maven suite làm quality gate ổn định trước khi xử lý P0-02A.
- SQL Server/Flyway migration chưa được rehearsal trong baseline E2E; H2 PASS không chứng minh production schema deploy được.
- SMTP health policy/credential chưa sẵn sàng cho readiness probe.
- Secret store, CORS/WebSocket production origin, persistent upload và topology deploy vẫn thiếu evidence; không xử lý trong lượt này.
- Payment giữ `DEFERRED_PAYMENT_PHASE`.

## 16. Hướng dẫn kiểm tra thủ công

### A. Xác nhận project và Git

1. Mở PowerShell tại repository root.
2. Chạy `git branch --show-current` và xác nhận `codex/ui-functional-audit-polish`.
3. Chạy `git rev-parse HEAD` và xác nhận commit `88f7da5d52dbcc6b802a44bcf19e9aca97ae905f`.
4. Chạy `rg --files -g '*.sln' -g '*.csproj' -g '*.fsproj' -g 'global.json'`; xác nhận không có kết quả.
5. Xác nhận `backend/pom.xml` và `backend/src/main/java/com/hotel/BackendApplication.java` tồn tại.

### B. Restore và build

1. Chạy `Set-Location backend`.
2. Chạy `.\mvnw.cmd -DskipTests dependency:go-offline`; kỳ vọng `BUILD SUCCESS`.
3. Chạy `.\mvnw.cmd -DskipTests package`; kỳ vọng `BUILD SUCCESS`.
4. Xác nhận `target/backend-0.0.1-SNAPSHOT.jar` tồn tại.

### C. Full regression và blocker đã chọn

1. Chạy `.\mvnw.cmd test`.
2. Với baseline hiện tại, đối chiếu tổng `833`, `9 failures`, `23 errors`, `0 skipped`.
3. Tìm `CredentialRegistrationIntegrationTest`; xác nhận 5 error tại `cleanUsers()` với `EMAIL_VERIFICATION_TOKENS not found`.
4. Chạy `.\mvnw.cmd '-Dtest=CredentialRegistrationIntegrationTest' test`.
5. Xác nhận focused run `5 tests`, `0 failures`, `0 errors`.
6. Nếu focused PASS nhưng full suite ERROR, xác nhận được tính order-dependent/shared-H2 isolation của P0-02A.

### D. Runtime E2E không lộ secret

1. Dùng terminal mới trong `backend/`.
2. Tạo giá trị process-only mà không in ra màn hình:

```powershell
$env:JWT_SECRET = ([guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N'))
$env:PROPERTY_PAYMENT_ENCRYPTION_KEY = ([guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N'))
$env:VNPAY_TMN_CODE = 'runtime-disabled'
$env:VNPAY_HASH_SECRET = [guid]::NewGuid().ToString('N')
```

3. Chạy JAR với profile/flags tại mục 10.
4. Chờ log `Started BackendApplication` và `Tomcat started on port 8082`.
5. Ở terminal khác, chạy lần lượt:

```powershell
Invoke-WebRequest http://127.0.0.1:8082/actuator/health/liveness -UseBasicParsing
Invoke-WebRequest http://127.0.0.1:8082/v3/api-docs -UseBasicParsing
Invoke-WebRequest http://127.0.0.1:8082/swagger-ui/index.html -UseBasicParsing
Invoke-WebRequest http://127.0.0.1:8082/api/public/locations/provinces -UseBasicParsing
```

6. Kỳ vọng bốn endpoint trên trả HTTP 200.
7. Probe `/actuator/health` và `/actuator/health/readiness`; baseline hiện trả 503 và log `MailHealthIndicator` do thiếu SMTP password.
8. Dừng process bằng `Ctrl+C`, sau đó xóa bốn biến process-only bằng `Remove-Item Env:JWT_SECRET,Env:PROPERTY_PAYMENT_ENCRYPTION_KEY,Env:VNPAY_TMN_CODE,Env:VNPAY_HASH_SECRET`.

## 17. Evidence index

| Evidence | Nội dung |
|---|---|
| `evidence/P0-02-backend-restore.log` | Maven dependency restore PASS |
| `evidence/P0-02-backend-build.log` | Backend package PASS |
| `evidence/P0-02-backend-tests.log` | Full 833-test regression và stack trace |
| `evidence/P0-02-backend-selected-blocker-focused.log` | Credential registration focused PASS 5/5 |
| `evidence/P0-02-backend-startup.stdout.log` | Startup, H2, health error và request log |
| `evidence/P0-02-backend-startup.stderr.log` | JVM stderr |
| `evidence/P0-02-backend-runtime-probes.log` | HTTP status của health/OpenAPI/Swagger/public API |

## 18. Trạng thái cuối

**P0-02 BACKEND BASELINE: COMPLETE**  
**Backend restore: PASS**  
**Backend build: PASS**  
**Backend automated regression: FAIL**  
**Backend runtime: PASS_WITH_DEGRADED_HEALTH**  
**Selected next blocker: P0-02A - Shared H2 test database lifecycle/test isolation**  
**Payment: DEFERRED_PAYMENT_PHASE**

Dừng tại đây. Không sửa P0-02A, không chuyển sang blocker khác, không xử lý payment.
