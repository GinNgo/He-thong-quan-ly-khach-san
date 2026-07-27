# Danh sách Bug (Bug Backlog)

| Bug ID | Mức độ | Trạng thái | Tiêu đề | Vấn đề / Hậu quả | Nguyên nhân dự kiến | Cách fix đề xuất |
| ------ | ------ | ---------- | ------- | ---------------- | ------------------- | ---------------- |
| BUG-001 | Blocker | Open | Frontend Vitest crash JIT Mode | Không thể chạy FE Unit Test | Angular 18 yêu cầu `setup-vitest.ts` khai báo JIT cho decorator, config hiện tại chưa có. | Bổ sung JIT setup file cho Vite. |
| BUG-002 | Blocker | Open | Playwright E2E crash khi parse config | Không thể chạy E2E | Cú pháp `test.describe` đặt ngoài block cho phép hoặc version mix match. | Refactor Playwright config và xóa test mẫu lỗi. |
| BUG-003 | High | Open | Hardcode mật khẩu DB và JWT | Rò rỉ bảo mật trên repo | Code agent trước đẩy thẳng `.env` / `application.properties` | Tạo `.env.example`, remove sensitive data, thêm vào `.gitignore` |
| BUG-004 | High | Open | DTO Booking chỉ cho 1 loại phòng | Bể thiết kế gốc (book nhiều phòng) | `ReservationRequestDTO` dùng ID đơn thay vì List. | Sửa DTO, Backend API logic và Entity Mapping. |
| BUG-005 | Medium | Open | Lỗ hổng IDOR trên Owner API | Owner truy cập chéo Data | Thiếu check `@PreAuthorize("hasPermission(#propertyId, 'PROPERTY_OWNER')")` | Dùng AOP hoặc Custom Security Expression kiểm tra PropertyID. |
| BUG-006 | Medium | Open | Thiếu Idempotency khi Payment | Thanh toán bị double | Callback/Frontend gọi nhiều lần sinh nhiều bill | Gắn Unique Key `(reservationId, transactionId)` tại DB. |