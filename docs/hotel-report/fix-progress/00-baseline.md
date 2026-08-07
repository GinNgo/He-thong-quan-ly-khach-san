# Phase 1 - Baseline trước sửa lỗi

Ngày kiểm tra: 2026-08-05 (Asia/Saigon)<br>
Phạm vi: xác nhận baseline, không sửa code, không xử lý payment.<br>
Kết luận baseline: build frontend/backend **PASS**, regression frontend/backend **FAIL**, runtime **chạy được có điều kiện**, deployment vẫn **NOT_READY**.

## 1. Git baseline

| Thuộc tính | Giá trị |
|---|---|
| Branch | `codex/ui-functional-audit-polish` |
| Commit | `88f7da5d52dbcc6b802a44bcf19e9aca97ae905f` (`88f7da5`) |
| Working tree | Rất bẩn: 540 status entry |
| Tracked changed | 279 entry, gồm 5 file deleted |
| Untracked | 261 entry |
| Phạm vi báo cáo | `docs/hotel-report/` đang untracked |

Baseline này không quy kết nguồn gốc 540 thay đổi và không sửa/revert bất kỳ source nào. Phase 1 chỉ tạo file `docs/hotel-report/fix-progress/00-baseline.md`; build đã cập nhật artifact trong `frontend/dist/` và `backend/target/` theo hành vi bình thường của lệnh build/test.

## 2. Cấu trúc hệ thống

| Lớp | Project | Công nghệ |
|---|---|---|
| Frontend | `frontend/` | Angular 22 standalone, TypeScript 6, RxJS 7.8, PrimeNG 21, Bootstrap 5, Vitest 4, Playwright 1.61 |
| Backend | `backend/` | Java 21, Spring Boot 3.2.5, Spring MVC/Security/JPA/WebSocket/Actuator |
| Database production/local | SQL Server | JPA + Flyway; local service `MSSQLSERVER01` đang chạy |
| Database test/E2E | H2 in-memory | Profile `application-e2e.yml`, Flyway tắt, Hibernate `create-drop` |
| Container | Root `docker-compose.yml` | Chỉ có SQL Server image `2022-latest`; chưa có frontend/backend container |

Database local được nhận diện qua registry là SQL Server `17.0.1125.2`, Enterprise Developer Edition. Compose lại nhắm `mcr.microsoft.com/mssql/server:2022-latest`; cần thống nhất target/version trước staging. Truy vấn `@@VERSION` bằng Windows integrated authentication không thực hiện được do `Cannot generate SSPI context`, nên không có kiểm chứng SQL query trong Phase 1.

## 3. Toolchain và dependency chính

| Công cụ | Phiên bản baseline | Ghi chú |
|---|---|---|
| Node.js | `v26.2.0` | Cao hơn version thường dùng với project; cần chốt version CI/staging |
| npm | `11.13.0` | `package.json` khai báo package manager `npm@11.1.0` |
| Angular core/router | `^22.0.7` | Build bằng Angular CLI trong workspace |
| TypeScript | `~6.0.3` | Frontend |
| Vitest | `4.0.8` | Unit/component regression |
| Java | Microsoft OpenJDK `21.0.11` LTS | Khớp `pom.xml` Java 21 |
| Maven Wrapper | `3.9.16` | Dùng `backend/mvnw.cmd` |
| Spring Boot | `3.2.5` | Backend parent |
| .NET SDK | `10.0.301` | Có trên máy nhưng dự án Hotel không dùng .NET |
| Docker Engine/Client | `29.6.2` | Docker Desktop đang chạy |
| Docker Compose | `v5.3.1` | Compose hiện chỉ cấp database |

## 4. Lệnh cài dependency

### Frontend

```powershell
Set-Location frontend
npm ci
```

`npm ci` là lệnh đề xuất cho môi trường sạch/CI vì sử dụng lockfile. Không chạy cài lại dependency trong Phase 1 do `node_modules` hiện có và build/test đã thực thi được.

### Backend

Maven Wrapper tự tải dependency khi build. Có thể chuẩn bị cache bằng:

```powershell
Set-Location backend
.\mvnw.cmd dependency:go-offline
```

### SQL Server local bằng Compose

```powershell
docker compose up -d sqlserver
```

Lệnh yêu cầu `MSSQL_SA_PASSWORD` ngoài source. Phase 1 không chạy Compose vì máy đã có SQL Server service ở cổng 1433 và không được phép thay đổi dữ liệu/runtime thật.

## 5. Lệnh build

### Frontend

```powershell
Set-Location frontend
npm run build
```

### Backend

```powershell
Set-Location backend
.\mvnw.cmd -DskipTests package
```

`-DskipTests` vẫn compile test source nhưng không chạy regression. Full quality gate phải chạy riêng `mvnw.cmd test`.

## 6. Lệnh chạy

### Frontend HTTP

```powershell
Set-Location frontend
npm start
```

Script này proxy API/WebSocket đến `127.0.0.1:8080`. Baseline hiện không thể dùng an toàn vì cổng 8080 thuộc container `videoai`, không phải Hotel.

### Frontend HTTPS cho E2E/demo

```powershell
Set-Location frontend
npm run start:https
```

Script chạy `https://localhost:4200` và proxy đến Hotel backend ở `127.0.0.1:8082`. Phiên frontend hiện có đang chạy đúng lệnh này và trả HTTP 200.

### Backend local

```powershell
.\backend\run-local.ps1
```

Script đọc `.env.local`, yêu cầu `JWT_SECRET`, `GOOGLE_CLIENT_ID`, `FACEBOOK_APP_ID`, sau đó chạy Maven với `-Dmaven.test.skip=true spring-boot:run`. `-ValidateOnly` hiện PASS mà không in giá trị secret.

### Backend E2E

Backend có thể chạy profile E2E/H2 trên cổng 8082 khi truyền process-only JWT/encryption fixture và ba cặp username/password E2E. Không ghi credential mẫu vào tài liệu hoặc source.

## 7. Kết quả frontend build

**Trạng thái: PASS**

- Lệnh: `npm run build`
- Exit code: `0`
- Thời gian: `67.8s` tổng shell; Angular generation `60.792s`
- Initial bundle: `1.11 MB` raw, `208.78 kB` estimated transfer
- Output: `frontend/dist/frontend`

Warning không chặn build:

1. `property-payment-configuration.component.css` là 14.56 kB, vượt budget 4.56 kB.
2. `admin-layout.css` là 10.27 kB, vượt budget 272 byte.
3. `@stomp/stompjs` và `sockjs-client` là CommonJS/non-ESM.

Phân loại: `BUILD_FRONTEND` = PASS có warning.

## 8. Kết quả backend build

**Trạng thái: PASS**

- Lệnh: `.\mvnw.cmd -DskipTests package`
- Exit code: `0`
- Thời gian: `30.8s` tổng shell; Maven `23.285s`
- Artifact: `backend/target/backend-0.0.1-SNAPSHOT.jar`
- Spring Boot repackage thành công.

Phân loại: `BUILD_BACKEND` = PASS; kết quả này không chứng minh regression vì test đã skip.

## 9. Regression baseline

### Frontend

**Trạng thái: FAIL**

- Lệnh: `npm test -- --watch=false`
- 105 test file: 99 PASS, 6 FAIL
- 317 test: 309 PASS, 8 FAIL
- 1 unhandled error

Failure trực tiếp:

| File | Số test fail | Phân loại | Function ID |
|---|---:|---|---|
| `core/services/chat.service.spec.ts` | 3 | RUNTIME/TEST_SETUP | `CROSS-011`, `CROSS-015`; regression guard `CROSS-014` |
| `admin/partner-overview/partner-overview.component.spec.ts` | 1 | PERMISSION/FUNCTIONAL | `PROP-SUB-006` |
| `shared/components/form-dialog/form-dialog.spec.ts` | 1 | TEST_SETUP/cascade | `CROSS-032`, `CROSS-033` |
| `management/subscription-billing/subscription-billing.component.spec.ts` | 1 | POLICY/REGRESSION | `PROP-SUB-018`, `PROP-SUB-019` |
| `client/profile/profile.component.spec.ts` | 1 | `PAYMENT_PENDING_CONFIGURATION`/wording | Không chọn xử lý ở phase này |
| `client/payment-result/payment-result.spec.ts` | 1 | `PAYMENT_PENDING_CONFIGURATION`/wording | Không chọn xử lý ở phase này |

Unhandled error: mock trong `account-settings.component.spec.ts` không có `AuthService.listSocialIdentities()`, liên quan `AUTH-018`.

Root failure đầu suite là `ChatService` đọc `authService.logout$.subscribe()` tại `chat.service.ts:44`, trong khi mock tại `chat.service.spec.ts:19` không khai báo `logout$`. Sau khi TestBed khởi tạo lỗi, các test sau có dấu hiệu cascade `Cannot configure the test module when the test module has already been instantiated`.

### Backend

**Trạng thái: FAIL**

- Lệnh: `.\mvnw.cmd test`
- 211 suite, 833 test
- 9 failure, 23 error, 0 skipped

Nhóm nguyên nhân đã xác minh:

| Nhóm | Bằng chứng | Function ID tiêu biểu |
|---|---|---|
| Context secret/config | `BackendApplicationTests` không resolve nested fallback `PROPERTY_PAYMENT_ENCRYPTION_KEY:${JWT_SECRET}` | `AUTH-022`, `CROSS-042`; payment config chỉ ghi pending |
| Test slice thiếu bean | `HotelControllerIntegrationTest` thiếu `PublicPlacementDisclosureService`; subscription tests thiếu `OperationalMetrics` | `PROP-SUB-019`, `CROSS-041`, `CROSS-042` |
| H2 schema/cleanup | Thiếu `EMAIL_VERIFICATION_TOKENS`; avatar cleanup vướng FK refresh token | `AUTH-001`, `AUTH-010`, `AUTH-013` |
| Nhiều `@SpringBootConfiguration` | 5 integration suite không chọn được application | `PUB-030`, `CROSS-042` |
| Contract/assertion lệch | Search decimal, tenant 404/409, auth message, feature gate 403/200 | `PUB-006`, `PROP-OPS-029`, `CROSS-031` |
| Endpoint security | Architecture test phát hiện endpoint thiếu security annotation | `AUTH-019`, `PROP-OPS-029`, `CROSS-042` |
| Lifecycle test fixture | `PropertyRefundRequestRepository` null trong locking test | `STAY-029` |
| Performance | Financial report p95 `3425.9ms` > budget `3000ms` | `CROSS-018`, `CROSS-023`, `CROSS-024` |
| Payment-specific failures | Property config/manual confirmation/refund/payment session | `PAYMENT_PENDING_CONFIGURATION`, không chọn xử lý hiện tại |

## 10. Runtime baseline

### Kết quả chạy

**Hệ thống chạy được có điều kiện.**

- Frontend HTTPS cổng 4200: HTTP 200.
- Backend E2E lần đầu: Tomcat lên cổng 8082 nhưng application thoát sau startup vì thiếu `LUXESTAY_E2E_CUSTOMER_PASSWORD`, `LUXESTAY_E2E_ADMIN_PASSWORD`, `LUXESTAY_E2E_OWNER_PASSWORD` khi fixtures bật.
- Backend E2E lần hai với credential fixture sinh tạm trong process: public locations API HTTP 200; frontend HTTPS proxy đến API HTTP 200.
- Backend process kiểm thử đã dừng; cổng 8082 đã được giải phóng.

### Health

- `/actuator/health`: HTTP 503.
- `/actuator/health/liveness`: HTTP 200.
- `/actuator/health/readiness`: HTTP 200.

Overall health 503 trong khi readiness 200 là `DEPLOYMENT/CONFIGURATION` finding: monitoring/load balancer phải dùng endpoint đã chốt và health contributor ngoài (đặc biệt SMTP trong baseline không credential) không được làm tín hiệu vận hành mơ hồ.

### Runtime error

1. `RUNTIME/CONFIGURATION`: E2E fixture startup thiếu credential contract trong `.env.example`.
2. `RUNTIME/DEPLOYMENT`: overall health 503 dù liveness/readiness 200.
3. Từ browser audit trước đó: `SyntaxError: Unexpected token '<'` khi điều hướng admin/management, thường do request mong JSON/JavaScript nhưng nhận HTML.

Function ID: `CROSS-031`, `CROSS-032`, `CROSS-041`, `CROSS-042`, `PUB-030`.

### Console error

Không mở lại browser console trong Phase 1. Bằng chứng được giữ từ `07-ui-screenshots.md`:

- `SyntaxError: Unexpected token '<'` ở admin/management.
- Google FedCM/network warning trong social login.

Phân loại: `RUNTIME`, `STATIC_ASSET/PROXY`, `AUTHENTICATION`; Function ID `AUTH-016`, `CROSS-031`, `CROSS-032`.

### HTTP error

- Public API trực tiếp và qua frontend proxy: HTTP 200.
- `/api/auth/my-menu` khi chưa xác thực: HTTP 200; inventory đã ghi route nằm dưới matcher auth quá rộng. Cần xác định đây là contract có chủ ý hay authorization gap (`AUTH-019`).
- Unknown protected API: HTTP 401 JSON.
- Bốn route management trong browser audit trả/redirect 403 với owner fixture:
  - Property payment configuration: `PAYMENT_PENDING_CONFIGURATION`.
  - Refunds: `PAYMENT_PENDING_CONFIGURATION`.
  - Subscription billing: `PROP-SUB-017`, `PROP-SUB-019`, `PROP-OPS-029`.
  - Audit log: `CROSS-030`, `PROP-OPS-029`.

## 11. Missing configuration

`.env.local` tồn tại và `backend/run-local.ps1 -ValidateOnly` PASS; Phase 1 không đọc/in giá trị.

Các biến được source/E2E tham chiếu nhưng chưa có trong `.env.example` gồm:

- `AUTH_REFRESH_COOKIE_SECURE`, `JWT_REFRESH_EXPIRATION`.
- `LUXESTAY_E2E_CUSTOMER_USERNAME/PASSWORD`.
- `LUXESTAY_E2E_ADMIN_USERNAME/PASSWORD`.
- `LUXESTAY_E2E_OWNER_USERNAME/PASSWORD`.
- `LUXESTAY_E2E_API_URL`, `LUXESTAY_E2E_WEB_URL`, property fixture IDs.
- `MAIL_OUTBOX_ENABLED`, `MAIL_OUTBOX_DELIVERY_ENABLED`, scan/backoff/max-attempt variables.
- `DEMO_ACCOUNT_PASSWORD`.

Các hạng mục staging còn thiếu/chưa có evidence:

- Domain/TLS/reverse proxy và production CORS/WebSocket allowlist.
- Secret manager/rotation; startup validation theo profile.
- SMTP sandbox và health policy.
- OAuth provider console/origin/test account.
- Persistent upload/object storage và backup/lifecycle.
- CI/CD, centralized log/metrics/alert, migration/backup/rollback runbook.
- Full-stack Docker/service topology.

Toàn bộ VNPay/MoMo/ZaloPay/simulator key, merchant, callback và provider test được phân loại duy nhất là **`PAYMENT_PENDING_CONFIGURATION`** và không được tính là blocker sửa trong giai đoạn hiện tại.

## 12. Phân loại lỗi theo yêu cầu

| Nhóm | Trạng thái | Evidence chính |
|---|---|---|
| `BUILD_FRONTEND` | PASS_WITH_WARNINGS | Angular build exit 0 |
| `BUILD_BACKEND` | PASS | Maven package exit 0, test skipped |
| `RUNTIME` | FAIL/PARTIAL | E2E thiếu fixture vars; console parse error; chạy được khi inject fixture |
| `AUTHENTICATION` | PARTIAL | Auth core có evidence tốt; unauth menu 200 và OAuth ngoài chưa xác minh |
| `PERMISSION` | FAIL | 4 màn 403; endpoint security architecture test fail |
| `DATABASE` | FAIL | H2 schema/cleanup, Flyway/Hibernate policy, test fixture errors |
| `CONFIGURATION` | FAIL | E2E/env contract và health policy chưa đầy đủ |
| `STATIC_ASSET` | PARTIAL | Public UI/assets render; còn proxy HTML-vs-JSON console error và upload lifecycle gap |
| `DEPLOYMENT` | FAIL | Không có frontend/backend Dockerfile, CI/CD hoặc full-stack staging topology |
| `PAYMENT_PENDING_CONFIGURATION` | PENDING | Không xử lý trong phase hiện tại |

## 13. Danh sách blocker theo ưu tiên

Có **8 nhóm P0 đang mở**. `P0-07` upload/static chưa được tính là blocker độc lập vì public assets render được; lỗi avatar hiện nằm trong backend regression/DB cleanup và vẫn được theo dõi.

| Thứ tự | Blocker | Trạng thái/evidence | Function ID chính |
|---:|---|---|---|
| P0-01 | Frontend regression | 8 fail + 1 unhandled error | `CROSS-011`, `CROSS-015`, `PROP-SUB-006`, `AUTH-018`, `CROSS-032/033` |
| P0-02 | Backend regression | 9 failure + 23 error | `AUTH-001/010/013/022`, `PROP-SUB-019`, `PUB-030`, `STAY-029`, `CROSS-042` |
| P0-03 | Runtime errors | E2E default fail, health 503, console parse error | `CROSS-031/032/041/042`, `PUB-030` |
| P0-04 | Authentication/HTTP contract | unauth menu 200, OAuth chưa xác minh | `AUTH-016/017/019` |
| P0-05 | Role/Permission | 403 ngoài kỳ vọng và architecture test fail | `AUTH-019`, `PROP-OPS-008/029`, `CROSS-030/042` |
| P0-06 | Database/migration/seed | H2 table/FK/config và schema ownership chưa hội tụ | `AUTH-001/010/013`, `PUB-030`, `STAY-029`, `CROSS-042` |
| P0-08 | Full-stack environment config | env example thiếu runtime/E2E contract; staging config chưa chốt | `AUTH-015/016/017`, `CROSS-041/042` |
| P0-09 | Staging topology | Compose chỉ có DB; không Dockerfile app/CI/reverse proxy | `CROSS-041` + deployment evidence |

Theo dõi sau regression:

- `P0-07` upload/static: `AUTH-013`, `PROP-OPS-012/013/017/019`.
- Payment: luôn `PAYMENT_PENDING_CONFIGURATION`, không nằm trong thứ tự sửa hiện tại.

## 14. Blocker đầu tiên đề xuất xử lý

### P0-01A - ChatService frontend test fixture bị lệch contract

**Chỉ xử lý một blocker này ở bước kế tiếp.**

| Thuộc tính | Chi tiết |
|---|---|
| Module | Frontend core chat/realtime |
| Source liên quan | `frontend/src/app/core/services/chat.service.ts:44` |
| Test liên quan | `frontend/src/app/core/services/chat.service.spec.ts:13` |
| Function ID | `CROSS-011`, `CROSS-015`; regression guard `CROSS-014` |
| Lỗi trực tiếp | `Cannot read properties of undefined (reading 'subscribe')` |
| Nguyên nhân | `ChatService` đăng ký `AuthService.logout$`, nhưng test mock không cung cấp observable này |
| Tác động | 3 test chat fail trực tiếp; TestBed lỗi khởi tạo tạo nhiễu/cascade cho test sau |

### Tại sao xử lý trước

1. Đúng thứ tự bắt buộc `P0-01 Frontend build/regression`.
2. Không liên quan payment.
3. Phạm vi nhỏ, nguyên nhân tái hiện ổn định và có line evidence.
4. Có khả năng loại bỏ 3 failure trực tiếp và giảm lỗi TestBed cascade trước khi đánh giá các failure độc lập.
5. Chat/logout/realtime là chức năng nhìn thấy trong demo admin/customer; test fixture phải phản ánh đúng lifecycle production.

### Acceptance criteria cho blocker kế tiếp

- Given mock `AuthService`, when `ChatService` được inject, then `logout$` là observable hợp lệ và service khởi tạo không lỗi.
- Given socket offline, when gửi message, then service không publish.
- Given customer history request, when gọi client, then endpoint principal-scoped được dùng.
- Given hai message publish, then mỗi message có correlation ID mới.
- Focused chat test PASS, full frontend test được chạy lại, sau đó frontend build vẫn PASS.

## 15. Rủi ro với buổi báo cáo

1. Full regression đỏ khiến demo có thể lỗi ở nhánh chưa rehearsal.
2. Backend E2E không one-command từ `.env.example`; thiếu fixture credential làm app thoát sau khi Tomcat đã lên.
3. Cổng 8080 đang bị project khác chiếm; chạy nhầm proxy có thể nhận JSON/HTML từ hệ thống khác.
4. Bốn route management bị 403 làm gián đoạn phần trình bày owner.
5. Console `Unexpected token '<'` có thể che lỗi proxy/static/API routing.
6. Overall health 503 gây staging health check sai dù readiness 200.
7. Worktree 540 entry làm tăng rủi ro thay đổi chồng chéo và khó rollback.
8. Payment chưa cấu hình; phải giới thiệu rõ là `PAYMENT_PENDING_CONFIGURATION`, không demo giao dịch provider thật.

## 16. Điều kiện còn thiếu để deploy staging

- Full frontend/backend regression đạt quality gate hoặc quarantine có phê duyệt và không chạm luồng demo.
- Runtime profile từ `.env.example` có thể khởi động lặp lại, không cần secret ngầm.
- Role x route x API matrix đạt cho customer/admin/owner/staff.
- Proxy/API/static routing sạch console và không xung đột cổng.
- SQL Server target/version, Flyway/Hibernate schema ownership và seed strategy được chốt.
- Frontend/backend container hoặc service runbook, reverse proxy/TLS, healthcheck và rollback.
- Persistent upload, backup/restore, log/metrics/alert và CI/CD.
- Payment vẫn có thể tắt fail-closed ở staging cho đến phase cấu hình riêng.

## 17. Kiểm tra thủ công người dùng cần thực hiện

Sau khi blocker P0-01A được sửa ở phase kế tiếp, kiểm tra thủ công:

1. Đăng nhập customer, mở chat và xác nhận trạng thái offline không gửi message giả thành công.
2. Đăng nhập admin/support, mở chat dashboard và xem lịch sử đúng tài khoản/property.
3. Logout khi chat đang kết nối và xác nhận socket ngắt, không tự reconnect bằng session cũ.
4. Mở DevTools Network/Console, xác nhận request history đi tới Hotel API và không có `Unexpected token '<'`.

## 18. Bước tiếp theo chính xác

Chờ xác nhận để bắt đầu Phase 2 với **duy nhất P0-01A**:

1. Sửa tối thiểu mock/lifecycle test của `ChatService`; không sửa module khác.
2. Chạy focused `chat.service.spec.ts`.
3. Chạy full `npm test -- --watch=false` để đo số failure còn lại.
4. Chạy `npm run build`.
5. Tạo evidence cho blocker, cập nhật progress và dừng; không chuyển sang failure tiếp theo nếu chưa có xác nhận.
