# Báo cáo kết quả Baseline Test

Ngày: 24/07/2026
Environment: Windows 11, Node.js (via absolute path), Maven Wrapper.

## 1. Backend (Spring Boot)
Lệnh đã chạy (trong phiên log trước):
`.\mvnw.cmd clean test`
`.\mvnw.cmd clean package`

Kết quả:
- Maven test: 60/60 pass.
- Build package: PASS.
- Build error: Không.
- Runtime error: Không ghi nhận ở log hiện tại.
- Mức độ ảnh hưởng: Backend core đã đạt trạng thái ổn định cho mốc thay đổi "Payment và Refund".

## 2. Frontend (Angular 18+)
Lệnh đã chạy:
`"C:\Program Files\nodejs\node.exe" "C:\Program Files\nodejs\node_modules\npm\bin\npm-cli.js" --prefix frontend run build`

Kết quả:
- Build: Pass (114s).
- Warning: Vượt budget initial chunk (2.52MB > 2MB), cảnh báo CommonJS từ `@stomp/stompjs` và `sockjs-client`.

Lệnh đã chạy:
`"C:\Program Files\nodejs\node.exe" "C:\Program Files\nodejs\node_modules\npm\bin\npm-cli.js" --prefix frontend run test -- --watch=false`

Kết quả:
- Lỗi JIT mode: `Standard Angular field decorators are not supported in JIT mode`.
- Test runner (Vitest) timeout và không tìm thấy test pass nào (`Error: No tests found`).
- Nguyên nhân dự kiến: Angular 18 thay đổi cơ chế build/test, Vitest config chưa đồng bộ với Angular compiler (thiếu JIT support cho class field decorator trong environment test).

## 3. End-to-End (Playwright)
Lệnh đã chạy:
`npx playwright test`

Kết quả:
- Lỗi: 100% crash khi load file config test. Playwright không nhận diện được `test.describe()`.
- Nguyên nhân dự kiến: Conflict version `@playwright/test` trong `package.json` hoặc import sai đối tượng `test` (đang gọi synchronous describe bên ngoài block được phép, hoặc mix version).

## Đánh giá mức độ ảnh hưởng
- Backend an toàn.
- Frontend: production build an toàn. Frontend Developer cần fix cấu hình Vitest và Playwright để có thể verify regressioin ở các giai đoạn sau.