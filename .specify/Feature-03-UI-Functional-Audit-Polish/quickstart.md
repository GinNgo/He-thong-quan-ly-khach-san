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
```

Nhánh actor thiếu biến môi trường sẽ được đánh dấu `skipped` với prerequisite cụ thể thay vì dùng tài khoản giả.

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
