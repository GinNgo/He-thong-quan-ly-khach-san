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

## 6. Completion contract

Feature chỉ được coi là hoàn thành khi:

1. 100% route/menu exposed có audit row.
2. Mọi P1 journey có outcome và evidence.
3. Mọi gap P1/P2 có disposition và next step.
4. Frontend test/build pass; backend regression pass hoặc có blocker được ghi rõ.
5. Browser regression sau sửa không tạo lỗi navigation, permission, responsive hoặc accessibility mới.
6. Spec Kit task/checklist/evidence đồng bộ với source hiện tại.
7. Báo cáo cuối tính tỷ lệ P1 scenarios hoàn thành, yêu cầu tối thiểu 90% hoặc ghi blocker cụ thể; regression smoke được đo và hoàn thành trong 45 phút.
