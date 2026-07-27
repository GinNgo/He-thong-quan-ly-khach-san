# Báo cáo xung đột giữa Tài liệu và Source Code

| Nghiệp vụ | Tài liệu mô tả | Code hiện tại (Backend/Frontend) | Database | Test | Kết luận |
| --------- | -------------- | -------------------------------- | -------- | ---- | -------- |
| Giới hạn gói (Subscription) | Tài liệu SUMMARY nói giới hạn phòng. BUSINESS REQ nói chia tính năng cao cấp. | Backend chưa khóa chặt tính năng theo giới hạn. | Bảng `subscription_plans`, `software_contracts` có nhưng áp dụng vào logic chưa toàn diện. | Không ghi nhận | Code_Only ở một số phần. Cần implement check feature/quota chặt chẽ (P2). |
| Quản lý dịch vụ khách (Services) | Đề cập Receptionist có thể thêm dịch vụ. Khách chọn dịch vụ lúc checkout. | Có API `addServicesToReservation`, nhưng Frontend UI cho khách tự book dịch vụ cùng phòng chưa hoàn chỉnh. | `reservation_services` | Backend test có cover. | Thiếu UI đồng bộ. Cần thêm luồng Customer tự thêm dịch vụ (P3). |
| Đặt nhiều RoomType | `FEATURE_SUMMARY.md` ghi giới hạn hiện tại chỉ 1 RoomType (quantity > 1). | `ReservationRequestDTO` chỉ có `roomTypeId` thay vì list. | `reservation_details` ánh xạ 1 Booking - 1 RoomType. | Theo code | Thực tế bị block ở API contract. Cần thiết kế lại DTO và entity nếu muốn book nhiều loại phòng (P2). |
| E2E Testing | Có Playwright tests đã pass mốc 15/07/2026. | `npx playwright test` crash do cấu hình `test.describe()`. Vitest JIT lỗi decorators. | N/A | Fail 100% | Config testing đã bị phá vỡ. Cần tái tạo lại (P1). |
| Hệ thống Role & Permission | Có ma trận Role (Admin, Owner, Receptionist, v.v). | Có phân quyền backend nhưng Guest/Customer gộp route, và Super Admin dùng chung màn admin. | `roles`, `permissions` | Pass | Tính năng đã hoạt động nhưng cần rà soát lại Security Mask để chặn API bypass (P1). |
| Đánh giá & Review | Có trong lộ trình (FEATURE_SUMMARY). | Chưa thấy UI Review hoặc bảng dữ liệu reviews trong DB. | Không có `reviews`. | Không | Tính năng missing hoàn toàn. (P6). |

## Đánh giá chung
- Khung chức năng cơ bản trùng khớp.
- Các tính năng nâng cao (nhiều roomtype, mua dịch vụ ngoài) mới ở dạng "Doc only" hoặc thiếu UI.
- Automation test (Frontend/E2E) xung đột nghiêm trọng với thực tế báo cáo (hiện tại fail toàn bộ).