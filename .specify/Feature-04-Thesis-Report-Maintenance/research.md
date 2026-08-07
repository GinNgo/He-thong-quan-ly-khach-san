# Research: Duy trì báo cáo khóa luận

## Quyết định 1: Nguồn sự thật theo thứ tự ưu tiên

Decision: Dùng thứ tự mã nguồn và migration hiện hành -> kiểm thử hiện hành -> FEATURE_SUMMARY -> API_SPEC/ERD/UML -> báo cáo lịch sử -> PROJECT_CONTEXT/FEATURE_ROADMAP.

Rationale: Báo cáo phải phản ánh chức năng đã chạy được, không chỉ phản ánh ý tưởng hoặc giao diện. Thứ tự này đã được ghi trong docs/README.md và phù hợp với yêu cầu trung thực của spec.

Alternatives considered: Dùng THESIS.md làm nguồn cao nhất; bị loại vì THESIS.md là đầu ra cần được cập nhật và có thể chứa kết quả lịch sử.

## Quyết định 2: Bộ trạng thái capability

Decision: Mỗi capability dùng một trong COMPLETE, PARTIAL, MISSING, BLOCKED, DEFERRED.

Rationale:

- COMPLETE: có source/contract và bằng chứng test hoặc lý do kiểm thử được phê duyệt.
- PARTIAL: có một phần hành trình, còn thiếu UI, permission, integration, test hoặc vận hành.
- MISSING: chưa có implementation/contract đáng tin cậy.
- BLOCKED: có ý định hoặc code nhưng việc xác minh bị chặn bởi môi trường, quyền truy cập hoặc lỗi cấu hình.
- DEFERRED: chủ động loại khỏi phiên bản hiện tại và ghi vào giới hạn/roadmap.

Alternatives considered: Chỉ dùng Done/Not done; bị loại vì không phân biệt được tính năng chưa làm, làm một phần và không thể kiểm tra.

## Quyết định 3: Record có thể truy vết

Decision: Mỗi capability record phải có mã ổn định, actor, UI/route, API, service, dữ liệu, trạng thái, evidence, ngày xác minh và tác động tới report.

Rationale: Khi source thay đổi, mã capability là khóa để tìm các đoạn văn, sơ đồ, screenshot và tiêu chí rubric bị ảnh hưởng.

Alternatives considered: Tìm kiếm theo tên tự do; bị loại vì tên có thể thay đổi và tạo orphan documentation.

## Quyết định 4: UML/ERD dùng source văn bản, DOCX dùng PNG

Decision: Duy trì Mermaid/PlantUML/SVG trong docs/UML.md, docs/ERD.md và docs/thesis-assets/diagrams/; raster bằng Chromium thành PNG độ phân giải cao và chỉ nhúng PNG vào DOCX.

Rationale: Repo đã có nhiều sơ đồ và source văn bản giúp diff/cập nhật. Thử nghiệm thực tế cho thấy patch media Word sang SVG-only có thể tạo khung ảnh rỗng trên một số phiên bản Microsoft Word; PNG là lựa chọn tương thích ổn định hơn. Chromium giữ được nhãn `foreignObject` mà một số bộ chuyển đổi ảnh khác làm mất.

Alternatives considered: Vẽ trực tiếp trong Word, bị loại vì khó review và tái tạo; nhúng SVG-only, bị loại vì lỗi tương thích Word; chuyển SVG bằng renderer không hỗ trợ `foreignObject`, bị loại vì mất nhãn.

## Quyết định 5: Thứ tự toàn văn

Decision: Gói nộp có 13 nhóm theo thứ tự người dùng cung cấp. Các nhóm “nếu có” được đánh dấu tình trạng, không tự ý bỏ khỏi checklist.

Rationale: Bản điện tử phải đáp ứng cả nội dung học thuật và thủ tục hành chính. Placeholder chỉ được dùng trong bản nháp và phải thay bằng mẫu/biên bản chính thức trước khi nộp.

## Quyết định 6: D01-D08 là scope gate và rubric chính thức

Decision: Dùng tám file D01-D08 đã tải và checksum làm nguồn biểu mẫu/rubric. Mỗi yêu cầu được phân loại `REQUIRED_BY_D01_D08`, `REQUIRED_FOR_TRUTHFUL_EVIDENCE` hoặc `OPTIONAL_SKIP`. D03/D04 được mapping 14/14 dòng vào ma trận rubric và hướng dẫn vấn đáp.

Rationale: Scope gate ngăn báo cáo phình to bởi yêu cầu trang trí hoặc chức năng không có trong dự án, nhưng vẫn giữ các artifact cần thiết để chứng minh trung thực chức năng thực tế.

Alternatives considered: Giữ mọi yêu cầu phát sinh, bị loại vì vượt phạm vi; chỉ giữ nội dung ghi nguyên văn trong mẫu, bị loại vì không đủ bằng chứng kỹ thuật cho chức năng thực tế.

## Quyết định 7: Kiểm thử và kết quả lịch sử

Decision: Chapter 4 ghi tách ba nhãn CURRENT, HISTORICAL, BLOCKED. Mỗi số liệu kèm ngày, lệnh và nguồn.

Rationale: BASELINE_TEST_REPORT ghi backend 60/60 ở thời điểm cũ nhưng frontend Vitest/Playwright bị lỗi cấu hình; FEATURE_SUMMARY có thể chứa số cũ. Không được trình bày số lịch sử như kết quả hiện tại.

## Quyết định 8: Kiểm tra xuất bản

Decision: Markdown là nguồn review; DOCX/PDF là artifact nộp. Mọi DOCX/PDF phải render thành PNG và kiểm tra từng trang trước khi chốt.

Rationale: Kiểm tra text không phát hiện ảnh bị cắt, caption lệch, font lỗi hoặc mục lục sai. Quy trình documents skill yêu cầu render -> inspect -> iterate.

## Quyết định 9: Privacy gate

Decision: Trước khi xuất bản phải quét secret, credential, đường dẫn máy cục bộ, email/số điện thoại thật và dữ liệu khách hàng không cần thiết; screenshot phải dùng dữ liệu demo.

Rationale: Báo cáo điện tử có thể được chia sẻ ngoài máy phát triển. Privacy gate cũng phù hợp với Constitution mục 5 và FR-014.

## Quyết định 10: Coverage sơ đồ theo capability

Decision: Mỗi capability đã triển khai phải có Use Case và được đánh giá áp dụng cho Class, Sequence, Activity, ERD. Trường hợp không áp dụng phải ghi lý do, không để ô trống. Sơ đồ quá rộng được tách theo bounded domain hoặc hai panel `(a)/(b)` có tên riêng.

Rationale: Một bộ sơ đồ “có đủ loại” nhưng không bao phủ chức năng vẫn không đáp ứng yêu cầu luận văn. Coverage matrix làm rõ chức năng nào xuất hiện ở hình nào và ngăn bỏ sót khi code thay đổi.

Alternatives considered: Một sơ đồ tổng quát rất lớn cho mỗi loại, bị loại vì chữ không đọc được trên A4 và khó đối chiếu source.

## Quyết định 11: Caption và accessibility là contract phát hành

Decision: Mỗi hình có figureId, caption duy nhất, alt text, tên source/png, panel và tham chiếu trong văn bản. Caption tách panel dùng `Hình x.y(a)` và `Hình x.y(b)`.

Rationale: Người đọc cần nhận biết hình trong Word, mục lục hình và khi trích dẫn; alt text cũng giúp kiểm tra package và accessibility có thể tự động hóa.

Alternatives considered: Dùng tên file làm chú thích, bị loại vì không diễn đạt nghiệp vụ và không ổn định khi đổi asset.

## Quyết định 12: Admin phải được xác minh theo hành trình, không theo shell

Decision: Audit 100% route Admin bằng record gồm route/menu, role, permission, component, API, data-load, mutation/action, unit test, E2E, status, blocker và completion task. `PASS` cần bằng chứng dữ liệu và thao tác/authorization; `body visible` chỉ là smoke.

Rationale: Các E2E hiện hữu chủ yếu kiểm tra trang hiển thị và có assertion không kiểm chứng hành vi. Một route có thể tải shell nhưng API, permission hoặc mutation vẫn hỏng.

Alternatives considered: Dùng route inventory hoặc component unit test làm bằng chứng duy nhất, bị loại vì không chứng minh integration end-to-end.

## Quyết định 13: Cô lập môi trường Admin E2E

Decision: Không dùng cổng `8080` hiện tại vì đang thuộc dịch vụ Docker khác. Admin E2E chỉ chạy sau khi LuxeStay backend được khởi động ở cổng riêng với profile E2E, SQL Server/fixture và bộ credential `LUXESTAY_E2E_*` đầy đủ.

Rationale: Frontend gọi nhầm backend tạo lỗi `Failed to fetch` không phản ánh trạng thái chức năng LuxeStay. Cô lập cổng và fixture giúp kết quả tái lập, không phá dịch vụ ngoài phạm vi.

Alternatives considered: Dừng container đang chiếm cổng, bị loại vì đó là tài nguyên ngoài phạm vi; coi lỗi fetch là fail chức năng, bị loại vì kết luận sai nguyên nhân.

## Câu hỏi còn mở

1. Cần xác nhận trường/giảng viên có yêu cầu Abstract tiếng Anh ngoài nội dung D07 hay không; hiện scope là `NOT_APPLICABLE`.
2. Các biên bản/phiếu nhận xét cần bản ký chính thức trước FINAL; bản REVIEW tiếp tục giữ slot có trạng thái.
3. Admin E2E còn bị chặn tới khi có bộ credential fixture và một cổng backend LuxeStay riêng.
4. Microsoft Word cần được dùng để xác nhận bản REVIEW cuối; render LibreOffice chỉ là QA bổ sung.
