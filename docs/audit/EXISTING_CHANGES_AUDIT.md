# Báo cáo thay đổi hiện có (Existing Changes Audit)

Dựa trên quá trình kiểm tra Git (`git status`, `git log`, `git diff`) vào thời điểm 22-24/07/2026.

## Tổng quan Git Status
- Branch hiện tại: Không xác định (ngầm định là main/master) nhưng có commit `5808019` từ agent trước liên quan đến Property, RoomType, Authentication.
- Working tree: Sạch trước khi bắt đầu (ngoại trừ log sinh ra khi chạy ứng dụng).

## Các thay đổi từ Agent trước (Commit gần nhất)

| File / Component | Loại thay đổi | Mục đích dự kiến | Trạng thái | Rủi ro | Có nên giữ | Việc cần làm |
| ---------------- | ------------- | ---------------- | ---------- | ------ | ---------- | ------------ |
| `ReservationRequestDTO`, `Reservation` | Data Model | Tạo Booking mới | Đang làm dở | High | Giữ và Refactor | Chỉ hỗ trợ 1 RoomType. Cần refactor lại để hỗ trợ mảng (List) các RoomType cho một Booking. |
| `PaymentController`, MoMo Service | Integration | Thanh toán Booking | Có lỗi tiềm ẩn | High | Giữ và Refactor | Chưa có Idempotency Key, dễ duplicate giao dịch khi gọi callback nhiều lần. Cần add unique constraint. |
| `PropertyController`, Security | RBAC / Authorization | Chặn quyền Owner | Có lỗi tiềm ẩn | Medium | Giữ và Refactor | Các API đang lấy toàn bộ danh sách. Cần thêm Aspect/AOP chặn theo `property_id` để tránh IDOR. |
| Automation Test (Playwright, Vitest) | Testing | Kiểm thử hồi quy | Broken | Blocker | Giữ và Fix | Cấu hình bị sai (`test.describe` ngoài luồng, thiếu JIT decorator provider). Không chạy được. Phải fix ngay. |
| `DataInitializer` | Seed Data | Tạo dữ liệu demo | Hoàn thiện | Low | Giữ | Đã tạo sẵn Admin, Owner, Property, Room. Tiết kiệm thời gian test manual. |
| `.env`, `application.properties` | Configuration | Cấu hình môi trường | Secret Leak | High | Sửa | Commit thẳng mật khẩu DB và JWT Secret lên repo. Cần tạo `.env.example` và đưa bản chính vào `.gitignore`. |

## Đánh giá chung
Agent trước đã hoàn thành tốt bộ khung sườn (Authentication, CRUD Property/RoomType), code Java compile thành công. Tuy nhiên, logic nghiệp vụ Core Booking và Payment chưa chặt chẽ. Hệ thống Test (FE và E2E) đã bị phá vỡ cấu hình.