# Hướng dẫn quay video demo LuxeStay

## 1. Mục tiêu và cách chia video

Nên quay thành 6 video ngắn, mỗi video 4-7 phút. Tổng thời lượng khoảng 30-40 phút. Mỗi video chỉ dùng đúng vai trò cần thiết để làm rõ phân quyền.

| Video | Vai trò | Nội dung chính |
|---|---|---|
| 1 | Khách hàng | Tìm khách sạn, đặt phòng, thanh toán, xem lịch sử và hủy phòng |
| 2 | Chủ cơ sở | Mua gói, quản lý cơ sở, phòng, dịch vụ và tài khoản con |
| 3 | Lễ tân | Xác nhận đặt phòng, check-in, thêm dịch vụ, checkout và in hóa đơn |
| 4 | Dọn phòng | Nhận phòng cần dọn, cập nhật tiến độ và hoàn tất |
| 5 | System Admin | Quản lý tenant, plan, thanh toán gói và doanh thu hệ thống |
| 6 | Phân quyền và báo cáo | Chứng minh cách ly tenant, quyền tài khoản con và các file xuất |

## 2. Chuẩn bị trước khi quay

1. Mở website: `https://luxustay.duckdns.org`.
2. Chuẩn bị bốn cửa sổ hoặc bốn profile trình duyệt: Customer, Property Owner, Receptionist và System Admin.
3. Dùng các tài khoản demo đã được cấp. Không quay hoặc đọc mật khẩu trong video.
4. Chọn một cơ sở duy nhất làm dữ liệu xuyên suốt.
5. Chuẩn bị một khách hàng thử nghiệm và một phòng đang `AVAILABLE`.
6. Chọn check-in là hôm nay hoặc ngày gần nhất được hệ thống cho phép; checkout là ngày hôm sau.
7. Bật quay toàn màn hình ở độ phân giải 1920x1080, thu phóng trình duyệt 90-100%.
8. Trước mỗi video, tải lại bằng `Ctrl + F5` và đóng các tab không liên quan.

## 3. Video 1 - Luồng khách hàng đặt phòng

### Cảnh 1: Tìm kiếm và xem chi tiết

1. Vào trang chủ.
2. Chọn điểm đến, ngày nhận phòng, ngày trả phòng và số khách.
3. Nhấn tìm kiếm.
4. Dùng bộ lọc giá, loại hình hoặc tiện nghi.
5. Mở chi tiết một khách sạn và chọn loại phòng.

Kết quả cần quay: danh sách đúng bộ lọc, hình ảnh cơ sở, giá phòng, tiện nghi và số phòng còn trống.

Lời thuyết minh: "Khách hàng tìm phòng theo địa điểm và thời gian. Hệ thống chỉ hiển thị loại phòng còn khả dụng trong khoảng ngày đã chọn."

### Cảnh 2: Tạo đặt phòng

1. Nhấn đặt phòng.
2. Kiểm tra thông tin khách, số người và yêu cầu đặc biệt.
3. Chọn phương thức thanh toán demo.
4. Xác nhận đặt phòng.

Kết quả cần quay: mã đặt phòng, trạng thái chờ hoặc đã thanh toán, tổng tiền và tiền cọc.

### Cảnh 3: Lịch sử, hóa đơn và hủy phòng

1. Vào hồ sơ hoặc lịch sử đặt phòng.
2. Mở đơn vừa tạo.
3. Giải thích trạng thái thanh toán và chính sách hủy.
4. Nếu cần demo hủy, dùng một đơn riêng chưa check-in.
5. Chọn lý do hủy và xác nhận.

Kết quả cần quay: trạng thái `CANCELLED`, số tiền đủ điều kiện hoàn và tiến trình hoàn tiền nếu có.

## 4. Video 2 - Chủ cơ sở mua gói và thiết lập khách sạn

### Cảnh 1: Mua hoặc nâng cấp gói

1. Đăng nhập Property Owner.
2. Vào `Quản lý > Gói phần mềm`.
3. So sánh hạn mức các gói.
4. Chọn mua hoặc nâng cấp gói.
5. Thực hiện thanh toán demo và quay trạng thái giao dịch thành công.

Kết quả cần quay: gói hiện tại, ngày bắt đầu/hết hạn và hạn mức mới.

### Cảnh 2: CRUD cơ sở và danh mục

1. Mở danh sách cơ sở.
2. Tạo hoặc chỉnh sửa thông tin cơ sở.
3. Thêm loại phòng.
4. Thêm phòng vật lý.
5. Thêm dịch vụ khách sạn.
6. Thử chỉnh sửa và ngừng sử dụng một bản ghi thử nghiệm.

Kết quả cần quay: dữ liệu mới xuất hiện đúng cơ sở và không xuất hiện ở tenant khác.

### Cảnh 3: Tạo tài khoản con

1. Vào quản lý nhân viên.
2. Tạo tài khoản Receptionist.
3. Chọn cơ sở và vai trò.
4. Đăng xuất rồi đăng nhập tài khoản vừa tạo.

Kết quả cần quay: tài khoản con đi thẳng vào portal quản trị, không đi qua trang mua hàng; menu chỉ hiện chức năng được cấp.

## 5. Video 3 - Lễ tân xử lý lưu trú

1. Đăng nhập Receptionist.
2. Mở danh sách đặt phòng hoặc timeline.
3. Tìm đơn của khách theo tên/mã đơn/ngày.
4. Gán phòng vật lý nếu đơn chưa được gán.
5. Nhấn check-in.
6. Mở chi tiết lưu trú và thêm dịch vụ phát sinh.
7. Kiểm tra bảng phí phòng, dịch vụ và điều chỉnh.
8. Xác nhận thanh toán đủ.
9. Nhấn checkout.
10. Mở hóa đơn và nhấn in.

Kết quả cần quay:

- Đơn chuyển sang `CHECKED_IN`, sau đó `CHECKED_OUT`.
- Hóa đơn có bảng chi tiết tiền phòng, dịch vụ, tổng cộng và thanh toán.
- Cửa sổ in chỉ hiển thị nội dung hóa đơn.
- Phòng chuyển sang trạng thái cần dọn sau checkout.

Lưu ý: hệ thống không cho checkout khi hóa đơn chưa thanh toán đủ; đây là quy tắc nghiệp vụ, không phải lỗi.

## 6. Video 4 - Quản lý dọn phòng

1. Dùng Receptionist hoặc Housekeeping có quyền phù hợp.
2. Vào `Dọn phòng`.
3. Tìm phòng vừa checkout.
4. Nhận hoặc phân công công việc.
5. Chuyển trạng thái sang đang dọn.
6. Hoàn tất dọn phòng.

Kết quả cần quay: task dọn phòng được tạo tự động, trạng thái task hoàn tất và phòng quay lại `AVAILABLE`.

## 7. Video 5 - System Admin và doanh thu nền tảng

### Cảnh 1: Chứng minh phân tách hệ thống/tenant

1. Đăng nhập System Admin.
2. Quay menu: không có phòng, loại phòng, đặt phòng, dịch vụ hoặc hóa đơn của tenant.
3. Vào dashboard; hệ thống chuyển đến `Doanh thu hệ thống`.
4. Mở thông báo và chứng minh không có thông báo đặt phòng của khách sạn.

### Cảnh 2: Quản lý plan

1. Vào `Gói dịch vụ`.
2. Nhấn `Thêm gói`.
3. Nhập mã, tên, chu kỳ và giá.
4. Nhập hạn mức hình ảnh, cơ sở, phòng, loại phòng, nhân viên, khuyến mãi và vị trí tài trợ.
5. Lưu gói.
6. Chỉnh sửa lại giá hoặc hạn mức.
7. Nhấn `Ngừng bán`, sau đó `Bán lại`.

Kết quả cần quay: plan mới xuất hiện, giá/hạn mức thay đổi và trạng thái bán được cập nhật. Giải thích rằng ngừng bán là xóa mềm nhằm giữ lịch sử giao dịch.

### Cảnh 3: Doanh thu hệ thống

1. Vào `Doanh thu nền tảng`.
2. Chọn khoảng ngày.
3. Lọc theo plan, nhà cung cấp hoặc loại giao dịch.
4. Giải thích tổng thu, hoàn tiền, điều chỉnh và doanh thu ròng.
5. Xuất CSV, Excel và PDF.
6. Mở nhanh từng file để chứng minh file đọc được, tiếng Việt đúng và PDF có nhiều trang khi dữ liệu dài.

## 8. Video 6 - Phân quyền, cách ly tenant và hỗ trợ

1. Dùng System Admin mở vai trò và phân quyền.
2. Chỉ ra quyền xem, tạo, sửa, xóa, duyệt và thực thi tác vụ.
3. Đăng nhập hai tenant ở hai cửa sổ khác nhau.
4. Chứng minh tenant A không xem được phòng, booking, doanh thu hoặc nhân viên tenant B.
5. Dùng tài khoản khách gửi yêu cầu hỗ trợ.
6. Dùng đúng tenant hoặc bộ phận hỗ trợ trả lời.

Kết quả cần quay: API hoặc giao diện trả `403/404` khi truy cập sai phạm vi; dữ liệu không bị lộ giữa hai tenant.

## 9. Thứ tự dữ liệu xuyên suốt đề xuất

```text
System Admin tạo plan
        ↓
Property Owner mua plan
        ↓
Property Owner tạo cơ sở, loại phòng, phòng, dịch vụ và Receptionist
        ↓
Customer tìm kiếm, đặt và thanh toán phòng
        ↓
Receptionist nhận thông báo, gán phòng và check-in
        ↓
Receptionist thêm dịch vụ, thanh toán, checkout và in hóa đơn
        ↓
Housekeeping dọn phòng và đưa phòng về AVAILABLE
        ↓
System Admin xem doanh thu mua gói; Owner xem doanh thu khách sạn
```

## 10. Checklist trước khi kết thúc mỗi video

- Không để lộ mật khẩu, token, key SSH hoặc thông tin thanh toán thật.
- Quay rõ URL và vai trò đang đăng nhập.
- Sau mỗi thao tác phải quay toast hoặc trạng thái kết quả.
- Không dùng cùng một booking để demo cả checkout và hủy.
- Khi xuất file, mở file vừa tải để chứng minh kết quả.
- Nếu thao tác thất bại, quay thông báo lỗi; không cắt mất bằng chứng.
- Mỗi video kết thúc bằng màn hình thể hiện trạng thái cuối cùng của nghiệp vụ.

## 11. Câu mở đầu và kết thúc gợi ý

Mở đầu: "LuxeStay là hệ thống quản lý khách sạn đa tenant, gồm cổng khách hàng, cổng chủ cơ sở, vận hành lễ tân/dọn phòng và quản trị nền tảng. Video này trình bày luồng [tên chức năng]."

Kết thúc: "Luồng đã hoàn tất với trạng thái [trạng thái cuối]. Hệ thống đồng thời kiểm soát phân quyền, phạm vi tenant và lưu vết giao dịch tương ứng."
