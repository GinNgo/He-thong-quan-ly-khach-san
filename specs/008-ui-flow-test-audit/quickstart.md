# Quickstart: UI Flow Test Audit

## 1. Verify Plan Isolation

Từ repository root:

```powershell
Get-Content -Raw .specify\feature.json
```

Expected:

```json
{
  "feature_directory": "specs/007-payment-billing-completion"
}
```

Feature test được mở trực tiếp tại `specs/008-ui-flow-test-audit`; không chạy lệnh Spec Kit downstream mà không đặt `SPECIFY_FEATURE_DIRECTORY` rõ ràng nếu muốn thao tác feature 008.

## 2. Prerequisites

- Node/npm dependencies của `frontend` đã được cài.
- Backend, SQL Server và dữ liệu E2E chạy theo cấu hình dự án.
- Không ghi credential vào file; dùng các biến môi trường `LUXESTAY_E2E_*` đã được suite hiện có hỗ trợ.
- Có tối thiểu các profile: customer, admin, owner/manager; thêm insufficient-permission và expired-subscription khi chạy nhóm tương ứng.

## 3. Static Baseline

```powershell
rg -n "path:" frontend\src\app\app.routes.ts
rg -n "routerLink|href=|\(click\)|\(onClick\)" frontend\src\app -g "*.html"
rg -n "page\.goto\(|goto\(" frontend\e2e -g "*.ts"
```

Expected:

- Mọi route/menu/control được map vào `test-matrix.md` hoặc có disposition rõ.
- Route cũ trong test được đánh dấu `STALE TEST`, không tính coverage.

## 4. Frontend Quality Baseline

```powershell
Set-Location frontend
npm test -- --watch=false
npm run build
```

Ghi exit code và lỗi còn lại. Không sửa lỗi trong feature 008; lỗi được phân loại product, stale test hoặc environment.

## 5. Discover Browser Tests

```powershell
npx playwright test --list
```

Expected:

- Suite được discovery không lỗi config.
- Test nào phụ thuộc credential phải skip có lý do rõ, không silent pass.
- Test dùng route không tồn tại phải được ghi vào gap loại `TEST_STALE`.

## 6. Run Real-Environment Smoke

Khởi động runtime theo README hoặc Docker Compose hiện có, sau đó trong `frontend`:

```powershell
npx playwright test e2e/real-environment-smoke.spec.ts --project=chromium
```

Expected:

- Public, customer, admin và management route groups trả kết quả pass/fail/block rõ.
- Không có blank screen, redirect loop hoặc console error chưa giải thích ở P1.

## 7. Run High-Risk Journeys

```powershell
.\node_modules\.bin\playwright.cmd test property-booking-payment.spec.ts property-booking-payment-negative.spec.ts stay-checkout-invoice.spec.ts property-payment-configuration.spec.ts --project=chromium --output=test-results/high-risk-existing --reporter=line
```

Observed 2026-08-01: 6 passed, 1 skipped. The skipped property-scope case requires real owner credentials.

Các suite intercepted/mock-only có thể chạy để chẩn đoán, nhưng không được dùng độc lập để chuyển capability sang `COMPLETE`.

## 8. Runtime Verification for Known Candidates

Thực hiện theo `incomplete-function-register.md`, ưu tiên:

1. Admin dashboard onboarding CTA và approval CTA.
2. Dashboard stat cards và work order table.
3. Export Excel/PDF từ shared data table.
4. Forgot-password surfaces.
5. Legal/support/cookie links dùng `#`.
6. Management `properties` route so với dashboard.
7. Coming Soon tabs phải disabled và không phát request.

Mỗi mục cập nhật `actual`, `capabilityStatus`, `severity`, `evidence` và `disposition`.

## 9. Responsive and Accessibility Pass

Cho từng P1 journey, chạy ít nhất 375, 768, 1024 và 1440 pixel; kiểm tra:

- Không có overflow ngoài thiết kế.
- Menu/dialog đóng bằng Escape và không trap keyboard sai.
- Focus visible, accessible name và error form được thông báo.
- Reduced motion không cản trở thao tác.

## 10. Run the Consolidated Audit

From `frontend`:

```powershell
.\scripts\run-ui-audit.ps1
```

The runner executes source inventory, browser capability/responsive checks and real-environment flows in sequence. It preserves each group under a separate output directory and writes a structured summary to `frontend/test-results/ui-audit-run-summary.json`.

An exit code of `1` is expected while verified gaps or credential blockers remain. Read the group results instead of treating the non-zero exit as a harness crash.

For authenticated real-integration evidence, use the isolated runtime runner:

```powershell
.\scripts\run-authenticated-ui-audit.ps1
```

This runner generates temporary credentials in memory, seeds them through the LuxeStay E2E initializer, starts the backend/frontend on dedicated ports `8082`/`4420`, applies the matching CORS origin, runs the authenticated suites and stops only the processes it created. Text artifacts are automatically redacted; Playwright traces are disabled because login traces can contain password input.

## 11. Observed Results and Evidence

Run date: 2026-08-01.

| Command/Group | Result |
|---------------|--------|
| `npm test -- --watch=false` | Exit 0; 54 files and 155 tests passed |
| `npm run build` | Exit 0; CSS budget warning plus two CommonJS warnings |
| Source inventory | Exit 1; 4 passed, 3 audit findings |
| Browser capability | Exit 1; 10 passed, 11 verified gaps |
| Real flow | Exit 1; 1 passed, 1 credential-matrix failure, 3 skipped, 3 not run |
| Authenticated real-flow follow-up | Exit 0; 14 passed, 1 foreign-property data skip |
| High-risk existing journeys | Exit 0; 6 passed, 1 skipped |

Evidence paths:

- `frontend/test-results/source-inventory/`
- `frontend/test-results/browser-capability/`
- `frontend/test-results/p1-gap-evidence/`
- `frontend/test-results/real-flow/`
- `frontend/test-results/real-flow-authenticated/`
- `frontend/test-results/high-risk-existing/`

Observed limitations:

- Customer/admin/owner credentials are generated and seeded by the authenticated runner; they are not written to repository files.
- The remaining authenticated skip requires a third property outside the E2E owner's assigned-property set.
- Port `8080` is occupied by an unrelated application and port `4200` can be occupied by another frontend, so authenticated audit uses isolated ports `8082` and `4420`.
- Fixture/intercepted tests verify UI handlers, state and regression behavior but cannot establish end-to-end `COMPLETE` status.
- Chromium is the only browser baseline in this audit iteration.

## 12. Completion Checklist

- `.specify/feature.json` vẫn trỏ feature 007.
- 100% route/menu/control có disposition.
- P1 có primary + error/recovery + permission khi phù hợp.
- Không có `COMPLETE` từ mocked-only evidence.
- Verified gaps có repro + expected/actual + evidence.
- Test stale không được tính vào release coverage.
