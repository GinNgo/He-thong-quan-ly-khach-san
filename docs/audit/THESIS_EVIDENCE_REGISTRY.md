# Evidence Registry khóa luận

Ngày khởi tạo: 2026-07-28
Ngày cập nhật: 2026-07-29

Registry này là danh sách đầu mối. Evidence chi tiết phải có claim, nguồn, ngày, freshness và privacy review.

| Evidence ID | Kind | Path/Command | Claim | CapturedAt | Freshness | Privacy | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| E-BASE-01 | SOURCE | docs/THESIS.md | Nội dung luận văn hiện tại có năm chương và audit note | 2026-07-28 | CURRENT | REVIEW | REVIEW |
| E-BASE-02 | SOURCE | docs/UML.md | Có 19 sơ đồ Use Case/Class/Sequence/Activity bám source | 2026-07-28 | CURRENT | REVIEW | VERIFIED |
| E-BASE-03 | SOURCE | docs/ERD.md | Có 4 ERD hiện hành và 1 ERD mục tiêu DEFERRED | 2026-07-28 | CURRENT | REVIEW | VERIFIED |
| E-BASE-04 | API | docs/API_SPEC.md | Có contract REST theo phân hệ | 2026-07-28 | CURRENT | REVIEW | REVIEW |
| E-BASE-05 | SUMMARY | docs/FEATURE_SUMMARY.md | Có tổng hợp capability, CURRENT/HISTORICAL và giới hạn; Admin 29 route được tách BLOCKED/PARTIAL | 2026-07-29 | CURRENT/MIXED | REVIEW | REVIEW |
| E-BASE-06 | TEST | docs/audit/BASELINE_TEST_REPORT.md | Có baseline test cũ và lỗi runner frontend | 2026-07-28 | HISTORICAL | REVIEW | HISTORICAL |
| E-BASE-07 | TEMPLATE | docs/thesis-assets/OFFICIAL_TEMPLATE_SOURCES.md | Có danh sách mẫu Drive cần export | 2026-07-28 | CURRENT | REVIEW | BLOCKED |
| E-BASE-08 | PARALLEL | backend/, frontend/ notification/security files | Backend/frontend unit verification đã có; authenticated E2E và release stability chưa chốt | 2026-07-28 | CURRENT/PARTIAL | REVIEW | REVIEW |
| E-ROUTE-01 | ROUTE | docs/audit/THESIS_ROUTE_EVIDENCE.md | Route/menu/guard hiện hành được lập bản đồ | 2026-07-28 | CURRENT | REVIEW | REVIEW |
| E-CODE-01 | SOURCE/API | docs/audit/THESIS_CODE_EVIDENCE.md | Controller và lớp evidence theo domain được lập bản đồ | 2026-07-28 | CURRENT | REVIEW | REVIEW |
| E-TEST-01 | TEST | docs/audit/THESIS_TEST_EVIDENCE.md | Test source và freshness rule được lập bản đồ | 2026-07-28 | CURRENT | REVIEW | REVIEW |
| E-TEST-02 | TEST | `cmd /c mvnw.cmd test`; backend/target/surefire-reports | 123 backend tests pass, gồm notification/security, fixture, location import và nghiệp vụ lõi | 2026-07-29 | CURRENT | REVIEW | VERIFIED |
| E-TEST-03 | TEST/BUILD | frontend/package.json; frontend/dist/frontend | 73 frontend unit tests pass trong 36 file và Angular production build pass | 2026-07-29 | CURRENT | REVIEW | VERIFIED |
| E-TEST-04 | E2E | frontend/playwright.config.ts; frontend/playwright-report; frontend/test-results | Playwright discovery 71 test/12 file; full run timeout; Admin core 1 fail/2 không chạy do login `admin/admin` | 2026-07-29 | BLOCKED | REVIEW | BLOCKED |
| E-DIAG-01 | DIAGRAM | docs/thesis-assets/diagrams; docs/screenshots/docx-panels; docs/audit/THESIS_DIAGRAM_QA.md | 24/24 Mermaid diagram render thành PNG bằng Chromium; DOCX không còn SVG media | 2026-07-29 | CURRENT | REVIEW | VERIFIED |
| E-RUBRIC-01 | RUBRIC | docs/thesis-assets/RUBRIC_MATRIX.md; docs/audit/RUBRIC_GAP_REPORT.md; docs/RUBRIC_RESPONSE_GUIDE.md | D03/D04 có 14/14 dòng mapping; phần trình bày/PDF và hỏi đáp còn NEEDS_EVIDENCE | 2026-07-29 | CURRENT/PARTIAL | REVIEW | REVIEW |
| E-FRONT-01 | FRONT_MATTER | docs/thesis-assets/front-matter | Lời cảm ơn, lời cam đoan và tóm tắt đã có bản nháp | 2026-07-28 | CURRENT | REVIEW | REVIEW |
| E-ADMIN-01 | ADMIN_AUDIT | docs/audit/ADMIN_FUNCTIONAL_VERIFICATION_PLAN.md | 29 route Admin đã được inventory; `/admin/plans` purchase PARTIAL và runtime data-backed còn BLOCKED | 2026-07-29 | CURRENT_STATIC/BLOCKED_RUNTIME | REVIEW | REVIEW |
| E-DOCX-01 | DOCX_QA | docs/audit/THESIS_DOCX_STRUCTURAL_QA.md; docs/export/LuxeStay_KhoaLuan_DRAFT_scrubbed.docx; docs/export/thesis-draft-a11y-20260729.json | DRAFT có 451 paragraph, 6 table, 47 inline image, 38 media PNG, không SVG; a11y 0 lỗi | 2026-07-29 | CURRENT | REVIEW | VERIFIED |
| E-DOCX-02 | FINAL_DOCX | docs/export/LuxeStay_KhoaLuan_FINAL.docx; docs/export/LuxeStay_HuongDan_TraLoi_Rubric_FINAL.docx | Hai FINAL DOCX pass package open, a11y và privacy; thesis có 459 paragraph, 49 hình/39 PNG/0 SVG, gồm architecture-01; FLOW-01..FLOW-04 ánh xạ UML-16..UML-19 | 2026-08-08 | CURRENT | PASS_DOCX/PDF_NOT_AVAILABLE | VERIFIED_STRUCTURAL |

## Quy tắc bổ sung

- CURRENT chỉ dùng khi source/test được kiểm tra trên worktree hiện hành.
- HISTORICAL phải giữ ngày và nguồn cũ.
- BLOCKED phải có nguyên nhân và bước mở khóa.
- Privacy REVIEW/FAIL ngăn đưa evidence vào bản nộp.
- Mỗi capability COMPLETE phải trỏ tới ít nhất một SOURCE/API và một TEST/SCREENSHOT/LOG hiện hành.
