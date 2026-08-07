# TÓM TẮT

Khóa luận xây dựng hệ thống quản lý khách sạn LuxeStay nhằm hỗ trợ hai nhóm nhu cầu chính: khách hàng tìm kiếm, đặt và theo dõi dịch vụ lưu trú; đơn vị vận hành quản lý cơ sở, loại phòng, phòng vật lý, đặt phòng và vòng đời lưu trú trong môi trường nhiều cơ sở. Hệ thống sử dụng Angular cho giao diện, Spring Boot cho backend và SQL Server cho lưu trữ dữ liệu; Flyway quản lý thay đổi schema.

Phương pháp thực hiện tập trung vào phân tích yêu cầu theo tác nhân, tách trách nhiệm giữa frontend, backend và dữ liệu, đồng thời truy vết mỗi chức năng từ route, API, service, entity/migration tới kiểm thử và phần báo cáo. Các quy tắc quan trọng gồm xác thực JWT, phân quyền bằng role và action mask, giới hạn dữ liệu theo cơ sở, tính tồn phòng theo khoảng ngày, kiểm tra sức chứa, chống giao dịch thanh toán trùng và quản lý trạng thái phòng trong quá trình check-in, check-out và housekeeping.

Phiên bản hiện tại hỗ trợ tìm kiếm địa điểm và cơ sở bằng tiếng Việt, xem RoomType và tồn phòng, đặt một RoomType với số lượng nhiều phòng, thanh toán VNPay hoặc simulator, hủy booking và ghi nhận hoàn tiền idempotent, xem hóa đơn, gán phòng, check-in, thêm dịch vụ trong thời gian lưu trú, check-out và tạo tác vụ dọn phòng. Hệ thống cũng có multi-property, feature limit theo subscription, import cơ sở, central support chat và notification ở các mức hoàn thiện khác nhau.

Kết quả kiểm thử ngày 28/07/2026 ghi nhận backend đạt 122/122 test, frontend unit đạt 66/66 test và production build thành công. Playwright phát hiện 71 test trong 12 file nhưng lần chạy đầy đủ bị timeout và có artifact lỗi redirect/search, vì vậy E2E được ghi `BLOCKED` thay vì kết luận pass.

Giới hạn của phiên bản hiện tại gồm chưa hỗ trợ nhiều RoomType trong cùng một booking, khách chưa tự chọn dịch vụ bổ sung tại checkout, chưa có review/favorites end-to-end, vòng đời subscription chưa đầy đủ, claim còn sử dụng identity cố định trong controller và báo cáo tài chính nâng cao chưa hoàn thiện. Các giới hạn này được tách khỏi chức năng đã chứng minh để bảo đảm báo cáo phản ánh đúng hiện trạng hệ thống.

**Từ khóa:** quản lý khách sạn, đặt phòng, multi-property, RBAC, tồn phòng, Spring Boot, Angular, SQL Server.
