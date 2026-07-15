# SỔ THEO DÕI TIẾN ĐỘ KHÓA LUẬN TỐT NGHIỆP (LOGBOOK)

**Tên đề tài:** Xây dựng Hệ thống Quản lý Khách sạn (Hotel Management System)
**Thời gian thực hiện:** 15 Tuần

Bảng dưới đây ghi chú lại chi tiết các công việc thực hiện hàng tuần để sinh viên mang đi báo cáo định kỳ với Giảng viên hướng dẫn (GVHD).

| Tuần | Thời gian dự kiến | Nội dung công việc thực hiện | Kết quả đạt được | Chữ ký GVHD |
|:---:|:---|:---|:---|:---:|
| **1** | Tuần 1 | **Công việc đã thực hiện:**<br>- Tìm hiểu và khảo sát nghiệp vụ quản lý khách sạn và đặt phòng trực tuyến.<br>- Tham khảo một số hệ thống thực tế như Booking.com, Agoda và phần mềm nội bộ.<br>- Xác định các đối tượng sử dụng chính: Khách hàng, Lễ tân và Quản trị viên.<br>- Phân tích sơ bộ các chức năng cần xây dựng cho hệ thống (Quản lý phòng, Đặt phòng, Phân quyền).<br>- Tìm hiểu và lựa chọn các công nghệ dự kiến sử dụng (Angular, Spring Boot, SQL Server). | **Kết quả đạt được:**<br>- Nắm được quy trình nghiệp vụ cơ bản.<br>- Xác định được phạm vi và hướng phát triển của đề tài.<br>- Chốt công nghệ phát triển lõi.<br><br>**Kế hoạch tuần 2:**<br>- Xây dựng đặc tả yêu cầu hệ thống.<br>- Thiết kế sơ đồ Use Case.<br>- Thiết kế cơ sở dữ liệu sơ bộ.<br>- Thiết kế giao diện nguyên mẫu (Mockup).<br>- Khởi tạo môi trường phát triển dự án. | |
| **2** | Tuần 2 | - Tìm hiểu và đánh giá công nghệ Backend (Java, Spring Boot 3, Spring Security).<br>- Tìm hiểu công nghệ Frontend (Angular 22, PrimeNG).<br>- Lập đề cương chi tiết cho đồ án. | Đề cương đồ án được phê duyệt, nắm vững stack công nghệ. | |
| **3** | Tuần 3 | - Thiết kế cấu trúc Cơ sở dữ liệu (Database Schema).<br>- Phân tích và vẽ Sơ đồ Thực thể kết hợp (ERD).<br>- Khởi tạo dự án Backend và cấu hình kết nối DB (SQL Server). | File thiết kế ERD, source code Backend (Init). | |
| **4** | Tuần 4 | - Thiết kế Kiến trúc phần mềm và vẽ các Biểu đồ Lớp (Class Diagram).<br>- Phác thảo giao diện người dùng (UI Mockups) cho khu vực Admin.<br>- Thiết lập dự án Frontend. | Các bản vẽ UML (Class, Use Case), source code Frontend (Init). | |
| **5** | Tuần 5 | - Triển khai lõi bảo mật Backend: Phân quyền RBAC, tích hợp xác thực JWT.<br>- Cấu hình Auditing để truy vết dữ liệu.<br>- Viết API Đăng nhập và Quản lý Người dùng/Vai trò. | Hệ thống Backend bảo mật hoàn chỉnh, API hoạt động tốt trên Swagger. | |
| **6** | Tuần 6 | - Xây dựng Layout Admin chuẩn (Sidebar, Header).<br>- Phát triển các thư viện dùng chung (Shared Components) như Data Table, Stat Card.<br>- Xây dựng giao diện Bảng điều khiển (Dashboard) với dữ liệu giả lập. | Giao diện Dashboard hiển thị mượt mà, sẵn sàng tích hợp API. | |
| **7** | Tuần 7 | - Thiết kế Biểu đồ tuần tự (Sequence Diagram) cho chức năng Quản lý Phòng.<br>- Viết API Quản lý Loại phòng, Quản lý Phòng và Dịch vụ đi kèm.<br>- Triển khai giao diện CRUD cho Room Management. | Phân hệ Quản lý Phòng hoàn thiện (Full-stack). | |
| **8** | Tuần 8 | - Phân tích logic nghiệp vụ Đặt phòng (Reservation).<br>- Viết API xử lý logic Check-in, Check-out, Hủy phòng.<br>- Xây dựng giao diện Quản lý Đặt phòng cho Lễ tân. | Phân hệ Đặt phòng cơ bản chạy ổn định. | |
| **9** | Tuần 9 | - Phân tích nghiệp vụ Thanh toán và xuất Hóa đơn.<br>- Tích hợp tính năng kết xuất Hóa đơn ra file PDF.<br>- Quản lý doanh thu theo từng giao dịch. | Phân hệ Hóa đơn xuất file thành công. | |
| **10** | Tuần 10 | - Tích hợp biểu đồ thống kê thực tế (Chart.js) thay cho Mock Data ở Dashboard.<br>- Viết API tổng hợp doanh thu, tỷ lệ lấp đầy phòng.<br>- Tối ưu hóa các câu truy vấn cơ sở dữ liệu. | Dashboard hiển thị số liệu thật chính xác theo thời gian thực. | |
| **11** | Tuần 11 | - Nâng cấp kiến trúc sang mô hình Đa cơ sở (Multi-Property).<br>- Tích hợp Feature Gate và Gói dịch vụ (Subscription).<br>- Viết API Quản lý Địa điểm, Thuê bao. | Hệ thống quản lý được nhiều khách sạn cùng lúc. | |
| **12** | Tuần 12 | - Tiến hành Kiểm thử hệ thống: Kiểm thử API (Postman), Kiểm thử UI/UX cơ bản.<br>- Sửa các lỗi (Bugs) phát sinh trong quá trình kiểm thử.<br>- Viết báo cáo: Chương 3 (Phân tích thiết kế hệ thống). | Hệ thống chạy ổn định không có lỗi nghiêm trọng, Bản thảo Chương 3. | |
| **13** | Tuần 13 | - Viết báo cáo: Chương 4 (Cài đặt & Kiểm thử) và Chương 5 (Kết luận).<br>- Cập nhật toàn bộ các Biểu đồ UML (Class, Sequence) cho khớp với source code thực tế. | Bản thảo báo cáo hoàn chỉnh từ Chương 1 đến 5. | |
| **14** | Tuần 14 | - Gửi bản nháp Báo cáo đồ án cho GVHD nhận xét.<br>- Tinh chỉnh giao diện lần cuối, dọn dẹp mã nguồn (Refactor).<br>- Chỉnh sửa báo cáo theo góp ý của GVHD. | Hệ thống đóng gói (Deploy ready), Báo cáo đã chỉnh sửa. | |
| **15** | Tuần 15 | - In quyển Báo cáo đồ án (Khóa luận).<br>- Soạn thảo Slide thuyết trình PowerPoint.<br>- Tập dượt bảo vệ Khóa luận trước hội đồng. | Slide trình chiếu, Quyển KLTN bản cứng. | |

---
**Ghi chú:**
*Lịch trình này có thể được điều chỉnh linh hoạt cộng trừ 1-2 tuần tùy vào tình hình thực tế và đánh giá của Giảng viên hướng dẫn trong mỗi buổi báo cáo.*

## [2026-07-15] - Dữ liệu demo toàn quốc và Owner Portal

- Đọc schema SQL Server thật và tạo 6 bảng backup trước migration.
- Cập nhật ERD, UML, API_SPEC, THESIS trước khi code; thêm Flyway V5 theo hướng chỉ bổ sung, không xóa dữ liệu thật.
- Chạy `STANDARD`: 34 tỉnh x 5 cơ sở = 170 cơ sở trên 170 Ward; 645 RoomType, 1.232 Room vật lý, 1.800 ảnh.
- Tạo 170 Owner: FREE 29, NO_PLAN 29, STANDARD 28, BUSINESS 28, LIFETIME 28, EXPIRED 28.
- Seed chạy lại nhiều lần giữ nguyên count, không trùng `seed_key`/room number và không đổi 11 cơ sở thật.
- API test trực tiếp xác nhận search có dấu/không dấu/địa chỉ/tỉnh/Ward, Owner context, Admin owner list, overbooking 409 và cross-property 403.
- E2E nghiệp vụ API thật: booking quantity 2, gán 2 phòng, check-in, thêm dịch vụ snapshot, check-out tạo invoice PAID, phòng DIRTY, housekeeping đưa về AVAILABLE/CLEAN.
- Backend `mvnw.cmd test` pass 30/30; frontend `npm run build` pass. Browser E2E chưa hoàn tất do in-app browser không attach được tab.

## [2026-07-11] - Triển khai Tự Động Bổ Sung Dữ Liệu
- **Bối cảnh**: Hệ thống cần bổ sung dữ liệu cơ sở lưu trú ban đầu mà không vi phạm chính sách crawling.
- **Giải pháp**: Xây dựng kiến trúc AccommodationDataProvider hỗ trợ lấy dữ liệu từ OpenStreetMap (Nominatim).
- **Thực hiện**:
  - Thiết kế các bảng staging và logs (batch, item) để admin kiểm duyệt trước khi đưa vào bảng hotels chính.
  - Implement thuật toán deduplicate đơn giản ở mức Service.
  - Xây dựng luồng Claim Ownership để chủ khách sạn có thể nhận quyền quản lý (cấp quyền OWNER).
  - Cập nhật ERD, UML, API_SPEC và THESIS.
# 2026-07-15 - Home Search

- Đọc component/API/search state hiện tại và cập nhật `API_SPEC.md` trước khi code.
- Chạy Flyway V6 trên SQL Server, kiểm tra trực tiếp API có dấu, không dấu, tên và địa chỉ cơ sở.
- Phát hiện E2E không render response dù HTTP 200 do callback RxJS trong Angular zoneless; sửa bằng `markForCheck()`.
- Dùng cache observable chia sẻ Popular Destinations để tránh Home và popup gọi trùng endpoint.
- Xác nhận backend 34 test pass, Angular build pass và Playwright 10/10 pass.
# 2026-07-15 - Public/Customer quality pass

Backed up four SQL Server tables, applied Flyway through V8, verified public API
media/pricing/availability, and ran backend, frontend unit, build, Home Search E2E,
and role-aware Profile Menu E2E. Detailed evidence is in
`PUBLIC_CUSTOMER_QUALITY_REPORT_2026-07-15.md`.
# 2026-07-15 - Search Result verification

- Audited Search Result, filter, detail, checkout and reservation layers.
- Verified SQL Server API against 180 demo properties.
- Created reservation #8 as an E2E inventory check; availability decreased 3 -> 2.
- Verified overbooking returns 409 and does not create a reservation.
- Backend 35/35, frontend 20/20, Angular build and Playwright 2/2 passed.
- Recorded remaining multi-RoomType, amenity/policy and checkout-service work in the phase report.
# 2026-07-15 - Admin Core

Audited Admin routes, database menu functions and permission masks. Applied
Flyway V9 after backing up function and role-permission tables. Implemented the
Roles, permission matrix, RoomType and physical Room workflows, fixed zoneless
loading states, and verified SQL Server/API/UI behavior. Final results: backend
35/35, frontend 20/20, Playwright Admin 3/3.
