# Acceptance Checklist - Feature 01

**Feature-01-Core-Auth-Tenant:** PASSED

## Gate P0-A: Secret Containment & Repo Hygiene

### TASK-A1 Acceptance — ACCEPTED
- [X] Không có file cấu hình nào trong git (sau khi checkout mới nhất) chứa plaintext password DB.
- [X] Không chứa `JWT_SECRET` plaintext.
- [X] Không chứa VNPAY hash/key plaintext.
- [X] Tồn tại `.env.example` chứa placeholder thay thế.
- [X] Chạy Spring Boot không set env -> **App crash/Fail-fast**, báo lỗi thiếu config.

### TASK-A2 Acceptance — PENDING
- [ ] File `HotelDB.bak` bị untrack (`git status` báo `deleted` khỏi index nhưng vẫn còn ở local).
- [ ] File `backend/hoteldb.mv.db`, `backend/hoteldb.trace.db` bị untrack.
- [ ] `git ls-files` không còn chứa `.bak` hay `.db`.

## Gate P0-B: Baseline Auth
- [ ] Gọi API với Token hết hạn -> Frontend xóa Token, redirect về `/login`.
- [ ] Gọi API với Token hợp lệ nhưng vào route bị cấm (khác Tenant/khác Role) -> HTTP 403.
- [ ] HTTP 403 không gây redirect loop liên tục trên Frontend (không tự động về `/login`).

## Gate P0-C: RBAC Mapping
- [ ] Xác nhận được rule Bitmask của `PermissionInterceptor` đã khớp với Database hoặc code khai báo (Ví dụ: `MANAGE_ROOMS` = bit 1, `MANAGE_BOOKINGS` = bit 2).
- [ ] `HOTEL_ADMIN` truy cập route thuộc quyền quản lý thành công (HTTP 200).

## Gate P0-D: Tenant Isolation — PASSED
- [x] Có Red Test chứng minh API đọc hoặc sửa (GET/PUT) dữ liệu bị lọt (IDOR) hoặc trả lời sai.
- [x] Sau khi vá, Tenant A không thể đọc/sửa dữ liệu của Tenant B.
- [x] `SUPER_ADMIN` không bị chặn, xem được tất cả Tenant.
- [x] Full Maven regression: PASSED, 86 tests, 0 failures, 0 errors, 0 skipped.
- [x] `AdminUserControllerIntegrationTest` dùng fixture authority `SUPER_ADMIN` thay cho `ADMIN`; không thay đổi production behavior.
