# Feature Specification: Báo cáo khóa luận có thể cập nhật

**Feature Branch**: `codex/thesis-report-maintenance`

**Created**: 2026-07-28

**Status**: Ready for Planning

**Input**: User description: "Lập kế hoạch hoàn thiện file thesis trong thư mục docs bằng Spec Kit; báo cáo phải cập nhật được khi thêm hoặc thay đổi chức năng, phản ánh đúng chức năng thực tế, có Use Case, Class, Sequence, Activity Diagram, tuân thủ biểu mẫu khóa luận và hướng dẫn trả lời rubric môn học chi tiết."

## User Scenarios & Testing

### User Story 1 - Đồng bộ báo cáo với hệ thống thực tế (Priority: P1)

Sinh viên có thể cập nhật báo cáo sau mỗi thay đổi chức năng mà không phải rà soát thủ công toàn bộ dự án, đồng thời phân biệt rõ chức năng đã hoàn thành, hoàn thành một phần, bị chặn, còn thiếu hoặc được hoãn.

**Why this priority**: Giá trị học thuật của báo cáo phụ thuộc trước hết vào tính trung thực và khả năng truy vết từ nội dung trình bày tới mã nguồn, dữ liệu và bằng chứng kiểm thử hiện hành.

**Independent Test**: Chọn một chức năng vừa thay đổi, thực hiện quy trình cập nhật và xác minh rằng nội dung liên quan trong ma trận chức năng, UML, API/ERD và luận văn đều được cập nhật hoặc được ghi rõ là không bị ảnh hưởng.

**Acceptance Scenarios**:

1. **Given** một chức năng đã có route, API hoặc mô hình dữ liệu thay đổi, **When** sinh viên chạy quy trình cập nhật báo cáo, **Then** mọi mô tả, sơ đồ và bằng chứng liên quan được rà soát theo cùng một mã chức năng.
2. **Given** một chức năng chỉ tồn tại trong roadmap hoặc mockup, **When** đối chiếu với source và test, **Then** báo cáo không mô tả chức năng đó là đã hoàn thành.
3. **Given** kết quả kiểm thử cũ mâu thuẫn với worktree hiện tại, **When** tổng hợp kết quả, **Then** kết quả cũ được ghi là bằng chứng lịch sử và không được dùng làm kết luận hiện hành.

---

### User Story 2 - Duy trì bộ UML đúng với cài đặt (Priority: P1)

Sinh viên có thể tạo và cập nhật Use Case, Class, Sequence, Activity Diagram cho các phân hệ cốt lõi, với nội dung đủ để giảng viên hiểu mục đích, luồng nghiệp vụ, đối tượng tham gia và kết luận thiết kế.

**Why this priority**: UML là phần bắt buộc của báo cáo và là bằng chứng trực tiếp cho năng lực phân tích, thiết kế hệ thống.

**Independent Test**: Chọn một phân hệ cốt lõi, đối chiếu actor, route, API, service và entity; sơ đồ phải khớp với contract hiện hành và có phần mục đích, mô tả, phân tích, kết luận.

**Acceptance Scenarios**:

1. **Given** một phân hệ được đánh dấu đã triển khai, **When** mở bộ UML, **Then** sơ đồ dùng đúng actor, trạng thái, endpoint và quan hệ dữ liệu hiện có.
2. **Given** một nghiệp vụ được hoãn như mixed RoomType, customer add-on services hoặc review, **When** đọc sơ đồ, **Then** nghiệp vụ đó được ghi là phạm vi tương lai, không xuất hiện như luồng hoàn thành.
3. **Given** một sơ đồ được đưa vào luận văn, **When** kiểm tra phần văn bản đi kèm, **Then** sơ đồ có chú thích hình và đủ mục đích, mô tả, phân tích, kết luận.

---

### User Story 3 - Hoàn thiện toàn văn theo biểu mẫu khóa luận (Priority: P2)

Sinh viên có thể lắp ráp toàn văn điện tử theo đúng thứ tự biểu mẫu của trường, giữ các trang hành chính ở đúng vị trí và hoàn thiện nội dung học thuật từ tóm tắt đến phụ lục.

**Why this priority**: Báo cáo đúng nội dung nhưng sai thứ tự biểu mẫu hoặc sai định dạng vẫn có thể bị trừ điểm hoặc bị yêu cầu chỉnh sửa trước khi nộp.

**Independent Test**: Kiểm tra mục lục gói nộp và xác nhận đủ 13 vị trí theo thứ tự quy định; mục chưa có dữ liệu được đánh dấu rõ là chờ bổ sung, không bị âm thầm bỏ qua.

**Acceptance Scenarios**:

1. **Given** các biểu mẫu hành chính và nội dung luận văn, **When** lắp ráp bản điện tử, **Then** các phần xuất hiện đúng thứ tự từ tờ bìa tới phụ lục.
2. **Given** một biểu mẫu chưa phát sinh như biên bản chỉnh sửa, **When** chuẩn bị bản nháp, **Then** trạng thái của biểu mẫu được ghi rõ và chỉ loại khỏi bản nộp khi quy định cho phép.
3. **Given** báo cáo được xuất sang Word hoặc PDF, **When** kiểm tra hình thức, **Then** khổ giấy, lề, font, cỡ chữ, giãn dòng, số trang, bảng, hình và chú thích tuân thủ quy định đã ban hành.

---

### User Story 4 - Trả lời rubric bằng bằng chứng (Priority: P2)

Sinh viên có thể chuẩn bị câu trả lời cho từng tiêu chí rubric bằng cấu trúc nhất quán: yêu cầu của tiêu chí, nội dung dự án đáp ứng, bằng chứng, giới hạn và cách trình bày khi bảo vệ.

**Why this priority**: Rubric cần được dùng như ma trận kiểm chứng, không phải danh sách câu trả lời chung chung hoặc khẳng định vượt quá mức hệ thống đã đạt.

**Independent Test**: Chọn bất kỳ tiêu chí rubric nào và xác minh câu trả lời có liên kết đến mục báo cáo, source/test/screenshot phù hợp và có cách trả lời ngắn khi vấn đáp.

**Acceptance Scenarios**:

1. **Given** rubric môn học và rubric khóa luận, **When** lập ma trận trả lời, **Then** 100% tiêu chí có vị trí trong báo cáo, bằng chứng và trạng thái chuẩn bị.
2. **Given** tiêu chí yêu cầu chức năng chưa hoàn thiện, **When** trả lời, **Then** sinh viên trình bày đúng giới hạn, nguyên nhân, rủi ro và hướng phát triển thay vì nhận là đã hoàn thành.
3. **Given** giảng viên hỏi truy vấn sâu, **When** đối chiếu ma trận, **Then** sinh viên có thể chỉ ra actor, luồng, lớp, API, dữ liệu và test liên quan trong thời gian ngắn.

---

### User Story 5 - Xác minh toàn bộ chức năng quản trị (Priority: P1)

Sinh viên có thể chứng minh trạng thái thực tế của từng route và thao tác trong khu vực Admin, thay vì chỉ dựa vào việc trang mở được hoặc giao diện có hiển thị. Mọi chức năng chưa chạy đủ luồng đọc, tạo, cập nhật, xóa/hành động nghiệp vụ và phân quyền phải có trạng thái, nguyên nhân và kế hoạch hoàn thiện.

**Why this priority**: Khu vực Admin bao phủ nhiều nghiệp vụ cốt lõi và là bằng chứng trực tiếp cho phần cài đặt, kiểm thử và rubric. Một trang hiển thị được nhưng gọi sai API, thiếu quyền hoặc mutation lỗi không thể được ghi là hoàn thành.

**Independent Test**: Chọn bất kỳ route Admin nào và tìm được actor, permission, component, API, các thao tác cần kiểm tra, bằng chứng unit/E2E, trạng thái hiện tại, blocker và task hoàn thiện nếu còn thiếu.

**Acceptance Scenarios**:

1. **Given** một route Admin tồn tại, **When** thực hiện audit, **Then** route đó được ánh xạ tới menu, role, permission, component, API và danh sách thao tác đọc/mutation thực tế.
2. **Given** trang Admin chỉ render khung hoặc chỉ vượt qua kiểm tra `body visible`, **When** đánh giá trạng thái, **Then** chức năng không được gắn `COMPLETE` nếu dữ liệu, mutation và authorization chưa được xác minh.
3. **Given** môi trường LuxeStay không thể chạy do xung đột cổng, thiếu credential hoặc thiếu fixture, **When** ghi kết quả, **Then** route được gắn `BLOCKED`, nêu đúng blocker và có bước thiết lập môi trường cô lập để kiểm tra lại.
4. **Given** một chức năng Admin thiếu hoặc lỗi, **When** kết thúc audit, **Then** có task hoàn thiện cụ thể kèm file/phân hệ, tiêu chí chấp nhận và test cần bổ sung.

### Edge Cases

- Biểu mẫu hoặc rubric trên Google Drive chưa thể truy cập/đọc đầy đủ tại thời điểm lập kế hoạch.
- Source code thay đổi nhưng test chưa chạy lại hoặc môi trường kiểm thử bị chặn.
- Tài liệu lịch sử ghi nhận số test hoặc trạng thái chức năng khác với worktree hiện hành.
- Route hiển thị trên giao diện nhưng API, permission, dữ liệu hoặc callback chưa hoàn thành hành trình.
- API tồn tại nhưng không có route/UI cho actor được mô tả trong báo cáo.
- Một sơ đồ quá rộng khi đưa vào trang A4 hoặc chứa tên lớp/quan hệ đã đổi.
- Word hiển thị khung rỗng khi DOCX nhúng SVG trực tiếp dù kiểm tra cấu trúc gói tin vẫn pass.
- Sơ đồ dài hoặc quá rộng vẫn đọc được trên màn hình nhưng chữ trở nên quá nhỏ khi đặt trên trang A4.
- Một capability xuất hiện trong báo cáo nhưng chưa được ánh xạ đủ loại sơ đồ cần thiết hoặc chưa có lý do `NOT_APPLICABLE`.
- Một route Admin tải được shell nhưng request dữ liệu hoặc thao tác tạo/sửa/xóa bị lỗi.
- Frontend đang trỏ tới dịch vụ khác chiếm cùng cổng backend, làm kết quả E2E sai lệch so với LuxeStay.
- Biểu mẫu hành chính chưa có chữ ký, điểm hoặc nhận xét ở giai đoạn bản nháp.
- Báo cáo có thông tin tài khoản, secret, đường dẫn máy cá nhân hoặc dữ liệu nhận dạng không phù hợp để nộp.

## Requirements

### Functional Requirements

- **FR-001**: Bộ báo cáo MUST sử dụng thứ tự nguồn sự thật: source/migration hiện hành; kiểm thử hiện hành; tổng hợp chức năng mới nhất; API/ERD/UML; báo cáo lịch sử; bối cảnh và roadmap.
- **FR-002**: Mỗi chức năng được nêu trong luận văn MUST có mã truy vết, actor, UI/route, API, service, dữ liệu, trạng thái, bằng chứng, ngày xác minh và tác động tới báo cáo.
- **FR-003**: Trạng thái chức năng MUST dùng một bộ giá trị thống nhất gồm `COMPLETE`, `PARTIAL`, `MISSING`, `BLOCKED` và `DEFERRED`; chỉ `COMPLETE` khi có bằng chứng hiện hành phù hợp.
- **FR-004**: Báo cáo MUST giữ cấu trúc năm chương bắt buộc và bổ sung đầy đủ tóm tắt, abstract khi dùng, tài liệu tham khảo và phụ lục theo mẫu trường.
- **FR-005**: Gói toàn văn điện tử MUST sắp xếp 13 nhóm biểu mẫu theo đúng thứ tự người dùng cung cấp, từ tờ bìa đến phụ lục.
- **FR-006**: Bộ UML MUST có Use Case, Activity, Sequence, Class và ERD cho các phân hệ cốt lõi; mỗi hình MUST có mục đích, mô tả, phân tích và kết luận.
- **FR-007**: Use Case MUST phản ánh đúng actor và quyền thực tế; Class Diagram MUST phản ánh lớp/entity/quan hệ hiện hành; Sequence và Activity Diagram MUST phản ánh đúng contract, nhánh lỗi và trạng thái nghiệp vụ.
- **FR-008**: Các nghiệp vụ chưa có contract đầy đủ như mixed RoomType booking, customer add-on services và customer reviews MUST được trình bày là giới hạn hoặc hướng phát triển, không là chức năng hoàn thành.
- **FR-009**: Chương cài đặt và kiểm thử MUST phân biệt kết quả vừa chạy, kết quả lịch sử và kết quả bị chặn; mọi số liệu test MUST ghi ngày, lệnh hoặc nguồn bằng chứng.
- **FR-010**: Mỗi màn hình minh họa MUST có ảnh, mô tả màn hình, chức năng và vai trò sử dụng; mọi hình và bảng MUST được tham chiếu trong nội dung.
- **FR-011**: Ma trận rubric MUST ánh xạ từng tiêu chí tới câu trả lời, mục báo cáo, bằng chứng, giới hạn, câu trả lời vấn đáp ngắn và trạng thái sẵn sàng.
- **FR-012**: Quy trình cập nhật MUST bắt đầu từ thay đổi source/feature, cập nhật ERD/UML/API trước THESIS, sau đó cập nhật bằng chứng kiểm thử và kiểm tra định dạng đầu ra.
- **FR-013**: Báo cáo MUST tuân thủ tiếng Việt học thuật, A4, lề trái 3 cm, phải 2 cm, trên 2 cm, dưới 2 cm, Times New Roman 13, giãn dòng 1,5 và số trang giữa chân trang, trừ khi mẫu chính thức quy định khác.
- **FR-014**: Bản nộp MUST loại bỏ secret, credential, dữ liệu cá nhân không cần thiết và đường dẫn máy cục bộ.
- **FR-015**: Hệ thống tài liệu MUST giữ báo cáo lịch sử nhưng MUST không dùng báo cáo lịch sử làm nguồn kết luận khi mâu thuẫn với bằng chứng hiện hành.
- **FR-016**: Mỗi thay đổi báo cáo MUST ghi ngày cập nhật, phạm vi ảnh hưởng, chức năng liên quan và bằng chứng đã kiểm tra.
- **FR-017**: DOCX phát hành MUST nhúng PNG độ phân giải cao làm ảnh chính cho sơ đồ; SVG MUST chỉ được giữ làm source bên ngoài DOCX và không được thay thế media PNG trong gói Word.
- **FR-018**: Mỗi hình MUST có tên/caption duy nhất, alt text, mã figure ổn định và ít nhất một tham chiếu trong nội dung; các panel tách MUST dùng hậu tố `(a)`, `(b)` và tên riêng rõ nghĩa.
- **FR-019**: Mọi capability được mô tả là đã triển khai MUST được ánh xạ tới Use Case và, khi phù hợp, Class, Sequence, Activity và ERD; loại sơ đồ không áp dụng MUST có lý do `NOT_APPLICABLE`.
- **FR-020**: Sơ đồ quá rộng hoặc quá dài MUST được tách theo bounded domain hoặc thành tối đa hai panel có tên; không được giảm chữ đến mức khó đọc chỉ để vừa một trang.
- **FR-021**: Mỗi yêu cầu đưa vào báo cáo MUST được phân loại `REQUIRED_BY_D01_D08`, `REQUIRED_FOR_TRUTHFUL_EVIDENCE` hoặc `OPTIONAL_SKIP`; yêu cầu `OPTIONAL_SKIP` MAY được bỏ khỏi bản nộp khi có lý do phạm vi.
- **FR-022**: Audit Admin MUST bao phủ 100% route hiện hành và ghi route, menu, role, permission, component, API, thao tác đọc, mutation/hành động, unit test, E2E, trạng thái, blocker và task hoàn thiện.
- **FR-023**: Trạng thái Admin MUST dùng `PASS`, `PARTIAL`, `FAIL`, `BLOCKED` hoặc `NOT_APPLICABLE`; `PASS` chỉ được dùng khi dữ liệu thật/fixture tải được, thao tác chính và authorization đã được xác minh phù hợp với chức năng.
- **FR-024**: Kiểm tra chỉ xác nhận trang hoặc `body` hiển thị MUST không được dùng làm bằng chứng duy nhất cho một chức năng Admin hoàn thành.
- **FR-025**: Mọi gap Admin MUST có completion task nêu phạm vi sửa, dependency, tiêu chí chấp nhận, test unit/integration/E2E và mức ưu tiên.
- **FR-026**: E2E Admin MUST chạy trên môi trường LuxeStay cô lập, đúng backend/database/fixture/credential; xung đột cổng hoặc thiếu cấu hình MUST được ghi `BLOCKED`, không được diễn giải thành pass hoặc fail chức năng.
- **FR-027**: Bản DOCX đầy đủ MUST bao phủ tất cả chức năng và flow thực tế đã xác minh trong dự án, đồng thời chỉ đưa chức năng chưa hoàn thiện vào phần giới hạn hoặc kế hoạch hoàn thiện.

### Key Entities

- **Report Capability**: Một chức năng/luồng được đưa vào báo cáo, gồm mã, actor, phạm vi, trạng thái và liên kết tới bằng chứng.
- **Evidence Record**: Bằng chứng source, test, ảnh, log hoặc tài liệu có ngày xác minh và độ tin cậy.
- **Diagram Specification**: Mô tả một UML/ERD, loại sơ đồ, phân hệ, nguồn đối chiếu, caption và phần phân tích bắt buộc.
- **Thesis Section**: Một mục báo cáo có mục tiêu, nội dung, bảng/hình, nguồn tham khảo và các capability liên quan.
- **Rubric Criterion**: Tiêu chí chấm với trọng số, câu trả lời, vị trí minh chứng, giới hạn và trạng thái chuẩn bị.
- **Template Slot**: Một phần trong thứ tự 13 biểu mẫu, gồm trạng thái bắt buộc/tùy chọn, nguồn mẫu và tình trạng hoàn thiện.
- **Documentation Change**: Một thay đổi chức năng làm phát sinh danh sách artifact cần cập nhật và kiểm tra lại.
- **Figure Asset**: Ảnh PNG nhúng trong DOCX cùng source SVG, kích thước, mật độ điểm ảnh, caption, alt text, panel và kết quả kiểm tra hiển thị.
- **Admin Verification Record**: Kết quả xác minh một route/chức năng Admin gồm role, permission, component, API, thao tác, test, trạng thái, blocker và task hoàn thiện.
- **Scope Decision**: Quyết định giữ hoặc bỏ một yêu cầu dựa trên D01-D08 hoặc nhu cầu bằng chứng trung thực.

## Success Criteria

### Measurable Outcomes

- **SC-001**: 100% route và mục menu hiện hành được ánh xạ tới actor, chức năng, API/dependency và trạng thái báo cáo.
- **SC-002**: 100% chức năng được ghi `COMPLETE` có ít nhất một bằng chứng source và một bằng chứng kiểm chứng hiện hành hoặc lý do rõ ràng khi không thể tự động kiểm thử.
- **SC-003**: 100% sơ đồ được đưa vào bản nộp có caption, tham chiếu trong văn bản và đủ bốn phần mục đích, mô tả, phân tích, kết luận.
- **SC-004**: 100% tiêu chí rubric được ánh xạ sau khi có bản rubric xuất được; không còn tiêu chí không có bằng chứng hoặc kế hoạch trả lời.
- **SC-005**: Gói toàn văn có đủ 13 vị trí theo thứ tự quy định; mọi vị trí chưa hoàn thiện được đánh dấu rõ trước khi xuất bản nộp.
- **SC-006**: Không còn mâu thuẫn chưa giải thích giữa THESIS, UML, API, ERD, source và kết quả kiểm thử tại thời điểm chốt bản.
- **SC-007**: Bản luận văn đạt phạm vi 50-100 trang, không tính bìa và phụ lục, sau khi lắp ráp theo mẫu chính thức.
- **SC-008**: Một thay đổi chức năng điển hình có thể được truy vết và cập nhật toàn bộ artifact liên quan trong một phiên làm việc không quá 60 phút, chưa tính thời gian chạy test dài.
- **SC-009**: Bản Word/PDF cuối không có hình/bảng bị cắt, font sai, caption tách khỏi nội dung hoặc mục lục/số trang lỗi sau bước kiểm tra trực quan.
- **SC-010**: 100% hình trong bản Word REVIEW hiển thị đầy đủ trong Microsoft Word và bản render kiểm tra; không còn biểu tượng ảnh lỗi, khung rỗng hoặc nhãn bị mất.
- **SC-011**: 100% capability đã triển khai có bản ghi diagram coverage; 100% figure có tên duy nhất, caption, alt text và tham chiếu trong văn bản.
- **SC-012**: 100% route Admin được phân loại bằng ma trận xác minh; không còn route chưa có owner, permission/API mapping hoặc trạng thái.
- **SC-013**: 100% chức năng Admin được ghi `PASS` có bằng chứng tải dữ liệu và ít nhất một bằng chứng cho thao tác chính/authorization; không dùng kiểm tra `body visible` làm kết luận duy nhất.
- **SC-014**: 100% gap Admin có task hoàn thiện và tiêu chí kiểm thử; các gap bị chặn môi trường có bước mở khóa có thể thực hiện lại.
- **SC-015**: 100% yêu cầu nội dung ngoài source chức năng được phân loại theo D01-D08 scope gate; bản nộp không chứa mục tùy chọn không có căn cứ hoặc nội dung trang trí không phục vụ rubric/bằng chứng.

## Assumptions

- `docs/THESIS.md` tiếp tục là nguồn nội dung luận văn chính trước khi xuất Word/PDF.
- `docs/ERD.md`, `docs/UML.md`, `docs/API_SPEC.md` và `docs/FEATURE_SUMMARY.md` là các artifact thiết kế trung gian phải được đồng bộ trước luận văn.
- Tám file D01-D08 đã được tải vào `docs/thesis-assets/templates/` và là nguồn mẫu chính thức; nội dung mẫu ưu tiên hơn quy tắc cục bộ nếu có khác biệt.
- Các biểu mẫu chấm điểm, nhận xét và chỉnh sửa có thể để placeholder trong bản soạn thảo nhưng phải thay bằng bản chính thức trước khi nộp.
- Báo cáo dùng tiếng Việt; Abstract tiếng Anh chỉ được thêm khi mẫu hoặc giảng viên yêu cầu.
- Feature này thay đổi tài liệu, pipeline tạo báo cáo và bộ kiểm thử/audit cần thiết. Gap sản phẩm Admin được lập kế hoạch chi tiết nhưng chỉ sửa code sản phẩm trong một task triển khai riêng hoặc khi người dùng yêu cầu rõ.
- Microsoft Word là trình đọc mục tiêu của DOCX; LibreOffice/PDF render là cổng QA bổ sung, không thay thế kiểm tra tương thích Word.
