# Baseline báo cáo khóa luận - 2026-07-28

## Phạm vi

Baseline này ghi nhận trạng thái tài liệu trước khi bắt đầu cập nhật nội dung thực tế theo Feature 04. Các thay đổi notification/security đang diễn ra trong worktree được xem là workstream song song; các claim liên quan chỉ được chốt sau khi code và test của workstream đó ổn định.

## Artifact đã có

- docs/THESIS.md: nội dung năm chương, có các audit note mới hơn ở cuối file.
- docs/THESIS_FORMAT_RULES.md: quy định A4, font, lề, heading, UML, caption và độ dài.
- docs/UML.md: nguồn Mermaid/PlantUML cho các phân hệ hiện có.
- docs/ERD.md: ERD và ghi chú migration.
- docs/API_SPEC.md: contract API theo phân hệ.
- docs/FEATURE_SUMMARY.md: tổng hợp capability và giới hạn.
- docs/audit/FEATURE_TRACEABILITY_MATRIX.md: ma trận truy vết hiện có.
- docs/audit/BASELINE_TEST_REPORT.md: kết quả kiểm thử lịch sử và các lỗi cấu hình frontend.

## Thứ tự đối chiếu

1. Source code và migration hiện hành.
2. Test vừa chạy trên worktree hiện hành.
3. FEATURE_SUMMARY.
4. API_SPEC, ERD, UML.
5. Audit/report lịch sử có ngày.
6. Context và roadmap.

## Mâu thuẫn đã biết

- FEATURE_SUMMARY có số liệu cũ: backend 60/60 và các mốc frontend/E2E lịch sử.
- BASELINE_TEST_REPORT ghi Vitest/Playwright chưa chạy được trên cấu hình cũ.
- THESIS.md có các kết luận 49/49, 86/86 và audit giao diện ngày 27/07/2026; cần gắn ngày/lệnh và xác minh lại trước bản nộp.
- Google Drive có rubric/mẫu nhưng chưa có bản export để chốt mã và trọng số.

## Quyết định baseline

- Không xóa report lịch sử.
- Không dùng số liệu cũ làm kết luận CURRENT.
- Không coi route/UI, roadmap hoặc mockup là evidence hoàn thành nếu thiếu API/service/data/test.
- Chưa chỉnh sửa THESIS/UML/ERD trong baseline này; các task US1/US2 sẽ thực hiện sau khi route/code/test evidence được lập.

## Next evidence

- THESIS_ROUTE_EVIDENCE.md
- THESIS_CODE_EVIDENCE.md
- THESIS_TEST_EVIDENCE.md
- THESIS_CHANGE_IMPACT_REGISTER.md
