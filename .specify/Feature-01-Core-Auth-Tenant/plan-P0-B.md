# Kế hoạch & Phân tích Nhóm P0-B: Chuẩn hóa HTTP 401/403 và Redirect Loop

## 1. Luồng Request: Angular → Java Security → Controller

```
Angular HttpClient
  → jwtInterceptor (đính Bearer token)
  → HTTP request
  → Spring Security Filter Chain
      → JwtAuthFilter (parse token, set SecurityContext)
      → ExceptionTranslationFilter
          → JwtAuthenticationEntryPoint (401 JSON nếu chưa xác thực)
          → JwtAccessDeniedHandler (403 JSON nếu Spring Security từ chối)
  → DispatcherServlet
      → PermissionInterceptor (HandlerInterceptor, KHÔNG nằm trong Security Filter Chain)
          → Kiểm tra @Permission bitmask → 403 JSON
          → Kiểm tra @RequireFeature subscription → 403 JSON
      → Controller
  → HTTP response
  → errorInterceptor (Angular)
      → 401 → logout + redirect /login
      → 403 → navigate /403
```

**Lưu ý quan trọng:** `PermissionInterceptor` là Spring MVC `HandlerInterceptor`,
chạy SAU Security Filter Chain, SAU DispatcherServlet. Lỗi phát sinh ở đây
KHÔNG được `JwtAccessDeniedHandler` bắt (handler đó chỉ bắt `AccessDeniedException`
trong Security Filter Chain).

## 2. Phân biệt các loại truy cập bị từ chối

| Loại | HTTP Status | Nơi phát sinh | Handler |
|------|-------------|----------------|---------|
| Chưa xác thực (thiếu/sai/hết hạn token) | 401 | Security Filter Chain | `JwtAuthenticationEntryPoint` |
| Thiếu role (Spring Security) | 403 | Security Filter Chain | `JwtAccessDeniedHandler` |
| Thiếu permission bitmask | 403 | `PermissionInterceptor` | Tự ghi response (hiện dùng `sendError` → HTML) |
| Thiếu subscription feature | 403 | `PermissionInterceptor` | Tự ghi response (hiện dùng `sendError` → HTML) |
| Tenant IDOR | Chưa xử lý | — | P0-D (ngoài phạm vi) |

## 3. Nguyên nhân gốc

### 3.1 "Tài khoản có quyền nhưng vẫn bị 403"
- **Trước P0-B:** `SecurityConfig` không có `authenticationEntryPoint` → Spring mặc định
  dùng `Http403ForbiddenEntryPoint` cho stateless → token hết hạn/thiếu → trả 403 thay vì 401.
- **Đã sửa:** Thêm `JwtAuthenticationEntryPoint` + `JwtAccessDeniedHandler` vào
  `SecurityConfig.exceptionHandling()`.

### 3.2 Redirect Loop
- Frontend `errorInterceptor` trước đây chỉ xử lý 403 ở `isAdminArea`.
  Client area nhận 403 (thực ra là 401 bị trả sai) → không redirect → component retry → loop.
- **Đã sửa:** 403 → `/403` mọi khu vực.

### 3.3 PermissionInterceptor trả HTML (CHƯA SỬA)
- `response.sendError(403, "message")` → Tomcat/Spring trả trang HTML lỗi mặc định.
- Frontend nhận HTML thay vì JSON → parse lỗi hoặc hiển thị sai.
- Cần đổi sang ghi JSON trực tiếp vào response (dùng `ObjectMapper`).

## 4. Phần đã hoàn thành (có test)

| Thay đổi | File | Test |
|----------|------|------|
| 401 JSON khi thiếu token | `JwtAuthenticationEntryPoint.java` | `AuthExceptionIntegrationTest.whenNoToken_thenReturns401Json` ✅ |
| 401 JSON khi token sai | `JwtAuthenticationEntryPoint.java` | `AuthExceptionIntegrationTest.whenInvalidToken_thenReturns401Json` ✅ |
| 403 JSON từ Security Filter | `JwtAccessDeniedHandler.java` | Chưa có test riêng (chỉ handler, chưa trigger được trong test) |
| Đăng ký handler | `SecurityConfig.java` | Covered bởi 2 test trên |

## 5. Phần còn thiếu

### 5.1 Backend
1. **PermissionInterceptor trả JSON thay vì HTML:**
   - Inject `ObjectMapper` vào `PermissionInterceptor`.
   - Thay tất cả `response.sendError(status, message)` bằng helper method ghi JSON
     với schema `{status, error, message, path}` và `Content-Type: application/json`.
2. **Test bổ sung:**
   - `whenExpiredToken_thenReturns401Json` — token hết hạn → 401 JSON.
   - Test cho PermissionInterceptor 403 JSON (cần endpoint có `@Permission` annotation,
     có thể dùng `@WebMvcTest` với mock controller).

### 5.2 Frontend
1. **Chống redirect loop:**
   - `error-interceptor.ts`: Kiểm tra `router.url` trước khi navigate.
     Nếu đang ở `/login` hoặc `/admin/login` → không redirect 401.
     Nếu đang ở `/403` → không redirect 403.
2. **Test file `error-interceptor.spec.ts`:** (chưa tồn tại)
   - 401 → gọi `logout()` + navigate `/login` đúng 1 lần.
   - 401 khi đang ở `/login` → không navigate lại.
   - 403 → navigate `/403`, không logout, không xóa token.
   - 403 khi đang ở `/403` → không navigate lại.
   - Rethrow error đúng convention.

## 6. Thứ tự hoàn thiện

| Bước | Loại | Mô tả |
|------|------|-------|
| 1 | Backend test (đỏ) | Thêm test PermissionInterceptor trả JSON (sẽ fail vì đang trả HTML) |
| 2 | Backend code | Sửa `PermissionInterceptor` dùng `ObjectMapper` ghi JSON |
| 3 | Backend test (xanh) | Chạy lại toàn bộ test suite, xác nhận pass |
| 4 | Frontend test (đỏ) | Tạo `error-interceptor.spec.ts` |
| 5 | Frontend code | Sửa `error-interceptor.ts` chống redirect loop |
| 6 | Frontend test (xanh) | Chạy `ng test --include=**/error-interceptor*` |
| 7 | Docs | Cập nhật `API_SPEC.md`, `UML.md` SAU KHI test xanh |
| 8 | Status | Cập nhật `tasks.md` TASK-B1, B2 → PASSED |

## 7. Acceptance Criteria

### TASK-B1 (Backend)
- [ ] Không có token → 401, Content-Type `application/json`, body `{status, error, message, path}`.
- [ ] Token sai chữ ký → 401 JSON.
- [ ] Token hết hạn → 401 JSON.
- [ ] Token OK + thiếu permission bitmask → 403 JSON (từ PermissionInterceptor).
- [ ] Token OK + thiếu subscription feature → 403 JSON (từ PermissionInterceptor).
- [ ] Không trả HTML ở bất kỳ trường hợp lỗi auth/authz nào.
- [ ] Tất cả test backend pass.

### TASK-B2 (Frontend)
- [ ] 401 → logout + redirect `/login` (admin area → `/admin/login`).
- [ ] Đang ở `/login` hoặc `/admin/login` nhận 401 → không redirect lại.
- [ ] 403 → navigate `/403`, không logout, không xóa token.
- [ ] Đang ở `/403` nhận 403 → không redirect lại.
- [ ] Rethrow error.
- [ ] File `error-interceptor.spec.ts` tồn tại và pass.