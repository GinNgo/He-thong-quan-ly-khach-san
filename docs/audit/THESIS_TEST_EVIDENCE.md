# Test evidence inventory - 2026-07-29

## Verification addendum 2026-07-29

- Backend `cmd /c mvnw.cmd test`: **123 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS** in 98 seconds using the Maven `test` profile and H2 in-memory database.
- Frontend `npm run test -- --watch=false`: **36 test files, 73 tests passed, 0 failed**; Angular build phase completed before Vitest.
- Frontend `npm run build`: **PASS**; bundle generated with existing warnings for optional chaining, canvas in jsdom chart tests, and CommonJS STOMP/SockJS dependencies.
- These are CURRENT component/service/backend results only. They do not prove all 29 Admin routes or their data-backed mutations/E2E authorization work.

## Targeted Playwright smoke addendum

- Command: `npx playwright test e2e/home.spec.ts e2e/real-environment-smoke.spec.ts --workers=1 --retries=0 --reporter=line`
- Result: `2 passed, 3 skipped, 0 failed` in 21.8 seconds on 2026-07-28.
- Scope: public home and recovery smoke are CURRENT; authenticated cases were skipped because no `LUXESTAY_E2E_*` credentials were configured.
- The full 71-test suite remains `BLOCKED`; this subset does not clear the release gate.

## Admin E2E addendum (2026-07-29)

- `npx playwright test e2e/admin-flows.spec.ts --workers=1 --retries=0 --reporter=line`: **17 passed** in 1.1 minutes, but this suite mostly checks `body visible`; login waits on a broad `**/admin/**` URL and the authorization test contains `expect(true).toBeTruthy()`. Treat as `CURRENT_SMOKE_ONLY`, not data-backed proof.
- `npx playwright test e2e/admin-core-management.spec.ts --workers=1 --retries=0 --reporter=line`: **1 failed, 2 did not run**. The login helper using `admin/admin` remained at `/admin/login` with “Sai tài khoản hoặc mật khẩu.” The failure is reproducible evidence that the current Admin E2E environment lacks a valid LuxeStay account/backend fixture; it does not prove the roles/rooms UI itself is broken.
- Failure screenshot: `frontend/test-results/admin-core-management-Admi-cb8af-ssion-matrix-load-from-APIs-chromium/test-failed-1.png`.
- Conclusion: Admin read/mutation/authorization coverage remains `BLOCKED`; use `docs/audit/ADMIN_FUNCTIONAL_VERIFICATION_PLAN.md` and completion backlog instead of the 17 smoke passes.

## Current worktree test sources

### Backend integration/security

- backend/src/test/java/com/hotel/integration/AuthControllerIntegrationTest.java
- backend/src/test/java/com/hotel/integration/HotelControllerIntegrationTest.java
- backend/src/test/java/com/hotel/integration/PaymentControllerIntegrationTest.java
- backend/src/test/java/com/hotel/integration/PropertySearchControllerIntegrationTest.java
- backend/src/test/java/com/hotel/integration/PublicDiscoveryControllerIntegrationTest.java
- backend/src/test/java/com/hotel/integration/SubscriptionControllerIntegrationTest.java
- backend/src/test/java/com/hotel/integration/TenantIsolationIntegrationTest.java
- backend/src/test/java/com/hotel/integration/UnicodeAndInventoryIntegrationTest.java
- backend/src/test/java/com/hotel/security/EndpointSecurityArchitectureTest.java
- backend/src/test/java/com/hotel/security/FeatureGateIntegrationTest.java
- backend/src/test/java/com/hotel/security/PermissionInterceptorTest.java

### Backend services

- AuthServiceTest
- ChatServiceTest
- HotelServiceLogicImplTest
- LocationImportServiceTest
- PaymentServiceImplTest
- ReservationServiceTest
- SubscriptionFeatureServiceTest
- UserServiceTest

### Parallel notification/security tests

- NotificationControllerIntegrationTest
- NotificationChannelInterceptorTest
- NotificationServiceTest
- E2eFixtureInitializerIntegrationTest

Các file này chứng minh test intent và hiện diện trong worktree. Kết quả PASS hiện hành được ghi tại các bảng bên dưới từ lần chạy ngày 29/07/2026; khi code tiếp tục thay đổi phải chạy lại trước bản phát hành.

### Frontend E2E sources

- frontend/e2e/home.spec.ts
- frontend/e2e/home-search.spec.ts
- frontend/e2e/public-flows.spec.ts
- frontend/e2e/public-customer-quality.spec.ts
- frontend/e2e/customer-flows.spec.ts
- frontend/e2e/search-result.spec.ts
- frontend/e2e/search-booking-flow.spec.ts
- frontend/e2e/payment.spec.ts
- frontend/e2e/admin-core-management.spec.ts
- frontend/e2e/admin-flows.spec.ts
- frontend/e2e/owner-flows.spec.ts
- frontend/e2e/real-environment-smoke.spec.ts

## Result freshness

| Nhãn | Ý nghĩa | Cách dùng |
| --- | --- | --- |
| CURRENT | Lệnh vừa chạy trên worktree hiện hành | Có thể dùng cho kết luận |
| HISTORICAL | Kết quả có ngày trong audit/report cũ | Chỉ dùng làm bối cảnh |
| BLOCKED | Runner/môi trường/quyền khiến chưa xác minh | Không gọi là pass |
| SOURCE_ONLY | Có test file nhưng chưa có run result | Chưa đủ cho COMPLETE |

BASELINE_TEST_REPORT.md ghi backend 60/60 và frontend/E2E cũ; các số đó vẫn là HISTORICAL. Run CURRENT ngày 29/07/2026 là backend 123/123, frontend unit 73/73 và build pass; Admin shell smoke có 17 pass nhưng Admin core data-backed run bị BLOCKED.

## Current backend verification

| Trường | Giá trị |
| --- | --- |
| Command | .\mvnw.cmd test |
| Date/time | 2026-07-29 02:20-02:21 Asia/Saigon |
| Profile/data | Maven test profile, H2 in-memory |
| Test classes with reports | 29 |
| Tests run | 123 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Result | BUILD SUCCESS |
| Reports | backend/target/surefire-reports |

Run này bao gồm Auth, Chat, E2E fixture, Hotel, Payment, Property Search, Public Discovery, Subscription, Tenant Isolation, Unicode/Inventory, Endpoint Security, Feature Gate, Permission, Notification channel/controller/service và các service tests. Đây là evidence CURRENT tại thời điểm ghi nhận.

Lần chạy xác nhận ngày 29/07/2026 bằng `cmd /c mvnw.cmd test` hoàn tất BUILD SUCCESS và được dùng làm kết luận CURRENT. Các số 122/122 trước đó được giữ HISTORICAL.

## Current frontend verification

| Trường | Giá trị |
| --- | --- |
| Unit command | npm run test -- --watch=false |
| Unit date/time | 2026-07-29 02:25 Asia/Saigon |
| Test files | 36 passed |
| Tests | 73 passed |
| Failures | 0 |
| Result | PASS |
| Build command | npm run build |
| Build date/time | 2026-07-29 02:26 Asia/Saigon |
| Build result | Application bundle generation complete |

Frontend unit tests bao gồm notification.service, chat.service, chat widget, payment simulator/result, booking checkout, admin flows, property search và shared components. Build pass nhưng ghi cảnh báo NG8107 tại client-layout, canvas context chưa được triển khai trong môi trường test chart và CommonJS của STOMP/SockJS.

## Current Playwright verification attempt

| Trường | Giá trị |
| --- | --- |
| Discovery command | npx playwright test --list |
| Discovered scope | 71 tests in 12 files |
| Run command | npx playwright test |
| Run date | 2026-07-28 |
| Result | BLOCKED: command timeout after 184 seconds |
| Partial artifacts | 14 error-context files, 8 unique failed test artifacts, playwright-report/index.html |

Các lỗi quan sát được tập trung ở redirect URL của customer/admin và dữ liệu/gợi ý Home Search; suite không trả summary hoàn chỉnh nên không suy ra tổng pass/fail. Playwright phải được chạy lại sau khi ổn định backend fixture, route redirect và dữ liệu demo.

### Targeted smoke verification

| Trường | Giá trị |
| --- | --- |
| Command | `npx playwright test e2e/home.spec.ts e2e/real-environment-smoke.spec.ts --workers=1 --retries=0 --reporter=line` |
| Date/time | 2026-07-28 21:44 Asia/Saigon |
| Scope | 5 tests in 2 files |
| Result | 2 passed, 3 skipped, 0 failed, 21.8s |
| Meaning | Public home and recovery smoke are CURRENT; skipped authenticated tests had no `LUXESTAY_E2E_*` credentials |

Subset smoke pass does not clear the full-suite BLOCKED status. The full 71-test run still needs authenticated fixture/redirect and Home Search stabilization before release.

## Commands for next verification

    .\mvnw.cmd test
    npm --prefix frontend run test -- --watch=false
    npx --prefix frontend playwright test

Mỗi lần chạy phải ghi ngày, profile, database/config, kết quả pass/fail/error/skipped và log path. Nếu runner lỗi, ghi nguyên nhân và chuyển status BLOCKED.
