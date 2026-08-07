# Đánh giá mức độ sẵn sàng triển khai

Ngày kiểm tra: 2026-08-05<br>
Kết luận: **NOT_READY**

## 1. Tóm tắt bằng chứng

| Hạng mục | Lệnh/kiểm tra | Kết quả |
|---|---|---|
| Frontend build | `npm run build` trong `frontend/` | PASS, exit 0; Angular generation 40.806 giây trong lần kiểm tra P0-01D |
| Frontend unit test | `npm test -- --watch=false` | FAIL toàn suite do payment exceptions: 105 file, 103 pass/2 fail; 317 test, 315 pass/2 fail; **0 unhandled error**. Non-payment regression PASS; hai payment wording test là `DEFERRED_PAYMENT_PHASE` |
| Backend compile | `mvnw.cmd -DskipTests compile` | PASS |
| Backend package | package với encryption key tạm chỉ trong process | PASS |
| Backend test | `mvnw.cmd test` | FAIL tổng thể do blocker cũ/payment-deferred: 837 test, 828 PASS, 5 FAIL, 4 ERROR, 0 SKIPPED; email-focused PASS 81/81 và P0-02A đến P0-02I guards PASS 131/131; không có email regression mới |
| Backend E2E runtime | profile E2E ở cổng 8082, nạp `.env.local` | PASS: startup, liveness, overall health, readiness, OpenAPI, Swagger và public search đều HTTP 200; SMTP credential hợp lệ, không có auto-send khi startup; tiến trình đã dừng |
| Frontend runtime | HTTPS cổng 4200 | 41 ảnh được chụp; có console/runtime warning và 4 route management trả 403 |
| Docker topology | `docker-compose.yml` | PARTIAL: chỉ có SQL Server |
| CI/CD | tìm workflow trong repository | FAIL: chưa thấy pipeline deploy/quality gate |

Build thành công không đủ để kết luận deploy-ready vì regression suite, secret/config, payment callback, runtime permission và topology vận hành còn blocker.

## 2. Kiến trúc triển khai hiện tại

```text
Browser
  -> Angular dev server/proxy (localhost:4200)
  -> Spring Boot chạy trực tiếp bằng Maven/Java (localhost:8080 hoặc 8082 E2E)
  -> SQL Server container/host (localhost:1433) hoặc H2 cho E2E
  -> SMTP/OAuth/Payment sandbox bên ngoài
```

`docker-compose.yml` chỉ định nghĩa `mcr.microsoft.com/mssql/server:2022-latest`, named volume và publish `1433:1433`. Chưa có Dockerfile frontend/backend, private application network, application health dependency, reverse proxy/TLS, resource limit, log strategy, migration job hoặc backup/restore workflow.

## 3. Dependency cần có

- Node.js/npm tương thích Angular 22; project khai báo npm 11.1.0.
- Java 21 và Maven Wrapper.
- SQL Server 2022 hoặc profile H2 chỉ dành cho test/E2E.
- Trình duyệt hiện đại; chứng chỉ localhost nếu chạy script HTTPS.
- Secret store/environment cho DB, JWT, encryption, SMTP, OAuth và payment.
- Public HTTPS domain cho frontend/API và callback provider ở staging/production.

## 4. Chạy local có kiểm soát

### Database SQL Server

1. Tạo `.env` hoặc truyền `MSSQL_SA_PASSWORD` ngoài source.
2. Chạy `docker compose up -d sqlserver`.
3. Xác nhận SQL Server ready trước khi start backend; Compose hiện chưa có healthcheck.

### Backend

1. Sao chép `.env.example` thành file local bị ignore; không commit giá trị.
2. Điền tối thiểu DB/JWT/encryption cho profile SQL Server hoặc dùng script/profile E2E theo hướng dẫn project.
3. Chạy `backend/run-local.ps1` nếu dùng workflow local hiện có, hoặc Maven Wrapper với profile phù hợp.
4. Kiểm tra `/actuator/health` và public API; không dùng cổng 8080 nếu đang thuộc project khác.

### Frontend

1. Trong `frontend/`, chạy `npm ci` khi lockfile và registry sẵn sàng.
2. Chạy `npm start` cho HTTP proxy hoặc `npm run start:https` khi chứng chỉ localhost đã có.
3. Xác nhận API proxy/base URL trỏ đúng backend và origin khớp CORS/WebSocket.

## 5. Build artifact

### Frontend

- Lệnh: `npm run build`.
- Kết quả audit: bundle ban đầu 1.11 MB raw, khoảng 208.78 kB estimated transfer.
- Warning: CSS `property-payment-configuration.component.css` vượt budget 4.56 kB; `admin-layout.css` vượt 272 byte; `@stomp/stompjs` và `sockjs-client` là CommonJS.
- Cần phục vụ artifact bằng web server/reverse proxy, cấu hình SPA fallback và cache policy.

### Backend

- Lệnh compile: `mvnw.cmd -DskipTests compile`.
- Lệnh package dự kiến: `mvnw.cmd package` sau khi test xanh và secret bắt buộc được inject.
- Artifact Spring Boot cần chạy bằng user không đặc quyền, có readiness/liveness, memory limit và graceful shutdown.

## 6. Database và migration

- SQL Server URL/user/password đọc từ environment; password là bắt buộc.
- Flyway bật với 56 migration, nhưng Hibernate `ddl-auto: update` vẫn bật.
- Trước deploy phải đặt `ddl-auto: validate` hoặc `none` theo policy, chạy migration trong bước có kiểm soát và ghi version.
- Phải diễn tập backup/restore trước thay đổi production; mỗi migration quan trọng cần pre-check, verification và rollback/forward-fix plan.
- Không publish cổng database ra Internet; Compose hiện publish 1433 và cần giới hạn về localhost hoặc private network.

## 7. Lỗi đang chặn deploy

### Cập nhật P0-SMTP-VERIFY (2026-08-06)

- **PASS** cho SMTP/email regression và runtime readiness; preview 9/9 đã được xác nhận trước lượt này và không gửi lại.
- Email/auth/booking/invoice/outbox focused PASS 81/81; P0-02A đến P0-02I guards PASS 131/131.
- Full backend: 837 total, 828 PASS, 5 FAIL, 4 ERROR, 0 skipped; toàn bộ FAIL/ERROR còn lại đã tồn tại trước SMTP changes.
- Package PASS. Runtime 8082: startup, liveness, overall health, readiness, OpenAPI, Swagger và public search HTTP 200.
- Invoice PDF fixture mở/render độc lập thành 2 trang A4, 5.105 byte; MIME attachment `application/pdf` PASS.
- `.env.local` bị ignore và không track; secret scan không thấy SMTP credential, bearer token dài hoặc private key trong source/evidence.
- Tổng thể vẫn **NOT_READY** do backend blocker non-email, payment, topology, CI/CD và staging secret management. SMTP/readiness 503 không còn là blocker khi nạp đúng environment.
- Báo cáo: `docs/hotel-report/fix-progress/P0-SMTP-VERIFY.md`.

| Mức | Blocker | Bằng chứng/tác động | Hành động tối thiểu |
|---|---|---|---|
| P0 | Backend regression không xanh | P0-02A đến P0-02G đã PASS; hiện còn 8 failure + 4 payment-deferred error/836 test | Tiếp tục từng blocker độc lập; đề xuất P0-02H cho `EndpointSecurityArchitectureTest`, không gộp payment/SMTP |
| P0 | Frontend regression có payment exceptions | P0-01A, P0-01B, P0-01C và P0-01D đã PASS; 315/317 test PASS, chỉ còn hai payment wording test `DEFERRED_PAYMENT_PHASE`, 0 unhandled error | Frontend P0 là `READY_WITH_PAYMENT_EXCEPTIONS`; chỉ xử lý hai test còn lại trong Payment Sandbox phase |
| P0 | Payment chưa có sandbox E2E | Thiếu merchant/project/key/callback public | Hoàn tất ít nhất simulator ký hoặc một provider sandbox |
| P0 | Permission management không nhất quán | 4 route owner fixture trả 403 | Chốt role matrix; sửa seed/guard/API authority và test |
| P1 | Runtime console parse error | `SyntaxError: Unexpected token '<'` khi điều hướng admin/management | Xác định request trả HTML thay JSON/JS, sửa proxy/base path/fallback |
| P1 | Chưa có full-stack container/deploy topology | Compose chỉ có SQL Server | Tạo Dockerfile/Compose staging hoặc runbook dịch vụ tương đương |
| P1 | Secret/config production chưa hoàn chỉnh | JWT/encryption/payment/SMTP/OAuth/callback | Dùng secret store, validation khi startup và rotation plan |
| P1 | Upload không có storage bền vững | filesystem `uploads/` | Gắn persistent volume hoặc object storage, backup/lifecycle |
| P1 | Không có CI/CD quality gate | Không thấy workflow | Build, test, scan, migration check và deploy approval |
| P2 | Bundle/CSS/CommonJS warning | Build có warning | Tối ưu trước production, không phải blocker demo đơn lẻ |

## 8. Phân tích lỗi test nổi bật

### Backend

Context test lỗi từ `application.yml` do `PROPERTY_PAYMENT_ENCRYPTION_KEY` fallback lồng sang `${JWT_SECRET}` nhưng test profile không cung cấp fallback này. Evidence chi tiết nằm ở `backend/target/surefire-reports/com.hotel.BackendApplicationTests.txt` tại thời điểm audit. Sau P0-02D không còn 2 ERROR của `HotelControllerIntegrationTest`; các failure/error còn lại thuộc context bootstrap, fixture cleanup, assertion/security/performance và payment-deferred.

### Cập nhật P0-02A

- Trạng thái blocker: **PASS** cho test isolation liên quan `AUTH-001`, `AUTH-010`, `AUTH-022`, `CROSS-042`.
- Root cause: `AuthControllerIntegrationTest`/registration dùng cached context trên `mem:testdb`; `ChatControllerIntegrationTest` cũng dùng `testdb` nhưng có `@DirtiesContext(AFTER_CLASS)`, nên Hibernate `create-drop` xóa schema của cached auth context.
- Thay đổi duy nhất: cấu hình `ChatControllerIntegrationTest` dùng H2 riêng `mem:chat-controller`; không sửa production code, assertion, payment hoặc SMTP.
- Checkpoint trước sửa: `stash@{0}`, object `c8597f4bb2bcf381574a048eb8d90b27682201e2`.
- Registration focused: PASS 5/5.
- Order-dependent guards: PASS 18/18 theo thứ tự thuận và PASS 18/18 theo thứ tự đảo; trước sửa lần lượt có 5 và 9 ERROR.
- Guard mở rộng auth/email/chat/WebSocket: PASS 26/26.
- Full backend regression: từ 801 PASS, 9 FAIL, 23 ERROR xuống 806 PASS, 9 FAIL, 18 ERROR; registration PASS 5/5, không phát sinh suite hoặc schema error mới.
- Backend package: PASS; runtime smoke trên 8082 PASS cho H2, liveness, OpenAPI, Swagger và public API.
- Overall health/readiness 503 do SMTP giữ nguyên ngoài phạm vi; payment giữ `DEFERRED_PAYMENT_PHASE`.
- Báo cáo/evidence: `docs/hotel-report/fix-progress/P0-02A.md`.

### Cập nhật P0-02B

- Trạng thái blocker: **PASS** cho `PROP-SUB-019`, `CROSS-041`, `CROSS-042`.
- Root cause: `@WebMvcTest` nạp `CorrelationIdFilter` qua MVC/security infrastructure nhưng không tạo custom wrapper `OperationalMetrics`; fixture chưa mock dependency mới của filter.
- Production context: đúng; runtime P0-02A đã khởi động và package/runtime observability hoạt động. Không sửa production code.
- Thay đổi duy nhất: thêm `@MockBean OperationalMetrics` vào `SubscriptionControllerIntegrationTest`.
- Checkpoint trước sửa: `stash@{0}`, object `f96161a18c04491d163dc6a9c09af5f28540964d`; worktree giữ nguyên 545 mục tại thời điểm checkpoint.
- Focused subscription controller: từ 0 PASS/6 ERROR thành PASS 6/6.
- Subscription service regression: PASS 18/18.
- P0-02A guards: PASS 18/18 ở mặc định, alphabetical và reversealphabetical.
- Full backend regression: từ 806 PASS, 9 FAIL, 18 ERROR sang 812 PASS, 9 FAIL, 12 ERROR; không phát sinh failure/error mới.
- Backend package: PASS. Runtime smoke không chạy lại vì thay đổi chỉ ở test source; production artifact không đổi về logic/configuration.
- Payment, SMTP và readiness 503 giữ nguyên ngoài phạm vi.
- Báo cáo/evidence: `docs/hotel-report/fix-progress/P0-02B.md`.

### Cập nhật P0-02C

- Trạng thái blocker: **PASS** cho regression evidence của `STAY-007`, `STAY-009`, `STAY-029`.
- Root cause: `ReservationService` có constructor dependency mới `PropertyRefundRequestRepository`; `ReservationLifecycleLockingTest` dùng `@InjectMocks` nhưng fixture không có mock tương ứng, nên field null và ba luồng trả DTO lỗi tại `mapToDTO()`.
- Production context: đúng; repository là Spring Data `JpaRepository`, nằm trong component/repository scan và runtime P0-02A đã khởi động thành công. Không sửa production code.
- Thay đổi duy nhất: thêm `@Mock PropertyRefundRequestRepository` và stub `findByReservationIdOrderByRequestedAtAsc(42L)` trả `List.of()` trong test fixture.
- Checkpoint trước sửa: `stash@{0}`, object `242f97711b036e4274d7c7c3e709d5486a65fcdc`; worktree giữ nguyên 546 mục tại thời điểm checkpoint.
- Focused locking: từ 1 PASS/3 ERROR thành PASS 4/4.
- Reservation/refund regression: PASS 36/36.
- P0-02A guards: PASS 18/18. Không chạy lại alphabetical/reversealphabetical vì thay đổi là Mockito unit fixture, không tạo Spring/H2 context.
- P0-02B regression: PASS 24/24.
- Full backend regression: từ 812 PASS, 9 FAIL, 12 ERROR sang 815 PASS, 9 FAIL, 9 ERROR; không phát sinh failure/error mới.
- Backend package: PASS. Runtime smoke không chạy lại vì thay đổi chỉ ở test source.
- Payment, SMTP và readiness 503 giữ nguyên ngoài phạm vi.
- Báo cáo/evidence: `docs/hotel-report/fix-progress/P0-02C.md`.

### Cập nhật P0-02D

- Trạng thái blocker: **PASS** cho `PROP-SUB-008`, `CROSS-042`.
- Root cause: `HotelController` autowire trực tiếp `PublicPlacementDisclosureService`; `@WebMvcTest(HotelController.class)` không load service ngoài MVC slice và fixture chưa có `@MockBean` cho dependency mới.
- Production context: đúng; `PublicPlacementDisclosureService` là `@Service`, runtime P0-02A đã khởi động thành công. Không sửa production code.
- Thay đổi duy nhất: thêm `@MockBean PublicPlacementDisclosureService` vào `HotelControllerIntegrationTest`; không stub vì hai test chỉ gọi `/my-hotels`.
- Checkpoint trước sửa: `stash@{0}`, object `9a8dc5f60fc63180daebfd14baa769f5ff85086d`; worktree giữ nguyên 547 mục tại thời điểm checkpoint.
- Focused Hotel controller: từ 0 PASS/2 ERROR thành PASS 2/2.
- Hotel/registration/disclosure regression: PASS 9/9.
- P0-02A guards: PASS 18/18; P0-02B regression PASS 24/24; P0-02C regression PASS 40/40.
- Full backend regression: từ 815 PASS, 9 FAIL, 9 ERROR sang 817 PASS, 9 FAIL, 7 ERROR; không phát sinh failure/error mới.
- Backend package: PASS. Runtime smoke không chạy lại vì thay đổi chỉ ở test source.
- Payment, SMTP và readiness 503 giữ nguyên ngoài phạm vi.
- Báo cáo/evidence: `docs/hotel-report/fix-progress/P0-02D.md`.

### Cập nhật P0-02E

- Trạng thái blocker: **PASS** cho `PUB-030`, kiểm chứng hẹp `CROSS-040` và integration harness `CROSS-042`.
- Root cause: hai test trong `com.hotel.integration` không chỉ định application class nên Spring tìm thấy bốn nested test-only `@SpringBootConfiguration` trước `com.hotel.BackendApplication`.
- Chỉ sửa hai test source: pin `BackendApplication.class` và cung cấp test-only encryption placeholder; không sửa production/payment source và không gọi provider.
- Focused PASS 1/1 và 2/2; combined/reverse PASS 3/3; location/import/inventory PASS 16/16.
- P0-02A/B/C/D guards PASS 18/18, 24/24, 40/40 và 11/11.
- Full backend: từ 833 total, 817 PASS, 9 FAIL, 7 ERROR sang 834 total, 820 PASS, 9 FAIL, 5 ERROR; ERROR giảm đúng 2, không phát sinh lỗi mới.
- Package PASS. Production runtime không bị tác động vì chỉ sửa test source; không bắt buộc chạy runtime lại.
- Payment giữ `DEFERRED_PAYMENT_PHASE`; SMTP/readiness 503 chưa xử lý.
- Báo cáo/evidence: `docs/hotel-report/fix-progress/P0-02E.md`.

### Cập nhật P0-02F

- Trạng thái blocker: **PASS** cho `AUTH-013`; regression context `AUTH-005`.
- Root cause: login trong avatar test tạo `auth_refresh_tokens`, nhưng `@BeforeEach` xóa parent user trước child token; production FK không cascade là đúng.
- Chỉ sửa `AvatarUploadIntegrationTest`: inject refresh-token repository và xóa child records trước parent; không sửa production/schema/payment.
- Focused avatar PASS 3/3 hai lần; avatar/upload PASS 10/10; auth/avatar ba thứ tự đều PASS 21/21.
- P0-02A/B/C/D/E guards PASS 18/18, 24/24, 40/40, 11/11 và 16/16.
- Full backend: từ 834 total, 820 PASS, 9 FAIL, 5 ERROR sang 834 total, 821 PASS, 9 FAIL, 4 ERROR; giảm đúng 1 ERROR và không có lỗi mới.
- Package PASS. Production runtime không bị tác động vì chỉ sửa test source; không bắt buộc chạy runtime lại.
- Bốn ERROR còn lại đều `DEFERRED_PAYMENT_PHASE`; SMTP/readiness 503 chưa xử lý.
- Báo cáo/evidence: `docs/hotel-report/fix-progress/P0-02F.md`.

### Cập nhật P0-02G

- Trạng thái blocker: **PASS** cho `PROP-SUB-021`; security context `AUTH-019`, `CROSS-042`.
- Contract đúng: authenticated read-only `/my-hotels` trả 200 không phụ thuộc feature; mutation thiếu `MAX_PROPERTIES` trả 403.
- P0-02H: **PASS**. `EndpointSecurityArchitectureTest` 1/1 PASS; 7/7 endpoint bị báo được chứng minh là false positive, 0 security gap thật. Security/controller matrix PASS 30/30; P0-02A đến P0-02G guards PASS; full backend 825 PASS / 7 FAIL / 4 ERROR; package PASS. Chỉ test source thay đổi nên runtime smoke không bắt buộc.
- P0-02I: **PASS**. Stable error envelope giữ status/code làm client contract và redacts raw `IllegalArgumentException` message; focused PASS 7/7, auth negative PASS 33/33, P0-02A đến P0-02H guards PASS, full backend 826 PASS / 6 FAIL / 4 ERROR, package PASS. Chỉ test source thay đổi nên runtime smoke không bắt buộc.
- P0-02J: **PASS**. Property-search pricing assertion nay so sánh giá trị số `BigDecimal` thay vì ép JSON integer thành `Double`; focused PASS 5/5, search/pricing PASS 18/18, P0-02A đến P0-02I guards PASS, full backend 827 PASS / 5 FAIL / 4 ERROR, package PASS. Chỉ test source thay đổi nên production artifact không bị tác động và runtime smoke không bắt buộc.
- Root cause là test contract cũ dùng legacy code `HOTEL`; production behavior đúng theo T198 và feature catalog hiện hành.
- Chỉ sửa test source, thêm test-only gated probe để giữ negative, positive và wrong-role coverage; không sửa production/frontend.
- Focused PASS 5/5; feature policy PASS 38/38; hotel regression PASS 11/11.
- P0-02A–F guards PASS 18/18, 24/24, 40/40, 11/11, 16/16 và 21/21.
- Full backend: từ 834 total, 821 PASS, 9 FAIL, 4 ERROR sang 836 total, 824 PASS, 8 FAIL, 4 ERROR; giảm đúng 1 FAIL, không có lỗi mới.
- Package PASS; runtime smoke không bắt buộc vì production source không đổi.
- Bốn ERROR tiếp tục `DEFERRED_PAYMENT_PHASE`; SMTP/readiness 503 chưa xử lý.
- Báo cáo/evidence: `docs/hotel-report/fix-progress/P0-02G.md`.

Không nên chỉ thêm một secret giả vào CI rồi coi toàn suite đã đạt; phải phân biệt lỗi khởi tạo context với các test nghiệp vụ còn lỗi.

### Frontend

P0-01A đã loại bỏ 3 failure của `chat.service.spec.ts`. P0-01B đã loại bỏ failure của `partner-overview.component.spec.ts`. P0-01C đã loại bỏ unhandled error do parent account-settings fixture thiếu Observable mock `listSocialIdentities()`. P0-01D đã loại bỏ subscription policy presentation failure bằng cách đồng bộ expectation với UI tiếng Việt. Hai failure còn lại đều là payment wording và được gắn `DEFERRED_PAYMENT_PHASE`.

### Cập nhật P0-01A

- Trạng thái blocker: **PASS** theo phạm vi `CROSS-011`, `CROSS-015`; regression guard `CROSS-014`.
- Git checkpoint trước sửa: object `961d725beb2226504b64f5fbce9a8d143fdfdfba` (hiện là `stash@{3}` sau khi tạo checkpoint P0-01D).
- Focused test: 1 file PASS, 3/3 test PASS.
- Full frontend regression: file chat PASS; toàn suite vẫn FAIL với 4 test fail và 1 unhandled error không thuộc blocker này.
- Frontend production build: PASS, exit 0; các warning CSS budget/CommonJS đã có vẫn còn.
- Backend build/test: không chạy lại vì P0-01A chỉ thay đổi frontend test fixture, không ảnh hưởng source hoặc contract backend.
- Bằng chứng và hướng dẫn kiểm tra: `docs/hotel-report/fix-progress/P0-01A.md`.

### Cập nhật P0-01B

- Trạng thái blocker: **PASS** theo phạm vi `PROP-SUB-006`; regression guard `AUTH-019`.
- Git checkpoint trước sửa: `stash@{2}`, object `d320fe0f922d0de594df1e1e1d685481ac6eec0f`.
- Focused `PartnerOverviewComponent`: 1 file PASS, 3/3 test PASS.
- Full frontend regression: partner overview PASS; toàn suite giảm từ 4 fail xuống 3 fail và vẫn còn 1 unhandled error ngoài phạm vi.
- Chat regression guard: `chat.service.spec.ts` PASS 3/3; `CROSS-011`, `CROSS-014`, `CROSS-015` không bị ảnh hưởng.
- Frontend production build: PASS, exit 0; warning CSS budget/CommonJS đã có vẫn còn.
- Backend build/test: không chạy lại vì P0-01B chỉ thay đổi frontend test fixture.
- Bằng chứng và hướng dẫn kiểm tra: `docs/hotel-report/fix-progress/P0-01B.md`.

### Cập nhật P0-01C

- Trạng thái blocker: **PASS** cho `AUTH-018`; chỉ sửa parent account-settings test fixture, không sửa production code.
- Git checkpoint trước sửa: `stash@{1}`, object `ba174d9201a96513356ef3f27f1a7f954b467efd`.
- Focused `account-settings.component.spec.ts`: PASS 1/1 file, 3/3 test; không còn `Errors`, `Unhandled Errors` hoặc async task treo.
- Frontend production build: PASS, exit 0; Angular generation 40.050 giây; warning CSS budget/CommonJS hiện hữu không đổi.
- Chat guard `CROSS-011`/`CROSS-014`/`CROSS-015`: PASS 3/3.
- Partner guard `PROP-SUB-006`/`AUTH-019`: PASS 3/3.
- Full frontend regression: 102/105 file PASS, 314/317 test PASS, 3 test FAIL và 0 unhandled error; số test fail không đổi, unhandled error giảm từ 1 xuống 0.
- Ba failure còn lại: một subscription policy và hai payment wording; không failure nào được sửa trong P0-01C.
- Backend build/test: không chạy lại vì P0-01C chỉ thay đổi frontend test fixture.
- Bằng chứng và hướng dẫn kiểm tra: `docs/hotel-report/fix-progress/P0-01C.md`.

### Cập nhật P0-01D

- Trạng thái blocker: **PASS** cho `PROP-SUB-018`; context regression `PROP-SUB-019`.
- Production behavior: **ĐÚNG**. Policy vẫn fail-closed, không tạo downgrade/credit mutation khi chưa có policy được phê duyệt; UI hiển thị trạng thái chặn bằng tiếng Việt.
- Root cause: hai expectation tiếng Anh trong spec đã cũ sau khi billing UI được Việt hóa; enum/status/API contract không thay đổi.
- Git checkpoint trước sửa: `stash@{0}`, object `d9c829cd0d3b45e13231b1db24aff394ed04f110`.
- Focused `SubscriptionBillingComponent`: PASS 1/1 file, 4/4 test, exit 0.
- Frontend production build: PASS, exit 0; Angular generation 40.806 giây; warning CSS budget/CommonJS hiện hữu không đổi.
- Regression guards: chat, partner approval và account settings PASS 3/3 file, 9/9 test; giữ `CROSS-011`, `CROSS-014`, `CROSS-015`, `PROP-SUB-006`, `AUTH-019`, `AUTH-018`.
- Full frontend regression: 103/105 file PASS, 315/317 test PASS, 2 test FAIL, 0 unhandled error.
- Hai failure còn lại: profile payment wording và payment result wording; cả hai là `DEFERRED_PAYMENT_PHASE`, không sửa trong P0-01D.
- Backend build/test: không chạy lại vì không có backend source/contract thay đổi.
- Bằng chứng và hướng dẫn kiểm tra: `docs/hotel-report/fix-progress/P0-01D.md`.

Đánh giá frontend sau P0-01D:

```text
Frontend build: PASS
Frontend non-payment regression: PASS
Payment wording regression: DEFERRED_PAYMENT_PHASE
Frontend P0 readiness: READY_WITH_PAYMENT_EXCEPTIONS
```

Không ghi full frontend suite PASS vì hai payment wording test vẫn FAIL có chủ đích.

## 9. Cấu hình đã có và còn thiếu

### Đã có contract trong source

- DB URL/user/password, JWT expiration, CORS/WebSocket origins.
- Mail host/port/user/password và outbox switches.
- Payment mode switches, simulator, VNPay, ZaloPay và provider recovery.
- Google/Facebook backend IDs/secrets.
- Actuator health/info/metrics.

### Thiếu hoặc chưa xác minh

- Giá trị secret production và startup validation theo từng environment.
- MoMo properties trong `application.yml`/`.env.example` dù Java config đang đọc chúng.
- Merchant sandbox, IP whitelist/domain/callback registration và end-to-end evidence.
- SMTP sandbox inbox/delivery evidence; OAuth console origins/domains/test accounts.
- Object storage/persistent upload volume; backup/restore.
- Production issuer, TLS/reverse proxy, DNS, observability sink, alerting và log retention.

Chi tiết nằm trong `05-missing-config-and-keys.md`.

## 10. Topology deploy đề xuất

1. CDN/static web hoặc Nginx phục vụ Angular qua HTTPS.
2. Reverse proxy/API gateway chuyển `/api` và WebSocket đến Spring Boot.
3. Backend chạy container/service không đặc quyền, ít nhất hai instance nếu cần HA; session vẫn stateless nhưng scheduler cần thiết kế tránh chạy trùng.
4. Managed SQL Server/private network, encryption, backup tự động và migration job một lần.
5. Object storage cho upload; SMTP/OAuth/payment sandbox ở staging, production credentials tách riêng.
6. Secret manager, centralized logs/metrics/traces, alert cho payment callback/recovery/outbox.

## 11. Checklist để đổi trạng thái

Chỉ được đổi từ `NOT_READY` khi tối thiểu:

- [ ] Full frontend test xanh, không có unhandled error.
- [ ] Full backend test xanh hoặc có danh sách quarantine được phê duyệt, không che lỗi luồng demo.
- [ ] Smoke test public, customer, admin, owner và staff role đều đạt.
- [ ] Không còn 403 ngoài dự kiến và không còn `Unexpected token '<'`.
- [ ] Payment simulator ký hợp lệ hoặc một provider sandbox chạy create-return-callback-refund/recovery.
- [ ] Secret/config validation, CORS, WebSocket, issuer và HTTPS domain đã chốt.
- [ ] Full-stack deploy topology có healthcheck, private DB, persistent storage và rollback.
- [ ] Migration rehearsal, backup/restore drill và CI/CD quality gate có bằng chứng.

Nếu chỉ phục vụ buổi báo cáo trên một máy local, có thể tạo demo profile riêng với fixture, payment simulator, script start/stop và checklist rehearsal; trạng thái đó vẫn không đồng nghĩa production-ready.
## P0-02K update (2026-08-06)

- Tenant/invoice focused matrix: PASS `15/15`; selected P0/security/email guards: PASS `115/115`.
- Full backend: `840 total / 833 PASS / 3 FAIL / 4 ERROR / 0 SKIPPED`; P0-02K failure is removed. Remaining failures/errors are payment-deferred. The previously observed performance failure passed in this run without a P0-02K code change.
- Package: PASS (`BUILD SUCCESS`).
- Runtime artifact smoke: BLOCKED by existing local startup/environment issues unrelated to P0-02K: SQL Server Flyway migration failure in the configured runtime, then H2 demo initialization failed on duplicate null location slug. The already-running service on port 8080 returned 401 for health/OpenAPI. No email was sent.
- Readiness status for P0-02K: `BLOCKED_RUNTIME_SMOKE`; tenant contract and automated regression are green, but the mandatory production-change runtime gate is not fully satisfied.
## P0-03A runtime profile isolation (2026-08-06)

- Status: **PASS**. Packaged runtime now has an explicit `local-h2` profile using H2 + Hibernate `create-drop`; Flyway is disabled only for this disposable profile. Base SQL Server runtime still loads all 56 migrations from `classpath:db/migration`.
- Before: packaged `test` profile did not contain `src/test/resources/application-test.yml`, fell back to base Flyway and failed at `V1__unicode_search_inventory.sql`, line 1, `SET XACT_ABORT ON`.
- After: `local-h2` startup PASS; H2 connected; JPA schema initialized; no Flyway SQL Server migration loaded; location bootstrap completed `10270 added / 0 errors`.
- HTTP smoke: overall health, liveness, readiness, OpenAPI, Swagger and public property search all HTTP 200. SMTP health recovered to UP; all delivery switches remained disabled and no email was sent.
- Package PASS. Focused tenant/security/email guards PASS 45/45. Full backend: 840 total / 832 PASS / 4 FAIL / 4 ERROR; the additional non-payment failure is the already-known flaky financial performance budget, not a new P0-03A regression. Payment failures/errors remain deferred.
- Evidence and manual verification: `fix-progress/P0-03A.md`.
## Status reconciliation before P0-02L

- P0-02K logic: `PASS`.
- P0-02K runtime smoke: `PASS_AFTER_P0-03A`.
- P0-03A: `PASS`.
- P0-03B: `NOT_REQUIRED`.
