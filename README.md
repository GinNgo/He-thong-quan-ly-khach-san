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
1. **Java Development Kit (JDK) 21**.
2. **Node.js (v18+)** và **npm**.
3. **Microsoft SQL Server**.

### 1. Cấu hình Cơ sở dữ liệu (Database)
- Đảm bảo SQL Server đang chạy và **TCP/IP Port 1433** đã được bật trong *SQL Server Configuration Manager*.
- Tạo một database mới tên là `HotelDB` (Hoặc đổi tên trong file cấu hình).
- Đăng nhập mặc định cho Backend: `sa` / `HotelAdmin@2026!`. (Sửa trong `/backend/src/main/resources/application.yml` nếu cần).

### 2. Chạy Backend
Mở Terminal/PowerShell tại thư mục root của dự án:
```bash
cd backend
./run.ps1
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