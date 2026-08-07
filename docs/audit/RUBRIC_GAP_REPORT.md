# Rubric gap report - 2026-07-29

## Kết luận hiện tại

Đã nhận và checksum hai bản export D03/D04 ngày 28/07/2026. Coverage nguồn đạt 14/14 dòng (7 tiêu chí D03 + 7 tiêu chí D04) trong `docs/thesis-assets/RUBRIC_MATRIX.md`. Các gap còn lại là gap evidence của bản nộp/bảo vệ, không còn là gap thiếu rubric.

## Gap register

| Gap ID | Gap | Tác động rubric | Status | Bước mở khóa |
| --- | --- | --- | --- | --- |
| RG-01 | Export rubric môn học D03 chưa có | Không biết mã/trọng số/yêu cầu nguyên văn | RESOLVED | Đã lưu D03 và ghi checksum |
| RG-02 | Export rubric khóa luận D04 chưa có | Không thể mapping 100% tiêu chí bảo vệ | RESOLVED | Đã lưu D04 và ghi checksum |
| RG-03 | Playwright full run timeout, có artifact redirect/search; Admin core data-backed 1 fail/2 không chạy | Tiêu chí E2E/UX/Admin chưa READY | BLOCKED | Cô lập backend LuxeStay, cấu hình `LUXESTAY_E2E_*`, ổn định fixture/redirect/Home Search và chạy lại read/mutation/authorization |
| RG-04 | Property claim dùng requester/reviewer ID cố định | Tiêu chí security/audit/ownership không thể đạt mức hoàn chỉnh | BLOCKED | Lấy identity từ principal, bỏ ID cố định, thêm integration test |
| RG-05 | Subscription lifecycle REST chưa đầy đủ | Tiêu chí completeness chỉ được trả lời PARTIAL | NEEDS_EVIDENCE | Spec/implement các mutation và history nếu rubric bắt buộc |
| RG-06 | Screenshot chưa phủ đầy đủ route/role/capability | Tiêu chí UI/demo thiếu evidence trực quan | NEEDS_EVIDENCE | Chụp theo SCREENSHOT_MANIFEST và privacy review |
| RG-07 | Chưa có DOCX/PDF bản REVIEW/FINAL theo mẫu chính thức | Tiêu chí trình bày/nộp chưa READY | BLOCKED | Assemble 13 slot, render từng trang và kiểm tra mục lục |
| RG-08 | Chưa chạy privacy scan cho artifact phát hành | Rủi ro lộ PII/path/secret | NEEDS_EVIDENCE | Chạy T056 trước REVIEW/FINAL |

## Evidence đã sẵn sàng

- Báo cáo 5 chương có phạm vi, giới hạn và test freshness.
- Capability traceability, route/code/test evidence và conflict report.
- 19 UML + 5 ERD render thành công; có caption và QA layout.
- Backend 123/123 CURRENT, frontend unit 73/73 trong 36 file CURRENT, production build CURRENT.
- Hướng dẫn câu trả lời theo Claim-Evidence-Reason-Boundary-Value.

## Gap evidence sau khi đã có rubric

| Gap ID | Dòng rubric liên quan | Evidence còn thiếu | Readiness hiện tại |
| --- | --- | --- | --- |
| RE-01 | D03-C5, D04-C6 | DOCX/PDF render từng trang, mục lục và số trang | NEEDS_EVIDENCE |
| RE-02 | D03-C6, D04-C3 | File slide và phiên trình bày/bảo vệ | NEEDS_EVIDENCE |
| RE-03 | D03-C7, D04-C7 | Biên bản hỏi đáp hoặc đánh giá của hội đồng | NEEDS_EVIDENCE |
| RE-04 | D03-C4, D04-C2 | Playwright authenticated/current và sửa identity claim | PARTIAL/BLOCKED |
| RE-05 | D04-C4 | Bằng chứng hỏi đáp về tài liệu tiếng Anh | NEEDS_EVIDENCE |

Coverage được xem là đủ về mặt mapping khi mọi dòng chính thức có mã truy vết, trọng số, yêu cầu nguyên văn, câu trả lời, vị trí báo cáo, evidence, boundary và readiness. Coverage không đồng nghĩa mọi dòng đã READY; các dòng thiếu evidence phải giữ `NEEDS_EVIDENCE/BLOCKED`.
