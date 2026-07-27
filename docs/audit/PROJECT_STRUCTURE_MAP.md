# Bản đồ kiến trúc toàn bộ dự án (Project Structure Map)

Dự án Hệ thống quản lý khách sạn LuxeStay được chia làm 2 thành phần chính: Backend (Java/Spring Boot) và Frontend (Angular 18).

## 1. Backend (Spring Boot 3.x)
- **Công nghệ:** Java 17+, Spring Boot 3, Spring Data JPA, Spring Security, JWT.
- **Database:** H2 Database (File-based cho development), SQL Server (Production). Được quản lý thông qua JPA/Hibernate (chưa thấy Flyway rõ ràng).
- **Core Modules & Packages:**
  - `com.hotel.controllers`: Các REST API endpoints.
  - `com.hotel.services` & `impl`: Chứa logic nghiệp vụ lõi (Booking, Authentication, Property Management, Inventory).
  - `com.hotel.repositories`: Tương tác với Database thông qua Spring Data JPA.
  - `com.hotel.entities`: Định nghĩa cấu trúc bảng (User, Property, RoomType, Room, Reservation, Payment...).
  - `com.hotel.dtos`: Request/Response format.
  - `com.hotel.security`: Xử lý JWT Token, Authorization filter.
  - `com.hotel.exceptions`: Global Exception Handler (`@ControllerAdvice`).
- **Thiếu liên kết (Gaps):**
  - Thiếu module Quartz/Scheduled Job để xử lý tự động Cancel booking hết hạn PENDING.
  - Phân quyền (Security) chưa liên kết chặt chẽ tới mức Data isolation cho Owner (Property IDOR).

## 2. Frontend (Angular 18+)
- **Công nghệ:** Angular 18, Tailwind CSS, Nx/RxJS, Vitest (Unit test), Playwright (E2E).
- **Cấu trúc:**
  - `src/app/core`: Interceptors (gắn JWT token), Guards (chặn route chưa login hoặc sai Role).
  - `src/app/features`: Các module theo nghiệp vụ (Admin, Owner, Receptionist, Guest).
  - `src/app/shared`: Components dùng chung (Button, Table, Form).
  - `src/environments`: API Endpoint config.
- **Thiếu liên kết (Gaps):**
  - Flow thanh toán (Payment) chỉ là Simulator, chưa có component xử lý Callback thật đồng bộ với Backend.
  - Flow đặt nhiều RoomType có UI (Search) nhưng bị chặn (Disconnected) khi đẩy xuống API do cấu trúc DTO không khớp.

## 3. Database Architecture
- **Relationships:**
  - `User` 1-N `UserProperty` N-1 `Property` (Owner quản lý nhiều Property).
  - `Property` 1-N `RoomType` 1-N `Room`.
  - `Reservation` liên kết với 1 `RoomType` (Lỗi thiết kế - cần sửa thành 1-N thông qua bảng trung gian).
  - `Reservation` 1-N `Payment`.
  - `Reservation` 1-1 `Invoice` (Chưa implement hoàn thiện).