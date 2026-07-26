# Đặc Tả Kỹ Thuật (Feature 01) - Core Auth, Tenant Isolation & Security Baseline

## 1. Giới thiệu
Feature 01 giải quyết các vấn đề nền tảng (P0) mang tính chặn (blocker) trước khi triển khai bất kỳ tính năng nghiệp vụ nào khác, bao gồm:
1. Rò rỉ thông tin nhạy cảm (Hard-coded secrets) và rác dữ liệu trong Git.
2. Thiết lập quy chuẩn (Contract) rõ ràng cho HTTP 401 và 403 giữa Backend và Frontend.
3. Đồng bộ RBAC bitmask để vá lỗi 403.
4. Kiểm tra, chứng minh và vá lỗ hổng Tenant Isolation (nguy cơ IDOR do thiếu Hibernate Filter).

## 2. Requirement - Task - Test Matrix

| Requirement | Plan Section | Task ID | Test Strategy | Acceptance Evidence |
| --- | --- | --- | --- | --- |
| REQ-01: Không chứa secret (DB, JWT, VNPay) trong source. | P0-A: Secret Containment | TASK-A1 | Quét grep tìm chuỗi bí mật trong file yml, kiểm tra cơ chế startup fail-fast nếu thiếu biến. | `application.yml` dùng placeholder `${VAR_NAME}`, app không khởi động nếu không set biến. |
| REQ-02: Dọn dẹp DB backup/artefacts khỏi Git index. | P0-A: Repo Hygiene | TASK-A2 | Kiểm tra `git ls-files` không còn chứa `*.bak`, `*.mv.db`, `*.trace.db`. | File được untrack (chỉ local), có rule `.gitignore`. |
| REQ-03: Phân biệt 401 (chưa auth) và 403 (cấm). | P0-B: Baseline Auth | TASK-B1 | Gửi token hết hạn -> 401. Gửi token hợp lệ nhưng route bị cấm -> 403. UI xử lý riêng biệt. | E2E/Unit test FE không bị redirect loop. |
| REQ-04: Sửa lỗi 403 do sai RBAC mapping hiện tại. | P0-C: RBAC Mapping | TASK-C1 | Truy cập route Admin bằng quyền hợp lệ, gọi log API kiểm tra bitmask trả về. | Route hoạt động trả HTTP 200 thay vì 403. |
| REQ-05: Ngăn chặn Tenant IDOR. Tenant A không đọc/sửa dữ liệu Tenant B. | P0-D: Tenant Isolation | TASK-D1 | Đăng nhập Tenant A, cố tình GET/PUT/DELETE resource của Tenant B. | Test fail lúc đầu (nếu có lỗ hổng), pass (nhận 404/403) sau khi áp dụng Hibernate Filter/Auth logic. |

## 3. Quy chuẩn mới (Contract)
- **HTTP 401 (Unauthorized):** Nghĩa là User chưa đăng nhập, token không có, không hợp lệ hoặc đã hết hạn. Frontend **phải** xóa local storage và chuyển hướng đến trang Đăng nhập.
- **HTTP 403 (Forbidden):** Nghĩa là User đã đăng nhập hợp lệ (Token sống), nhưng bị từ chối do thiếu quyền, sai Tenant, hoặc hết gói cước. Frontend **không được** logout, **không được** chuyển về Đăng nhập (gây vòng lặp). Thay vào đó, chuyển sang route `/403-access-denied` hoặc hiển thị Toast/Dialog báo lỗi.

## 4. Tenant Isolation Logic
- Không tự động kết luận hệ thống có IDOR nếu chưa test. Cần có Red Test (Test thất bại trước khi sửa).
- Giải pháp (sau khi có bằng chứng IDOR): Ưu tiên `Hibernate @Filter` ở mức global Entity để tránh sót. Tuy nhiên, `System Admin` và `Scheduler` thread không có tenantId, nên cần cấu hình bypass (AOP Aspect) để không bị nuốt dữ liệu oan.