# Quickstart: Run and Verify the UI Audit

## Prerequisites

- JDK 21, Node.js 20+ và npm.
- SQL Server local có database `HotelDB`, hoặc cấu hình datasource local tương đương.
- Các biến môi trường nhạy cảm chỉ đặt trong terminal/local secret store, không ghi vào artifact.
- Tài khoản đại diện cho Customer, System Admin và Property Owner/Manager; nếu thiếu phải ghi `BLOCKED` cho actor tương ứng.

Để chạy smoke spec môi trường thật mà không hardcode credential, đặt các cặp biến cục bộ tương ứng với actor đang có:

```powershell
$env:LUXESTAY_E2E_CUSTOMER_USERNAME = '<local-customer>'
$env:LUXESTAY_E2E_CUSTOMER_PASSWORD = '<local-only>'
$env:LUXESTAY_E2E_ADMIN_USERNAME = '<local-system-admin>'
$env:LUXESTAY_E2E_ADMIN_PASSWORD = '<local-only>'
$env:LUXESTAY_E2E_OWNER_USERNAME = '<local-owner-or-manager>'
$env:LUXESTAY_E2E_OWNER_PASSWORD = '<local-only>'
```

Không ghi các giá trị này vào `.env`, screenshot, trace hoặc artifact Git.

### Reproducible local E2E fixtures

Profile `e2e` dùng H2 in-memory và bật `app.e2e-fixtures.enabled`. Khi ba cặp credential ở trên được cung cấp, backend idempotently tạo:

- Customer có approved property inventory, owned reservation và successful payment context.
- Admin có full permission, dùng được `SYSTEM.AI_CHAT`, system notification và personal notification.
- Property Owner được gán hai cơ sở cùng active subscription.
- Actor `<owner-username>-expired` dùng cùng owner password nhưng chỉ có expired subscription.
- Customer là denied actor cho admin/management permission scenarios.

```powershell
$env:SPRING_PROFILES_ACTIVE = 'e2e'
$env:JWT_SECRET = '<local-only-secret-with-sufficient-length>'
Set-Location backend
.\mvnw.cmd spring-boot:run
```

Chạy frontend và Playwright trong terminal cũng có các biến `LUXESTAY_E2E_*`. Profile này không ghi vào SQL Server chính; dữ liệu fixture tự động được dọn khi process backend dừng. Với profile `development`, fixture chỉ chạy khi chủ động đặt `APP_E2E_FIXTURES_ENABLED=true`; nên dùng database local riêng và reset database đó sau phiên test.

## 1. Check workspace

```powershell
git status --short --branch
Get-Content .specify\feature.json
```

Expected: branch `codex/ui-functional-audit-polish`; feature directory trỏ tới `.specify/Feature-03-UI-Functional-Audit-Polish`.

## 2. Start backend

Trong terminal backend, đặt các biến local cần thiết, ví dụ:

```powershell
$env:DB_PASSWORD = '<local-only>'
$env:JWT_SECRET = '<local-only-secret-with-sufficient-length>'
$env:SPRING_PROFILES_ACTIVE = 'development'
Set-Location backend
.\mvnw.cmd spring-boot:run
```

Expected: API lắng nghe ở `http://localhost:8080`; Swagger ở `http://localhost:8080/swagger-ui.html`.

## 3. Start frontend

```powershell
Set-Location frontend
npm install
npm start
```

Expected: Angular app ở `http://localhost:4200`.

## 4. Browser audit order

1. Public: `/`, `/search`, `/hotel/:id`, login/register và error routes.
2. Customer: checkout/payment, profile, booking history, invoices, settings, partner registration/status.
3. System Admin: dashboard, users/customers, room types/rooms/services, reservations, invoices, modules/chat, properties/plans, roles/permissions, import/claim và partner administration.
4. Property Operations: dashboard/properties, room types, rooms và billing.
5. Responsive sampling ở 375, 768, 1024, 1440; keyboard-only và reduced motion.

Ghi kết quả vào `audit-matrix.md` và gap vào `gap-register.md` theo contract.

## 5. Frontend verification

```powershell
Set-Location frontend
npm test -- --watch=false
npm run build
```

Nếu cần chạy regression Playwright đã có và môi trường đủ dữ liệu:

```powershell
npx playwright test
```

Smoke suite không dùng mock và không mutation dữ liệu:

```powershell
npx playwright test e2e/real-environment-smoke.spec.ts
npx playwright test e2e/support-chat-lifecycle.spec.ts --project=chromium --workers=1 --retries=0
```

Nhánh actor thiếu biến môi trường sẽ được đánh dấu `skipped` với prerequisite cụ thể thay vì dùng tài khoản giả.

Khi không có biến actor, lần xác minh 2026-07-28 cho kết quả mong đợi `1 passed, 3 skipped` trong khoảng 10 giây: public recovery chạy thật, còn customer/admin/owner dừng đúng tại prerequisite. Nếu public test fail vì locator mơ hồ thì đó là lỗi regression suite, không phải lý do để dùng mock thay thế.

Không dùng mocked Playwright result làm bằng chứng duy nhất cho runtime integration.

## 6. Backend verification

```powershell
Set-Location backend
.\mvnw.cmd test
```

Ưu tiên kiểm tra các test auth, permission, tenant isolation, reservation, payment và public discovery.

## 7. Final consistency gates

```powershell
git diff --check
git status --short
```

Sau khi evidence hoàn chỉnh, chạy Spec Kit analyze ở chế độ read-only. Nếu analyze tìm thấy inconsistency, cần sự đồng ý của người dùng trước khi sửa artifact theo quy tắc skill. Cuối cùng chạy converge để append task còn thiếu nếu có.
