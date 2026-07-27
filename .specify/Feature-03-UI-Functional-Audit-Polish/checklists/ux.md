# UX Requirements Quality Checklist: Full UI Functional Audit & Premium Polish

**Purpose**: Xác nhận yêu cầu UX đủ cụ thể, nhất quán và có thể kiểm chứng trước khi audit/implementation. Checklist này đánh giá chất lượng requirement, không đánh giá code đã hoàn thành.
**Created**: 2026-07-27
**Feature**: [spec.md](../spec.md)

## Visual Direction and Consistency

- [x] CHK001 Yêu cầu xác định rõ visual language cần giữ: LuxeStay primary blue, navy surfaces và gold có chủ đích.
- [x] CHK002 Yêu cầu cấm hardcode component colors và bắt buộc dùng design token hiện có.
- [x] CHK003 Khái niệm “premium” được chuyển thành tiêu chí có thể review: hierarchy, spacing, typography, elevation, state clarity và consistency.
- [x] CHK004 Phạm vi phân biệt public/customer/admin/management nhưng vẫn yêu cầu ngôn ngữ thiết kế thống nhất.

## Interaction and State Completeness

- [x] CHK005 Yêu cầu nêu đầy đủ loading, empty, error/recovery, success và disabled/submitting states cho async UI được sửa.
- [x] CHK006 Yêu cầu duplicate-submit protection được nêu cho booking/payment/mutation quan trọng.
- [x] CHK007 Yêu cầu primary, alternate, error, recovery và permission scenarios được nêu cho P1 journey.
- [x] CHK008 Trạng thái audit có định nghĩa rõ để tránh nhầm `PARTIAL`, `BLOCKED` và `BROKEN`.

## Navigation and Permissions

- [x] CHK009 Yêu cầu current location, recovery path, expired-session và permission-denied behavior có thể kiểm tra.
- [x] CHK010 Requirement không coi ẩn menu phía frontend là authorization boundary.
- [x] CHK011 Property context và cross-property isolation được nêu trong acceptance scenarios.
- [x] CHK012 Route alias/shared-component behavior được đưa vào edge cases.

## Responsive and Accessibility

- [x] CHK013 Breakpoint acceptance được định lượng tại 375, 768, 1024 và 1440px.
- [x] CHK014 Touch target tối thiểu 44px, visible focus, semantic controls và keyboard trap được nêu rõ.
- [x] CHK015 Horizontal overflow có tiêu chí rõ và ngoại lệ table có chủ đích.
- [x] CHK016 Reduced-motion behavior được nêu thành acceptance scenario.
- [x] CHK017 Requirement hướng tới WCAG AA+ nhưng không tuyên bố compliance nếu chưa có evidence.

## Audit Evidence and Traceability

- [x] CHK018 100% exposed route/menu là tiêu chí coverage định lượng.
- [x] CHK019 Mỗi audit item yêu cầu actor, route, action, dependency, permission, status và evidence.
- [x] CHK020 Gap P1/P2 yêu cầu reproduction, expected/actual, severity và next step.
- [x] CHK021 Mock/fake evidence bị loại khỏi tiêu chí hoàn thiện.
- [x] CHK022 Feature cần backend/domain lớn được yêu cầu defer rõ thay vì mô phỏng UI.

## Testability and Completion

- [x] CHK023 Success criteria có outcome đo được cho coverage, P1 journey, responsive, accessibility và regression.
- [x] CHK024 Điều kiện `BLOCKED` yêu cầu prerequisite cụ thể, tránh silent skip.
- [x] CHK025 Completion yêu cầu cả automated verification và browser regression sau sửa.
- [x] CHK026 Requirement phân biệt audit artifact, implementation task và verification evidence.

## Notes

- Checklist requirement quality hiện đạt yêu cầu để sinh task và bắt đầu runtime audit.
- Việc checkbox đã hoàn thành không có nghĩa UI implementation đã pass; kết quả thực thi nằm ở `audit-matrix.md`, `gap-register.md` và `tasks.md`.
