# P0-D: Tenant Isolation & Chống IDOR - Plan

## 1. Tenant Model Thực Tế

- **Identity & Token**: Phân quyền quản lý Tenant được xác định qua `UserProperty` (chứa liên kết User - Hotel và vai trò) và được trừu tượng hóa bằng `PropertyAccessService`.
- **Tenant Boundary Central**:
  - `accessibleHotelIds()`: Lấy danh sách Property (Hotel ID) mà user (staff/owner) được truy cập.
  - `requireAccessibleOrNotFound(Long hotelId, String entityName)`: Guard kiểm tra quyền. Ném `ResourceNotFoundException` (HTTP 404) để chặn IDOR.
- **Hierarchy Model**: Tenant -> `Hotel` -> `RoomType` -> `Room` -> `Reservation` & `HousekeepingTask` -> `Invoice` & `Payment`.
- **Bypass**:
  - `isSystemAdministrator()`: Trả về true nếu là `SUPER_ADMIN`, cho phép truy cập cross-tenant.
- **Customer Ownership**: Dựa trên so sánh `reservation.getUser().getUsername()` với username lấy từ Authentication Principal.

## 2. Ma Trận Endpoint Đầy Đủ

| Controller.method | HTTP Method & Route | Entity | Actor | Identifier | Nguồn/Quyền Hiện Tại | Repo/Service | Nguy Cơ | Hành Vi (P0-D) | Verdict |
|---|---|---|---|---|---|---|---|---|---|
| `HotelController.submitHotel` | `POST /hotels/{id}/submit` | Hotel | Staff | Path `id` | `requireAccessibleOrNotFound` | `HotelManagementService` | Trống quyền trước P0-C, nay đã bọc Guard. | 404 | SAFE (Theo dõi) |
| `ManagementPortalController.updateRoomType` | `PUT /management/room-types/{id}` | RoomType | Staff | Path `id` | Không có (chuyền payload ID) | `ManagementPortalService` | Sửa RoomType tenant khác | HIGH | 404 | FIX IN P0-D |
| `ManagementPortalController.updateRoom` | `PUT /management/rooms/{id}` | Room | Staff | Path `id` | Không có (chuyền payload ID) | `ManagementPortalService` | Sửa Room tenant khác | HIGH | 404 | FIX IN P0-D |
| `ReservationController.getReservationById` | `GET /reservations/{id}` | Reservation| All | Path `id` | Có check Guard | `ReservationService` | Đọc Booking ng khác | CRITICAL| 404 | SAFE (Theo dõi) |
| `ReservationController.addExtraService` | `POST /reservations/{id}/services` | Reservation| Staff | Path `id` | Có check Guard | `ReservationService` | Add fee láo | HIGH | 404 | SAFE (Theo dõi) |
| `ManagementPortalController.completeHousekeeping`| `POST /housekeeping/{taskId}/complete`| Task | Staff | Path `taskId`| Thiếu kiểm tra guard | `ManagementPortalService` | Fake complete task | HIGH | 404 | FIX IN P0-D |
| `InvoiceController.getInvoiceByReservation` | `GET /invoices/reservation/{id}`| Invoice | Staff/Cust| Path `id` | Không có (Dựa vào reservationId)| `InvoiceService` | Lộ hóa đơn | CRITICAL| 404 | FIX IN P0-D |
| `InvoiceController.generateInvoice` | `POST /invoices/reservation/{id}`| Invoice | Staff | Path `id` | Không có | `InvoiceService` | Fake hóa đơn | HIGH | 404 | FIX IN P0-D |
| `PaymentController.getPaymentsByReservation` | `GET /payments/reservation/{id}`| Payment | Staff/Cust| Path `id` | Có RBAC (`@Permission`) nhưng thiếu Tenant Guard | `PaymentService` | Lộ GD | CRITICAL| 404 | FIX IN P0-D |
| `PaymentController.createPaymentUrl` | `GET /payments/create-url` | Payment | Staff/Cust| Query `id` | Lấy Reservation nhưng thiếu check owner | `PaymentController` | Trả tiền hộ hoặc cào bill | HIGH | 404 | FIX IN P0-D |

## 3. Danh sách FIX IN P0-D Theo Ưu Tiên

1. **Billing (Invoice & Payment) [CRITICAL]**:
   - `InvoiceServiceImpl.getInvoiceByReservation` & `generateInvoice`: Thêm xác thực truy cập qua `ReservationService.getReservationById()` hoặc gọi trực tiếp `authorizeReservationView` / `requireOperationalAccess`.
   - `PaymentController.getPaymentsByReservation` & `createPaymentUrl`: Validate user có quyền trên Reservation đó không trước khi xử lý.

2. **Management Portal & Housekeeping [HIGH]**:
   - `ManagementPortalService`:
     - `updateRoomType`: Load `RoomType` -> lấy `HotelId` -> ném 404 nếu không có quyền.
     - `updateRoom`: Load `Room` -> lấy `HotelId` -> ném 404 nếu không có quyền.
     - `completeHousekeeping`: Load `Task` -> lấy `HotelId` -> ném 404 nếu sai quyền.

## 4. File Dự Kiến Sửa

- `backend/src/main/java/com/hotel/services/ManagementPortalService.java`
- `backend/src/main/java/com/hotel/services/impl/InvoiceServiceImpl.java`
- `backend/src/main/java/com/hotel/controllers/PaymentController.java`
- `backend/src/test/java/com/hotel/integration/TenantIsolationIntegrationTest.java` (Khôi phục và hoàn thiện)

## 5. Test Đỏ Bắt Buộc

Thiết lập Fixture: Tenant A, Tenant B, Customer C, Customer D, SUPER_ADMIN.
1. **[Ngăn IDOR]**: Tenant A gọi `PUT /management/rooms/{id-cua-Tenant-B}` -> HTTP 404.
2. **[Ngăn Lộ Hóa Đơn]**: Customer C gọi `GET /invoices/reservation/{id-cua-Customer-D}` -> HTTP 404.
3. **[Ngăn Task Chéo]**: Tenant A gọi `POST /housekeeping/{taskId-cua-Tenant-B}/complete` -> HTTP 404.
4. **[Payment Bypass]**: Customer C gọi tạo link thanh toán cho Reservation của D -> HTTP 404.
5. **[Quyền Hợp Lệ]**: Tenant A thao tác trên entity Tenant A -> 200 OK.
6. **[SUPER_ADMIN]**: Admin thao tác entity của mọi Tenant -> 200 OK.
7. **[Unauthenticated]**: Không truyền Token -> HTTP 401.

## 6. Quyết Định 403 hay 404

**Sử dụng mã HTTP 404 (Not Found)** qua `ResourceNotFoundException`.
*Lý do*: Theo nguyên tắc bảo mật, nếu trả về 403, kẻ tấn công biết rằng tài nguyên tồn tại nhưng không có quyền, giúp liệt kê và dò quét (Enumeration). Trả về 404 ngụy trang IDOR thành một trang không tồn tại, bảo vệ tuyệt đối data của tenant khác. Các logic phân quyền RBAC cấp Application (Filter) vẫn giữ nguyên 403.

## 7. Phân Nhóm Triển Khai

- **D1**: Hạ tầng test. Viết `TenantIsolationIntegrationTest` chuẩn bị các user và entity (A, B, C, D) không vi phạm DB constraint.
- **D2**: Vá 3 endpoint Management (`updateRoomType`, `updateRoom`, `completeHousekeeping`).
- **D3**: Vá Invoice và Payment endpoints.
- **D4**: Regression Testing. Xác minh test đỏ -> xanh.

## 8. Rủi Ro Và Ngoài Phạm Vi

- Áp dụng 404 tại Service Layer có thể cần Frontend handle lỗi (nếu frontend kì vọng 403). Tuy nhiên, frontend interceptor dự án đã xử lý chuẩn.
- Không sửa Hibernate Filter, bảo vệ thuần qua Security Service layer để không break workflow Admin.
- Bỏ qua check tenant trong P0-A/B/C.

## 9. Acceptance Criteria

- Mã nguồn vượt qua bài test `TenantIsolationIntegrationTest`.
- Mọi truy cập vào ID của tenant khác đều nhận `404 Not Found`.
- Chức năng của SUPER_ADMIN không bị khóa.

**Kết luận**: P0-D PLAN READY