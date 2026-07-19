# Tổng hợp tính năng LuxeStay

Ngày tổng hợp: 19/07/2026.

Tài liệu này tổng hợp các tính năng hiện có trong source và các báo cáo kiểm thử gần nhất. Những thay đổi payment đang nằm ngoài commit phải được chạy lại toàn bộ test trước khi được xem là hoàn thành.

## 1. Xác thực và tài khoản

- Đăng nhập Customer và Admin bằng JWT.
- Phân quyền theo Role và Action Mask.
- Angular Route Guard và kiểm tra quyền độc lập tại backend.
- Cập nhật hồ sơ Customer/Admin, đổi mật khẩu và đăng xuất.
- Upload avatar và đồng bộ avatar mới lên header.
- Menu tài khoản responsive.
- Trạng thái đối tác: chưa đăng ký, chờ duyệt và đã duyệt.

## 2. Role, Permission và Menu

- Quản lý vai trò: danh sách, tìm kiếm, thêm, sửa và ngừng sử dụng.
- Bảo vệ system role.
- Ma trận quyền VIEW, CREATE, UPDATE, DELETE, EXPORT và APPROVE.
- Khai báo module/trang và left menu lấy từ database.
- Menu hiển thị theo role; route và function code được loại trùng.
- SUPER_ADMIN có toàn quyền; API trả HTTP 403 khi không đủ quyền.

## 3. Public Home và tìm kiếm

- Tìm Province, Ward và Property; không dùng District.
- Tìm tiếng Việt có dấu, không dấu, tên cơ sở và địa chỉ.
- Autocomplete phân nhóm, tìm kiếm gần đây và điểm đến phổ biến từ database.
- Điều hướng bàn phím, loading, empty và error state.
- Search State dùng chung giữa Home, sticky search và trang kết quả.
- Giữ ngày, số khách và số phòng khi chuyển trang.
- Responsive desktop/mobile; ảnh local có fallback và không dùng ảnh Agoda.

## 4. Trang kết quả tìm kiếm

- Lọc theo tỉnh, phường/xã, loại cơ sở, giá, hạng sao và điểm đánh giá.
- Sắp xếp và phân trang phía server.
- Active filter chips, xóa bộ lọc, loading, retry và empty state.
- Hiển thị tồn phòng, giá theo ngày, số đêm và số phòng.
- Định dạng tiền VND và mobile filter sheet.

## 5. Chi tiết cơ sở và đặt phòng

- Chi tiết cơ sở bằng DTO, không còn JSON đệ quy.
- Gallery, RoomType, ảnh, giường, diện tích, sức chứa và số phòng còn lại.
- Chọn một RoomType với số lượng nhiều phòng.
- Kiểm tra người lớn, trẻ em, sức chứa và availability.
- Tính tiền phòng, thuế/phí và tổng tiền.
- Backend kiểm tra lại RoomType, giá và tồn phòng; overbooking trả HTTP 409.
- Availability giảm đúng sau khi đặt.

Giới hạn hiện tại: hỗ trợ một RoomType với `quantity > 1`, chưa hỗ trợ nhiều RoomType trong cùng booking.

## 6. Booking và vận hành lưu trú

- Tạo Reservation và lưu `reservation_details`.
- Booking cá nhân, booking quản trị và timeline.
- Kiểm tra phòng trống và gán nhiều phòng vật lý.
- Check-in; chặn phòng sai RoomType, sai cơ sở, OCCUPIED hoặc MAINTENANCE.
- Thêm dịch vụ khi khách đang ở và lưu snapshot đơn giá.
- Check-out, tạo invoice/payment và chuyển phòng thành DIRTY.
- Tạo housekeeping task; hoàn tất dọn phòng chuyển AVAILABLE/CLEAN.

## 7. Thanh toán và hóa đơn

- Payment entity và API thanh toán booking.
- VNPay callback, payment callback và payment simulator.
- Trang kết quả thanh toán.
- Hóa đơn Customer và danh sách hóa đơn Admin.
- Invoice được giới hạn theo chủ booking.

Trạng thái: worktree có thay đổi chưa commit về payment idempotency, chống giao dịch trùng và E2E payment; chưa tính là hoàn thành cho đến khi test lại.

## 8. Quản lý cơ sở

- Multi-property và mapping `user_properties` cho Owner/Admin/Receptionist/Staff.
- Active Property Context; Owner chỉ thấy cơ sở được gán.
- Dashboard Owner và thông tin gói hiện tại.
- Theo dõi số phòng đã dùng và giới hạn gói.
- CRUD RoomType và phòng vật lý.
- Tạo phòng hàng loạt, bảo trì, mở lại phòng và xóa mềm.
- Kiểm tra subscription limit.

## 9. Subscription và Owner

Các nhóm đã hỗ trợ:

- FREE
- NO_PLAN
- STANDARD
- BUSINESS
- LIFETIME
- EXPIRED

Chức năng hiện có:

- Subscription Plan, Account Subscription, Subscription Payment và Software Contract.
- Kiểm tra feature limit và CTA nâng cấp cho NO_PLAN/EXPIRED.
- Admin xem Owner, Owner chưa mua gói, thanh toán và hợp đồng dạng bảng.

Trạng thái một phần: activate, renew, upgrade, downgrade và revoke có lịch sử chưa hoàn thiện toàn bộ.

## 10. Admin

- Dashboard, người dùng, khách hàng, vai trò và phân quyền.
- Khai báo trang/module.
- Cơ sở lưu trú, import cơ sở từ nguồn mở và claim quyền sở hữu.
- Loại phòng, phòng vật lý và dịch vụ.
- Booking, check-in, check-out và hóa đơn.
- Chủ cơ sở, đăng ký cơ sở, tài khoản chưa mua gói và nhân viên cơ sở.
- Subscription order, payment và contract.

## 11. Unicode và địa giới

- 34 Province và 6.283 Ward; không có District.
- Các cột tiếng Việt quan trọng dùng NVARCHAR.
- Import JSON UTF-8 và xử lý BOM.
- Chuẩn hóa tiếng Việt để tìm không dấu.
- Sau reimport không còn Location chứa dấu `?` do lỗi encoding.
- Có index tìm kiếm cho Location và Hotel.

## 12. Dữ liệu demo

Theo báo cáo seed STANDARD gần nhất:

- 170 cơ sở demo tại 34/34 tỉnh/thành.
- 170 Owner.
- 645 RoomType.
- 1.232 phòng vật lý.
- 1.800 ảnh local.
- 170 Ward có cơ sở demo.
- Các loại HOTEL, MOTEL, HOMESTAY, HOSTEL, APARTMENT, VILLA, RESORT và GUEST_HOUSE.
- Seeder idempotent, không sửa cơ sở thật và production mặc định không hiển thị dữ liệu demo.

## 13. Phần còn thiếu hoặc mới hoàn thiện một phần

- Nhiều RoomType trong một booking.
- Khách chọn dịch vụ ngay tại checkout.
- Favorites và Customer Reviews.
- Quy trình duyệt review và tính điểm đánh giá thật.
- Customer Messages riêng.
- Đa ngôn ngữ đầy đủ.
- Room status history và RoomType amenities.
- Subscription CRUD và lịch sử nâng/hạ/gia hạn đầy đủ.
- Đối soát payment chuyên biệt.
- UI Owner đầy đủ cho ảnh, nhân viên, dịch vụ và toàn bộ vận hành lưu trú.
- Báo cáo doanh thu/công suất hoàn chỉnh theo cơ sở và khoảng ngày.

## 14. Kết quả kiểm thử gần nhất

- Backend: 35/35 test pass.
- Frontend unit: 20/20 test pass.
- Home Search Playwright: 10/10 pass.
- Public/Customer Playwright: 5/5 pass.
- Search Result Playwright: 2/2 pass.
- Admin Playwright: 3/3 pass.
- Angular production build: pass, còn warning bundle/CommonJS.

Các kết quả trên là mốc đã ghi nhận trong báo cáo ngày 15/07/2026. Cần chạy lại sau khi hoàn tất các thay đổi payment đang có trong worktree.
