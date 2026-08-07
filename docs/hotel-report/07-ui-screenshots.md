# Danh mục ảnh giao diện

Ngày chụp/đối chiếu: 2026-08-05<br>
Tổng số file ảnh: **41**. Trong đó **37 route-specific screen đã render** và **4 ảnh ghi nhận permission denied (403)**.

## 1. Cách hiểu trạng thái

- **PASS:** route/màn hình mục tiêu render được và thể hiện chức năng chính tại thời điểm chụp.
- **PARTIAL:** màn hình render nhưng luồng chưa hoàn tất, dùng fixture, thiếu token/provider, hoặc có runtime console error cần điều tra.
- **FAIL:** route mục tiêu không render được; ảnh ghi nhận lỗi/403.
- Trạng thái ảnh không thay thế trạng thái capability trong `02-functional-inventory.md` và không chứng minh mọi thao tác CRUD/API đều hoạt động.

Trong phiên browser, điều hướng admin/management nhiều lần phát sinh `SyntaxError: Unexpected token '<'`, thường là dấu hiệu một request mong JSON/JavaScript nhưng nhận HTML. Vì vậy các màn admin/management được đánh dấu PARTIAL dù phần giao diện nhìn thấy đã render.

## 2. Public và customer

| Mã ảnh | Tên chức năng | Đường dẫn ảnh | Mô tả ngắn | Trạng thái | Ghi chú |
|---|---|---|---|---|---|
| UI-001 | Trang chủ desktop | `screenshots/UI-001-home-desktop.png` | Hero tìm kiếm và nội dung public trên viewport desktop | PASS | Có bằng chứng render thực tế |
| UI-002 | Trang chủ mobile | `screenshots/UI-002-home-mobile.png` | Trang chủ ở viewport mobile | PASS | Bằng chứng responsive, không thay thế accessibility audit |
| UI-003 | Kết quả tìm kiếm | `screenshots/UI-003-search-results.png` | Danh sách khách sạn theo tiêu chí hợp lệ | PASS | Public search API đã phản hồi trong E2E runtime |
| UI-004 | Chi tiết khách sạn | `screenshots/UI-004-hotel-detail.png` | Thông tin khách sạn, phòng và lựa chọn đặt | PASS | Luồng tiếp tục được đến checkout |
| UI-005 | Đăng nhập | `screenshots/UI-005-login.png` | Form đăng nhập customer | PASS | OAuth ngoài chưa được tính là PASS |
| UI-006 | Đăng ký | `screenshots/UI-006-register.png` | Form đăng ký tài khoản | PASS | SMTP preview tiếng Việt đã xác minh 9/9 trong P0-SMTP-VERIFY |
| UI-007 | Payment simulator | `screenshots/UI-007-payment-simulator.png` | Simulator hiển thị trạng thái session không hợp lệ khi thiếu signed token | PARTIAL | Fail-closed đúng kỳ vọng; chưa có signed success/fail/cancel flow |
| UI-008 | Đăng ký đối tác | `screenshots/UI-008-partner-register.png` | Form onboarding đối tác/property | PASS | Frontend regression còn lỗi partner approval action ở nhánh admin |
| UI-009 | Hồ sơ khách hàng | `screenshots/UI-009-customer-profile.png` | Thông tin hồ sơ customer | PASS | Dùng fixture/session kiểm thử |
| UI-010 | Lịch sử booking | `screenshots/UI-010-booking-history.png` | Danh sách booking của customer | PASS | Chưa chứng minh toàn bộ status/action |
| UI-011 | Hóa đơn của tôi | `screenshots/UI-011-my-invoices.png` | Danh sách hóa đơn customer | PASS | Chưa kiểm chứng export/reconciliation |
| UI-011A | PDF hóa đơn E2E | `fix-progress/evidence/P0-SMTP-VERIFY-invoice-page1.png`, `P0-SMTP-VERIFY-invoice-page2.png` | Render invoice attachment | PASS | 2 trang A4, 5.105 byte; line items, payment, refund và totals đầy đủ |
| UI-012 | Lịch sử hoàn tiền | `screenshots/UI-012-refund-history.png` | Danh sách refund customer | PASS | Provider refund sandbox chưa E2E |
| UI-013 | Cài đặt tài khoản | `screenshots/UI-013-account-settings.png` | Màn hình account/password settings | PASS | Social identity mock còn lỗi trong frontend test khác |
| UI-014 | Booking checkout | `screenshots/UI-014-booking-checkout.png` | Form xác nhận khách, phòng và thanh toán | PASS | Checkout render; provider payment chưa hoàn tất |

## 3. System admin

| Mã ảnh | Tên chức năng | Đường dẫn ảnh | Mô tả ngắn | Trạng thái | Ghi chú |
|---|---|---|---|---|---|
| UI-015 | Admin dashboard | `screenshots/UI-015-admin-dashboard.png` | KPI và điều hướng quản trị | PARTIAL | Màn render; phiên có console parse error cần xử lý |
| UI-016 | Quản lý người dùng | `screenshots/UI-016-admin-users.png` | Danh sách/quản lý user | PARTIAL | Màn render; chưa chạy CRUD E2E đầy đủ |
| UI-017 | Quản lý khách sạn | `screenshots/UI-017-admin-properties.png` | Danh sách property phía platform | PARTIAL | Màn render; cần test tenant/publish action |
| UI-018 | Quản lý loại phòng | `screenshots/UI-018-admin-room-types.png` | Loại phòng phía admin | PARTIAL | Màn render; cần test validation/tenant |
| UI-019 | Quản lý phòng | `screenshots/UI-019-admin-rooms.png` | Phòng vật lý phía admin | PARTIAL | Màn render; cần test lifecycle/inventory |
| UI-020 | Quản lý booking | `screenshots/UI-020-admin-reservations.png` | Danh sách reservation/booking | PARTIAL | Màn render; backend regression chưa xanh |
| UI-021 | Quản lý dịch vụ | `screenshots/UI-021-admin-services.png` | Danh mục/dịch vụ khách sạn | PARTIAL | Màn render; chưa kiểm tra charge E2E |
| UI-022 | Quản lý hóa đơn | `screenshots/UI-022-admin-invoices.png` | Danh sách hóa đơn platform | PARTIAL | Màn render; chưa kiểm chứng reconciliation/export |
| UI-023 | Quản lý role | `screenshots/UI-023-admin-roles.png` | Danh sách role | PARTIAL | Cần test mutation và protected role |
| UI-024 | Phân quyền role | `screenshots/UI-024-admin-role-permissions.png` | Ánh xạ permission cho role | PARTIAL | Cần đối chiếu route/API matrix |
| UI-025 | Quản lý plan | `screenshots/UI-025-admin-plans.png` | Gói dịch vụ/subscription plans | PARTIAL | Frontend test còn lỗi policy wording |
| UI-026 | Cấu hình thanh toán | `screenshots/UI-026-admin-payment-config.png` | Payment configuration phía platform | PARTIAL | Provider credential/sandbox chưa đầy đủ |
| UI-027 | Doanh thu nền tảng | `screenshots/UI-027-admin-platform-revenue.png` | Báo cáo doanh thu platform | PARTIAL | Backend financial performance test còn lỗi |
| UI-028 | Audit log | `screenshots/UI-028-admin-audit-log.png` | Nhật ký hoạt động platform | PARTIAL | Màn render; cần test filter/export/retention |
| UI-029 | Admin chat | `screenshots/UI-029-admin-chat.png` | Chat/notification phía admin | PARTIAL | Frontend chat setup test còn lỗi |
| UI-030 | Phê duyệt property | `screenshots/UI-030-admin-property-approvals.png` | Hàng đợi duyệt đối tác/property | PARTIAL | Partner approval action test còn lỗi |

## 4. Property management

| Mã ảnh | Tên chức năng | Đường dẫn ảnh | Mô tả ngắn | Trạng thái | Ghi chú |
|---|---|---|---|---|---|
| UI-031 | Management dashboard | `screenshots/UI-031-management-dashboard.png` | Dashboard property trên desktop | PARTIAL | Màn render với owner fixture; console parse error cần điều tra |
| UI-032 | Management loại phòng | `screenshots/UI-032-management-room-types.png` | Quản lý loại phòng trong property scope | PARTIAL | Chưa chạy mutation E2E |
| UI-033 | Management phòng | `screenshots/UI-033-management-rooms.png` | Quản lý phòng trong property scope | PARTIAL | Chưa chạy inventory/status E2E |
| UI-034 | Housekeeping | `screenshots/UI-034-management-housekeeping.png` | Danh sách/nhiệm vụ housekeeping | PARTIAL | Chưa kiểm tra assignment race/lifecycle |
| UI-035 | Management dịch vụ | `screenshots/UI-035-management-services.png` | Quản lý dịch vụ property | PARTIAL | Chưa kiểm tra charge/invoice E2E |
| UI-036 | Payment configuration của property | `screenshots/UI-036-management-payment-config.png` | Ảnh ghi nhận route bị từ chối | FAIL | Owner fixture bị redirect/trả 403; cần chốt permission |
| UI-037 | Management refunds | `screenshots/UI-037-management-refunds.png` | Ảnh ghi nhận route refund bị từ chối | FAIL | Owner fixture bị redirect/trả 403 |
| UI-038 | Doanh thu property | `screenshots/UI-038-management-property-revenue.png` | Báo cáo doanh thu theo property | PARTIAL | Màn render; financial test còn lỗi |
| UI-039 | Subscription billing | `screenshots/UI-039-management-subscription-billing.png` | Ảnh ghi nhận route subscription bị từ chối | FAIL | Owner fixture bị redirect/trả 403 |
| UI-040 | Management audit log | `screenshots/UI-040-management-audit-log.png` | Ảnh ghi nhận route audit log bị từ chối | FAIL | Owner fixture bị redirect/trả 403 |
| UI-041 | Management dashboard mobile | `screenshots/UI-041-management-dashboard-mobile.png` | Dashboard property ở viewport mobile | PARTIAL | Responsive render; console issue và behavior sâu chưa kiểm chứng |

## 5. Thống kê ảnh

| Nhóm | Số ảnh | PASS | PARTIAL | FAIL |
|---|---:|---:|---:|---:|
| Public/customer | 14 | 13 | 1 | 0 |
| System admin | 16 | 0 | 16 | 0 |
| Property management | 11 | 0 | 7 | 4 |
| **Tổng** | **41** | **13** | **24** | **4** |

## 6. Khoảng trống cần chụp thêm sau khi sửa

- Payment simulator với signed token cho success, failure, cancel và replay.
- VNPay/MoMo/ZaloPay sandbox return/callback result của provider được chọn.
- CRUD success/error cho user, property, room type, room, booking, service và role/permission.
- Check-in, in-house charge, check-out và housekeeping completion.
- Bốn route management sau khi sửa permission.
- Error/empty/loading states quan trọng và accessibility keyboard/focus evidence.
