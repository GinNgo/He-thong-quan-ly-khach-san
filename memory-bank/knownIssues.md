# Known Issues

## Đang mở

### KI-001 — Git status/diff toàn repository bị timeout khi thiết lập

- Trạng thái: Mở.
- Ngày xác minh: 2026-07-23.
- Bằng chứng: Lệnh đọc repository root, branch, tracked status và diff trong một lần không hoàn tất trong 30 giây. Lần thử lại với `status --short --untracked-files=no` và diff giới hạn `backend frontend` vẫn timeout sau 20 giây. Lần kiểm tra riêng source và nhóm cấu hình ứng viên cũng timeout sau 20 giây.
- Nguyên nhân: TBD - Chưa xác định từ source.
- Ảnh hưởng: Chưa có snapshot Git đầy đủ cho lần khởi tạo Memory Bank; không thể dùng Git để xác nhận source sạch.
- Hướng kiểm tra: Dừng các terminal bị treo, sau đó chạy từng lệnh Git riêng ngoài task thiết lập; không quét untracked nặng.

### KI-002 — Hai cơ chế quản lý schema cùng xuất hiện trong cấu hình

- Trạng thái: Cần đánh giá khi sửa database.
- Ngày xác minh: 2026-07-23.
- Bằng chứng: Backend khai báo Flyway; cấu hình ứng dụng đồng thời bật Hibernate schema update.
- Nguyên nhân thiết kế: TBD - Chưa xác định từ source.
- Ảnh hưởng: Có thể gây sai khác schema nếu migration và ORM cùng thay đổi cấu trúc.
- Hướng kiểm tra: Xác minh profile và quy trình migration trước mọi thay đổi database.

### KI-003 — Tài liệu repository có ví dụ credential

- Trạng thái: Mở; không xử lý vì ngoài phạm vi task.
- Ngày xác minh: 2026-07-23.
- Bằng chứng: `README.md` chứa ví dụ cấu hình đăng nhập database.
- Giá trị: Không sao chép vào Memory Bank.
- Hướng xử lý: Thay bằng placeholder hoặc biến môi trường trong task bảo mật riêng.

## Đã xử lý

Chưa có lỗi ứng dụng được sửa trong task thiết lập này.