# HỆ THỐNG QUẢN LÝ KHÁCH SẠN VÀ NỀN TẢNG ĐẶT PHÒNG (LUXESTAY) - CONSTITUTION

## 1. NGUYÊN TẮC KIẾN TRÚC VÀ CÔNG NGHỆ LÕI
- **Backend:** Java 21, Spring Boot 3. (Tuyệt đối không dùng giả định .NET/C#).
- **Frontend:** Angular 22+, Standalone Components.
- **Database:** SQL Server 2022.
- **Persistence:** JPA / Hibernate (ưu tiên Spring Data JPA).
- **Tooling:** Maven (backend), npm (frontend).

## 2. NGUYÊN TẮC MULTI-TENANCY VÀ CÁCH LY DỮ LIỆU
- Hệ thống chia sẻ chung Database, chung Schema. Tenant định danh bằng `hotel_id` (hoặc `property_id`).
- Mọi bảng nghiệp vụ liên quan đến cơ sở lưu trú bắt buộc có cột `hotel_id`.
- Chống IDOR: Không truyền tay `hotel_id` từ client làm nguồn sự thật. Backend xác định `hotel_id` hợp lệ qua token của user (truy xuất từ `user_properties` thông qua `PropertyAccessService`).
- **Bắt buộc:** Áp dụng Hibernate `@Filter` ở cấp độ entity cho mọi Repository nghiệp vụ để tự động lọc theo `hotel_id`. Chấm dứt việc lọc thủ công bằng `findByHotelId`.

## 3. NGUYÊN TẮC XÁC THỰC VÀ PHÂN QUYỀN (RBAC)
- **Authentication:** Stateless qua JWT.
- **Authorization:** Kiểm tra tập trung ở Backend qua HandlerInterceptor (`PermissionInterceptor`).
- Phân quyền động dựa trên phép toán Bitmask (so sánh `permissionMasks` và `AppFunction.functionCode`).
- UI Guard (Frontend): Chỉ dùng để ẩn/hiện thành phần UI hoặc chặn luồng cục bộ; mọi hành động sinh mutation/dữ liệu bắt buộc phải được backend xác thực lại.
- Tránh vòng lặp 401/403: Không tự ý redirect tự động giữa các module khác Role nếu logic phân quyền chưa hội tụ.

## 4. NGUYÊN TẮC NGHIỆP VỤ LÕI
- **Tồn phòng (Inventory):** Không lưu bảng tồn phòng cố định. Tồn phòng được tính động bằng phép trừ: `Tổng phòng vật lý - Tổng booking giao cắt khoảng thời gian [checkIn, checkOut)`.
- **Chống Overbooking:** Giao dịch đặt phòng bắt buộc sử dụng Pessimistic Locking (`FOR UPDATE`) hoặc DB constraint bảo vệ concurrent access.
- **Trạng thái:** Tách biệt rạch ròi trạng thái Reservation (Đã đặt, Đang ở, Đã trả) và trạng thái Room (Sạch, Bẩn, Đang dọn).
- **Subscription (SaaS):** Tính năng bị khoá theo gói (Feature Limits). Chặn mutation khi hết hạn, nhưng vẫn cho phép Tenant đọc/xuất dữ liệu lịch sử theo chính sách.

## 5. BẢO MẬT VÀ PRODUCTION READINESS
- **Cấm Hardcode Secrets:** Mọi thông tin nhạy cảm (DB password `sa`, VNPay secret, JWT secret) phải đọc từ biến môi trường (`.env`). Không để trong `application.yml` hay Git.
- Cấm để lộ đường dẫn file local (vd: `C:\...` hay `../docs/34_tinh_huyen_xa.json`) vào file cấu hình chạy.
- Exception Handling: Không dùng block `catch` rỗng. Cấm in `e.printStackTrace()` hoặc `System.err.println()`. Dùng chuẩn logger (SLF4J/Logback).