# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Multi-Property & Subscriptions**: Khởi tạo cấu trúc dữ liệu và tài liệu thiết kế (ERD, UML, API_SPEC) cho phân hệ Đa cơ sở (Locations, Properties, UserProperties) và Hệ thống Gói dịch vụ (SubscriptionPlans, AccountSubscriptions).
- **DataSeeder**: Added `DataSeeder.java` to automatically assign `SUPER_ADMIN` role to the `admin` account and create a default `support` account on application startup to prevent login lockouts.

### Changed
- **Dashboard UI**: Completely overhauled the Admin Dashboard UI in the frontend:
  - Migrated a Tailwind CSS mockup to standard Bootstrap 5.
  - Implemented a sticky header with user profile dropdown, notifications, and search bar.
  - Implemented a collapsible, sticky dark-themed sidebar with interactive navigation.
  - Added statistics cards and placeholder charts for Engineering Work Orders.
  - Added a detailed work order table with priority and status badges.
- **Login UI**: Improved the login page interface by adjusting flex ratios for perfect balance and updating the application logo and favicon.

### Removed
- **AiAssistant**: Removed the floating AI Assistant button from the root layout (`app.html`) and moved it exclusively into the protected `admin-layout.html`.

### Added - Nationwide demo and owner operations (2026-07-15)
- Flyway V5 thêm metadata demo, `seed_key`, ảnh loại phòng, trạng thái housekeeping, tác vụ dọn phòng và tiến độ seed.
- Seeder `STANDARD`/`FULL_COVERAGE` idempotent theo batch; tài khoản demo lấy password từ `DEMO_ACCOUNT_PASSWORD`.
- Các nhóm owner FREE, NO_PLAN, STANDARD, BUSINESS, LIFETIME, EXPIRED cùng mapping cơ sở, subscription, payment và contract demo.
- API Admin theo dõi cơ sở, owner, đăng ký, chưa mua gói, nhân viên, loại phòng, phòng, đơn/thanh toán gói và hợp đồng.
- API Owner context, CRUD RoomType/Room, bulk room và giới hạn gói; dashboard hiển thị tồn phòng/housekeeping.
- Endpoint available rooms, assign rooms, check-in, service snapshot, check-out/invoice/payment và hoàn tất housekeeping.

### Changed - Nationwide demo and owner operations (2026-07-15)
- Public search ẩn `is_demo=true` trong profile production trừ khi cấu hình cho phép.
- Check-out chuyển phòng `OCCUPIED -> DIRTY`; chỉ housekeeping hoàn tất mới chuyển `AVAILABLE/CLEAN`.
- Reservation operation kiểm tra scope `user_properties`, chặn thao tác chéo cơ sở.
- Seeder chạy lại giữ nguyên trạng thái vận hành của phòng hiện có.

## [Unreleased]
### Added
- Tính năng Tự động Nhập Dữ Liệu (Automated Property Import) từ nguồn trực tuyến (Nominatim/OSM).
- Các thực thể mới: PropertyImportBatch, PropertyImportItem, PropertyClaimRequest, PropertyExternalPhoto.
- Cơ chế Deduplication 5 cấp kiểm tra trùng lặp cơ sở (Mã ngoài, Tên, SĐT, Website, Tọa độ).
- Luồng duyệt quyền sở hữu cơ sở (Claim Ownership) cho các cơ sở được import tự động (trạng thái IMPORTED_PENDING_REVIEW).
- UI/UX Quản lý Import và Claim cho Admin.
- Component yêu cầu Claim cho phía Khách hàng (User/Client) trên trang chi tiết khách sạn.
# 2026-07-15 - Home Search và Location Autocomplete

- Thêm API suggestion phân nhóm Province, Ward, Property và API Popular Destinations dùng dữ liệu SQL Server thật.
- Thiết kế lại popup LuxeStay với hai trạng thái, keyboard accessibility, recent search, loading/error/empty và mobile full-screen.
- Đồng bộ Search State qua Home, Search Result và Property Detail; giữ ngày, khách và số phòng khi điều hướng.
- Thêm index public discovery, test tích hợp backend và 10 E2E Playwright.
- Loại bỏ dữ liệu Home hard-code và tài nguyên ảnh bên ngoài khỏi các section đã sửa.
# 2026-07-15 - Public/Customer data quality

- Replaced demo Admin screenshots with licensed local property, destination, and
  room media; added type-specific fallbacks and asset attribution.
- Added Flyway V7/V8 for deterministic pricing, truthful demo review state,
  legacy-demo classification, and idempotent media repair.
- Added contextual account menu, partner-status route, customer invoices,
  account settings, real profile/avatar updates, and HTTP view refresh fixes.
- Added Public/Customer Playwright coverage and repaired the legacy Angular specs.
# 2026-07-15 - Search Result and booking flow

- Unified compact and Home search state, autocomplete, dates and guest selector.
- Added URL-backed server filters, Vietnamese currency formatting and responsive states.
- Rebuilt result cards with licensed local media, availability and explicit pricing.
- Added RoomType quantity selection, sticky booking summary and enriched checkout request.
- Added compact public hotel detail DTO and removed recursive entity serialization.
- Fixed public 401/403 redirect behavior and stale authentication state.
- Added pricing/filter integration tests, filter unit tests and Search Result Playwright tests.
# Admin Route, Permission and Inventory - 2026-07-15

- Replaced RoomType and Room placeholders with API-backed administration pages.
- Added role status/system protection/user counts and expanded permission matrix controls.
- Added safe menu deduplication migration with backup tables and canonical function codes.
- Added physical-room bulk creation, maintenance/open actions and soft deactivation.
- Added Admin Playwright regression and route/menu audit documentation.
