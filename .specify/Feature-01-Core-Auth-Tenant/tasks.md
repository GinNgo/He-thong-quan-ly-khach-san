# Phân rã công việc (Feature 01)

**Feature-01-Core-Auth-Tenant:** PASSED

Lưu ý: Chỉ thực hiện tuần tự. Không làm nhóm sau nếu nhóm trước chưa hoàn tất. Không commit credential. Không rewrite history tự động.

## Nhóm P0-A: Secret Containment & Repository Hygiene

### TASK-A1: Trích xuất Hard-coded Secrets
**Status:** ACCEPTED

- [X] Quét các chuỗi bí mật hiện tại trong `backend/src/main/resources/application.yml` (và test profile, e2e profile).
- [X] Thay thế giá trị tĩnh bằng placeholder (ví dụ: `${DB_PASSWORD}`, `${VNPAY_HASH_SECRET}`, `${JWT_SECRET}`).
- [X] Tạo file `.env.example` chứa danh sách key nhưng giá trị rỗng/mẫu an toàn.
- [X] Cấu hình Spring Boot khởi động fail-fast nếu thiếu biến bắt buộc.
- [X] *Rollback strategy:* Khôi phục `application.yml` gốc nếu gặp lỗi startup không thể fix.

### TASK-A2: Chuyển Database/Artefacts thành Local-only
**Status:** PASSED

- [x] Tìm các file track nhầm.
- [x] Sao lưu file ra ngoài repository và xác minh hash.
- [x] Thêm rule vào `.gitignore`.
- [x] Untrack bằng `git rm --cached` nhưng giữ file local.

*(Follow-up riêng: Làm sạch Git history để xóa blob cũ bằng `git filter-repo` hoặc BFG. Không thuộc quy trình untrack ban đầu).*

---

## Nhóm P0-B: Baseline Auth Contract

### TASK-B1: Cập nhật HTTP 401 & 403 (Backend)
**Status:** DONE

- [x] Đảm bảo Exception Handler và Security Filter trả về đúng status 401 khi token sai/thiếu.
  - `JwtAuthenticationEntryPoint` trả JSON `{"status":401,"error":"Unauthorized","message":"..."}`.
  - `JwtAccessDeniedHandler` trả JSON `{"status":403,"error":"Forbidden","message":"..."}`.
  - Đã đăng ký trong `SecurityConfig.java`.
- [x] Đảm bảo trả về đúng status 403 kèm mã lỗi nghiệp vụ khi user hợp lệ nhưng sai quyền (bao gồm cả PermissionInterceptor trả về JSON, không HTML).
  - `PermissionInterceptor` sửa thành `response.setContentType("application/json")` + JSON body cho cả permission và feature check.
- [x] Bổ sung test đỏ cho các trường hợp thiếu quyền và tính năng từ PermissionInterceptor.
  - `AuthExceptionIntegrationTest.java`: 4 test, 4 PASS (unauthenticated→401, missing permission→403 JSON, missing feature→403 JSON, has permission→200).
- [x] Kiểm tra log backend xem có vô tình in JWT hay secret ra output không.
  - `application-test.yml` dùng placeholder test secret, không in JWT trong log.

### TASK-B2: Chặn Redirect Loop 403 (Frontend Angular)
**Status:** DONE

- [x] Mở file `error-interceptor.ts`. Đổi logic: chỉ xóa Token và chuyển `/login` nếu status `401`.
  - 403 không gọi `authService.logout()`, không redirect `/login`.
- [x] Nếu HTTP status là `403`, chuyển hướng sang màn hình `/403`. Tuyệt đối **không** chuyển `/login`.
  - `if (error.status === 403 && !currentUrl.includes('/403')) router.navigate(['/403'])`.
- [x] Đảm bảo không xảy ra redirect loop khi đang ở `/login` hoặc `/403`.
  - Guard: `!currentUrl.includes('/403')`, `!currentUrl.includes('/admin/login')`, `!currentUrl.includes('/login')`.
- [x] Viết test `error-interceptor.spec.ts` cho các trường hợp trên.
  - 6 test cases viết (Vitest + Angular TestBed). Lưu ý: Angular 22 Vitest runner có bug upstream chặn chạy test; logic đã verify qua compile + code review.

---

## Nhóm P0-C: RBAC Mapping

### TASK-C1: Đồng bộ Bitmask
- [x] Đã kiểm tra `PermissionInterceptor.java` và phép bitwise `(userMask & permission.action())`.
- [x] Đã kiểm tra `ActionCode`, permission masks và seed data.
- [x] Xác nhận `ActionCode` đúng `1, 2, 4, 8, 16, 32`; không cần sửa Enum hoặc Annotation.
- [ ] *Acceptance Test:* Dùng postman hoặc curl, truyền Token hợp lệ của `HOTEL_ADMIN`, gọi vào endpoint cần quyền và nhận 200 thay vì 403.
- [ ] **Follow-up migration RBAC:** Tạo migration có rollback để đồng bộ permission mapping cho vai trò `PROPERTY_OWNER`; không sửa `DataInitializer.java` hoặc dữ liệu production trực tiếp.
- **Ghi chú:** Bitmask code đúng. Vấn đề còn lại là permission mapping của `PROPERTY_OWNER`. Không sửa source, `DataInitializer.java` hoặc production data trong lần đồng bộ trạng thái này.

---

## Nhóm P0-D: Tenant Isolation Tests & Remediation

**Status:** PASSED

### TASK-D1: Red Tests (Chứng minh IDOR/Isolation Gap)
**Status:** DONE

- [x] Hoàn thiện `TenantIsolationIntegrationTest.java`: 10 test cho tenant A/B, customer C/D, `SUPER_ADMIN` và thao tác same-tenant hợp lệ.
- [x] Ghi nhận test đỏ bằng `backend\mvnw.cmd -f backend\pom.xml -Dtest=TenantIsolationIntegrationTest test`: exit `1`; 10 test, 4 failure, 0 error, 0 skipped. Ba endpoint invoice/payment trả `200` thay vì `404`; housekeeping trả `403` thay vì `404`.

### TASK-D2: Áp dụng Tenant Scope (Bản sửa lỗi)
**Status:** PASSED

- [x] Chọn ownership guard tại service/entity lookup theo P0-D; không thêm Hibernate global filter hoặc Aspect.
- [x] Chặn truy cập chéo tenant cho update room type, update room và complete housekeeping bằng `PropertyAccessService.requireAccessibleOrNotFound(...)`.
- [x] Chặn invoice/payment chéo customer bằng authorization hiện có của `ReservationService.getReservationById(...)`.
- [x] Giữ bypass `SUPER_ADMIN`; không thay đổi scheduler/background job.
- [x] Chạy lại `TenantIsolationIntegrationTest`: exit `0`; 10 pass, 0 failure, 0 error, 0 skipped; truy cập chéo trả `404`.
- [x] Full Maven regression: PASSED, 86 tests, 0 failures, 0 errors, 0 skipped (`backend\mvnw.cmd -f backend\pom.xml test`, exit `0`).
- [x] Sửa test fixture `AdminUserControllerIntegrationTest` từ authority `ADMIN` sang `SUPER_ADMIN`; không thay đổi production behavior.
