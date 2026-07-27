# Contract: UI Audit Evidence and Quality

## 1. Audit row contract

Mỗi route/menu được công bố phải có đúng một row trong `audit-matrix.md` với các cột:

```text
ID | Story/Requirement | Area | Actor | Route/Menu | Component | Primary action | API/Data |
Permission/Scope | Scenarios | Status | Evidence | Gap/Next step
```

Rules:

1. `Status` chỉ dùng `COMPLETE`, `PARTIAL`, `MISSING`, `BLOCKED`, `BROKEN`.
2. `COMPLETE` phải có browser evidence và không còn gap ngăn acceptance criteria.
3. `BLOCKED` phải nêu prerequisite cụ thể để mở khóa.
4. Redirect/alias có thể chung một row với route canonical nhưng phải liệt kê đầy đủ.
5. Route dùng chung component với route-data khác phải được kiểm tra như behavior riêng.
6. Mỗi row phải liên kết ít nhất một user story và các `FR-/SC-` trực tiếp để truy ngược từ requirement tới route, evidence và source change.

## 2. Scenario contract

Mỗi hành trình P1 phải bao phủ:

- Primary success path.
- Ít nhất một validation/error path.
- Recovery hoặc retry path khi async operation có thể thất bại.
- Permission/ownership denial khi route hoặc resource bị giới hạn.
- Duplicate-submit protection cho create/payment/status mutation.

## 3. Browser evidence contract

Evidence tối thiểu gồm:

- URL/route và actor.
- Viewport khi liên quan.
- Preconditions không chứa password/token.
- Các bước tái hiện ngắn.
- Expected và actual result.
- Screenshot hoặc ghi chú console/network cho failure visual/integration.
- Thời gian interaction-to-feedback cho async/mutation được sửa; mục tiêu không quá 300ms trước khi loading/progress state xuất hiện.
- Thời điểm kiểm thử.

E2E dùng mock hoặc source review không được gán `COMPLETE` thay cho browser integration thật.

Quy tắc định danh evidence:

1. Mỗi record dùng một ID duy nhất dạng `EVD-###` và chỉ được định nghĩa một lần trong evidence catalog.
2. ID tăng tuần tự, không tái sử dụng ID đã có; mọi tham chiếu phải trỏ tới một record tồn tại.
3. Khi một audit row hoặc gap dùng nhiều evidence, liệt kê ID bằng dấu phẩy, ví dụ `EVD-012, EVD-013`; không dùng dấu gạch chéo hoặc mô tả khoảng mơ hồ.
4. Evidence trước và sau sửa được giữ riêng để không làm mất lịch sử lỗi; record sau sửa phải nêu rõ phạm vi đã PASS và phần còn bị chặn.

## 4. Premium UI contract

Mọi surface được sửa phải thỏa:

- Dùng semantic CSS variables hiện có; không hardcode component color.
- Primary action dùng primary blue; gold chỉ dùng premium/status có chủ đích.
- Trang có hierarchy rõ: page title, context, primary action, content state.
- Async content có loading, empty, error/recovery; mutation có submitting/disabled và success/failure feedback.
- Focus nhìn thấy; semantic label/aria phù hợp; touch target tối thiểu 44px.
- Không overflow ngoài vùng table có chủ đích tại 375/768/1024/1440.
- Motion không cản thao tác và tắt/giảm khi `prefers-reduced-motion`.

## 5. Gap contract

Gap P1/P2 phải có:

```text
Gap ID | Severity | Category | Actor/Route | Preconditions | Reproduction |
Expected | Actual | Evidence | Disposition | Next step | Verification
```

Gap cần backend/domain lớn phải dùng disposition `DEFER_FEATURE`; không dựng fake UI để đóng gap.

`State` của gap là vòng đời xác minh, tách biệt với audit status và disposition, và chỉ dùng một trong bốn giá trị:

- `REVALIDATE`: giả thuyết lịch sử/source chưa đủ bằng chứng runtime hiện tại.
- `CONFIRMED_PARTIAL`: đã có bằng chứng hiện tại; một phần hành vi đúng nhưng acceptance hoặc nhánh quan trọng vẫn thiếu/bị chặn.
- `CONFIRMED_BROKEN`: đã có bằng chứng hiện tại rằng hành vi được công bố thất bại, không nối hoặc không thể bắt đầu.
- `FIXED`: phạm vi lỗi đã được sửa và có source/test/browser evidence sau sửa; blocker còn lại phải được ghi riêng, không ẩn trong trạng thái này.

Không dùng trạng thái trung gian như `SOURCE_OBSERVED`; source-only hypothesis thuộc `REVALIDATE`, còn lỗi đã có runtime evidence thuộc `CONFIRMED_BROKEN` hoặc `CONFIRMED_PARTIAL`.

## 6. Completion contract

Feature chỉ được coi là hoàn thành khi:

1. 100% route/menu exposed có audit row.
2. Mọi P1 journey có outcome và evidence.
3. Mọi gap P1/P2 có disposition và next step.
4. Frontend test/build pass; backend regression pass hoặc có blocker được ghi rõ.
5. Browser regression sau sửa không tạo lỗi navigation, permission, responsive hoặc accessibility mới.
6. Spec Kit task/checklist/evidence đồng bộ với source hiện tại.
7. Báo cáo cuối tính tỷ lệ P1 scenarios hoàn thành, yêu cầu tối thiểu 90% hoặc ghi blocker cụ thể; regression smoke được đo và hoàn thành trong 45 phút.
