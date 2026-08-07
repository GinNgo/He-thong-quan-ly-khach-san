# Dàn ý toàn văn và kế hoạch minh họa

## A. Thứ tự 13 nhóm biểu mẫu

| STT | Nhóm | Trạng thái cần quản lý | Ghi chú chuẩn bị |
| --- | --- | --- | --- |
| 1 | Tờ bìa khóa luận tốt nghiệp | REQUIRED | Lấy đúng mẫu trường, điền tên đề tài, sinh viên, GVHD, lớp, năm |
| 2 | Biên bản chấm/bảng điểm | REQUIRED | Chèn bản scan/PDF chính thức sau khi có điểm |
| 3 | Phiếu nhận xét GV phản biện | REQUIRED | Giữ nguyên mẫu và chữ ký |
| 4 | Biên bản chỉnh sửa | CONDITIONAL | Chỉ thêm khi trường/GV yêu cầu hoặc có phát sinh |
| 5 | Phiếu nhận xét GV hướng dẫn | REQUIRED | Chèn bản chính thức |
| 6 | Lời cảm ơn | OPTIONAL | Viết ngắn, trang trọng |
| 7 | Lời cam đoan | OPTIONAL/RECOMMENDED | Cam kết nguồn, dữ liệu và tính trung thực |
| 8 | Tóm tắt | REQUIRED | Mục tiêu, phương pháp, kết quả, giới hạn |
| 9 | Abstract | CONDITIONAL | Thêm khi mẫu hoặc GV yêu cầu; dịch từ bản tóm tắt đã chốt |
| 10 | Mục lục | REQUIRED | Sinh sau khi chốt heading và pagination |
| 11 | Nội dung khóa luận | REQUIRED | Năm chương và các bảng/hình/sơ đồ |
| 12 | Tài liệu tham khảo | REQUIRED | Chỉ giữ nguồn đã trích dẫn, thống nhất kiểu |
| 13 | Phụ lục | CONDITIONAL | API excerpt, test evidence, screenshot, rubric matrix, biên bản |

## B. Nội dung năm chương

### Chương 1 - Tổng quan đề tài

- Bối cảnh quản lý khách sạn và đặt phòng.
- Vấn đề, mục tiêu, đối tượng, phạm vi, phương pháp.
- Phân biệt phạm vi đã triển khai với roadmap.
- Bảng capability scope hiện hành và cách đọc status.

### Chương 2 - Cơ sở lý thuyết

- Kiến trúc web nhiều tầng, REST API, DTO.
- JWT, RBAC/action mask, multi-property và tenant isolation.
- Tồn phòng động, chống overbooking, trạng thái reservation/room.
- Idempotency thanh toán/refund, Unicode search và các công nghệ thực tế.

### Chương 3 - Phân tích và thiết kế

- Tác nhân và phân quyền; Use Case tổng quát.
- Kiến trúc frontend/backend/database/integration.
- Thiết kế auth/RBAC và multi-property/subscription.
- ERD và các ràng buộc inventory, reservation, payment, invoice.
- Quy trình booking, cancellation/refund, assignment, check-in, service, checkout, housekeeping.
- Search/location, import/claim property và central support chat khi đã có contract.
- Thiết kế giao diện theo role và bằng chứng route.

### Chương 4 - Cài đặt và kiểm thử

- Cấu trúc source và môi trường.
- Auth/RBAC, search/availability, property/room, booking/payment/refund.
- Operations: assignment, check-in, services during stay, checkout, housekeeping.
- Multi-property, subscription/feature management, import/claim.
- Giao diện đã kiểm tra; screenshot có caption, role và dữ liệu demo.
- Chiến lược test, kết quả CURRENT/HISTORICAL/BLOCKED, đánh giá khoảng trống.

### Chương 5 - Kết luận và hướng phát triển

- Kết quả đạt được theo capability COMPLETE/PARTIAL.
- Giới hạn có bằng chứng: mixed RoomType, customer add-on services, reviews, favorites, lifecycle subscription/history và financial reconciliation nâng cao nếu chưa hoàn tất.
- Rủi ro kiểm thử bị chặn và tác động tới kết luận.
- Hướng phát triển gắn với task/contract, không trình bày như chức năng hiện tại.

## C. Ma trận sơ đồ bắt buộc

| Sơ đồ | Phạm vi tối thiểu | Nguồn cần đối chiếu | Vị trí đề xuất |
| --- | --- | --- | --- |
| Use Case | Guest/Customer, Receptionist, Owner, Admin, Support và quyền thực tế | app.routes.ts, controllers, permission codes, UML.md | Chương 3.1 |
| Class Diagram | Entity/service chính của auth, property, room, reservation, payment, operations | Java entities/services, ERD.md | Chương 3.3-3.4 |
| Sequence Diagram | Auth; search/availability; booking/payment; cancellation/refund; operations; chat nếu applicable | API_SPEC.md, service tests, UML.md | Chương 3.3-3.7 |
| Activity Diagram | Booking; cancellation/refund; check-in/check-out; import/claim hoặc subscription | service states, controller branches, UML.md | Chương 3.5-3.6 |
| ERD | users/properties/rooms/reservations/payments/invoices/operations/subscriptions | migrations, JPA entities, ERD.md | Chương 3.4 |

Mỗi sơ đồ khi đưa vào Word/PDF phải có bốn đoạn: Mục đích, Mô tả, Phân tích, Kết luận. Caption được đánh số thống nhất và phải có câu tham chiếu trong prose.

## D. Quy tắc screenshot

Mỗi màn hình đã triển khai cần có: mã ảnh, route, vai trò, mục tiêu, thao tác chính, kết quả quan sát được, ngày chụp, dữ liệu demo và ghi chú privacy. Không chụp token, secret, email/số điện thoại thật hoặc đường dẫn cục bộ.

## E. Definition of Done cho bản báo cáo

- 13 slot được kiểm tra theo thứ tự.
- Năm chương có heading đúng format.
- Capability trong prose đều có record/evidence.
- Sơ đồ, ERD, API và THESIS không mâu thuẫn.
- Test result có ngày/lệnh và nhãn hiện hành/lịch sử/bị chặn.
- Tài liệu tham khảo chỉ gồm nguồn đã trích dẫn.
- DOCX/PDF render sạch từng trang và không chứa thông tin nhạy cảm.
