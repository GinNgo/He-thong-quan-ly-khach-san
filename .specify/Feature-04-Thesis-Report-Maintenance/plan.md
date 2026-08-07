# Implementation Plan: Duy trì báo cáo khóa luận theo chức năng thực tế

Branch: codex/thesis-report-maintenance
Date: 2026-07-29
Spec: .specify/Feature-04-Thesis-Report-Maintenance/spec.md

## Summary

Feature này xây dựng quy trình và bộ tài liệu trung gian để hoàn thiện docs/THESIS.md thành toàn văn khóa luận có thể kiểm chứng và cập nhật được. Báo cáo không được xem roadmap, mockup hoặc API rời rạc là bằng chứng hoàn thành; mọi khẳng định phải truy ngược được về mã nguồn, migration, kiểm thử hiện hành hoặc ảnh chụp được xác định ngày.

Phạm vi gồm:

- Ma trận truy vết capability -> actor -> route/UI -> API -> service -> dữ liệu -> test -> phần báo cáo.
- Quy ước trạng thái COMPLETE, PARTIAL, MISSING, BLOCKED, DEFERRED.
- Bộ UML/ERD có nguồn Mermaid/PlantUML, caption, mục đích, mô tả, phân tích và kết luận.
- Ma trận coverage nối mọi capability đã triển khai với Use Case, Class, Sequence, Activity và ERD hoặc lý do không áp dụng.
- Lắp ráp 13 nhóm biểu mẫu theo đúng thứ tự người dùng cung cấp.
- Ma trận trả lời rubric theo D03/D04 chính thức, hiện đã mapping 14/14 tiêu chí.
- Pipeline xuất DOCX nhúng PNG độ phân giải cao để tương thích Microsoft Word; SVG chỉ là source ngoài DOCX.
- Ma trận xác minh 100% route/chức năng Admin và backlog hoàn thiện cho mọi gap.
- Scope gate phân loại yêu cầu theo D01-D08, bằng chứng trung thực hoặc tùy chọn có thể bỏ.
- Quy trình xuất DOCX/PDF, kiểm tra riêng nội dung, cấu trúc, accessibility và kiểm tra trực quan từng trang trước khi nộp.

Feature thay đổi tài liệu, pipeline tạo DOCX và artifact kiểm thử/audit. Các lỗi sản phẩm Admin được ghi thành backlog chi tiết; không sửa code sản phẩm trong feature này và không chạm các file notification/security hoặc Feature 03 đang được thực hiện song song.

## Technical Context

Language/Version: Markdown UTF-8; Python 3 cho lắp ráp DOCX; Mermaid/PlantUML/SVG làm source sơ đồ; Java 21/Spring Boot 3 backend; Angular 22+ frontend.

Primary Dependencies: D01-D08 trong docs/thesis-assets/templates/, docs/THESIS.md, docs/UML.md, docs/ERD.md, docs/API_SPEC.md, docs/FEATURE_SUMMARY.md, python-docx/Pillow, Chromium/Playwright để raster SVG, backend JUnit/Spring tests, frontend Vitest/Playwright, Microsoft Word và DOCX/PDF renderer.

Storage: SQL Server 2022 và JPA/Hibernate là nguồn dữ liệu của hệ thống; tài liệu nguồn được lưu trong Git tại docs/ và .specify/.

Testing: Maven tests; Angular build/unit; Playwright E2E trên môi trường LuxeStay cô lập; kiểm tra tĩnh route/component/API/permission; audit ảnh/caption/alt text trong DOCX; render DOCX/PDF và kiểm tra PNG từng trang.

Target Platform: Ứng dụng web LuxeStay chạy trên trình duyệt, backend Spring Boot và SQL Server; đầu ra báo cáo là DOCX/PDF A4.

Project Type: Web application đa tenant kèm bộ tài liệu kỹ thuật/học thuật có thể tái sinh.

Performance Goals: Một thay đổi chức năng điển hình được truy vết và lập danh sách artifact bị ảnh hưởng trong tối đa 60 phút, chưa tính test dài; không đặt mục tiêu hiệu năng runtime mới.

Constraints:

- Báo cáo phải dùng tiếng Việt, A4, lề trái 3 cm và các lề còn lại 2 cm, Times New Roman 13 pt, giãn dòng 1,5, canh đều, thụt đầu dòng 1 cm.
- Chỉ tuyên bố COMPLETE khi có bằng chứng hiện hành. Kết quả cũ phải gắn nhãn lịch sử.
- Không đưa secret, credential, dữ liệu cá nhân không cần thiết hoặc đường dẫn máy cục bộ vào bản nộp.
- Mixed RoomType booking, customer add-on services, customer review, favorites và các báo cáo tài chính nâng cao phải được trình bày là giới hạn/roadmap nếu chưa có contract đầy đủ.
- PNG là media chính trong DOCX; không patch quan hệ media sang SVG-only. SVG được lưu để diff và tái sinh.
- Sơ đồ rộng phải tách theo domain hoặc hai panel có tên; không thu nhỏ chữ dưới ngưỡng đọc được trên A4.
- Mỗi route Admin phải được kiểm tra dữ liệu, thao tác chính và authorization; `body visible` chỉ là smoke evidence.
- E2E Admin phải dùng backend LuxeStay và fixture/credential riêng, không dùng dịch vụ khác đang chiếm cổng `8080`.
- Yêu cầu ngoài D01-D08 chỉ giữ khi cần cho tính trung thực của chức năng/bằng chứng; phần trang trí hoặc ngoài rubric được phép bỏ.

## Constitution Check

| Nguyên tắc | Kiểm tra trước thiết kế | Kết quả |
| --- | --- | --- |
| An toàn chức năng | Chỉ cập nhật tài liệu, pipeline DOCX và audit/test; không sửa code sản phẩm và giữ nguyên thay đổi notification/security/Feature 03 song song. | PASS |
| Hiểu biết toàn diện | Đã đối chiếu source, route, controller, test, THESIS, UML, ERD, API và audit reports. | PASS |
| Tái sử dụng | Dùng lại các artifact docs hiện có và chuẩn hóa capability/evidence record thay vì tạo nguồn sự thật thứ hai. | PASS |
| Validation & Error Handling | Có trạng thái BLOCKED/MISSING/PARTIAL, quy tắc evidence và checklist mâu thuẫn. | PASS |
| Trải nghiệm thực tế | Báo cáo phân biệt code-only, historical và implemented; không biến mockup/roadmap thành tính năng. | PASS |
| Kiểm định & xác minh | Có quickstart cho Maven, frontend, Admin E2E cô lập, traceability, raster/Word QA; kết quả bị chặn phải ghi rõ. | PASS |
| Ghi chép | Mỗi lần cập nhật ghi ngày, phạm vi ảnh hưởng, artifact đã kiểm tra và bằng chứng. | PASS |

Re-check sau thiết kế: Không có vi phạm cần ghi vào Complexity Tracking. D01-D08 đã có đủ; rủi ro còn lại là môi trường Admin E2E và kiểm tra tương thích Microsoft Word, đều có gate và trạng thái BLOCKED rõ ràng.

## Project Structure

### Documentation artifacts

- .specify/Feature-04-Thesis-Report-Maintenance/spec.md: yêu cầu và tiêu chí chấp nhận.
- .specify/Feature-04-Thesis-Report-Maintenance/plan.md: kế hoạch triển khai quy trình.
- .specify/Feature-04-Thesis-Report-Maintenance/research.md: quyết định và căn cứ.
- .specify/Feature-04-Thesis-Report-Maintenance/data-model.md: mô hình capability/evidence/section/rubric.
- .specify/Feature-04-Thesis-Report-Maintenance/contracts/: contract cho record, update và validation.
- .specify/Feature-04-Thesis-Report-Maintenance/quickstart.md: hướng dẫn kiểm tra end-to-end.
- .specify/Feature-04-Thesis-Report-Maintenance/report-outline.md: dàn ý 13 biểu mẫu + 5 chương.
- .specify/Feature-04-Thesis-Report-Maintenance/rubric-response-guide.md: khung trả lời rubric.
- docs/THESIS_REPORT_PLAN.md: entrypoint cho người viết báo cáo.
- docs/RUBRIC_RESPONSE_GUIDE.md: entrypoint rubric dành cho buổi bảo vệ.
- docs/audit/ADMIN_FUNCTIONAL_VERIFICATION_PLAN.md: ma trận route/chức năng Admin và backlog hoàn thiện.
- docs/audit/THESIS_DIAGRAM_COVERAGE.md: ánh xạ capability -> loại sơ đồ -> figure/panel.
- docs/tools/render_diagrams_png.js: raster SVG bằng Chromium thành PNG độ phân giải cao.
- docs/tools/build_thesis_docx.py: lắp ráp DOCX với PNG tương thích Word, caption và alt text.

### Source of truth

- docs/THESIS.md: nội dung khóa luận chính.
- docs/THESIS_FORMAT_RULES.md: định dạng và cấu trúc bắt buộc.
- docs/UML.md: mã nguồn sơ đồ chi tiết.
- docs/ERD.md: mô hình dữ liệu và migration notes.
- docs/API_SPEC.md: contract API.
- docs/FEATURE_SUMMARY.md: tổng hợp capability và giới hạn.
- docs/audit/: báo cáo đối chiếu, test baseline và truy vết.
- backend/src/main/, backend/src/test/, frontend/src/app/, frontend/e2e/: bằng chứng thực thi.

### Structure decision

Giữ mô hình web application hiện có gồm backend, frontend và docs. Feature 04 không tạo project runtime mới; nó thêm lớp tài liệu có cấu trúc để cập nhật cùng thay đổi code.

## Implementation Phases

### Phase 0 - Research and baseline

1. Chốt thứ tự ưu tiên nguồn sự thật và quy tắc gắn ngày cho bằng chứng.
2. Đối chiếu các capability hiện tại với route, controller, service, entity, migration và test.
3. Lập danh sách chức năng COMPLETE/PARTIAL/MISSING/BLOCKED/DEFERRED; giữ các báo cáo lịch sử nhưng không dùng làm kết luận hiện hành.
4. Đối chiếu D01-D08 và phân loại từng yêu cầu theo scope gate.
5. Inventory toàn bộ route Admin, guard/permission, component, service/API và test hiện có.

### Phase 1 - Design artifacts

1. Chuẩn hóa mô hình dữ liệu của capability, evidence, diagram, section, rubric và template slot.
2. Định nghĩa contract cho một capability record, một change impact record và một rubric response.
3. Lập dàn ý năm chương, bảng hình, bảng và screenshot cần bổ sung.
4. Chọn Mermaid/PlantUML theo cú pháp đang có; mỗi sơ đồ phải có mục đích, mô tả, phân tích, kết luận, caption, alt text và coverage record.
5. Thiết kế Figure Asset contract: source SVG, PNG nhúng, kích thước, DPI, panel, Word/render status.
6. Thiết kế Admin Verification contract và completion backlog cho mọi route/gap.
7. Viết quickstart để lặp lại việc kiểm tra mà không phụ thuộc trí nhớ cá nhân.

### Phase 2 - Report assembly

1. Cập nhật UML/ERD/API trước khi sửa nội dung THESIS.
2. Cập nhật Chapter 1-5 theo capability đã xác minh.
3. Raster toàn bộ sơ đồ bằng Chromium thành PNG; tách các sơ đồ quá rộng thành domain/panel có tên.
4. Bổ sung screenshot không chứa dữ liệu nhạy cảm; ghi vai trò và ngữ cảnh màn hình.
5. Ghép các biểu mẫu hành chính theo 13 vị trí; mục chưa phát sinh dùng trạng thái chờ thay vì xóa âm thầm.
6. Nhúng PNG, caption và alt text; không patch media Word thành SVG-only.
7. Thêm tài liệu tham khảo có thật và phụ lục bằng chứng khi cần.

### Phase 3 - Rubric and release QA

1. Duy trì mapping 14/14 tiêu chí D03/D04 tới mục báo cáo, source/test/screenshot, giới hạn và câu trả lời vấn đáp.
2. Chạy backend test, frontend unit/build và audit tĩnh toàn bộ Admin route.
3. Thiết lập backend LuxeStay cô lập với fixture/credential E2E, sau đó kiểm tra data load, mutation và permission cho từng chức năng Admin.
4. Ghi mọi gap vào completion backlog với tiêu chí chấp nhận và test cần bổ sung.
5. Xuất DOCX/PDF, audit package chỉ chứa PNG cho hình nhúng, kiểm tra Microsoft Word và render PNG từng trang.
6. Chốt bản phát hành cùng manifest nguồn, ngày xác minh, Admin coverage và figure coverage.

## Deliverables

- Bộ artifact Spec Kit trong thư mục Feature-04.
- Kế hoạch báo cáo dễ đọc trong docs/THESIS_REPORT_PLAN.md.
- Hướng dẫn rubric trong docs/RUBRIC_RESPONSE_GUIDE.md.
- Ma trận Admin đầy đủ và kế hoạch hoàn thiện trong docs/audit/ADMIN_FUNCTIONAL_VERIFICATION_PLAN.md.
- Ma trận coverage sơ đồ và bộ PNG tương thích Word trong docs/audit/THESIS_DIAGRAM_COVERAGE.md và docs/thesis-assets/diagrams/png/.
- Bảng manifest cuối bản nộp gồm version, ngày xác minh, test command và artifact hash nếu cần.

## Update Trigger Matrix

| Thay đổi | Artifact bắt buộc kiểm tra | Kiểm tra cuối |
| --- | --- | --- |
| Route/menu/actor/permission | FEATURE_TRACEABILITY_MATRIX, UML Use Case, API_SPEC, Chapter 3-4, screenshot | route scan + authorization test |
| Entity/migration/inventory | ERD, data-model, API_SPEC, Class/Sequence/Activity, Chapter 3-4 | migration/test + ERD review |
| Booking/payment/refund | capability matrix, sequence/activity, Chapter 3-4-5, rubric evidence | service/integration test + ledger/idempotency evidence |
| UI flow/screenshot | THESIS screen section, caption/table index, rubric evidence | manual browser check + privacy scrub |
| Diagram source/layout | diagram coverage, PNG asset, caption/alt text, Chapter 3 | Chromium raster + Word/render visual QA |
| Admin route/component/API | Admin verification matrix, Chapter 4, test evidence, completion backlog | data-backed read + mutation + permission test |
| D01-D08/template/rubric | scope decision, report outline, whole-document manifest | checksum + template/rubric mapping review |
| Test/config/security | BASELINE_TEST_REPORT, Chapter 4.8-4.10, limitation/risk notes | rerun or label BLOCKED |
| New or removed feature | FEATURE_SUMMARY, roadmap/limitations, UML/API/ERD as applicable | convergence checklist |

## Complexity Tracking

Không có vi phạm hiến pháp cần biện minh. Việc duy trì SVG source, PNG nhúng và DOCX/PDF là cần thiết: SVG phục vụ diff/tái sinh, PNG bảo đảm tương thích Word, còn DOCX/PDF đáp ứng định dạng nộp.
