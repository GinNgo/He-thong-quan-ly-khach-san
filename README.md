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