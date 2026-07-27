# Báo Cáo Khảo Sát Hệ Thống - 25/07/2026

## 1. Executive Summary
- Stack thực tế: Java 21 / Spring Boot 3 (backend), Angular 22 (frontend), SQL Server. Không dùng .NET như dự kiến.
- Multi-tenancy (SaaS): Chưa cô lập dữ liệu. Dùng `findByHotelId` thủ công.
- RBAC: Dùng bitmask. Chênh lệch logic bitmask giữa FE và BE gây lỗi 403 và redirect loop.
- Thanh toán: VNPay. Cấu hình nhạy cảm hardcode trong `application.yml`.
- Trạng thái: Chưa sẵn sàng production. Thiếu an toàn dữ liệu tenant và payment.

## 2. Kiến trúc hiện tại
- Backend: Spring Boot Rest API. Clean Architecture lỏng (Repository/Service/Controller).
- Frontend: Angular 22 Standalone Components. AuthGuard chặn tại route.
- Database: SQL Server. ORM: Hibernate/Spring Data JPA.

## 3. Sơ đồ module và dependency
- Frontend -> API Gateway (trực tiếp Spring Boot) -> Services (Auth, Property, Booking, Payment, Housekeeping) -> SQL Server.
- Thanh toán gọi external VNPay.

## 4. Danh sách vai trò, role và permission
- Roles: `SYSTEM_ADMIN`, `HOTEL_ADMIN`, `RECEPTIONIST`, `HOUSEKEEPING`, `CUSTOMER`.
- Permissions: Quản lý bằng bitmask (`MANAGE_ROOMS=1`, `MANAGE_BOOKINGS=2`...).
- Thiếu permission mapping rõ ràng cho `PROPERTY_PUBLISHER` hoặc `ACCOUNTANT`.

## 5. Đánh giá multi-tenancy và cách ly dữ liệu
- Cơ chế hiện tại: `PropertyAccessService` kiểm tra quyền của User đối với `hotelId`. JPA Query thủ công bằng `findByHotelId(...)`.
- Điểm yếu: Rất dễ quên lọc, gây IDOR. Chưa có `@Filter` của Hibernate.

## 6. Ma trận chức năng theo role (Tóm tắt)
- SYSTEM_ADMIN: Xem tất cả (UI ONLY).
- HOTEL_ADMIN: Sửa phòng, xem booking (PARTIALLY IMPLEMENTED).
- RECEPTIONIST: Check-in/out (IMPLEMENTED BUT UNVERIFIED).
- CUSTOMER: Tìm phòng, đặt chỗ (VERIFIED WORKING).

## 7. Phân tích nguyên nhân lỗi 403
- **File:** `PermissionInterceptor.java` (BE) và `error.interceptor.ts`, `client.auth.guard.ts` (FE).
- **Chuỗi:** UI -> Guard (pass do token còn hạn) -> API Request -> Interceptor (thiếu bitmask, ném 403) -> `error.interceptor.ts` (không bắt 403 đúng cách, hoặc nhầm sang 401, gây loop).
- **Nguyên nhân gốc:** Frontend hardcode bitmask hoặc role không đồng bộ với DB. Interceptor không báo chi tiết lỗi.

## 8. Phân tích lỗi subscription/gia hạn
- API trả về URL thanh toán VNPay nhưng Webhook xử lý/IPN chưa update lại trạng thái `Subscription`.
- Chưa có logic chặn API nếu gói bị quá hạn (chỉ mới chặn UI hoặc database có field nhưng backend không query).

## 9. Đánh giá nghiệp vụ đặt phòng
- Mới hỗ trợ đặt phòng cơ bản (Reservation). Thiếu chống overbooking bằng Lock cơ sở dữ liệu. Không quản lý inventory theo ngày (chỉ có số lượng tổng của RoomType).

## 10. Đánh giá marketplace phía khách
- Có tính năng tìm kiếm (location-import), Home Search. Verified qua báo cáo 15/07. Cần test kỹ load data tỉnh thành.

## 11. Đánh giá UI/UX
- Đã có `design_system_luxestay.md`. Cần áp dụng đồng bộ.
- Components hiện tại (Login, Admin Dashboard) còn basic, form thiếu validation lỗi. Nên tái sử dụng system hiện có thay vì viết mới.

## 12. Danh sách mock/hardcode
- Hardcode secret trong `application.yml` (DB, JWT, VNPay).
- API `SubscriptionFeatureService` trả về giới hạn tĩnh chưa check theo gói (Mock).

## 13. Danh sách lỗi (Critical, High, Medium, Low)
- **Critical:** IDOR tenant (thiếu Hibernate Filter), Hardcode Secrets.
- **High:** Lỗi redirect loop 403, Overbooking race condition.
- **Medium:** Thanh toán xong chưa cộng ngày subscription.
- **Low:** Giao diện xấu, thiếu UX báo lỗi.

## 14. Backlog MUST/SHOULD/COULD
- **MUST:** Fix 403 loop, Security (Env config), Hibernate Tenant Filter.
- **SHOULD:** Quản lý tồn phòng theo ngày, đồng bộ thanh toán IPN.
- **COULD:** Báo cáo doanh thu, Chatbot.

## 15. Câu hỏi chưa rõ
- Khách sạn có nhiều chi nhánh (nhiều `Property`) quản lý ra sao?
- Có cho phép một User vừa làm Receptionist khách sạn A, vừa làm Admin khách sạn B không?

## 16. Đề xuất chia feature Spec Kit
1. Auth & Tenant Isolation (Core)
2. Booking Engine (Overbooking fix, Inventory)
3. SaaS Subscription & Payment
4. UI/UX Refactor

## 17. Đề xuất feature đầu tiên
**Feature 01: Core Auth & Tenant Isolation** (Đã tạo sẵn ở .specify/Feature-01-Core-Auth-Tenant).

## 18. Prompt Constitution
```
/speckit.constitution Update rules for Java 21, Spring Boot 3, Angular 22. Enforce Hibernate @Filter for tenant isolation. Prohibit hardcoded secrets. Use bitmask consistently for RBAC.
```

## 19. Prompt Specify
```
/speckit.specify Feature 01: Core Auth & Tenant Isolation. Move DB/JWT/VNPay secrets to .env. Implement Hibernate @Filter("tenantFilter") for Room, RoomType, Reservation. Fix Angular 403 redirect loop in errorInterceptor.
```

## 20. Kết luận
**READY TO CREATE SPECS**