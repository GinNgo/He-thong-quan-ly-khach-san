# Product Context

## Nhóm người dùng đã thấy trong source

- Khách công khai: xem trang chủ, tìm kiếm và xem chi tiết cơ sở.
- Khách hàng đã đăng nhập: đặt phòng, xem hóa đơn, hồ sơ và lịch sử.
- Đối tác/chủ cơ sở: đăng ký đối tác, quản lý cơ sở, tồn phòng và subscription.
- Nhân sự/quản trị: quản lý người dùng, vai trò, quyền, phòng, dịch vụ, đặt phòng, hóa đơn và báo cáo.
- Quản trị hệ thống: quản lý module, function, role và permission.

## Luồng nghiệp vụ chính đã xác minh từ source

1. Xác thực
   - Frontend gửi yêu cầu qua `core/services/auth.ts`.
   - Backend nhận tại `controllers/AuthController.java`.
   - `services/AuthService.java` xác thực, tạo JWT và trả thông tin quyền.
2. Tìm kiếm công khai
   - Trang home/search gọi service frontend.
   - Backend public discovery/search trả gợi ý, điểm đến, kết quả và chi tiết cơ sở.
3. Đặt phòng
   - Người dùng chọn cơ sở, loại phòng và số lượng.
   - `ReservationService` kiểm tra sức chứa và phòng trống, tạo reservation cùng chi tiết liên quan trong transaction.
4. Quản trị
   - Route Angular được bảo vệ bằng auth, role, feature hoặc permission guard.
   - Backend áp dụng policy URL, method authorization, permission và kiểm soát quyền truy cập cơ sở.
5. Thanh toán và hóa đơn
   - Controller/service/entity riêng tồn tại cho payment và invoice.
   - Chi tiết đầy đủ về đối soát production: TBD - Chưa xác định từ source.

## Giá trị sản phẩm

- Một hệ thống chung cho trải nghiệm đặt phòng công khai và vận hành khách sạn.
- Phân quyền theo vai trò, quyền chức năng và phạm vi cơ sở.
- API backend dùng chung cho Angular admin, management và client flows.

## Chưa xác minh

- Actor matrix chính thức và quyền đầy đủ từng vai trò: TBD - Chưa xác định từ source.
- SLA, quy mô production và yêu cầu phục hồi: TBD - Chưa xác định từ source.
- Quy trình vận hành thanh toán production: TBD - Chưa xác định từ source.