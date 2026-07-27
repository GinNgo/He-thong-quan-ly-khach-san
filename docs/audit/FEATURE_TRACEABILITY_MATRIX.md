# Ma trận truy vết chức năng

| ID | Actor | Nghiệp vụ | Yêu cầu | UI | API | Service | Database | Test | Trạng thái | Bằng chứng | Khoảng trống |
| -- | ----- | --------- | ------- | -- | --- | ------- | -------- | ---- | ---------- | ---------- | ------------ |
| AUTH-01 | Guest | Đăng nhập/Đăng ký | Authentication JWT | Có | Có | Có | `users` | Pass | VERIFIED | `UserServiceTest` | - |
| PROP-01 | Owner/Admin | Quản lý Property | CRUD Property | Có | Có | Có | `properties` | - | PARTIAL | - | Thiếu test E2E. |
| ROOM-01 | Owner/Admin | Quản lý RoomType | CRUD RoomType | Có | Có | Có | `room_types` | - | PARTIAL | - | Hình ảnh upload xử lý thủ công. |
| BOOK-01 | Customer | Tạo Booking | Giữ chỗ, giảm Inventory | Có | Có | Có | `reservations`, `inventory` | Pass | VERIFIED | `ReservationServiceTest` | Thiếu nhiều RoomType (BOOK-02). |
| BOOK-02 | Customer | Đặt nhiều loại phòng | Chọn nhiều RoomType 1 lúc | Không | Không | Không | - | - | MISSING | `ReservationRequestDTO` | Đang bị hardcode 1 booking - 1 room type. |
| PAY-01 | Customer | Thanh toán | MoMo tích hợp | Có | Có | Có | `payments` | - | PARTIAL | - | Thiếu Idempotency key chặt chẽ tại Backend. |
| OPS-01 | Receptionist | Gán phòng vật lý | Chọn Room cho Booking | Có | Có | Có | `room_assignments` | - | PARTIAL | - | UI chưa map chặt với status dọn phòng. |
| SUB-01 | Admin | Quản lý Subscription | Giới hạn tính năng Owner | Có | Có | Có | `subscription_plans` | - | CODE_ONLY | - | Backend chưa chặn ở mọi endpoint. |