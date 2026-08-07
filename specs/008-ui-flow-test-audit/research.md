# Research: UI Flow Test Audit

## Decision 1 - Giữ feature test tách khỏi plan đang chạy

**Decision**: Dùng `specs/008-ui-flow-test-audit` làm feature directory riêng, chạy setup plan với `SPECIFY_FEATURE_DIRECTORY` chỉ trong phiên tạo artifact, sau đó phục hồi `.specify/feature.json` về `specs/007-payment-billing-completion`.

**Rationale**: Người dùng yêu cầu plan test không liên quan plan đang chạy. Cách này vẫn tuân theo Spec Kit nhưng không thay đổi feature mặc định của các lệnh tiếp theo.

**Alternatives considered**:

- Ghi thêm test vào feature 007: bị loại vì trộn phạm vi payment/billing với audit toàn UI.
- Tiếp tục dùng `.specify/Feature-03-UI-Functional-Audit-Polish`: bị loại vì feature đó đã trộn audit, implementation và polish.
- Tạo branch mới: chưa cần vì yêu cầu hiện tại là artifact plan, không phải triển khai hoặc commit độc lập.

## Decision 2 - Router cộng runtime menu là nguồn inventory

**Decision**: Lấy Angular router làm danh sách route chuẩn và runtime menu theo tài khoản làm danh sách capability được công bố; menu tĩnh ở client/management layout là nguồn bổ sung.

**Rationale**: Chỉ đọc router sẽ bỏ sót menu động theo quyền; chỉ click menu sẽ bỏ sót route có thể truy cập trực tiếp hoặc route không còn lối vào.

**Alternatives considered**:

- Chỉ dùng tài liệu/README: dễ lỗi thời.
- Chỉ dùng danh sách test Playwright: hiện có route cũ như `/client/profile` và coverage không đồng đều.

## Decision 3 - Real integration quyết định trạng thái COMPLETE

**Decision**: Unit/component test và request interception được dùng để kiểm tra nhánh logic, nhưng `COMPLETE` chỉ được gắn khi UI thực hiện với backend và database/fixture integration thật.

**Rationale**: Constitution yêu cầu trải nghiệm thực tế và không dùng mock/dummy để chứng minh hoàn thiện.

**Alternatives considered**:

- Dùng route interception cho toàn bộ E2E: nhanh, ổn định nhưng có thể che lỗi contract, permission, tenant và persistence.
- Chỉ manual test: khó lặp lại và khó chống hồi quy.

## Decision 4 - Status taxonomy phân biệt bề mặt và năng lực

**Decision**: Dùng `COMPLETE`, `PARTIAL`, `DISPLAYED_ONLY`, `BROKEN`, `BLOCKED`, `MISSING`, `DORMANT` cho capability; dùng `PASS`, `FAIL`, `BLOCKED`, `NOT_RUN` cho execution result.

**Rationale**: Một nút Coming Soon không phải lỗi runtime giống một nút có vẻ hoạt động nhưng không có handler. Tách hai loại trạng thái giúp backlog đúng ưu tiên.

**Alternatives considered**:

- Chỉ PASS/FAIL: không mô tả được chức năng có UI nhưng chưa có backend hoặc bị blocker môi trường.
- Dùng trạng thái Feature-03 cũ: thiếu `DISPLAYED_ONLY` và `DORMANT`, hai trạng thái quan trọng cho yêu cầu mới.

## Decision 5 - Ưu tiên theo hành trình và rủi ro

**Decision**: P1 gồm auth/RBAC, search-detail-booking-payment, reservation/check-in/checkout/invoice, property context/tenant isolation, role-permission và support/payment lifecycle. P2 gồm CRUD quản trị, export/import, billing, responsive/accessibility rộng. P3 gồm nội dung phụ, Coming Soon và visual consistency không chặn nghiệp vụ.

**Rationale**: Audit mọi control có thể rất lớn; ưu tiên theo tác động giúp smoke dưới 45 phút và vẫn duy trì inventory 100%.

**Alternatives considered**:

- Test theo thứ tự route: đơn giản nhưng bỏ qua dependency và rủi ro nghiệp vụ.
- Chạy toàn bộ suite cho mọi thay đổi: chậm, dễ flaky và không phù hợp vòng lặp ngắn.

## Decision 6 - Fixture theo vai trò và trạng thái nghiệp vụ

**Decision**: Chuẩn hóa account/data matrix gồm guest, customer active, customer có booking/invoice, admin đủ quyền, admin thiếu quyền, owner nhiều property, owner expired subscription và property không thuộc phạm vi.

**Rationale**: Luồng RBAC, subscription và tenant không thể được xác minh chỉ bằng một tài khoản admin toàn quyền.

**Alternatives considered**:

- Dùng một super-admin cho mọi test: che lỗi permission và tenant.
- Tạo dữ liệu ngẫu nhiên hoàn toàn: tăng flaky và khó tái hiện.

## Decision 7 - Evidence theo failure-first

**Decision**: PASS lưu kết quả gọn; failure P1/P2 bắt buộc có trace/screenshot, expected/actual, request/status liên quan và bước tái hiện. Evidence không chứa token, password hoặc PII nhạy cảm.

**Rationale**: Lưu mọi ảnh cho mọi bước làm artifact nặng; chỉ lưu evidence chi tiết khi cần điều tra vẫn đảm bảo truy xuất.

**Alternatives considered**:

- Screenshot mọi bước: tốn dung lượng và khó review.
- Chỉ lưu terminal output: không đủ cho lỗi focus, responsive, modal và trạng thái UI.

## Decision 8 - Test hiện có phải được audit trước khi tái sử dụng

**Decision**: Mỗi file Playwright được phân loại `REAL_INTEGRATION`, `INTERCEPTED`, `SMOKE_ONLY`, `STALE` hoặc `DUPLICATE`. Route literal và assertion được đối chiếu với router/runtime trước khi tính coverage.

**Rationale**: Số lượng test không đồng nghĩa coverage đúng; baseline đã thấy `customer-flows.spec.ts` dùng `/client/profile`, không còn trong router hiện tại.

**Alternatives considered**:

- Tính mọi test hiện có là coverage: tạo false confidence.
- Xóa suite cũ ngay: ngoài phạm vi plan test-only và có thể mất intent hữu ích.
