---
description: "Danh sách công việc hoàn thiện và duy trì báo cáo khóa luận LuxeStay"
---

# Tasks: Báo cáo khóa luận có thể cập nhật

Input: .specify/Feature-04-Thesis-Report-Maintenance/

Prerequisites: plan.md, spec.md, research.md, data-model.md, contracts/report-maintenance-contract.md, quickstart.md

Organization: Task được nhóm theo năm user story để có thể xác minh độc lập. Feature này cập nhật tài liệu, pipeline DOCX và artifact audit/test; gap sản phẩm Admin được lập backlog riêng và không sửa code notification/security/Feature 03 song song.

## Phase 1: Setup

Purpose: Chuẩn bị nguồn mẫu, thư mục bằng chứng và manifest có thể kiểm tra.

- [x] T001 Tạo manifest nguồn mẫu chính thức và ngày phiên bản trong docs/thesis-assets/OFFICIAL_TEMPLATE_SOURCES.md
- [x] T002 Tạo thư mục và quy ước tên cho biểu mẫu, sơ đồ, screenshot và test evidence trong docs/thesis-assets/README.md
- [x] T003 [P] Export hoặc nhận các file Google Drive chính thức vào docs/thesis-assets/templates/ và ghi checksum/nguồn trong docs/thesis-assets/OFFICIAL_TEMPLATE_SOURCES.md (verified 2026-07-28; D01-D08 CURRENT)
- [x] T004 [P] Tạo baseline báo cáo ngày hiện hành trong docs/audit/THESIS_REPORT_BASELINE_2026-07-28.md
- [x] T005 [P] Tạo evidence registry ban đầu theo contract trong docs/audit/THESIS_EVIDENCE_REGISTRY.md

Checkpoint: Có nơi lưu mẫu và bằng chứng, đồng thời biết rõ file nào còn chờ người dùng cung cấp.

---

## Phase 2: Foundational

Purpose: Tạo nền tảng truy vết bắt buộc trước khi sửa nội dung luận văn.

- [x] T006 Chuẩn hóa cột capability ID, status, verifiedAt và reportSections trong docs/audit/FEATURE_TRACEABILITY_MATRIX.md
- [x] T007 [P] Lập bản đồ frontend route/menu/actor hiện hành trong docs/audit/THESIS_ROUTE_EVIDENCE.md
- [x] T008 [P] Lập bản đồ backend controller/service/entity/migration hiện hành trong docs/audit/THESIS_CODE_EVIDENCE.md
- [x] T009 [P] Lập bản đồ backend test, frontend unit và Playwright evidence trong docs/audit/THESIS_TEST_EVIDENCE.md
- [x] T010 Đối chiếu T007-T009 để gán COMPLETE, PARTIAL, MISSING, BLOCKED hoặc DEFERRED trong docs/audit/FEATURE_TRACEABILITY_MATRIX.md
- [x] T011 Tạo change-impact register và quy tắc cập nhật artifact trong docs/audit/THESIS_CHANGE_IMPACT_REGISTER.md

Checkpoint: Mọi claim dự kiến đưa vào báo cáo có capability record và bằng chứng hoặc trạng thái thiếu rõ ràng.

---

## Phase 3: User Story 1 - Đồng bộ báo cáo với hệ thống thực tế (Priority: P1) MVP

Goal: Cập nhật báo cáo và tài liệu kỹ thuật để chỉ mô tả chức năng có evidence hiện hành.

Independent Test: Chọn một capability vừa thay đổi và truy từ route/API/service/entity/test tới FEATURE_SUMMARY, THESIS và limitations mà không gặp claim mâu thuẫn.

- [x] T012 [P] [US1] Rà soát auth, JWT, RBAC/action mask và tenant access trong docs/audit/THESIS_CODE_EVIDENCE.md
- [x] T013 [P] [US1] Rà soát search/location/availability, property, RoomType và Room trong docs/audit/THESIS_CODE_EVIDENCE.md
- [x] T014 [P] [US1] Rà soát booking/payment/cancellation/refund/invoice và stay operations trong docs/audit/THESIS_CODE_EVIDENCE.md
- [x] T015 [P] [US1] Rà soát subscription/features, import/claim và support chat sau khi code song song ổn định trong docs/audit/THESIS_CODE_EVIDENCE.md
- [x] T016 [US1] Giải quyết hoặc ghi chú toàn bộ conflict hiện hành trong docs/audit/DOCUMENT_AND_CODE_CONFLICTS.md
- [x] T017 [US1] Cập nhật capability, giới hạn và nhãn CURRENT/HISTORICAL/BLOCKED trong docs/FEATURE_SUMMARY.md
- [x] T018 [US1] Cập nhật endpoint/permission/deferred domains theo source trong docs/API_SPEC.md
- [x] T019 [US1] Cập nhật phạm vi, mục tiêu và phương pháp truy vết tại Chương 1 trong docs/THESIS.md
- [x] T020 [US1] Cập nhật mô tả implementation và evidence tại Chương 4.1-4.7 trong docs/THESIS.md
- [x] T021 [US1] Chạy test khả dụng và cập nhật bảng CURRENT/HISTORICAL/BLOCKED tại Chương 4.8-4.10 trong docs/THESIS.md
- [x] T022 [US1] Đồng bộ kết luận, hạn chế và hướng phát triển với capability status tại Chương 5 trong docs/THESIS.md
- [x] T023 [US1] Xác minh không còn capability DEFERRED được mô tả là hoàn thành trong docs/THESIS.md và docs/FEATURE_SUMMARY.md

Checkpoint: Báo cáo có baseline chức năng trung thực, tự đứng độc lập dù các phần rubric và toàn văn chưa lắp ráp.

---

## Phase 4: User Story 2 - Duy trì bộ UML/ERD đúng cài đặt (Priority: P1)

Goal: Hoàn thiện Use Case, Class, Sequence, Activity và ERD với actor, contract, state và quan hệ đúng source.

Independent Test: Chọn một phân hệ và kiểm tra được actor/route, endpoint, service, class/entity, state, nhánh lỗi và phần giải thích sơ đồ.

- [x] T024 [P] [US2] Cập nhật Use Case tổng quát và theo public/customer, operations, owner/admin trong docs/UML.md
- [x] T025 [P] [US2] Cập nhật Class Diagram auth/RBAC, property/inventory, reservation/payment và stay operations trong docs/UML.md
- [x] T026 [P] [US2] Cập nhật Sequence Diagram auth, search/availability, booking/payment và cancellation/refund trong docs/UML.md
- [x] T027 [P] [US2] Cập nhật Sequence Diagram stay operations, import/claim và support chat khi applicable trong docs/UML.md
- [x] T028 [P] [US2] Cập nhật Activity Diagram booking, cancellation/refund, stay lifecycle và import/claim trong docs/UML.md
- [x] T029 [US2] Đối chiếu JPA entity/migration để cập nhật schema hiện hành và mô hình mục tiêu riêng biệt trong docs/ERD.md
- [x] T030 [US2] Bổ sung purpose, description, analysis, conclusion và caption cho mọi sơ đồ dùng trong docs/UML.md và docs/ERD.md
- [x] T031 [US2] Chèn hoặc cập nhật sơ đồ đã xác minh tại Chương 3 trong docs/THESIS.md
- [x] T032 [US2] Render Mermaid/PlantUML và ghi lỗi cú pháp/layout trong docs/audit/THESIS_DIAGRAM_QA.md
- [x] T033 [US2] Xác minh mọi hình có caption và được tham chiếu trong docs/THESIS.md

Checkpoint: Bộ sơ đồ có thể render và khớp với source cho các phân hệ cốt lõi.

---

## Phase 5: User Story 3 - Hoàn thiện toàn văn theo biểu mẫu (Priority: P2)

Goal: Lắp ráp đúng 13 nhóm biểu mẫu, nội dung năm chương, tài liệu tham khảo và phụ lục thành DOCX/PDF đạt định dạng.

Independent Test: Đọc manifest từ slot 1 tới 13, kiểm tra mỗi slot có trạng thái/nguồn/owner và bản DOCX/PDF không có lỗi layout.

- [x] T034 [P] [US3] Tạo manifest 13 slot với required/conditional/status/owner trong docs/thesis-assets/WHOLE_DOCUMENT_MANIFEST.md
- [x] T035 [P] [US3] Soạn hoặc chuẩn hóa Lời cảm ơn và Lời cam đoan trong docs/thesis-assets/front-matter/
- [x] T036 [P] [US3] Viết Tóm tắt theo mục tiêu-phương pháp-kết quả-giới hạn trong docs/thesis-assets/front-matter/TOM_TAT.md
- [x] T037 [P] [US3] Kiểm tra yêu cầu Abstract; D07 không yêu cầu Abstract tiếng Anh, nên ghi `NOT_APPLICABLE` trong WHOLE_DOCUMENT_MANIFEST thay vì tự tạo file (verified 2026-07-28)
- [x] T038 [US3] Chuẩn hóa heading, bảng, hình, caption và tham chiếu theo docs/THESIS_FORMAT_RULES.md trong docs/THESIS.md
- [x] T039 [P] [US3] Rà soát citation và chỉ giữ nguồn được trích trong phần TÀI LIỆU THAM KHẢO của docs/THESIS.md
- [x] T040 [P] [US3] Lập inventory screenshot theo route/role/capability/privacy trong docs/thesis-assets/SCREENSHOT_MANIFEST.md
- [x] T041 [US3] Chụp và chọn screenshot dữ liệu demo vào docs/screenshots/ theo docs/thesis-assets/SCREENSHOT_MANIFEST.md (CURRENT public desktop/mobile; blocked API states recorded 2026-07-28)
- [x] T042 [US3] Lắp lại các biểu mẫu và nội dung thành docs/export/LuxeStay_KhoaLuan_DRAFT.docx bằng PNG tương thích Word; structural package QA PASS 2026-07-29, visual Word/PDF gate còn ở T043/T044/T058
- [ ] T043 [US3] Render DOCX/PDF thành ảnh và ghi kết quả từng trang trong docs/audit/THESIS_RENDER_QA.md
- [ ] T044 [US3] Sửa layout, mục lục, số trang, hình/bảng bị cắt và phát hành docs/export/LuxeStay_KhoaLuan_REVIEW.docx

Checkpoint: Có bản REVIEW đúng thứ tự và hình thức; slot chưa có chữ ký/điểm được đánh dấu, không bị bỏ qua.

---

## Phase 6: User Story 4 - Trả lời rubric bằng bằng chứng (Priority: P2)

Goal: Mapping 100% tiêu chí rubric tới báo cáo, evidence, giới hạn và câu trả lời vấn đáp.

Independent Test: Chọn bất kỳ tiêu chí nào và tìm được câu trả lời 30-60 giây, section, source/test/screenshot và trạng thái sẵn sàng.

- [x] T045 [US4] Nhập bản export rubric môn học và khóa luận vào docs/thesis-assets/rubrics/ (D03/D04 checksum verified 2026-07-28)
- [x] T046 [US4] Trích nguyên văn STT, trọng số và yêu cầu vào docs/thesis-assets/RUBRIC_MATRIX.md; nguồn không có mã tiêu chí riêng nên dùng trace ID nội bộ (verified 2026-07-28)
- [x] T047 [US4] Mapping từng tiêu chí tới section/figure/table/evidence trong docs/thesis-assets/RUBRIC_MATRIX.md (14/14 dòng, verified 2026-07-28)
- [x] T048 [P] [US4] Viết câu trả lời 30-60 giây theo Claim-Evidence-Reason-Boundary-Value trong docs/RUBRIC_RESPONSE_GUIDE.md
- [x] T049 [P] [US4] Lập danh sách tiêu chí thiếu evidence và task xử lý trong docs/audit/RUBRIC_GAP_REPORT.md
- [x] T050 [US4] Xử lý gap và gắn READY/NEEDS_EVIDENCE/PARTIAL/BLOCKED/NOT_APPLICABLE trong docs/thesis-assets/RUBRIC_MATRIX.md (verified 2026-07-28)
- [x] T051 [US4] Xác minh 100% tiêu chí có mapping và câu trả lời trong docs/audit/RUBRIC_GAP_REPORT.md (14/14 source rows mapped; verified 2026-07-28)

Checkpoint: Bộ rubric sẵn sàng cho bảo vệ và không chứa claim vượt phạm vi hệ thống.

---

## Phase 7: Polish & Cross-Cutting Concerns

Purpose: Chốt tính nhất quán, privacy, test evidence và artifact nộp.

- [x] T052 Chạy kiểm tra chéo source-test-API-ERD-UML-THESIS và cập nhật docs/audit/DOCUMENT_AND_CODE_CONFLICTS.md
- [x] T053 [P] Chạy backend Maven test và ghi ngày/lệnh/kết quả trong docs/audit/THESIS_TEST_EVIDENCE.md
- [x] T054 [P] Chạy frontend unit/build và ghi pass/fail/blocked trong docs/audit/THESIS_TEST_EVIDENCE.md
- [x] T055 [P] Chạy Playwright E2E khả dụng và ghi pass/fail/blocked trong docs/audit/THESIS_TEST_EVIDENCE.md (targeted smoke 2 passed/3 skipped; full 71-test suite remains BLOCKED, verified 2026-07-28)
- [x] T056 Quét secret, PII và đường dẫn cục bộ trong docs/, screenshot và artifact phát hành; hai FINAL DOCX PASS 2026-07-29, PDF chưa tồn tại do T058 render blocked và phải quét bổ sung khi được tạo
- [x] T057 Chạy quickstart.md và release contract; ghi manifest phiên bản trong docs/export/RELEASE_MANIFEST.md
- [ ] T058 Render và kiểm tra từng trang bản cuối, sau đó phát hành docs/export/LuxeStay_KhoaLuan_FINAL.docx và PDF tương ứng

---

## Phase 8: Corrective Work - Word diagrams, full capability coverage and Admin verification

Purpose: Xử lý bằng chứng trực quan mới từ Microsoft Word, bao phủ mọi chức năng/sơ đồ và kiểm tra toàn bộ khu vực Admin trước khi kết luận báo cáo đúng thực tế.

- [x] T059 [P] [US3] Phân loại yêu cầu báo cáo theo `REQUIRED_BY_D01_D08`, `REQUIRED_FOR_TRUTHFUL_EVIDENCE`, `OPTIONAL_SKIP` và ghi quyết định trong docs/audit/THESIS_SCOPE_DECISIONS.md
- [x] T060 [P] [US5] Inventory 100% route/menu Admin và tạo record ban đầu trong docs/audit/ADMIN_FUNCTIONAL_VERIFICATION_PLAN.md
- [x] T061 [US5] Ánh xạ từng route Admin tới role, permission guard/backend authorization, component, API data/mutation và thao tác nghiệp vụ trong docs/audit/ADMIN_FUNCTIONAL_VERIFICATION_PLAN.md
- [x] T062 [P] [US5] Audit chất lượng unit/E2E hiện hữu; đánh dấu test chỉ kiểm tra shell/body-visible và lập danh sách test data-backed còn thiếu trong docs/audit/ADMIN_FUNCTIONAL_VERIFICATION_PLAN.md
- [x] T063 [US5] Viết runbook môi trường E2E LuxeStay cô lập gồm cổng backend riêng, SQL Server, profile e2e, fixture và `LUXESTAY_E2E_*` trong docs/audit/ADMIN_FUNCTIONAL_VERIFICATION_PLAN.md
- [x] T064 [P] [US5] Chạy lại frontend build/unit hiện hành và cập nhật kết quả CURRENT trong docs/audit/THESIS_TEST_EVIDENCE.md (73/73, build PASS, 2026-07-29)
- [x] T065 [P] [US5] Chạy lại backend Maven tests và cập nhật kết quả CURRENT trong docs/audit/THESIS_TEST_EVIDENCE.md (123/123, 2026-07-29)
- [ ] T066 [US5] Chạy E2E data-backed cho thao tác đọc/load/empty/error trên 100% route Admin; ghi PASS/PARTIAL/FAIL/BLOCKED theo từng route
- [ ] T067 [US5] Chạy E2E mutation/action và authorization cho từng chức năng Admin applicable; không dùng `body visible` làm bằng chứng duy nhất
- [x] T068 [US5] Tạo completion backlog cho mọi Admin gap với mức ưu tiên, file/phân hệ, dependency, acceptance criteria và unit/integration/E2E cần bổ sung
- [x] T069 [P] [US2] Tạo docs/audit/THESIS_DIAGRAM_COVERAGE.md ánh xạ mọi capability đã triển khai tới Use Case/Class/Sequence/Activity/ERD hoặc lý do NOT_APPLICABLE
- [x] T070 [US2] Tạo docs/tools/render_diagrams_png.js để raster SVG bằng Chromium thành PNG độ phân giải cao, giữ đầy đủ nhãn `foreignObject` (24/24 PNG, 2026-07-29)
- [x] T071 [US2] Tách asset sơ đồ/ảnh quá cao thành panel `(a)/(b)` có overlap, tên/caption duy nhất và cập nhật coverage; source domain vẫn giữ nguyên để diff
- [x] T072 [US3] Sửa docs/tools/build_thesis_docx.py để nhúng PNG trực tiếp, bỏ patch SVG-only, gắn caption/alt text/title và không dùng placeholder 32x32
- [x] T073 [US3] Dựng lại DRAFT và audit package DOCX: 24 appendix diagrams + chapter placements, media PNG, đủ caption/alt text/reference, không có relationship sơ đồ trỏ SVG-only
- [ ] T074 [US3] Mở/kiểm tra bản REVIEW trong Microsoft Word, render DOCX/PDF thành ảnh và xem 100% trang; cập nhật docs/audit/THESIS_RENDER_QA.md
- [x] T075 [US1] Đồng bộ kết quả Admin/diagram/scope vào docs/THESIS.md, FEATURE_SUMMARY, evidence registry và release manifest; user-approved FINAL DOCX fallback phát hành 2026-07-29 với render/PDF/Admin blockers được ghi rõ (remaining gates T043/T044/T058/T066/T067/T074)

---

## Dependencies & Execution Order

### Phase dependencies

- Phase 1 không phụ thuộc phase khác.
- Phase 2 phụ thuộc Phase 1 và chặn mọi user story.
- US1 và US2 có thể bắt đầu sau Phase 2; US2 dùng capability baseline của US1 khi cần.
- US3 có thể chuẩn bị front matter song song, nhưng lắp bản REVIEW phụ thuộc US1 và US2.
- US4 cần rubric export cho T045-T047; các mẫu câu hỏi chung có thể chuẩn bị trước.
- Phase 7 phụ thuộc các user story được chọn cho bản release.
- Phase 8 là corrective gate bắt buộc trước T044/T058; T060-T065 và T069-T070 có thể chạy song song, T066-T067 phụ thuộc T063 và môi trường E2E khả dụng, T073 phụ thuộc T070-T072, T074 phụ thuộc T073.

### User story dependencies

- US1 là MVP và cần hoàn thành trước khi báo cáo có thể được gọi là đúng thực tế.
- US2 độc lập về source diagram nhưng T031-T033 cần baseline US1.
- US3 phụ thuộc nội dung và sơ đồ đã xác minh.
- US4 phụ thuộc file rubric chính thức và evidence từ US1-US3.

### Parallel opportunities

- T003-T005 có thể thực hiện song song.
- T007-T009 có thể thực hiện song song.
- T012-T015 có thể audit song song vì khác phân hệ.
- T024-T028 có thể cập nhật từng nhóm diagram song song nhưng phải phối hợp khi cùng sửa docs/UML.md.
- T034-T037 và T039-T040 có thể chuẩn bị song song.
- T048-T049 có thể chạy song song sau khi có matrix.
- T053-T055 có thể chạy song song nếu môi trường không tranh chấp port/database.
- T059-T060-T062-T064-T065-T069 có thể chuẩn bị song song vì sửa các artifact khác nhau.

## Parallel Examples

US1:

- Audit auth/RBAC/multi-property vào THESIS_CODE_EVIDENCE.
- Audit booking/payment/stay operations vào cùng schema nhưng chia section rõ ràng.
- Audit tests vào THESIS_TEST_EVIDENCE.

US2:

- Một luồng cập nhật Use Case/Class.
- Một luồng cập nhật Sequence/Activity.
- Một luồng đối chiếu ERD với entity/migration.

## Implementation Strategy

### MVP first

1. Hoàn thành Phase 1-2.
2. Hoàn thành US1 từ T012 đến T023.
3. Dừng và kiểm tra một capability end-to-end.
4. Chỉ khi baseline đúng mới đưa thêm sơ đồ, hình thức và rubric.

### Incremental delivery

1. Baseline đúng thực tế.
2. UML/ERD đúng source.
3. Bản REVIEW đúng 13 slot.
4. Rubric mapping đầy đủ.
5. Bản FINAL qua test/privacy/render QA.

## Notes

- Không sửa hoặc revert code notification/security của công việc song song trong feature này.
- Kết quả test cũ chỉ được ghi HISTORICAL.
- D03/D04 đã được mapping 14/14; không quay lại trạng thái TBD trừ khi trường phát hành phiên bản rubric mới.
- Mọi task hoàn thành phải cập nhật verifiedAt và evidence tương ứng.
- T042 được mở lại vì structural QA không phát hiện lỗi tương thích SVG của Microsoft Word; từ nay visual Word gate là bắt buộc.
