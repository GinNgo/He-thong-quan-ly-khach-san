# Hệ Thống Quản Lý Khách Sạn (Hotel Management System)

Dự án Đồ án xây dựng Hệ thống Quản lý Khách sạn toàn diện bao gồm các chức năng cốt lõi: Xác thực và Phân quyền, Quản lý Nhân sự, Quản lý Phòng & Dịch vụ, Đặt phòng trực tuyến, và Thống kê doanh thu.

## Công Nghệ Sử Dụng (Tech Stack)
- **Backend**: Java 21, Spring Boot 3, Spring Security (JWT), Spring Data JPA.
- **Frontend**: Angular 22 (Standalone Components), PrimeNG, Tailwind CSS / Bootstrap.
- **Cơ sở dữ liệu**: Microsoft SQL Server.

## Tiến Độ Phát Triển
- ✅ **Phase 1: Foundation & Authentication**
  - Cấu hình kiến trúc Backend (Clean Architecture) & Frontend.
  - Tích hợp Spring Security, JWT Auth.
  - Phân quyền theo mô hình Role-Based Access Control (RBAC).
  - Quản trị người dùng (Admin Dashboard).
- ✅ **Phase 2: Room & Service Management**
  - Quản lý Loại phòng (Room Types).
  - Quản lý Phòng vật lý (Rooms).
  - Quản lý Hình ảnh phòng (Room Images).
  - Quản lý Dịch vụ đi kèm (Hotel Services).
- ⏳ **Phase 3: Core Business (Đang phát triển)**
  - Đặt phòng (Booking/Reservation).
  - Thanh toán (Payment) & Hóa đơn (Invoice).
- ⏳ **Phase 4: Analytics & AI Assistant**
  - Thống kê doanh thu, báo cáo.
  - Tích hợp trợ lý ảo AI (Chatbot).

## Cấu Trúc Thư Mục
- `/backend`: Mã nguồn Java Spring Boot.
  - Chạy backend: Sử dụng file `run.bat` (Windows CMD) hoặc `run.ps1` (PowerShell) để chạy backend nhanh với Java 21.
- `/frontend`: Mã nguồn Angular 22.
  - Chạy frontend: `npm install` và `npm start`.

## Hướng Dẫn Cài Đặt (Local Development)

### Yêu cầu hệ thống:
1. **Java Development Kit (JDK) 21**. (Bắt buộc thiết lập biến môi trường `JAVA_HOME`).
2. **Node.js (v18+)** và **npm**.
3. **Microsoft SQL Server**.

### 1. Cấu hình Cơ sở dữ liệu (Database) & Biến môi trường
- Đảm bảo SQL Server đang chạy và **TCP/IP Port 1433** đã được bật trong *SQL Server Configuration Manager* (Vào SQL Server Network Configuration -> Protocols cho SQLEXPRESS -> Chuột phải TCP/IP chọn Enable, sau đó restart lại service SQL Server).
- Tạo một database mới tên là `HotelDB` trong SQL Server.
- Mở file `backend/src/main/resources/application.yml` và cấu hình lại thông tin đăng nhập SQL Server của máy tính ở nhà:
  ```yaml
  spring:
    datasource:
      url: jdbc:sqlserver://localhost:1433;databaseName=HotelDB;encrypt=true;trustServerCertificate=true
      username: sa             # Thay bằng username SQL Server ở nhà của bạn
      password: HotelAdmin@2026! # Thay bằng mật khẩu SQL Server ở nhà của bạn
  ```

### 2. Chạy Backend
Mở Terminal/PowerShell tại thư mục root của dự án:
```bash
cd backend
./run.ps1 # Hoặc ./run.bat nếu dùng CMD
# Lưu ý: Nếu ở nhà bạn cài Java 21 ở thư mục khác, hãy mở file run.ps1/run.bat và sửa lại đường dẫn JAVA_HOME cho đúng với máy tính ở nhà, hoặc xóa dòng set JAVA_HOME đi để máy tự dùng biến môi trường toàn cục.

# Backend sẽ khởi chạy tại http://localhost:8080
# Xem danh sách API tại: http://localhost:8080/swagger-ui.html
```

### 3. Chạy Frontend
Mở một Terminal khác tại thư mục root của dự án:
```bash
cd frontend
npm install
npm start
# Frontend sẽ khởi chạy tại http://localhost:4200
```
### 4. Cấu hình dữ liệu demo toàn quốc

Seeder chỉ hoạt động với profile `development`, `demo` hoặc `test`; production không chạy mặc định. Mật khẩu tài khoản demo bắt buộc lấy từ biến môi trường và không được commit.

```yaml
app:
  demo-data:
    enabled: true
    nationwide-property-seed: true
    coverage-mode: STANDARD
    properties-per-province: 5
    properties-per-ward: 1
    max-total-properties: 5000
    batch-size: 50
    allow-public-demo: true
```

PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'development'
$env:DEMO_ACCOUNT_PASSWORD = '<mật khẩu demo cục bộ>'
cd backend
.\mvnw.cmd spring-boot:run
```

`STANDARD` tạo 3-5 cơ sở theo tỉnh và không bao phủ toàn bộ phường/xã. `FULL_COVERAGE` tạo theo từng phường/xã, chạy theo batch/progress và vẫn tôn trọng `max-total-properties`. Mọi cơ sở seed có `is_demo=true`, `data_source=DEMO`; production mặc định không trả dữ liệu demo qua public search.

### 5. Property Import

Property Import chỉ dùng provider dữ liệu mở được cấu hình và phải tuân thủ điều khoản/rate limit của provider. Không lấy dữ liệu từ Agoda, Booking hoặc Traveloka.

### 6. Public Home Search

Home Search dùng `GET /api/public/search/suggestions` để trả riêng Province, Ward và Property; điểm đến phổ biến lấy từ `GET /api/public/popular-destinations`. Frontend không hard-code địa giới, cơ sở hoặc số lượng. Báo cáo triển khai và kiểm thử nằm tại `docs/HOME_SEARCH_REPORT_2026-07-15.md`.
# Public demo media

Development/demo property media is stored under `frontend/public/assets` and is
documented in `docs/ASSET_LICENSES.md`. Demo repair migrations only target rows
explicitly marked as demo or legacy rows matching both a `DEMO-*` code and a
`demo.local` email. See `docs/PUBLIC_CUSTOMER_QUALITY_REPORT_2026-07-15.md` for
database backups, migration metrics, and verification results.
# Search Result and booking status (2026-07-15)

Public Search now shares one search state between Home, sticky/compact search and
results. Price/type/star/review filters execute server-side, cards use local media
and real availability, and the detail flow supports selecting a RoomType quantity
before checkout. See `docs/SEARCH_RESULT_BOOKING_REPORT_2026-07-15.md` for verified
API/test results and the explicitly deferred multi-RoomType/service work.
# Admin Core Status (2026-07-15)

Admin navigation is database-backed and deduplicated. Roles, role permissions,
room types and physical rooms now use real APIs and no longer contain placeholder
pages. See `docs/ADMIN_CORE_REPORT_2026-07-15.md` for verified results and the
remaining PARTIAL/BLOCKED modules.
