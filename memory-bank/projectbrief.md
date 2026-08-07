# Project Brief

## Dự án

Hệ thống quản lý khách sạn full-stack, gồm backend Spring Boot và frontend Angular.

## Mục tiêu đã xác minh

- Xác thực, phân quyền và quản lý người dùng.
- Quản lý khách sạn, loại phòng, phòng vật lý và dịch vụ.
- Tìm kiếm cơ sở lưu trú công khai.
- Đặt phòng, hóa đơn và thanh toán.
- Quản lý đối tác/cơ sở, subscription, thông báo và chat.
- Dashboard quản trị và thống kê.

## Phạm vi source

- Backend: `backend/`
- Frontend: `frontend/`
- Tài liệu kiểm chứng: `docs/`
- Entrypoint backend: `backend/src/main/java/com/hotel/BackendApplication.java`
- Entrypoint frontend: `frontend/src/main.ts`

## Ràng buộc

- Source và yêu cầu hiện tại ưu tiên hơn Memory Bank.
- Chỉ ghi kiến thức đã xác minh; phần chưa rõ ghi `TBD - Chưa xác định từ source`.
- Không lưu secret, credential, connection string hoặc dữ liệu thật.
- Không dùng câu trả lời AI trước làm bằng chứng.
- Build/test phải được chạy theo phạm vi task trước khi ghi trạng thái đã kiểm chứng.

## Ngoài phạm vi lần khởi tạo

- Không sửa source nghiệp vụ.
- Không chạy migration.
- Không chạy build hoặc test theo yêu cầu thiết lập Memory Bank.
- Không xác minh dữ liệu trong file database, backup hoặc upload.