# Kế Hoạch Pre-Implementation - Feature 01

## 1. Mục tiêu và giới hạn

Feature 01 thiết lập baseline bảo mật cho secret, repository hygiene, authentication, RBAC và tenant isolation.

Không được:
- Coi thiếu Hibernate Filter là bằng chứng chắc chắn của IDOR.
- Chọn Hibernate Filter trước khi hoàn tất kiểm kê endpoint/query và red tests.
- Ghi credential mới vào repository.
- Xóa bản database/backup local trước khi có bản sao ngoài repository.
- Rewrite Git history nếu chưa được người dùng phê duyệt.
- Trộn giới hạn subscription vào lỗi authentication.
- Bypass tenant chỉ vì thread không có tenant context.

Mỗi nhóm P0 phải build và test độc lập. Chỉ chuyển nhóm sau khi evidence của nhóm hiện tại được lưu.

## 2. Thứ tự triển khai

### P0-A — Secret containment và repository hygiene

#### A1. Kiểm kê secret
Kiểm kê từng file/key và trạng thái Git:
- `backend/src/main/resources/application.yml`
  - `spring.datasource.username`
  - `spring.datasource.password`
  - `payment.vnpay.tmn-code`
  - `payment.vnpay.hash-secret`
  - `jwt.secret`
- Các profile test/e2e, `.env`, Docker Compose, log và tài liệu có thể lặp lại giá trị.
- Dùng `git ls-files`, `git log -S` hoặc tương đương read-only để xác định giá trị từng được commit hay chưa. Không in nguyên secret vào báo cáo/log; chỉ ghi fingerprint đã che và vị trí.

#### A2. Containment
- Chuyển secret bắt buộc sang environment variables hoặc cơ chế secret hiện có.
- Cấu hình mẫu chỉ dùng placeholder an toàn.
- Không có default bí mật cho production.
- Xác minh ứng dụng fail-fast với thông báo tên biến còn thiếu nhưng không lộ giá trị.
- Xác minh startup/runtime log không chứa secret, connection string có password, JWT hoặc payment signature.
- Credential từng commit phải được xoay vòng ở nhà cung cấp tương ứng. Nếu không có quyền, ghi `BLOCKED — credential rotation requires owner/provider access`.
- Rollback cấu hình: khôi phục file cấu hình từ revision trước; truyền credential cũ qua kênh ngoài repository trong thời gian rollback. Không commit credential.

#### A3. Database artefacts
- Phân biệt tracked với local-only bằng `git ls-files` và `git status --ignored`.
- Kiểm tra loại file, kích thước, metadata/schema an toàn; không dump dữ liệu/PII vào log.
- Người có thẩm quyền xác định dữ liệu thật/nhạy cảm. Khi chưa xác định, coi là dữ liệu nhạy cảm.
- Sao lưu mỗi artefact ra vị trí ngoài repository, kiểm tra hash trước/sau.
- Thêm ignore rule cho `*.bak`, `*.mv.db`, `*.trace.db` và artefact runtime phù hợp.
- Chỉ sau backup mới dùng `git rm --cached` để bỏ khỏi index, không xóa bản local.
- Tách task làm sạch Git history. Nếu artefact đã push, task đó ở trạng thái `REQUIRES APPROVAL`; không rewrite trong P0-A mặc định.
- Rollback untrack: `git restore --staged` trước commit; nếu đã commit thì revert commit hygiene. Không dùng reset destructive.

#### Gate P0-A
- Build/config validation độc lập.
- Secret scan không thấy giá trị đã biết trong tracked working tree.
- Missing-variable test fail-fast.
- Log scan không lộ secret.
- Hash chứng minh backup ngoài repository tồn tại.
- Git index không chứa artefact mục tiêu sau task được duyệt.
- Rotation hoàn tất hoặc có blocker và owner rõ ràng.

### P0-B — Baseline authentication và contract 401/403

Chuẩn hóa contract:
- `401`: thiếu token, token hết hạn, sai chữ ký hoặc không hợp lệ.
- `403`: principal đã xác thực nhưng thiếu permission, tenant membership hoặc entitlement.
- Hết gói dùng error code nghiệp vụ ổn định trong response 403 hoặc status nghiệp vụ khác đã được API contract phê duyệt; không giả thành 401.
- Error body chỉ chứa `code`, thông báo an toàn, correlation ID nếu có; không chứa mask nội bộ, token, tenant khác hoặc stack trace.
- Frontend 401 xóa session và điều hướng Login đúng một lần.
- Frontend 403 giữ session và điều hướng Access Denied hoặc hiển thị lỗi nghiệp vụ; không điều hướng Login.
- Route guard và HTTP interceptor có trách nhiệm riêng, không tạo vòng lặp.

Test độc lập:
1. Không token, token sai và token hết hạn trả 401.
2. Token hết hạn chỉ điều hướng Login một lần.
3. Token hợp lệ thiếu permission trả 403 và không logout.
4. Sai tenant trả 403 hoặc 404 theo policy chống enumeration đã đặc tả, không lộ dữ liệu.
5. Hết gói trả code nghiệp vụ đã thống nhất, không trả 401.
6. Backend cho phép nhưng frontend guard chặn bị contract/E2E test phát hiện.
7. Frontend hiện menu nhưng backend từ chối bị integration/E2E test phát hiện.

Rollback:
- Backend và frontend contract được thay theo commit độc lập.
- Nếu frontend mới chưa tương thích backend, feature flag hoặc deploy backend-compatible response trước; rollback về handler cũ không được làm lộ dữ liệu.

### P0-C — RBAC mapping và lỗi permission hiện tại

- Lập inventory authoritative: function code, action bits, roles, role-permission rows, endpoint annotation, frontend menu/guard.
- Đối chiếu giá trị action bit thực tế; không giả định `1/2/4/8` nếu source/seed chưa xác nhận.
- Tái hiện 403 bằng tài khoản đã được cấp đúng permission theo DB.
- Ghi decision trace an toàn: correlation ID, user ID nội bộ, function code/action yêu cầu và lý do từ chối; không log JWT, secret hoặc dữ liệu tenant khác.
- Sửa đúng lớp gây lệch sau khi red test xác định: seed/migration, backend mapping, claim, guard hoặc menu.
- Permission backend là nguồn quyết định cuối. UI chỉ hỗ trợ trải nghiệm.

Test độc lập:
- Mỗi role có positive test và negative test với endpoint đại diện.
- Nhiều role phải hợp permission theo rule đã đặc tả.
- Permission thay đổi có hiệu lực theo quy tắc token/cache đã đặc tả.
- Menu/guard/API matrix không lệch.
- 403 response không lộ mask hoặc role nội bộ không cần thiết.

Rollback:
- Sao lưu mapping trước thay đổi.
- Migration permission phải có script/ghi chú đảo ngược dữ liệu; không sửa dữ liệu trực tiếp ngoài migration được duyệt.

### P0-D — Tenant isolation evidence và remediation

#### D1. Inventory bắt buộc
Kiểm kê resource tenant-bound và mọi đường truy cập:
- Read-by-ID.
- Create/update/delete.
- List/search/filter/pagination/export.
- Service authorization.
- Derived/custom repository method.
- JPQL/native query.
- Entity relationship, lazy/eager traversal và serialization.
- Background job, scheduler, report và admin API.
- Tenant/property ID từ path, query, body, token/claim và membership.
- System Admin use case cần cross-tenant.

#### D2. Red tests bắt buộc
Ít nhất một resource đại diện và sau đó mở rộng đến toàn bộ resource Critical:
1. Tenant A không đọc resource Tenant B.
2. Tenant A không cập nhật resource Tenant B.
3. Tenant A không xóa resource Tenant B.
4. List/search/page/export của Tenant A không chứa Tenant B.
5. Thay `tenantId`/`hotelId` trong path, query hoặc body không vượt quyền.
6. User bị thu hồi membership bị từ chối.
7. Entity relationship/DTO không kéo dữ liệu Tenant B.
8. Native/JPQL/custom query giữ scope.
9. System Admin chỉ bypass khi endpoint/use case và permission cho phép.
10. User thường không thể kích hoạt bypass bằng role/parameter/claim tự cung cấp.

#### D3. Architecture decision sau evidence
Chọn một hoặc kết hợp:
- Centralized tenant filter.
- Repository-level scoped query.
- Service authorization.
- Database-level control nếu khả thi.

Decision record phải nêu:
- Endpoint/query bị hổng và test chứng minh.
- Coverage và blind spot của lựa chọn.
- Cách xác định tenant authoritative.
- Quy tắc resource không trực tiếp có tenant ID.
- Bypass System Admin explicit, auditable và deny-by-default.
- Cách tránh lọc hai lần: một lớp chịu trách nhiệm scope dữ liệu; service authorization chỉ kiểm tra hành động/resource khi cần.
- Tác động scheduler, public marketplace và migration.
- 403 hay 404 cho cross-tenant lookup, nhất quán với chống enumeration.

Rollback:
- Giữ scoped repository checks cũ cho đến khi centralized filter có coverage tương đương.
- Không xóa lớp phòng thủ cũ trong cùng lượt bật filter.
- Có kill switch cấu hình ngoài code cho filter nếu được phê duyệt; mặc định fail closed cho request tenant-bound.

### P0-E — Regression tests frontend/backend

- Backend security contract suite.
- Tenant isolation integration suite.
- Frontend guard/interceptor unit tests.
- E2E role/menu/API contract tests.
- Build backend/frontend độc lập và full smoke test.
- Không dùng production DB hoặc migration ghi dữ liệu; test dùng môi trường cô lập.
- Test không được skip. Evidence gồm command, exit code, report và timestamp.

Rollback: test-only change có thể revert độc lập; không hạ tiêu chí để làm build xanh.

### P0-F — Audit log cho thay đổi role/permission quan trọng

Audit event tối thiểu:
- Actor, target user/role, tenant/property scope, action, before/after đã giảm dữ liệu nhạy cảm, timestamp, correlation ID và outcome.
- Không ghi token, secret, password hoặc PII không cần thiết.
- Chỉ actor có permission audit mới đọc.
- Audit record chống sửa/xóa qua API thông thường.
- Ghi cả success và denied attempt theo retention policy được phê duyệt.

Test độc lập:
- Gán/gỡ role hoặc permission tạo đúng một audit event.
- Failed/denied mutation tạo outcome phù hợp.
- User không có quyền không đọc audit.
- Không có secret/token trong event.

Rollback: tắt consumer/sink bằng cấu hình nếu sink lỗi; không bỏ authorization hoặc làm mất transaction nghiệp vụ.

## 3. Dependency và gate

```text
P0-A ── độc lập, làm đầu tiên
P0-B ── baseline cho P0-C/P0-D
P0-C ── cần P0-B contract
P0-D ── cần P0-B, dùng mapping đã xác minh từ P0-C
P0-E ── gom regression sau B/C/D nhưng test cục bộ chạy trong từng nhóm
P0-F ── cần mapping P0-C, có thể triển khai độc lập sau contract
```

Không bắt đầu remediation P0-D trước khi D1 inventory và D2 red tests hoàn tất.

## 4. Acceptance evidence chung

Mỗi task phải lưu:
- File/class/function hoặc config key được kiểm tra.
- Lệnh test an toàn.
- Exit code và test report.
- Expected/actual result.
- Không chứa secret hoặc dữ liệu khách hàng.
- Rollback đã diễn tập hoặc được review.
- Blocker, owner và điều kiện gỡ blocker nếu phụ thuộc quyền ngoài repository.